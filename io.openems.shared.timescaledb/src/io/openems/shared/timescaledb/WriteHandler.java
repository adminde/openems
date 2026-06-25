package io.openems.shared.timescaledb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Handles asynchronous, multi-threaded batched inserts to TimescaleDB.
 */
public class WriteHandler {

	private final Logger log = LoggerFactory.getLogger(WriteHandler.class);

	private final HikariDataSource dataSource;
	private final ChannelManager channelManager;
	
	// Queue to decouple WebSocket network threads from Database IO
	private final BlockingQueue<DataPoint> writeQueue = new ArrayBlockingQueue<>(1_000_000);
	
	private final ExecutorService executor;
	private volatile boolean isRunning = true;

	// Bounded retry on flush failure: enough attempts to ride out a brief DB
	// restart / failover / network blip, but bounded so a poison-pill batch
	// (e.g. a deterministically-failing row) can't loop forever and back the
	// queue up until block-on-full freezes the WebSocket threads.
	private static final int MAX_FLUSH_RETRIES = 3;
	private static final long FLUSH_RETRY_BASE_DELAY_MS = 500;

	/**
	 * Constructor for the WriteHandler.
	 * 
	 * @param dataSource      The Hikari connection pool
	 * @param channelManager  The ChannelManager for resolving channel IDs
	 * @param numThreads      Number of background worker threads
	 */
	public WriteHandler(HikariDataSource dataSource, ChannelManager channelManager, int numThreads) {
		this.dataSource = dataSource;
		this.channelManager = channelManager;
		this.executor = Executors.newFixedThreadPool(numThreads);
		
		for (int i = 0; i < numThreads; i++) {
			this.executor.submit(this::writeWorker);
		}
	}

	/**
	 * Adds a batch of data points to the processing queue.
	 * This method returns almost instantly, freeing the calling network thread.
	 *
	 * @param points The list of data points to insert
	 */
	public void writeBatch(List<DataPoint> points) {
		if (points == null || points.isEmpty()) {
			return;
		}
		
		for (DataPoint p : points) {
			try {
				this.writeQueue.put(p);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				this.log.warn("Thread interrupted while waiting to add to write queue", e);
			}
		}
	}

	/**
	 * The background worker loop that pulls from the queue and executes JDBC inserts.
	 */
	private void writeWorker() {
		List<DataPoint> buffer = new ArrayList<>(10_000);
		// Keep draining after isRunning flips so deactivate() doesn't strand
		// queued points. Bounded by the deadline in deactivate().
		while (this.isRunning || !this.writeQueue.isEmpty()) {
			try {
				// Block for up to 1 second waiting for the first point
				DataPoint first = this.writeQueue.poll(1, TimeUnit.SECONDS);
				if (first == null) {
					continue;
				}
				buffer.add(first);
				
				// Drain up to 9,999 more points currently sitting in the queue
				this.writeQueue.drainTo(buffer, 9_999);
				
				this.flushBuffer(buffer);
				buffer.clear();
				
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break; // Exit loop on shutdown
			} catch (Exception e) {
				if (this.isConnectionError(e)) {
					this.log.warn("Database unavailable. Worker pausing and retrying infinitely to preserve data...", e);
					int backoffMs = 1000;
					while (this.isRunning) {
						try {
							Thread.sleep(backoffMs);
							if (backoffMs < 10000) backoffMs *= 2;
							this.flushBuffer(buffer);
							break; // Success
						} catch (InterruptedException ie) {
							Thread.currentThread().interrupt();
							break;
						} catch (Exception retryE) {
							if (!this.isConnectionError(retryE)) {
								this.log.error("Error changed to data error. Dropping {} points.", buffer.size(), retryE);
								break;
							}
						}
					}
				} else {
					// Bounded retry handles the common transient cause (e.g. deadlock).
					// After MAX_FLUSH_RETRIES we drop to avoid a poison-pill batch looping forever.
					if (!this.retryFlush(buffer)) {
						this.log.error("Dropping {} points after {} failed flush attempts (Data Error)",
								buffer.size(), MAX_FLUSH_RETRIES, e);
					}
				}
				buffer.clear();
			}
		}
	}

	private boolean isConnectionError(Exception e) {
		Throwable cause = e;
		while (cause != null) {
			if (cause instanceof java.sql.SQLException) {
				String state = ((java.sql.SQLException) cause).getSQLState();
				// state == null: Hikari pool timeout
				// 08: Connection Exception
				// 53: Insufficient Resources
				// 57: Operator Intervention
				// 40P01: Deadlock detected
				if (state == null || state.startsWith("08") || state.startsWith("53") || state.startsWith("57")
						|| state.equals("40P01")) {
					return true;
				}
			}
			cause = cause.getCause();
		}
		return false;
	}

	/**
	 * Retries {@link #flushBuffer} up to {@link #MAX_FLUSH_RETRIES} times with
	 * exponential backoff. Returns {@code true} if a retry eventually succeeded.
	 *
	 * @param buffer The buffer that failed to flush
	 * @return {@code true} if a subsequent flush succeeded, {@code false} if all
	 *         retries were exhausted or the thread was interrupted
	 */
	private boolean retryFlush(List<DataPoint> buffer) {
		for (int attempt = 1; attempt <= MAX_FLUSH_RETRIES; attempt++) {
			try {
				Thread.sleep(FLUSH_RETRY_BASE_DELAY_MS * (1L << (attempt - 1)));
				this.flushBuffer(buffer);
				return true;
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				return false; // shutting down — give up
			} catch (Exception retryError) {
				this.log.warn("Flush retry {}/{} failed", attempt, MAX_FLUSH_RETRIES, retryError);
			}
		}
		return false;
	}

	/**
	 * Groups data points by target table and executes the batched INSERT.
	 * 
	 * @param points The points to insert
	 * @throws SQLException on database error
	 */
	private void flushBuffer(List<DataPoint> points) throws SQLException {
		if (points.isEmpty()) {
			return;
		}

		// PASS 1 — resolve every distinct channel
		var distinct = new LinkedHashMap<String, DataPoint>();
		for (DataPoint p : points) {
			distinct.merge(ChannelManager.channelKey(p), p, WriteHandler::mergeForResolve);
		}

		// Channels already warm in the cache need no Database round-trip, so a
		// steady-state flush (all channels known) opens no connection in pass 1.
		var infoByKey = new HashMap<String, ChannelInfo>();
		var toResolve = new ArrayList<DataPoint>();
		for (var entry : distinct.entrySet()) {
			var cached = this.channelManager.peekResolved(entry.getValue());
			if (cached != null) {
				infoByKey.put(entry.getKey(), cached);
			} else {
				toResolve.add(entry.getValue());
			}
		}

		if (!toResolve.isEmpty()) {
			toResolve.sort(Comparator.comparing(DataPoint::channelName)
					.thenComparing(DataPoint::componentAlias)
					.thenComparing(DataPoint::edgeName));
			try (Connection con = this.dataSource.getConnection()) {
				con.setAutoCommit(false);
				for (DataPoint p : toResolve) {
					infoByKey.put(ChannelManager.channelKey(p), this.channelManager.resolveChannel(con, p));
				}
				con.commit();
			}
		}

		// PASS 2 — pure appends into the data_* hypertables.
		try (Connection con = this.dataSource.getConnection()) {
			con.setAutoCommit(false);

			// Group points by target table
			Map<String, List<Object[]>> byTable = new HashMap<>();
			for (DataPoint p : points) {
				ChannelInfo info = infoByKey.get(ChannelManager.channelKey(p));
				String table = tableFor(info.dataType());
				byTable.computeIfAbsent(table, k -> new ArrayList<>())
						.add(new Object[] { p.timestamp(), info.channelId(), p.value(), info.core() });
			}

			for (Map.Entry<String, List<Object[]>> entry : byTable.entrySet()) {
				String sql = "INSERT INTO " + entry.getKey() + " (time, channel_id, core, value) VALUES (?,?,?,?)";
				try (PreparedStatement pst = con.prepareStatement(sql)) {
					for (Object[] row : entry.getValue()) {
						OffsetDateTime utcTime = OffsetDateTime.ofInstant(
								Instant.ofEpochMilli((long) row[0]), ZoneOffset.UTC);
						pst.setObject(1, utcTime);
						pst.setObject(2, row[1]);
						pst.setBoolean(3, (boolean) row[3]);
						pst.setObject(4, row[2]);
						pst.addBatch();
					}
					pst.executeBatch();
				}
			}
			con.commit();
		}
	}

	/**
	 * Merges two DataPoints for the same channel into the strongest resolve input:
	 * core uses "highest wins" (Fast Lane is sticky) and the unit takes the first
	 * non-null value. Identity fields are taken from {@code base}. Ensures the
	 * single pass-1 resolve performs any needed promotion / unit backfill, so
	 * pass 2 never has to re-resolve.
	 *
	 * @param base     the representative kept so far
	 * @param incoming a further point for the same channel
	 * @return a DataPoint carrying the merged core and unit
	 */
	private static DataPoint mergeForResolve(DataPoint base, DataPoint incoming) {
		boolean core = base.core() || incoming.core();
		String unit = base.unit() != null ? base.unit() : incoming.unit();
		if (core == base.core() && java.util.Objects.equals(unit, base.unit())) {
			return base;
		}
		return new DataPoint(base.timestamp(), base.edgeName(), base.componentAlias(), base.componentType(),
				base.channelName(), base.dataType(), core, unit, base.value());
	}

	/**
	 * Determines the target raw table for a given data type.
	 *
	 * @param dataType The SQL data type (e.g. INTEGER, FLOAT)
	 * @return The target raw table name
	 */
	private static String tableFor(String dataType) {
		return switch (dataType) {
		case "INTEGER" -> "data_integer";
		case "FLOAT"   -> "data_float";
		default        -> "data_string";
		};
	}

	/**
	 * Gracefully shuts down the executor and prevents new data from being added.
	 */
	public void deactivate() {
		this.isRunning = false;
		this.executor.shutdown();
		try {
			// Workers keep draining the queue once isRunning is false; the
			// deadline is the hard ceiling so a stuck database can't block past
			// container-kill windows (k8s default 30s, systemd default 10s).
			// Anything still queued at the deadline is forfeited — the Edge
			// will replay it on reconnect to the next backend instance.
			if (!this.executor.awaitTermination(30, TimeUnit.SECONDS)) {
				this.log.warn("Shutdown deadline reached with {} points still queued; forcing termination",
						this.writeQueue.size());
				this.executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			this.executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}
