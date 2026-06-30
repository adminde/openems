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

public class TimescaleDbHandler {

	private final HikariDataSource dataSource;
	
	private final ChannelManager channelManager;
	private final SchemaHandler schemaHandler;
	private final WriteHandler writeHandler;
	private final ReadHandler readHandler;

	/**
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
		
		// Rewrite batched inserts into bulk inserts for performance.
		pgds.setReWriteBatchedInserts(true);

		var hikari = new HikariDataSource();
		hikari.setMaximumPoolSize(config.poolSize());
		hikari.setDataSource(pgds);
		this.dataSource = hikari;

		// Pick the deployment-specific ChannelManager + SchemaHandler. All other
		// variance between Edge (single-edge) and Backend (multi-edge) is confined
		// to these two classes; the read/write handlers stay deployment-agnostic.
		ChannelManager cm;
		SchemaHandler sh;
		switch (config.deployment()) {
		case EDGE -> {
			cm = new EdgeChannelManager();
			sh = new EdgeSchemaHandler(this.dataSource, config.rawRetentionDays(), config.rawCompressionDays(),
					config.createMinutelyAggregate());
		}
		case BACKEND -> {
			cm = new BackendChannelManager();
			sh = new BackendSchemaHandler(this.dataSource, config.rawRetentionDays(), config.rawCompressionDays(),
					config.createMinutelyAggregate());
		}
		default -> throw new IllegalArgumentException("Unsupported TimescaleDB deployment: " + config.deployment());
		}
		this.channelManager = cm;
		this.schemaHandler = sh;
		this.writeHandler = new WriteHandler(this.dataSource, this.channelManager, config.writeWorkers());
		this.readHandler = new ReadHandler(this.dataSource, this.channelManager, config.createMinutelyAggregate());

		// Apply schema on startup
		try {
			this.schemaHandler.applySchema();
		} catch (SQLException | RuntimeException e) {
			this.writeHandler.deactivate();
			this.dataSource.close();
			throw e;
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
		this.writeHandler.deactivate();
		if (this.dataSource != null && !this.dataSource.isClosed()) {
			this.dataSource.close();
		}
	}
}
