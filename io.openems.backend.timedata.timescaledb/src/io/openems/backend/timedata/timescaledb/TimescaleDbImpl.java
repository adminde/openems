package io.openems.backend.timedata.timescaledb;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import io.openems.backend.common.component.AbstractOpenemsBackendComponent;
import io.openems.backend.common.debugcycle.DebugLoggable;
import io.openems.backend.common.metadata.Edge;
import io.openems.backend.common.metadata.Metadata;
import io.openems.backend.common.timedata.Timedata;
import io.openems.common.event.EventReader;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jsonrpc.notification.AbstractDataNotification;
import io.openems.common.jsonrpc.notification.AggregatedDataNotification;
import io.openems.common.jsonrpc.notification.ResendDataNotification;
import io.openems.common.jsonrpc.notification.TimestampedDataNotification;
import io.openems.common.timedata.Resolution;
import io.openems.common.types.ChannelAddress;
import io.openems.common.types.EdgeConfig;
import io.openems.shared.timescaledb.data.DataPoint;
import io.openems.shared.timescaledb.schema.Aggregate;
import io.openems.shared.timescaledb.schema.Tenancy;
import io.openems.shared.timescaledb.schema.AggregateChannels;
import io.openems.shared.timescaledb.TimescaleDbConnector;
import io.openems.shared.timescaledb.Type;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "Timedata.TimescaleDB",
		configurationPolicy = ConfigurationPolicy.REQUIRE,
		immediate = true
)
@EventTopics({ //
		Edge.Events.ON_SET_ONLINE //
})
public class TimescaleDbImpl extends AbstractOpenemsBackendComponent implements Timedata, EventHandler, DebugLoggable {

	private final Logger log = LoggerFactory.getLogger(TimescaleDbImpl.class);


	private static final int INIT_RETRY_SECONDS = 10;

	private Config config;
	
	private volatile TimescaleDbConnector connector;
	private volatile boolean active;
	private ScheduledExecutorService initExecutor;
	private TimeFilter timeFilter;
	private ChannelFilter channelFilter;

	private final Map<String, Set<String>> timestampedChannelsForEdge = new ConcurrentHashMap<>();

	/** Edges already reported as having no EdgeConfig, to log each one once. */
	private final Set<String> missingConfigWarned = ConcurrentHashMap.newKeySet();

	@Reference
	private volatile Metadata metadata;

	public TimescaleDbImpl() {
		super("Timedata.TimescaleDB");
	}

	@Override
	public String id() {
		return this.config.id();
	}

	@Activate
	private void activate(Config config) {
		this.config = config;
		this.timeFilter = TimeFilter.from(config.startDate(), config.endDate());
		this.channelFilter = ChannelFilter.from(config.blacklistedChannels(), config.blacklistedChannelIds());
		this.initExecutor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "TimescaleDB-init"));
		this.initExecutor.execute(this::tryInitialize);
		this.active = true;
	}

	private void tryInitialize() {
		if (!this.active) {
			return;
		}
		TimescaleDbConnector connector;
		try {
			connector = new TimescaleDbConnector(Tenancy.MULTI, config.host(), config.username(), config.password()) //
					.database(config.database()) //
					.port(config.port()) //
					.poolSize(config.poolSize()) //
					.writeWorkers(config.writeWorkers()) //
					.rawRetentionDays(config.retentionDays()) //
					.rawCompressionDays(config.compressionDays()) //
					.aggregates(Aggregate.of(Tenancy.MULTI)) //
					.readOnly(config.isReadOnly()) //
					.connect();

		} catch (OpenemsNamedException | RuntimeException e) {
			this.logError(this.log, "TimescaleDB initialization failed; retrying in "
					+ INIT_RETRY_SECONDS + "s: " + e.getMessage());
			try {
				this.initExecutor.schedule(this::tryInitialize, INIT_RETRY_SECONDS, TimeUnit.SECONDS);

			} catch (RejectedExecutionException re) {
				// Do nothing
			}
			return;
		}
		synchronized (this) {
			if (!this.active) {
				connector.deactivate();
				return;
			}
			this.connector = connector;
		}
		this.logInfo(this.log, "TimescaleDB Backend activated and schema applied");
	}

	@Deactivate
	private void deactivate() {
		this.active = false;
		if (this.initExecutor != null) {
			this.initExecutor.shutdownNow();
		}
		TimescaleDbConnector toClose;
		synchronized (this) {
			toClose = this.connector;
			this.connector = null;
		}
		if (toClose != null) {
			toClose.deactivate();
		}
	}

	@Override
	public void write(String edgeId, TimestampedDataNotification notification) {
		this.writeData(edgeId, notification, (edge, channel) -> {
			this.timestampedChannelsForEdge
					.computeIfAbsent(edge, e -> ConcurrentHashMap.newKeySet())
					.add(channel);
			return true;
		});
	}

	@Override
	public void write(String edgeId, AggregatedDataNotification notification) {
		this.writeData(edgeId, notification, (edge, channel) -> !this.isTimestampedChannel(edge, channel));
	}

	@Override
	public void write(String edgeId, ResendDataNotification data) {
		this.writeData(edgeId, data, (edge, channel) -> true);

		// Resend writes into an already materialized region, which the aggregate
		// refresh policies never revisit. Report the range so the tiers get
		// re-materialized; without this the resent points would only ever be
		// visible through the raw hypertables.
		var connector = this.connector;
		if (connector == null) {
			return;
		}
		// TreeBasedTable keeps the row keys sorted, so first/last are the range
		var timestamps = data.getData().rowKeySet();
		if (timestamps.isEmpty()) {
			return;
		}
		connector.scheduleAggregateRefresh(timestamps.first(), timestamps.last());
	}
	
	private boolean isTimestampedChannel(String edgeId, String channel) {
		var channelSet = this.timestampedChannelsForEdge.get(edgeId);
		if (channelSet == null) {
			return true;
		}
		return channelSet.contains(channel);
	}

	private void writeData(String edgeId, AbstractDataNotification notification,
			BiPredicate<String, String> shouldWrite) {
		var handler = this.connector;
		if (handler == null) {
			return;
		}
		// dealing with JSON
		var data = notification.getData();
		var dataEntries = data.rowMap().entrySet();
		if (dataEntries.isEmpty()) {
			return;
		}

		var points = new ArrayList<DataPoint>();

		// The EdgeConfig supplies value type, unit and the component nature. It can
		// be missing for two different reasons — the Edge never sent one, or the
		// metadata database is unreachable — and OdooEdgeHandler reports both as
		// the same exception, so they cannot be told apart here. Either way this
		// only costs metadata for components that are already registered; a
		// component that is not gets its points dropped.
		EdgeConfig edgeConfig = null;
		try {
			edgeConfig = this.metadata.edge().getEdgeConfig(edgeId);
		} catch (OpenemsNamedException e) {
			if (this.missingConfigWarned.add(edgeId)) {
				this.logWarn(this.log, "No EdgeConfig for [" + edgeId
						+ "]; unregistered channels of this Edge are dropped: " + e.getMessage());
			}
		}

		for (var dataEntry : dataEntries) {
			var timestamp = dataEntry.getKey();
			if (!this.timeFilter.isValid(timestamp)) {
				continue;
			}
			var channelEntries = dataEntry.getValue().entrySet();

			for (var channelEntry : channelEntries) {
				var channelString = channelEntry.getKey();
				var element = channelEntry.getValue();
				if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
					continue;
				}
				if (!this.channelFilter.isValid(channelString)) {
					continue;
				}
				if (!shouldWrite.test(edgeId, channelString)) {
					continue;
				}

				var primitive = element.getAsJsonPrimitive();
				try {
					var addr = ChannelAddress.fromString(channelString);
					var componentId = addr.getComponentId();
					var channelId = addr.getChannelId();

					boolean aggregate = AggregateChannels.isAggregate(componentId, channelId);

					// null = nature unknown. Harmless for a component that is already
					// registered; a component that is not gets its points dropped,
					// because channel_def is scoped by the nature.
					String componentType = null;
					EdgeConfig.Component.Channel ch = null;
					if (edgeConfig != null) {
						var componentOpt = edgeConfig.getComponent(componentId);
						if (componentOpt.isPresent()) {
							var component = componentOpt.get();
							componentType = component.getFactoryId();
							ch = component.getChannels().get(channelId);
						}
					}

					Type type = ch != null ? Type.fromOpenemsType(ch.getType()) : Type.detect(primitive);
					Object value = type.coerce(primitive);

					String unit = ch != null && ch.getUnit() != null ? ch.getUnit().symbol : null;

					points.add(new DataPoint(
							timestamp, edgeId, componentId, componentType, channelId, type, aggregate, unit, value));
				} catch (OpenemsNamedException e) {
					this.logWarn(this.log, "Unable to parse ChannelAddress [" + channelString + "]: " + e.getMessage());
				}
			}
		}

		handler.writeBatch(points);
	}

	@Override
	public void handleEvent(Event event) {
		// Drop an Edge's classification when it goes offline; rebuilt on reconnect.
		if (Edge.Events.ON_SET_ONLINE.equals(event.getTopic())) {
			var reader = new EventReader(event);
			var edgeId = reader.getString(Edge.Events.OnSetOnline.EDGE_ID);
			var isOnline = reader.getBoolean(Edge.Events.OnSetOnline.IS_ONLINE);
			if (!isOnline) {
				this.timestampedChannelsForEdge.remove(edgeId);
			}
		}
	}

	@Override
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException {
		var handler = this.connector;
		if (handler == null || !this.timeFilter.isValid(fromDate, toDate)) {
			return new TreeMap<>();
		}
		return handler.queryHistoricData(edgeId, fromDate, toDate, channels, resolution);
	}

	@Override
	public String debugLog() {
		var handler = this.connector;
		return handler != null ? handler.debugLog() : "TimescaleDB [connecting...]";
	}

	@Override
	public Map<String, JsonElement> debugMetrics() {
		var handler = this.connector;
		if (handler == null) {
			return Map.of();
		}
		var result = new HashMap<String, JsonElement>();
		handler.debugMetrics().forEach((table, sizeMb) -> result.put(table, new JsonPrimitive(sizeMb)));
		return result;
	}

	@Override
	public SortedMap<ChannelAddress, JsonElement> queryHistoricEnergy(String edgeId, ZonedDateTime fromDate,
			ZonedDateTime toDate, Set<ChannelAddress> channels) throws OpenemsNamedException {
		var handler = this.connector;
		if (handler == null || !this.timeFilter.isValid(fromDate, toDate)) {
			return new TreeMap<>();
		}
		return handler.queryHistoricEnergy(edgeId, fromDate, toDate, channels);
	}

	@Override
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyPerPeriod(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException {
		var handler = this.connector;
		if (handler == null || !this.timeFilter.isValid(fromDate, toDate)) {
			return new TreeMap<>();
		}
		return handler.queryHistoricEnergyPerPeriod(edgeId, fromDate, toDate, channels, resolution);
	}
}
