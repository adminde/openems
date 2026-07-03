package io.openems.shared.timescaledb;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;

import org.postgresql.Driver;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.zaxxer.hikari.HikariDataSource;

import io.openems.common.timedata.Resolution;
import io.openems.common.types.ChannelAddress;
import io.openems.shared.timescaledb.data.DataPoint;
import io.openems.shared.timescaledb.data.ReadHandler;
import io.openems.shared.timescaledb.data.WriteHandler;
import io.openems.shared.timescaledb.schema.ChannelManager;
import io.openems.shared.timescaledb.schema.SchemaHandler;
import io.openems.shared.timescaledb.schema.Tenancy;

/**
 * Facade and entry point to the shared TimescaleDB persistence.
 *
 * <p>
 * Required parameters (tenancy and connection credentials) are passed to the
 * constructor; everything with a sensible default is an optional fluent setter.
 * {@link #connect()} ends the configuration phase: it opens the connection
 * pool, applies the schema and starts the write workers.
 *
 * <pre>
 * var db = new TimescaleDbHandler(Tenancy.SINGLE, host, username, password) //
 * 		.database("data") //
 * 		.port(5432) //
 * 		.poolSize(10) //
 * 		.connect();
 * </pre>
 */
public class TimescaleDbConnector {

	private static final long READ_ONLY_LOG_INTERVAL_SECONDS = 300;

	private final Logger log = LoggerFactory.getLogger(TimescaleDbConnector.class);

	// Required parameters
	private final Tenancy tenancy;
	private final String host;
	private final String username;
	private final String password;

	// Optional parameters, overridable before connect()
	private String database = "data";
	private int port = 5432;
	private int poolSize = 10;
	private int writeWorkers = 4;
	private int rawRetentionDays = 30;
	private int rawCompressionDays = 7;
	private boolean readOnly = false;

	// Initialized by connect()
	private HikariDataSource dataSource;
	private ChannelManager channelManager;
	private WriteHandler writeHandler;
	private ReadHandler readHandler;

	private volatile Instant lastReadOnlyLog = Instant.EPOCH;

	public TimescaleDbConnector(Tenancy tenancy, String host, String username, String password) {
		this.tenancy = tenancy;
		this.host = host;
		this.username = username;
		this.password = password;
	}

	/**
	 * Sets the database name. Default: "data".
	 *
	 * @param database the database name
	 * @return myself for chaining
	 */
	public TimescaleDbConnector database(String database) {
		this.assertNotConnected();
		this.database = database;
		return this;
	}

	/**
	 * Sets the database port. Default: 5432.
	 *
	 * @param port the port
	 * @return myself for chaining
	 */
	public TimescaleDbConnector port(int port) {
		this.assertNotConnected();
		this.port = port;
		return this;
	}

	/**
	 * Sets the HikariCP connection pool size. Default: 10.
	 *
	 * @param poolSize the maximum pool size
	 * @return myself for chaining
	 */
	public TimescaleDbConnector poolSize(int poolSize) {
		this.assertNotConnected();
		this.poolSize = poolSize;
		return this;
	}

	/**
	 * Sets the number of background threads draining the write queue. Keep below
	 * the pool size so reads still get a connection. Default: 4.
	 *
	 * @param writeWorkers the number of write workers
	 * @return myself for chaining
	 */
	public TimescaleDbConnector writeWorkers(int writeWorkers) {
		this.assertNotConnected();
		this.writeWorkers = writeWorkers;
		return this;
	}

	/**
	 * Sets the days to keep raw data before deletion. Default: 30.
	 *
	 * @param rawRetentionDays the retention in days
	 * @return myself for chaining
	 */
	public TimescaleDbConnector rawRetentionDays(int rawRetentionDays) {
		this.assertNotConnected();
		this.rawRetentionDays = rawRetentionDays;
		return this;
	}

	/**
	 * Sets the days to wait before compressing raw data. Default: 7.
	 *
	 * @param rawCompressionDays the compression delay in days
	 * @return myself for chaining
	 */
	public TimescaleDbConnector rawCompressionDays(int rawCompressionDays) {
		this.assertNotConnected();
		this.rawCompressionDays = rawCompressionDays;
		return this;
	}

	/**
	 * Activates the read-only mode: {@link #writeBatch} silently discards all
	 * points (with a rate-limited log message). Default: false.
	 *
	 * @param readOnly whether to activate read-only mode
	 * @return myself for chaining
	 */
	public TimescaleDbConnector readOnly(boolean readOnly) {
		this.assertNotConnected();
		this.readOnly = readOnly;
		return this;
	}

	/**
	 * Ends the configuration phase: opens the connection pool, applies the
	 * database schema and starts the write workers.
	 *
	 * @return myself for chaining
	 * @throws SQLException on database connection or schema creation failure
	 */
	public TimescaleDbConnector connect() throws SQLException {
		this.assertNotConnected();

		if (!Driver.isRegistered()) {
			Driver.register();
		}
		var pgds = new PGSimpleDataSource();
		pgds.setServerNames(new String[] { this.host });
		pgds.setPortNumbers(new int[] { this.port });
		pgds.setDatabaseName(this.database);
		pgds.setUser(this.username);
		pgds.setPassword(this.password);

		// Rewrite batched inserts into bulk inserts for performance.
		pgds.setReWriteBatchedInserts(true);

		var hikari = new HikariDataSource();
		hikari.setMaximumPoolSize(this.poolSize);
		hikari.setDataSource(pgds);
		this.dataSource = hikari;

		// SchemaHandler and ChannelManager derive their SQL from the tenancy;
		// the read/write handlers stay tenancy-agnostic.
		var schema = new SchemaHandler(this.tenancy, this.dataSource, this.rawRetentionDays, this.rawCompressionDays);
		this.channelManager = new ChannelManager(this.tenancy);
		this.writeHandler = new WriteHandler(this.dataSource, this.channelManager, this.writeWorkers);
		this.readHandler = new ReadHandler(this.dataSource, this.channelManager);

		// Apply schema on startup
		try {
			schema.applySchema();
		} catch (SQLException | RuntimeException e) {
			this.writeHandler.deactivate();
			this.dataSource.close();
			this.dataSource = null;
			throw e;
		}

		// Warm up the channel cache with one bulk query, so the first writes and
		// reads skip their per-channel lookups. Best effort — a cold start is fine.
		try (var con = this.dataSource.getConnection()) {
			var count = this.channelManager.warmUpCache(con);
			this.log.info("TimescaleDB channel cache warmed up with {} channels", count);
		} catch (SQLException e) {
			this.log.warn("TimescaleDB channel cache warm-up failed; continuing cold: {}", e.getMessage());
		}
		return this;
	}

	private void assertNotConnected() {
		if (this.dataSource != null) {
			throw new IllegalStateException("Already connected; parameters must be set before connect()");
		}
	}

	public void writeBatch(List<DataPoint> points) throws SQLException {
		if (this.readOnly) {
			var now = Instant.now();
			if (now.isAfter(this.lastReadOnlyLog.plusSeconds(READ_ONLY_LOG_INTERVAL_SECONDS))) {
				this.lastReadOnlyLog = now;
				this.log.info("Read-Only mode is active. Discarding {} points", points.size());
			}
			return;
		}
		this.writeHandler.writeBatch(points);
	}

	/**
	 * Returns a short status line for continuous debug logging: write-queue fill
	 * level and total points written since start.
	 *
	 * @return the debug log string
	 */
	public String debugLog() {
		return new StringBuilder("TimescaleDB [queue:") //
				.append(this.writeHandler.queueSize()).append("/").append(this.writeHandler.queueCapacity()) //
				.append("|written:").append(this.writeHandler.totalWritten()) //
				.append(this.readOnly ? "|READ_ONLY" : "") //
				.append("]").toString();
	}

	/**
	 * Returns the size of each hypertable in MB, for monitoring database growth.
	 *
	 * @return map of hypertable name to size in MB; empty on error
	 */
	public Map<String, Number> debugMetrics() {
		var data = new HashMap<String, Number>();
		try (var con = this.dataSource.getConnection(); //
				var st = con.createStatement()) {
			var rs = st.executeQuery("""
					SELECT hypertable_name,
					       hypertable_size(format('%I.%I', hypertable_schema, hypertable_name)::regclass) / 1024 / 1024
					FROM timescaledb_information.hypertables""");
			while (rs.next()) {
				data.put(rs.getString(1), rs.getInt(2));
			}
		} catch (SQLException e) {
			this.log.warn("Unable to query debugMetrics: {}", e.getMessage());
		}
		return data;
	}

	// Queries historic data for a set of channels at a specific resolution
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(
			String edgeName, ZonedDateTime from, ZonedDateTime to,
			Set<ChannelAddress> channels, Resolution resolution) throws SQLException {
		return this.readHandler.queryHistoricData(edgeName, from, to, channels, resolution);
	}

	// Queries the total historic energy consumed/produced during a period.
	public SortedMap<ChannelAddress, JsonElement> queryHistoricEnergy(
			String edgeName, ZonedDateTime from, ZonedDateTime to,
			Set<ChannelAddress> channels) throws SQLException {
		return this.readHandler.queryHistoricEnergy(edgeName, from, to, channels);
	}

	// Queries historic energy per bucket resolution.
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyPerPeriod(
			String edgeName, ZonedDateTime from, ZonedDateTime to,
			Set<ChannelAddress> channels, Resolution resolution) throws SQLException {
		return this.readHandler.queryHistoricEnergyPerPeriod(edgeName, from, to, channels, resolution);
	}

	// Queries the most recent known value for a channel.
	public Optional<Object> queryLatestValue(String edgeName, ChannelAddress addr) throws SQLException {
		return this.readHandler.queryLatestValue(edgeName, addr);
	}

	public java.util.List<Long> getResendTimestamps(String edgeName,
			ChannelAddress notSendChannel, long lastResendTimestamp) throws SQLException {
		return this.readHandler.getResendTimestamps(edgeName, notSendChannel, lastResendTimestamp);
	}

	public SortedMap<Long, SortedMap<ChannelAddress, JsonElement>> queryResendData(
			String edgeName, ZonedDateTime from, ZonedDateTime to,
			Set<ChannelAddress> channels) throws SQLException {
		return this.readHandler.queryResendData(edgeName, from, to, channels);
	}

	public void deactivate() {
		if (this.writeHandler != null) {
			this.writeHandler.deactivate();
		}
		if (this.dataSource != null && !this.dataSource.isClosed()) {
			this.dataSource.close();
		}
	}
}
