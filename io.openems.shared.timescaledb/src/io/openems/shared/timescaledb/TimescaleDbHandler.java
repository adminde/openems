package io.openems.shared.timescaledb;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;

import org.postgresql.Driver;
import org.postgresql.ds.PGSimpleDataSource;

import com.google.gson.JsonElement;
import com.zaxxer.hikari.HikariDataSource;

import io.openems.common.types.ChannelAddress;

/**
 * Main facade for the TimescaleDB implementation.
 * Initializes connections, builds the schema, and delegates read/write operations
 * to the respective handler classes.
 */
public class TimescaleDbHandler {

	private final HikariDataSource dataSource;
	
	private final ChannelManager channelManager;
	private final SchemaHandler schemaHandler;
	private final WriteHandler writeHandler;
	private final ReadHandler readHandler;

	/**
	 * Constructor. Initializes the database connection, builds the schema, and sets up read/write handlers.
	 * 
	 * @param config The database configuration details
	 * @throws SQLException on database connection or schema creation failure
	 */
	public TimescaleDbHandler(TimescaleDbConfig config) throws SQLException {
		if (!Driver.isRegistered()) {
			Driver.register();
		}
		var pgds = new PGSimpleDataSource();
		pgds.setServerNames(new String[] { config.host() });
		pgds.setPortNumbers(new int[] { config.port() });
		pgds.setDatabaseName(config.database());
		pgds.setUser(config.username());
		pgds.setPassword(config.password());
		
		// Optimization: Rewrite batched inserts into bulk inserts for massive performance gains
		pgds.setReWriteBatchedInserts(true);

		var hikari = new HikariDataSource();
		hikari.setMaximumPoolSize(config.poolSize());
		hikari.setDataSource(pgds);
		this.dataSource = hikari;

		// Initialize sub-components
		this.channelManager = new ChannelManager();
		this.schemaHandler = new SchemaHandler(this.dataSource, config.rawRetentionDays(), config.rawCompressionDays());
		this.writeHandler = new WriteHandler(this.dataSource, this.channelManager, 4); // 4 threads for writing
		this.readHandler = new ReadHandler(this.dataSource, this.channelManager);

		// Apply database schema on startup. If the Database is unreachable here
		// (e.g. Postgres still booting), applySchema throws. The WriteHandler has
		// already spawned its worker threads and the Hikari pool is open, so we
		// must release both before rethrowing — otherwise each retry by the caller
		// would orphan 4 threads and a connection pool.
		try {
			this.schemaHandler.applySchema();
		} catch (SQLException | RuntimeException e) {
			this.writeHandler.deactivate();
			this.dataSource.close();
			throw e;
		}
	}

	/**
	 * Enqueues a batch of DataPoints to be written to TimescaleDB asynchronously.
	 * 
	 * @param points The points to write
	 * @throws SQLException never thrown (kept for signature compatibility)
	 */
	public void writeBatch(List<DataPoint> points) throws SQLException {
		this.writeHandler.writeBatch(points);
	}

	/**
	 * Queries historic data for a set of channels at a specific resolution.
	 * 
	 * @param edgeName       The Edge ID
	 * @param from           The start time
	 * @param to             The end time
	 * @param channels       The requested channels
	 * @param bucketSeconds  The bucket resolution in seconds
	 * @return A map of timestamps to channel values
	 * @throws SQLException on database error
	 */
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(
			String edgeName, ZonedDateTime from, ZonedDateTime to,
			Set<ChannelAddress> channels, long bucketSeconds) throws SQLException {
		return this.readHandler.queryHistoricData(edgeName, from, to, channels, bucketSeconds);
	}

	/**
	 * Queries the total historic energy consumed/produced during a period.
	 * 
	 * @param edgeName The Edge ID
	 * @param from     The start time
	 * @param to       The end time
	 * @param channels The requested channels
	 * @return A map of channels to their energy deltas
	 * @throws SQLException on database error
	 */
	public SortedMap<ChannelAddress, JsonElement> queryHistoricEnergy(
			String edgeName, ZonedDateTime from, ZonedDateTime to,
			Set<ChannelAddress> channels) throws SQLException {
		return this.readHandler.queryHistoricEnergy(edgeName, from, to, channels);
	}

	/**
	 * Queries historic energy per bucket resolution.
	 * 
	 * @param edgeName       The Edge ID
	 * @param from           The start time
	 * @param to             The end time
	 * @param channels       The requested channels
	 * @param bucketSeconds  The bucket resolution in seconds
	 * @return A map of timestamps to channel energy deltas
	 * @throws SQLException on database error
	 */
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyPerPeriod(
			String edgeName, ZonedDateTime from, ZonedDateTime to,
			Set<ChannelAddress> channels, long bucketSeconds) throws SQLException {
		return this.readHandler.queryHistoricEnergyPerPeriod(edgeName, from, to, channels, bucketSeconds);
	}

	/**
	 * Queries the most recent known value for a channel.
	 * 
	 * @param edgeName The Edge ID
	 * @param addr     The requested channel
	 * @return The latest value if present
	 * @throws SQLException on database error
	 */
	public Optional<Object> queryLatestValue(String edgeName, ChannelAddress addr) throws SQLException {
		return this.readHandler.queryLatestValue(edgeName, addr);
	}

	/**
	 * Returns failed-send timestamps (epoch seconds) newer than
	 * {@code lastResendTimestamp} for the given {@code notSendChannel}.
	 */
	public java.util.List<Long> getResendTimestamps(String edgeName,
			ChannelAddress notSendChannel, long lastResendTimestamp) throws SQLException {
		return this.readHandler.getResendTimestamps(edgeName, notSendChannel, lastResendTimestamp);
	}

	/**
	 * Returns raw samples for the given channels in {@code [from, to)} keyed by
	 * epoch-second timestamp, for resend payload assembly.
	 */
	public SortedMap<Long, SortedMap<ChannelAddress, JsonElement>> queryResendData(
			String edgeName, ZonedDateTime from, ZonedDateTime to,
			Set<ChannelAddress> channels) throws SQLException {
		return this.readHandler.queryResendData(edgeName, from, to, channels);
	}

	/**
	 * Shuts down background workers and closes the database connection pool.
	 */
	public void deactivate() {
		this.writeHandler.deactivate();
		if (this.dataSource != null && !this.dataSource.isClosed()) {
			this.dataSource.close();
		}
	}
}
