package io.openems.backend.timedata.timescaledb;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import io.openems.backend.common.component.AbstractOpenemsBackendComponent;
import io.openems.backend.common.metadata.Metadata;
import io.openems.backend.common.timedata.Timedata;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.notification.AbstractDataNotification;
import io.openems.common.jsonrpc.notification.AggregatedDataNotification;
import io.openems.common.jsonrpc.notification.ResendDataNotification;
import io.openems.common.jsonrpc.notification.TimestampedDataNotification;
import io.openems.common.timedata.Resolution;
import io.openems.common.types.ChannelAddress;
import io.openems.common.types.EdgeConfig;
import io.openems.common.types.OpenemsType;
import io.openems.shared.timescaledb.DataPoint;
import io.openems.shared.timescaledb.Priorities;
import io.openems.shared.timescaledb.TimescaleDbConfig;
import io.openems.shared.timescaledb.TimescaleDbHandler;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "Timedata.TimescaleDB",
		configurationPolicy = ConfigurationPolicy.REQUIRE,
		immediate = true
)
public class TimescaledbImpl extends AbstractOpenemsBackendComponent implements Timedata {

	private final Logger log = LoggerFactory.getLogger(TimescaledbImpl.class);

	// Seconds between background initialization attempts while the Database is
	// unreachable (e.g. Postgres still booting after a host/Docker restart).
	private static final int INIT_RETRY_SECONDS = 10;

	private Config config;
	// volatile: published by the init-retry thread, read by the WebSocket
	// threads in writeData and by deactivate().
	private volatile TimescaleDbHandler dbHandler;
	private volatile boolean active;
	private ScheduledExecutorService initExecutor;

	// Used to resolve component types and channel core flags from the
	// EdgeConfig the Edge pushes on connect (controller.api.backend OnOpen).
	@Reference
	private volatile Metadata metadata;

	public TimescaledbImpl() {
		super("Timedata.TimescaleDB");
	}

	@Override
	public String id() {
		return this.config.id();
	}

	@Activate
	private void activate(Config config) {
		this.config = config;
		this.active = true;
		this.initExecutor = Executors.newSingleThreadScheduledExecutor(
				r -> new Thread(r, "TimescaleDB-init"));
		this.initExecutor.execute(this::tryInitialize);
	}

	/**
	 * Attempts to build the {@link TimescaleDbHandler} (which opens the connection
	 * pool and applies the schema). On failure — typically the Database not being
	 * reachable yet — schedules another attempt in {@link #INIT_RETRY_SECONDS}.
	 * The handler construction is resource-safe on failure, so repeated attempts
	 * do not leak pools or threads.
	 */
	private void tryInitialize() {
		if (!this.active) {
			return; // deactivated while an attempt was queued
		}
		TimescaleDbHandler handler;
		try {
			handler = new TimescaleDbHandler(new TimescaleDbConfig(
					config.host(), config.port(), config.database(), config.username(), config.password(),
					config.poolSize(), config.rawRetentionDays(), config.rawCompressionDays()));
		} catch (SQLException | RuntimeException e) {
			this.logError(this.log, "TimescaleDB initialization failed; retrying in "
					+ INIT_RETRY_SECONDS + "s: " + e.getMessage());
			try {
				this.initExecutor.schedule(this::tryInitialize, INIT_RETRY_SECONDS, TimeUnit.SECONDS);
			} catch (RejectedExecutionException ree) {
				// executor shut down by deactivate() — stop retrying
			}
			return;
		}
		synchronized (this) {
			if (!this.active) {
				handler.deactivate();
				return;
			}
			this.dbHandler = handler;
		}
		this.logInfo(this.log, "TimescaleDB Backend activated and schema applied");
	}

	@Deactivate
	private void deactivate() {
		this.active = false;
		if (this.initExecutor != null) {
			this.initExecutor.shutdownNow();
		}
		TimescaleDbHandler toClose;
		synchronized (this) {
			toClose = this.dbHandler;
			this.dbHandler = null;
		}
		if (toClose != null) {
			toClose.deactivate();
		}
	}

	@Override
	public void write(String edgeId, TimestampedDataNotification notification) {
		this.writeData(edgeId, notification);
	}

	@Override
	public void write(String edgeId, AggregatedDataNotification notification) {
		this.writeData(edgeId, notification);
	}

	@Override
	public void write(String edgeId, ResendDataNotification data) {
		this.writeData(edgeId, data);
	}

	private void writeData(String edgeId, AbstractDataNotification notification) {
		var handler = this.dbHandler;
		if (handler == null) {
			return;
		}

		var data = notification.getData();
		var dataEntries = data.rowMap().entrySet();
		if (dataEntries.isEmpty()) {
			return;
		}

		var points = new ArrayList<DataPoint>();

		EdgeConfig edgeConfig = null;
		try {
			edgeConfig = this.metadata.edge().getEdgeConfig(edgeId);
		} catch (OpenemsNamedException e) {
			// config not yet known — placeholders are used below
		}

		for (var dataEntry : dataEntries) {
			var timestamp = dataEntry.getKey();
			var channelEntries = dataEntry.getValue().entrySet();

			for (var channelEntry : channelEntries) {
				var channelString = channelEntry.getKey();
				var element = channelEntry.getValue();

				if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
					continue;
				}

				var primitive = element.getAsJsonPrimitive();

				try {
					var addr = ChannelAddress.fromString(channelString);
					var componentId = addr.getComponentId();
					var channelId = addr.getChannelId();

					String componentType = "backend";
					boolean core = false;
					EdgeConfig.Component.Channel ch = null;
					if (edgeConfig != null) {
						var componentOpt = edgeConfig.getComponent(componentId);
						if (componentOpt.isPresent()) {
							var component = componentOpt.get();
							componentType = component.getFactoryId();
							ch = component.getChannels().get(channelId);
							if (ch != null) {
								core = Priorities.isCore(ch.getDetail().getPersistencePriority());
							}
						}
					}

					String dataType = ch != null ? toDataType(ch.getType()) : inferDataType(primitive);
					Object value = coerceValue(primitive, dataType);

					String unit = ch != null && ch.getUnit() != null ? ch.getUnit().symbol : null;

					points.add(new DataPoint(
							timestamp, edgeId, componentId, componentType, channelId, dataType, core, unit, value));
				} catch (OpenemsNamedException e) {
					this.logWarn(this.log, "Unable to parse ChannelAddress [" + channelString + "]: " + e.getMessage());
				}
			}
		}

		try {
			handler.writeBatch(points);
		} catch (SQLException e) {
			this.logWarn(this.log, "Failed to write batch to TimescaleDB: " + e.getMessage());
		}
	}

	/**
	 * Maps an {@link OpenemsType} to the schema's data-type bucket.
	 *
	 * @param type the channel's declared type
	 * @return "INTEGER", "FLOAT", or "STRING"
	 */
	private static String toDataType(OpenemsType type) {
		return switch (type) {
		case BOOLEAN, SHORT, INTEGER, LONG -> "INTEGER";
		case FLOAT, DOUBLE -> "FLOAT";
		case STRING -> "STRING";
		};
	}

	/**
	 * Fallback type inference from a JSON value, used only when the channel is
	 * not present in the EdgeConfig.
	 *
	 * @param primitive the JSON value
	 * @return "INTEGER", "FLOAT", or "STRING"
	 */
	private static String inferDataType(JsonPrimitive primitive) {
		if (primitive.isBoolean()) {
			return "INTEGER";
		}
		if (primitive.isNumber()) {
			return primitive.getAsString().indexOf('.') == -1 ? "INTEGER" : "FLOAT";
		}
		return "STRING";
	}

	/**
	 * Coerces a JSON value to match the resolved data type, so a channel's
	 * stored values are consistent with its hypertable.
	 *
	 * @param primitive the JSON value
	 * @param dataType  the resolved data type
	 * @return the coerced value (Long, Double, or String)
	 */
	private static Object coerceValue(JsonPrimitive primitive, String dataType) {
		return switch (dataType) {
		case "INTEGER" -> primitive.isBoolean() ? (primitive.getAsBoolean() ? 1L : 0L) : primitive.getAsLong();
		case "FLOAT" -> primitive.getAsDouble();
		default -> primitive.getAsString();
		};
	}

	@Override
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException {
		var handler = this.dbHandler;
		if (handler == null) return new TreeMap<>();
		try {
			return handler.queryHistoricData(edgeId, fromDate, toDate, channels, resolution.toSeconds());
		} catch (SQLException e) {
			throw new OpenemsException(e);
		}
	}

	@Override
	public SortedMap<ChannelAddress, JsonElement> queryHistoricEnergy(String edgeId, ZonedDateTime fromDate,
			ZonedDateTime toDate, Set<ChannelAddress> channels) throws OpenemsNamedException {
		var handler = this.dbHandler;
		if (handler == null) return new TreeMap<>();
		try {
			return handler.queryHistoricEnergy(edgeId, fromDate, toDate, channels);
		} catch (SQLException e) {
			throw new OpenemsException(e);
		}
	}

	@Override
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyPerPeriod(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException {
		var handler = this.dbHandler;
		if (handler == null) return new TreeMap<>();
		try {
			return handler.queryHistoricEnergyPerPeriod(edgeId, fromDate, toDate, channels, resolution.toSeconds());
		} catch (SQLException e) {
			throw new OpenemsException(e);
		}
	}
}
