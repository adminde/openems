package io.openems.backend.timedata.timescaledb;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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
import io.openems.backend.common.debugcycle.DebugLoggable;
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
import io.openems.shared.timescaledb.data.DataPoint;
import io.openems.shared.timescaledb.schema.Tenancy;
import io.openems.shared.timescaledb.TimescaleDbConnector;
import io.openems.shared.timescaledb.Type;
import io.openems.shared.timescaledb.Utils;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "Timedata.TimescaleDB",
		configurationPolicy = ConfigurationPolicy.REQUIRE,
		immediate = true
)
public class TimescaleDbImpl extends AbstractOpenemsBackendComponent implements Timedata, DebugLoggable {

	private final Logger log = LoggerFactory.getLogger(TimescaleDbImpl.class);


	private static final int INIT_RETRY_SECONDS = 10;

	private Config config;
	
	private volatile TimescaleDbConnector connector;
	private volatile boolean active;
	private ScheduledExecutorService initExecutor;

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
		this.active = true;
		this.initExecutor = Executors.newSingleThreadScheduledExecutor(
				r -> new Thread(r, "TimescaleDB-init"));
		this.initExecutor.execute(this::tryInitialize);
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
					.rawRetentionDays(config.rawRetentionDays()) //
					.rawCompressionDays(config.rawCompressionDays()) //
					.readOnly(config.isReadOnly()) //
					.connect();
		} catch (SQLException | RuntimeException e) {
			this.logError(this.log, "TimescaleDB initialization failed; retrying in "
					+ INIT_RETRY_SECONDS + "s: " + e.getMessage());
			try {
				this.initExecutor.schedule(this::tryInitialize, INIT_RETRY_SECONDS, TimeUnit.SECONDS);
			} catch (RejectedExecutionException ree) {
				
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
					boolean rollup = false;
					EdgeConfig.Component.Channel ch = null;
					if (edgeConfig != null) {
						var componentOpt = edgeConfig.getComponent(componentId);
						if (componentOpt.isPresent()) {
							var component = componentOpt.get();
							componentType = component.getFactoryId();
							ch = component.getChannels().get(channelId);
							if (ch != null) {
								rollup = Utils.isRollup(ch.getDetail().getPersistencePriority());
							}
						}
					}

					Type type = ch != null ? Type.fromOpenemsType(ch.getType()) : Type.detect(primitive);
					Object value = type.coerce(primitive);

					String unit = ch != null && ch.getUnit() != null ? ch.getUnit().symbol : null;

					points.add(new DataPoint(
							timestamp, edgeId, componentId, componentType, channelId, type, rollup, unit, value));
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

	@Override
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException {
		var handler = this.connector;
		if (handler == null) return new TreeMap<>();
		try {
			return handler.queryHistoricData(edgeId, fromDate, toDate, channels, resolution);
		} catch (SQLException e) {
			throw new OpenemsException(e);
		}
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
		var handler = this.connector;
		if (handler == null) return new TreeMap<>();
		try {
			return handler.queryHistoricEnergyPerPeriod(edgeId, fromDate, toDate, channels, resolution);
		} catch (SQLException e) {
			throw new OpenemsException(e);
		}
	}
}
