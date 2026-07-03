package io.openems.shared.timescaledb.data;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

import io.openems.shared.timescaledb.Type;
import io.openems.shared.timescaledb.schema.ChannelManager;

/**
 * Orchestrates the asynchronous write pipeline:
 *
 * <pre>
 * writeBatch() → source queue → {@link DataPointRouter} (resolve + route)
 *              → per-{@link Type} {@link MergePointsWorker} (batch up)
 *              → {@link BulkWriter} (binary COPY, retry policy)
 * </pre>
 *
 * <p>
 * Every queue is bounded and every stage blocks when its successor is full, so
 * overload back-pressures the producers instead of dropping data or growing
 * unbounded. A scheduled monitor logs queue depth and drain rate.
 */
public class WriteHandler {

	private static final int QUEUE_CAPACITY = 1_000_000;

	// Backlog monitoring. The monitor logs queue depth and drain rate on a fixed
	// cadence so a database that can't keep up is visible in the logs before the
	// queues fill and back-pressure the producers.
	private static final long MONITOR_INTERVAL_SECONDS = 30;
	private static final int QUEUE_LOG_THRESHOLD = QUEUE_CAPACITY / 100; // 1% — stay quiet below this
	private static final int QUEUE_HIGH_WATER = QUEUE_CAPACITY / 2;      // 50% — escalate to WARN

	private static final long SHUTDOWN_DRAIN_TIMEOUT_MS = 30_000;

	private final Logger log = LoggerFactory.getLogger(WriteHandler.class);

	// Queue to decouple the producers (OSGi event / WebSocket threads) from
	// Database IO.
	private final BlockingQueue<DataPoint> sourceQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

	private final Map<Type, MergePointsWorker> mergeWorkers = new EnumMap<>(Type.class);
	private final BulkWriter bulkWriter;
	private final DataPointRouter router;
	private final ThreadPoolExecutor executor;
	private final ScheduledExecutorService monitor;

	private volatile boolean isRunning = true;
	private long lastWrittenSnapshot = 0;

	public WriteHandler(HikariDataSource dataSource, ChannelManager channelManager, int numThreads) {
		int workers = Math.max(1, numThreads); // never leave the pipeline without writers
		if (workers != numThreads) {
			this.log.warn("Configured writeWorkers={} is invalid; using {}", numThreads, workers);
		}
		this.bulkWriter = new BulkWriter(dataSource, () -> this.isRunning);

		// Bounded write parallelism: with a SynchronousQueue and CallerRunsPolicy
		// a batch is executed immediately by a free writer thread, or (when all
		// are busy) in the submitting MergePointsWorker thread itself, which
		// pauses that lane and back-pressures naturally.
		var threadNumber = new AtomicInteger(0);
		this.executor = new ThreadPoolExecutor(workers, workers, 60, TimeUnit.SECONDS,
				new SynchronousQueue<>(),
				r -> new Thread(r, "TimescaleDB-Write-" + threadNumber.getAndIncrement()),
				new ThreadPoolExecutor.CallerRunsPolicy());
		this.executor.allowCoreThreadTimeOut(true);

		for (var type : Type.values()) {
			var worker = new MergePointsWorker(type, this.bulkWriter, this.executor);
			this.mergeWorkers.put(type, worker);
			worker.activate();
		}

		this.router = new DataPointRouter(dataSource, channelManager, this.sourceQueue, this.mergeWorkers,
				() -> this.isRunning);
		this.router.activate();

		this.monitor = Executors.newSingleThreadScheduledExecutor(r -> {
			var thread = new Thread(r, "TimescaleDB-Monitor");
			thread.setDaemon(true);
			return thread;
		});
		this.monitor.scheduleAtFixedRate(this::logQueueDepth,
				MONITOR_INTERVAL_SECONDS, MONITOR_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	/**
	 * Adds a batch of data points to the pipeline. Blocks when the source queue
	 * is full (backpressure) instead of dropping data.
	 *
	 * @param points the points to write
	 */
	public void writeBatch(List<DataPoint> points) {
		if (points == null || points.isEmpty()) {
			return;
		}
		for (DataPoint point : points) {
			try {
				this.sourceQueue.put(point);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				this.log.warn("Thread interrupted while waiting to add to write queue", e);
				return;
			}
		}
	}

	/**
	 * Logs the current backlog and the drain rate since the last tick. Stays
	 * silent while the backlog is negligible. Escalates to WARN once it passes
	 * the high-water mark. Never throws an exception. Exceptions here would
	 * cancel all future scheduled runs.
	 */
	private void logQueueDepth() {
		try {
			int depth = this.queueSize();
			long written = this.bulkWriter.totalWritten();
			long rate = (written - this.lastWrittenSnapshot) / MONITOR_INTERVAL_SECONDS;
			this.lastWrittenSnapshot = written;

			if (depth < QUEUE_LOG_THRESHOLD) {
				return;
			}
			int pct = depth * 100 / QUEUE_CAPACITY;
			if (depth >= QUEUE_HIGH_WATER) {
				this.log.warn("TimescaleDB write backlog HIGH: {} points ({}% of capacity), draining ~{} points/s [{}]",
						depth, pct, rate, this.lanesDebugLog());
			} else {
				this.log.info("TimescaleDB write backlog: {} points ({}% of capacity), draining ~{} points/s [{}]",
						depth, pct, rate, this.lanesDebugLog());
			}
		} catch (Exception e) {
			this.log.warn("Queue-depth monitor failed", e);
		}
	}

	private String lanesDebugLog() {
		var sb = new StringBuilder("source:").append(this.sourceQueue.size());
		for (var worker : this.mergeWorkers.values()) {
			sb.append(" ").append(worker.debugLog());
		}
		return sb.toString();
	}

	/**
	 * Current number of points anywhere in the pipeline (source queue plus all
	 * per-type queues).
	 *
	 * @return the backlog depth
	 */
	public int queueSize() {
		var size = this.sourceQueue.size();
		for (var worker : this.mergeWorkers.values()) {
			size += worker.queueSize();
		}
		return size;
	}

	/**
	 * Capacity of the source queue.
	 *
	 * @return the capacity
	 */
	public int queueCapacity() {
		return QUEUE_CAPACITY;
	}

	/**
	 * Total number of points written since start.
	 *
	 * @return the counter value
	 */
	public long totalWritten() {
		return this.bulkWriter.totalWritten();
	}

	/**
	 * Shuts the pipeline down: waits (bounded) for the queues to drain, then
	 * stops router, merge workers and writer threads.
	 */
	public void deactivate() {
		// Best-effort drain while the pipeline is still running.
		var deadline = System.currentTimeMillis() + SHUTDOWN_DRAIN_TIMEOUT_MS;
		while (this.queueSize() > 0 && System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		if (this.queueSize() > 0) {
			this.log.warn("Shutdown drain deadline reached with {} points still queued", this.queueSize());
		}

		this.isRunning = false;
		this.monitor.shutdownNow();
		this.router.deactivate();
		this.mergeWorkers.values().forEach(MergePointsWorker::deactivate);

		this.executor.shutdown();
		try {
			if (!this.executor.awaitTermination(30, TimeUnit.SECONDS)) {
				this.log.warn("Writer threads did not terminate in time; forcing shutdown");
				this.executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			this.executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}
