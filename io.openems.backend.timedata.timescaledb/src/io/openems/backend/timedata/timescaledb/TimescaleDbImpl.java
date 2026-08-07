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
		Edge.Events.ON_SET_ONLINE, //
		Edge.Events.ON_SET_CONFIG //
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
			connector = new TimescaleDbConnector(Tenancy.MULTI, //
					this.config.host(), //
					this.config.username(), //
					this.config.password()) //
					.database(this.config.database()) //
					.port(this.config.port()) //
					.poolSize(this.config.poolSize()) //
					.writeWorkers(this.config.writeWorkers()) //
					.rawRetentionDays(this.config.retentionDays()) //
					.rawCompressionDays(this.config.compressionDays()) //
					.aggregates(Aggregate.of(Tenancy.MULTI)) //
					.readOnly(this.config.isReadOnly()) //
					.connect();

		} catch (OpenemsNamedException | RuntimeException e) {
			this.logError(this.log, "TimescaleDB initialization failed; retrying in "
					+ INIT_RETRY_SECONDS + "s: " + e.getMessage());
			try {
				this.initExecutor.schedule(this::tryInitialize, INIT_RETRY_SECONDS, TimeUnit.SECONDS);

			} catch (RejectedExecutionException re) {
				// deactivate() shut the executor down while we were retrying
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

	private void writeData(String edgeId, AbstractDataNotification notification,
			BiPredicate<String, String> shouldWrite) {
		var connector = this.connector;
		if (connector == null) {
			return;
		}
		var data = notification.getData();
		var dataEntries = data.rowMap().entrySet();
		if (dataEntries.isEmpty()) {
			return;
		}

		var points = new ArrayList<DataPoint>();

		EdgeConfig edgeConfig = null;
		var edgeConfigResolved = false;

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
					var channelAddress = ChannelAddress.fromString(channelString);
					var channelId = channelAddress.getChannelId();
					var componentId = channelAddress.getComponentId();

					boolean aggregate = AggregateChannels.isAggregate(componentId, channelId);

					String componentType = null;
					Type channelType;
					String channelUnit;

					var channelInfo = connector.peekChannel(edgeId, componentId, channelId);
					if (channelInfo != null) {
						channelType = channelInfo.type();
						channelUnit = channelInfo.unit();

					} else {
						if (!edgeConfigResolved) {
							edgeConfigResolved = true;
							try {
								edgeConfig = this.metadata.edge().getEdgeConfig(edgeId);
							} catch (OpenemsNamedException e) {
								if (this.missingConfigWarned.add(edgeId)) {
									this.logWarn(this.log, "No EdgeConfig for [" + edgeId
											+ "]; unregistered channels of this Edge are dropped: " + e.getMessage());
								}
							}
						}
						EdgeConfig.Component.Channel channel = null;
						if (edgeConfig != null) {
							var componentOpt = edgeConfig.getComponent(componentId);
							if (componentOpt.isPresent()) {
								var component = componentOpt.get();
								componentType = component.getFactoryId();
								channel = component.getChannels().get(channelId);
							}
						}
						channelType = channel != null ? Type.fromOpenemsType(channel.getType()) : Type.detect(primitive);
						channelUnit = channel != null && channel.getUnit() != null ? channel.getUnit().symbol : null;
					}
					Object value = channelType.coerce(primitive);
					points.add(new DataPoint(
							timestamp, edgeId, componentId, componentType, channelId, channelType, aggregate, channelUnit, value));

				} catch (OpenemsNamedException e) {
					this.logWarn(this.log, "Unable to parse ChannelAddress [" + channelString + "]: " + e.getMessage());
				}
			}
		}
		connector.writeBatch(points);
	}

	private boolean isTimestampedChannel(String edgeId, String channel) {
		var channelSet = this.timestampedChannelsForEdge.get(edgeId);
		if (channelSet == null) {
			return true;
		}
		return channelSet.contains(channel);
	}

	@Override
	public void handleEvent(Event event) {
		var reader = new EventReader(event);

		// Drop an Edge's classification when it goes offline; rebuilt on reconnect.
		if (Edge.Events.ON_SET_ONLINE.equals(event.getTopic())) {
			var edgeId = reader.getString(Edge.Events.OnSetOnline.EDGE_ID);
			var isOnline = reader.getBoolean(Edge.Events.OnSetOnline.IS_ONLINE);
			if (!isOnline) {
				this.timestampedChannelsForEdge.remove(edgeId);
			}
			return;
		}

		// A new configuration is the only way a channel's unit or value type can
		// change without the data showing it. Drop the Edge's cached channels so
		// the next write resolves them against the fresh EdgeConfig.
		if (Edge.Events.ON_SET_CONFIG.equals(event.getTopic())) {
			var edge = (Edge) reader.getProperty(Edge.Events.OnSetConfig.EDGE);
			var connector = this.connector;
			if (edge != null && connector != null) {
				connector.invalidateEdge(edge.getId());
				this.missingConfigWarned.remove(edge.getId());
			}
		}
	}

	@Override
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException {
		var connector = this.connector;
		if (connector == null || !this.timeFilter.isValid(fromDate, toDate)) {
			return new TreeMap<>();
		}
		return connector.queryHistoricData(edgeId, fromDate, toDate, channels, resolution);
	}

	@Override
	public String debugLog() {
		var connector = this.connector;
		return connector != null ? connector.debugLog() : "TimescaleDB [connecting...]";
	}

	@Override
	public Map<String, JsonElement> debugMetrics() {
		var connector = this.connector;
		if (connector == null) {
			return Map.of();
		}
		var result = new HashMap<String, JsonElement>();
		connector.debugMetrics().forEach((table, sizeMb) -> result.put(table, new JsonPrimitive(sizeMb)));
		return result;
	}

	@Override
	public SortedMap<ChannelAddress, JsonElement> queryHistoricEnergy(String edgeId, ZonedDateTime fromDate,
			ZonedDateTime toDate, Set<ChannelAddress> channels) throws OpenemsNamedException {
		var connector = this.connector;
		if (connector == null || !this.timeFilter.isValid(fromDate, toDate)) {
			return new TreeMap<>();
		}
		return connector.queryHistoricEnergy(edgeId, fromDate, toDate, channels);
	}

	@Override
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyPerPeriod(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException {
		var connector = this.connector;
		if (connector == null || !this.timeFilter.isValid(fromDate, toDate)) {
			return new TreeMap<>();
		}
		return connector.queryHistoricEnergyPerPeriod(edgeId, fromDate, toDate, channels, resolution);
	}
}
