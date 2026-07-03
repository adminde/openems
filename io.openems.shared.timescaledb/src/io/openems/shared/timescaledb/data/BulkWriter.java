package io.openems.shared.timescaledb.data;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

import de.bytefish.pgbulkinsert.row.SimpleRowWriter;
import de.bytefish.pgbulkinsert.util.PostgreSqlUtils;
import io.openems.shared.timescaledb.Type;

/**
 * Writes one batch of {@link DataRow}s per call into its raw
 * hypertable, streamed via the PostgreSQL binary COPY protocol (pgBulkInsert).
 *
 * <p>
 * Failure handling distinguishes two cases: connection errors are retried
 * infinitely with backoff (the batch is preserved until the database returns),
 * while data errors get a small bounded number of retries so a poison batch
 * cannot stall the pipeline forever.
 */
public class BulkWriter {

	private static final int MAX_DATA_ERROR_RETRIES = 3;
	private static final long RETRY_BASE_DELAY_MS = 500;
	private static final long MAX_BACKOFF_MS = 10_000;

	private final Logger log = LoggerFactory.getLogger(BulkWriter.class);

	private final HikariDataSource dataSource;
	private final BooleanSupplier isRunning;
	private final AtomicLong totalWritten = new AtomicLong();

	public BulkWriter(HikariDataSource dataSource, BooleanSupplier isRunning) {
		this.dataSource = dataSource;
		this.isRunning = isRunning;
	}

	/**
	 * Writes one batch, applying the retry policy described in the class javadoc.
	 * Never throws — this runs detached on a writer thread.
	 *
	 * @param type   the value type; determines the target hypertable
	 * @param points the batch to write
	 */
	public void write(Type type, List<DataRow> points) {
		if (points.isEmpty()) {
			return;
		}
		try {
			this.writeOnce(type, points);
		} catch (Exception e) {
			if (isConnectionError(e)) {
				this.retryUntilConnected(type, points, e);
			} else if (!this.retryBounded(type, points)) {
				this.log.error("Dropping {} points after {} failed attempts (data error)",
						points.size(), MAX_DATA_ERROR_RETRIES, e);
			}
		}
	}

	private void retryUntilConnected(Type type, List<DataRow> points, Exception first) {
		this.log.warn("Database unavailable. Retrying infinitely to preserve data...", first);
		long backoffMs = 1000;
		while (this.isRunning.getAsBoolean()) {
			try {
				Thread.sleep(backoffMs);
				if (backoffMs < MAX_BACKOFF_MS) {
					backoffMs *= 2;
				}
				this.writeOnce(type, points);
				return;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception e) {
				if (!isConnectionError(e)) {
					this.log.error("Error changed to data error. Dropping {} points.", points.size(), e);
					return;
				}
			}
		}
	}

	private boolean retryBounded(Type type, List<DataRow> points) {
		for (int attempt = 1; attempt <= MAX_DATA_ERROR_RETRIES; attempt++) {
			try {
				Thread.sleep(RETRY_BASE_DELAY_MS * (1L << (attempt - 1)));
				this.writeOnce(type, points);
				return true;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			} catch (Exception e) {
				this.log.warn("Write retry {}/{} failed", attempt, MAX_DATA_ERROR_RETRIES, e);
			}
		}
		return false;
	}

	private void writeOnce(Type type, List<DataRow> points) throws SQLException {
		try (var connection = this.dataSource.getConnection()) {
			var table = new SimpleRowWriter.Table(null, type.rawTableName,
					new String[] { "time", "channel_id", "rollup", "value" });
			try (var writer = new SimpleRowWriter(table, PostgreSqlUtils.getPGConnection(connection))) {
				for (var point : points) {
					writer.startRow(row -> {
						row.setTimeStampTz(0, ZonedDateTime.ofInstant(
								Instant.ofEpochMilli(point.timestamp()), ZoneOffset.UTC));
						row.setUUID(1, point.channelId());
						row.setBoolean(2, point.rollup());
						switch (type) {
						case INTEGER -> row.setLong(3, ((Number) point.value()).longValue());
						case FLOAT -> row.setDouble(3, ((Number) point.value()).doubleValue());
						default -> row.setText(3, point.value().toString());
						}
					});
				}
			} // writer.close() ends the COPY and flushes
		}
		this.totalWritten.addAndGet(points.size());
	}

	/**
	 * Total number of points written since start.
	 *
	 * @return the counter value
	 */
	public long totalWritten() {
		return this.totalWritten.get();
	}

	/**
	 * Whether the given exception chain indicates a connectivity problem (worth
	 * retrying indefinitely) rather than a data problem.
	 *
	 * @param e the exception
	 * @return true for connection errors
	 */
	static boolean isConnectionError(Throwable e) {
		Throwable cause = e;
		while (cause != null) {
			if (cause instanceof java.io.IOException) {
				// e.g. socket failure during a binary COPY (wrapped by pgBulkInsert
				// in a RuntimeException)
				return true;
			}
			if (cause instanceof SQLException se) {
				String state = se.getSQLState();
				// state == null: Hikari pool timeout
				// 08: Connection Exception
				// 53: Insufficient Resources
				// 57: Operator Intervention
				// 40P01: Deadlock detected
				if (state == null
						|| state.startsWith("08")
						|| state.startsWith("53")
						|| state.startsWith("57")
						|| state.equals("40P01")) {
					return true;
				}
			}
			cause = cause.getCause();
		}
		return false;
	}
}
