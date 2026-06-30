package io.openems.edge.timedata.timescaledb;

import java.sql.SQLException;
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

import io.openems.shared.timescaledb.DataPoint;
import io.openems.shared.timescaledb.TimescaleDbConfig.Deployment;
import io.openems.shared.timescaledb.Priorities;
import io.openems.shared.timescaledb.TimescaleDbConfig;
import io.openems.shared.timescaledb.TimescaleDbHandler;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
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
		name = "Timedata.Timescaledb", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = { //
				"event.topics=" + EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE //
		})
public class TimescaledbImpl extends AbstractOpenemsComponent
		implements Timedata, OpenemsComponent, EventHandler {

	private static final int BUFFER_MAX_SIZE = 5000;

	private final Logger log = LoggerFactory.getLogger(TimescaledbImpl.class);
	private final List<DataPoint> buffer = new ArrayList<>();

	@Reference
	private Cycle cycle;

	@Reference
	private ComponentManager componentManager;

	private Config config;
	private int cycleCount = 0;
	private TimescaleDbHandler dbHandler;

	public TimescaledbImpl() {
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
			this.dbHandler = new TimescaleDbHandler(new TimescaleDbConfig(
					config.host(), config.port(), config.database(), config.username(), config.password(), config.poolSize(),
					config.rawRetentionDays(), config.rawCompressionDays(),
					false,
					config.writeWorkers(),
					Deployment.EDGE
			));
			this.log.info("TimescaleDB connected and schema applied");
		} catch (SQLException e) {
			this.log.error("Failed to connect to TimescaleDB: " + e.getMessage(), e);
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		this.flush();
		if (this.dbHandler != null) {
			this.dbHandler.deactivate();
		}
		super.deactivate();
	}

	// WRITE PATH
	
	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled() || this.dbHandler == null) {
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
		var edgeName = this.config.edgeName();

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
								String dataType;
								Object value;
								boolean core = Priorities.isCore(channel.channelDoc().getLocalPersistencePriority());

								switch (channel.getType()) {
								case BOOLEAN -> {
									dataType = "INTEGER";
									value = ((Boolean) raw) ? 1L : 0L;
								}
								case SHORT -> {
									dataType = "INTEGER";
									value = ((Short) raw).longValue();
								}
								case INTEGER -> {
									dataType = "INTEGER";
									value = ((Integer) raw).longValue();
								}
								case LONG -> {
									dataType = "INTEGER";
									value = raw;
								}
								case FLOAT -> {
									dataType = "FLOAT";
									value = ((Float) raw).doubleValue();
								}
								case DOUBLE -> {
									dataType = "FLOAT";
									value = raw;
								}
								case STRING -> {
									dataType = "STRING";
									value = raw.toString();
								}
								default -> {
									return;
								}
								}

								var unit = channel.channelDoc().getUnit().symbol;
								synchronized (this.buffer) {
									this.buffer.add(new DataPoint(
											timestamp, edgeName, componentAlias, componentType,
											channel.channelId().id(), dataType, core, unit, value));
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
		try {
			this.dbHandler.writeBatch(toWrite);
		} catch (SQLException e) {
			this.log.error("Failed to write batch: " + e.getMessage(), e);
		}
	}


	@Override
	public CompletableFuture<Optional<Object>> getLatestValue(ChannelAddress channelAddress) {
		return CompletableFuture.supplyAsync(() -> {
			if (this.dbHandler == null) {
				return Optional.empty();
			}
			try {
				return this.dbHandler.queryLatestValue(this.config.edgeName(), channelAddress);
			} catch (SQLException e) {
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
			String edgeId, ZonedDateTime fromDate, ZonedDateTime toDate,
			Set<ChannelAddress> channels, Resolution resolution) throws OpenemsNamedException {

		if (this.dbHandler == null) {
			return new TreeMap<>();
		}
		var edge = edgeId != null ? edgeId : this.config.edgeName();
		try {
			return this.dbHandler.queryHistoricData(edge, fromDate, toDate, channels, resolution.toSeconds());
		} catch (SQLException e) {
			throw new OpenemsException("queryHistoricData failed: " + e.getMessage(), e);
		}
	}

	@Override
	public SortedMap<ChannelAddress, JsonElement> queryHistoricEnergy(
			String edgeId, ZonedDateTime fromDate, ZonedDateTime toDate,
			Set<ChannelAddress> channels) throws OpenemsNamedException {

		if (this.dbHandler == null) {
			var empty = new TreeMap<ChannelAddress, JsonElement>();
			channels.forEach(c -> empty.put(c, JsonNull.INSTANCE));
			return empty;
		}
		var edge = edgeId != null ? edgeId : this.config.edgeName();
		try {
			return this.dbHandler.queryHistoricEnergy(edge, fromDate, toDate, channels);
		} catch (SQLException e) {
			throw new OpenemsException("queryHistoricEnergy failed: " + e.getMessage(), e);
		}
	}


	@Override
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyPerPeriod(
			String edgeId, ZonedDateTime fromDate, ZonedDateTime toDate,
			Set<ChannelAddress> channels, Resolution resolution) throws OpenemsNamedException {

		if (this.dbHandler == null) {
			return new TreeMap<>();
		}
		var edge = edgeId != null ? edgeId : this.config.edgeName();
		try {
			return this.dbHandler.queryHistoricEnergyPerPeriod(edge, fromDate, toDate, channels, resolution.toSeconds());
		} catch (SQLException e) {
			throw new OpenemsException("queryHistoricEnergyPerPeriod failed: " + e.getMessage(), e);
		}
	}


	@Override
	public Timeranges getResendTimeranges(ChannelAddress notSendChannel, long lastResendTimestamp)
			throws OpenemsNamedException {
		var timeranges = new Timeranges();
		if (this.dbHandler == null) {
			return timeranges;
		}
		try {
			for (long ts : this.dbHandler.getResendTimestamps(
					this.config.edgeName(), notSendChannel, lastResendTimestamp)) {
				timeranges.insert(ts);
			}
			return timeranges;
		} catch (SQLException e) {
			throw new OpenemsException("getResendTimeranges failed: " + e.getMessage(), e);
		}
	}

	@Override
	public SortedMap<Long, SortedMap<ChannelAddress, JsonElement>> queryResendData(
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels)
			throws OpenemsNamedException {
		if (this.dbHandler == null) {
			return new TreeMap<>();
		}
		try {
			return this.dbHandler.queryResendData(this.config.edgeName(), fromDate, toDate, channels);
		} catch (SQLException e) {
			throw new OpenemsException("queryResendData failed: " + e.getMessage(), e);
		}
	}
}
