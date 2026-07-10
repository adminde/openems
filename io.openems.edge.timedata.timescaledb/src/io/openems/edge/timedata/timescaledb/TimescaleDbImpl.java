package io.openems.edge.timedata.timescaledb;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import io.openems.shared.timescaledb.data.DataPoint;
import io.openems.shared.timescaledb.schema.AggregateRetention;
import io.openems.shared.timescaledb.schema.Tenancy;
import io.openems.shared.timescaledb.TimescaleDbConnector;
import io.openems.shared.timescaledb.RollupChannels;
import io.openems.shared.timescaledb.Type;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.timedata.Resolution;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.cycle.Cycle;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.Timeranges;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Timedata.TimescaleDB", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = { //
				"event.topics=" + EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE //
		})
public class TimescaleDbImpl extends AbstractOpenemsComponent
		implements TimescaleDb, Timedata, OpenemsComponent, EventHandler {

	private static final int BUFFER_MAX_SIZE = 5000;

	private final Logger log = LoggerFactory.getLogger(TimescaleDbImpl.class);
	private final List<DataPoint> buffer = new ArrayList<>();

	@Reference
	private Cycle cycle;

	@Reference
	private ComponentManager componentManager;

	private Config config;
	private int cycleCount = 0;
	private TimescaleDbConnector connector;

	public TimescaleDbImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Timedata.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		try {
			this.connector = new TimescaleDbConnector(Tenancy.SINGLE, //
					config.host(), config.username(), config.password()) //
					.database(config.database()) //
					.port(config.port()) //
					.poolSize(config.poolSize()) //
					.writeWorkers(config.writeWorkers()) //
					.rawRetentionDays(config.retentionDays()) //
					.rawCompressionDays(config.compressionDays()) //
					.aggregateRetention(AggregateRetention.EDGE_DEFAULTS) //
					.connect();
			this.log.info("TimescaleDB connected");
		} catch (OpenemsNamedException e) {
			this.log.error("Failed to connect to TimescaleDB: " + e.getMessage(), e);
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		this.flush();
		if (this.connector != null) {
			this.connector.deactivate();
		}
		super.deactivate();
	}

	// WRITE PATH
	
	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled() || this.connector == null) {
			return;
		}
		var cycleTime = this.cycle.getCycleTime();
		var timestamp = System.currentTimeMillis() / cycleTime * cycleTime;

		this.collectChannelValues(timestamp);

		if (++this.cycleCount >= this.config.noOfCycles() || this.buffer.size() >= BUFFER_MAX_SIZE) {
			this.cycleCount = 0;
			this.flush();
		}
	}

	private void collectChannelValues(long timestamp) {
		var minPriority = this.config.persistencePriority();
		this.componentManager.getEnabledComponents().stream()
				.forEach(component -> {
					var componentAlias = component.id();
					var componentType = component.serviceFactoryPid();

					component.channels().stream()
							.filter(channel -> channel.channelDoc().getAccessMode() != AccessMode.WRITE_ONLY)
							.filter(channel -> channel.channelDoc().getLocalPersistencePriority().isAtLeast(minPriority))
							.forEach(channel -> {
								var valueOpt = channel.value().asOptional();
								if (valueOpt.isEmpty()) {
									return;
								}

								var raw = valueOpt.get();
								boolean rollup = RollupChannels.isRollup(componentAlias, channel.channelId().id());
								var type = Type.fromOpenemsType(channel.getType());
								var value = type.coerce(raw);

								var unit = channel.channelDoc().getUnit().symbol;
								synchronized (this.buffer) {
									this.buffer.add(new DataPoint(
											timestamp, null, componentAlias, componentType,
											channel.channelId().id(), type, rollup, unit, value));
								}
							});
				});
	}

	private void flush() {
		List<DataPoint> toWrite;
		synchronized (this.buffer) {
			if (this.buffer.isEmpty()) {
				return;
			}
			toWrite = new ArrayList<>(this.buffer);
			this.buffer.clear();
		}
		this.connector.writeBatch(toWrite);
	}

	@Override
	public CompletableFuture<Optional<Object>> getLatestValue(ChannelAddress channelAddress) {
		return CompletableFuture.supplyAsync(() -> {
			if (this.connector == null) {
				return Optional.empty();
			}
			try {
				return this.connector.queryLatestValue(null, channelAddress);
			} catch (OpenemsNamedException e) {
				this.log.error("getLatestValue failed: " + e.getMessage(), e);
				return Optional.empty();
			}
		});
	}

	/**
	 * Returns time-series data for the requested channels, bucketed at the
	 * given resolution.
	 *
	 * <p>
	 * Backing source is chosen automatically based on the resolution — sub-5m
	 * queries hit raw hypertables, larger windows are answered from the
	 * pre-computed continuous aggregates (5m / 1h / 1d). This is what makes
	 * "show me the last 2 years" return in milliseconds.
	 *
	 * <p>
	 * The {@code edgeId} parameter is honoured if non-null; otherwise we use
	 * the edge name configured for this bundle.
	 */
	@Override
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException {
		if (this.connector == null) {
			return new TreeMap<>();
		}
		return this.connector.queryHistoricData(null, fromDate, toDate, channels, resolution);
	}

	@Override
	public SortedMap<ChannelAddress, JsonElement> queryHistoricEnergy(
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels)
			throws OpenemsNamedException {
		if (this.connector == null) {
			var empty = new TreeMap<ChannelAddress, JsonElement>();
			channels.forEach(c -> empty.put(c, JsonNull.INSTANCE));
			return empty;
		}
		return this.connector.queryHistoricEnergy(null, fromDate, toDate, channels);
	}

	@Override
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyPerPeriod(
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException {
		if (this.connector == null) {
			return new TreeMap<>();
		}
		return this.connector.queryHistoricEnergyPerPeriod(null, fromDate, toDate, channels, resolution);
	}

	@Override
	public Timeranges getResendTimeranges(ChannelAddress notSendChannel, long lastResendTimestamp)
			throws OpenemsNamedException {
		var timeranges = new Timeranges();
		if (this.connector == null) {
			return timeranges;
		}
		for (long timestamp : this.connector.getResendTimestamps(
				null, notSendChannel, lastResendTimestamp)) {
			timeranges.insert(timestamp);
		}
		return timeranges;
	}

	@Override
	public SortedMap<Long, SortedMap<ChannelAddress, JsonElement>> queryResendData(
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels)
			throws OpenemsNamedException {
		if (this.connector == null) {
			return new TreeMap<>();
		}
		return this.connector.queryResendData(null, fromDate, toDate, channels);
	}
}
