package io.openems.shared.timescaledb;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;

import io.openems.shared.timescaledb.schema.Schema;
import io.openems.shared.timescaledb.worker.ReadHandler;
import io.openems.shared.timescaledb.worker.WriteHandler;
import org.postgresql.Driver;
import org.postgresql.ds.PGSimpleDataSource;

import com.google.gson.JsonElement;
import com.zaxxer.hikari.HikariDataSource;

import io.openems.common.types.ChannelAddress;
import io.openems.shared.timescaledb.schema.ChannelManager;
import io.openems.shared.timescaledb.schema.tenancy.MultiTenantChannelManager;
import io.openems.shared.timescaledb.schema.tenancy.SingleTenantChannelManager;
import io.openems.shared.timescaledb.schema.tenancy.MultiTenantSchema;
import io.openems.shared.timescaledb.schema.tenancy.SingleTenantSchema;

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
 * var db = new TimescaleDbHandler(Tenancy.SINGLE_TENANT, host, username, password) //
 * 		.database("data") //
 * 		.port(5432) //
 * 		.poolSize(10) //
 * 		.connect();
 * </pre>
 */
public class TimescaleDbHandler {

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
	private Boolean minutelyAggregate = null; // null = derived from tenancy

	// Initialized by connect()
	private HikariDataSource dataSource;
	private ChannelManager channelManager;
	private WriteHandler writeHandler;
	private ReadHandler readHandler;

	public TimescaleDbHandler(Tenancy tenancy, String host, String username, String password) {
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
	public TimescaleDbHandler database(String database) {
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
	public TimescaleDbHandler port(int port) {
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
	public TimescaleDbHandler poolSize(int poolSize) {
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
	public TimescaleDbHandler writeWorkers(int writeWorkers) {
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
	public TimescaleDbHandler rawRetentionDays(int rawRetentionDays) {
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
	public TimescaleDbHandler rawCompressionDays(int rawCompressionDays) {
		this.assertNotConnected();
		this.rawCompressionDays = rawCompressionDays;
		return this;
	}

	/**
	 * Overrides whether the 1-minute Fast Lane aggregate is built (refreshes
	 * every 60s; costs resources on small devices).
	 *
	 * <p>
	 * Default is derived from the tenancy: {@code true} for
	 * {@link Tenancy#MULTI} (central database), {@code false} for
	 * {@link Tenancy#SINGLE} (resource-limited Edge device).
	 *
	 * @param minutelyAggregate whether to build the 1-minute aggregate
	 * @return myself for chaining
	 */
	public TimescaleDbHandler minutelyAggregate(boolean minutelyAggregate) {
		this.assertNotConnected();
		this.minutelyAggregate = minutelyAggregate;
		return this;
	}

	/**
	 * Ends the configuration phase: opens the connection pool, applies the
	 * database schema and starts the write workers.
	 *
	 * @return myself for chaining
	 * @throws SQLException on database connection or schema creation failure
	 */
	public TimescaleDbHandler connect() throws SQLException {
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

		var createMinutelyAggregate = this.minutelyAggregate != null //
				? this.minutelyAggregate
				: this.tenancy == Tenancy.MULTI;

		// Pick the tenancy-specific ChannelManager + SchemaHandler. All other
		// variance between single-tenant (one edge per database) and multi-tenant
		// (many edges per database) is confined to these two classes; the
		// read/write handlers stay tenancy-agnostic.
		Schema schemaHandler;
		switch (this.tenancy) {
		case SINGLE -> {
			this.channelManager = new SingleTenantChannelManager();
			schemaHandler = new SingleTenantSchema(this.dataSource, this.rawRetentionDays,
					this.rawCompressionDays, createMinutelyAggregate);
		}
		case MULTI -> {
			this.channelManager = new MultiTenantChannelManager();
			schemaHandler = new MultiTenantSchema(this.dataSource, this.rawRetentionDays,
					this.rawCompressionDays, createMinutelyAggregate);
		}
		default -> throw new IllegalArgumentException("Unsupported TimescaleDB tenancy: " + this.tenancy);
		}
		this.writeHandler = new WriteHandler(this.dataSource, this.channelManager, this.writeWorkers);
		this.readHandler = new ReadHandler(this.dataSource, this.channelManager, createMinutelyAggregate);

		// Apply schema on startup
		try {
			schemaHandler.applySchema();
		} catch (SQLException | RuntimeException e) {
			this.writeHandler.deactivate();
			this.dataSource.close();
			this.dataSource = null;
			throw e;
		}
		return this;
	}

	private void assertNotConnected() {
		if (this.dataSource != null) {
			throw new IllegalStateException("Already connected; parameters must be set before connect()");
		}
	}

	public void writeBatch(List<DataPoint> points) throws SQLException {
		this.writeHandler.writeBatch(points);
	}

	// Queries historic data for a set of channels at a specific resolution
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(
			String edgeName, ZonedDateTime from, ZonedDateTime to,
			Set<ChannelAddress> channels, long bucketSeconds) throws SQLException {
		return this.readHandler.queryHistoricData(edgeName, from, to, channels, bucketSeconds);
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
			Set<ChannelAddress> channels, long bucketSeconds) throws SQLException {
		return this.readHandler.queryHistoricEnergyPerPeriod(edgeName, from, to, channels, bucketSeconds);
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
