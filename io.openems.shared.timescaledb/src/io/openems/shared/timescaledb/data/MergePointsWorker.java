package io.openems.shared.timescaledb.data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.openems.common.worker.AbstractImmediateWorker;
import io.openems.shared.timescaledb.Type;

/**
 * Merges single {@link DataRow}s from its queue into batches and
 * hands them to the {@link BulkWriter} — one worker per {@link Type}, so every
 * batch targets exactly one hypertable and a slow lane (e.g. strings) cannot
 * delay the others.
 *
 * <p>
 * A batch is closed after {@value #MAX_POINTS_PER_WRITE} points or
 * {@value #MAX_AGGREGATE_WAIT_SECONDS} seconds, whichever comes first (same
 * pattern as {@code io.openems.shared.influxdb.MergePointsWorker}).
 */
public class MergePointsWorker extends AbstractImmediateWorker {

	private static final int MAX_POINTS_PER_WRITE = 10_000;
	private static final int MAX_AGGREGATE_WAIT_SECONDS = 10;
	private static final int POINTS_QUEUE_SIZE = 1_000_000;

	private final Type type;
	private final BulkWriter bulkWriter;
	private final ExecutorService executor;
	private final BlockingQueue<DataRow> queue = new ArrayBlockingQueue<>(POINTS_QUEUE_SIZE);

	public MergePointsWorker(Type type, BulkWriter bulkWriter, ExecutorService executor) {
		this.type = type;
		this.executor = executor;
		this.bulkWriter = bulkWriter;
	}

	/**
	 * Activates the worker thread.
	 */
	public void activate() {
		this.activate("TimescaleDB-Merge-" + this.type.name());
	}

	/**
	 * Adds a point, blocking while the queue is full — this back-pressures the
	 * {@link DataPointRouter} (and transitively the producers) instead of
	 * dropping data.
	 *
	 * @param point the point to enqueue
	 * @throws InterruptedException if interrupted while waiting for capacity
	 */
	public void put(DataRow point) throws InterruptedException {
		this.queue.put(point);
	}

	@Override
	protected void forever() throws InterruptedException {
		var points = this.pollPoints();
		if (points.isEmpty()) {
			return;
		}
		// Write asynchronously; the executor's CallerRunsPolicy executes in this
		// thread when all writer threads are busy — natural backpressure.
		this.executor.execute(() -> this.bulkWriter.write(this.type, points));
	}

	private List<DataRow> pollPoints() throws InterruptedException {
		final Instant deadline = Instant.now().plusSeconds(MAX_AGGREGATE_WAIT_SECONDS);
		var points = new ArrayList<DataRow>();
		for (int i = 0; i < MAX_POINTS_PER_WRITE; i++) {
			DataRow point;
			try {
				point = this.queue.poll(MAX_AGGREGATE_WAIT_SECONDS, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				if (points.isEmpty()) {
					throw e;
				}
				// shutting down — submit what we already collected
				Thread.currentThread().interrupt();
				break;
			}
			if (point == null) {
				break;
			}
			points.add(point);
			if (Instant.now().isAfter(deadline)) {
				break;
			}
		}
		return points;
	}

	/**
	 * Current number of queued points.
	 *
	 * @return the queue depth
	 */
	public int queueSize() {
		return this.queue.size();
	}

	/**
	 * Returns a DebugLog String.
	 *
	 * @return debug log
	 */
	public String debugLog() {
		final var queueSize = this.queue.size();
		return new StringBuilder() //
				.append(this.type.name()) //
				.append(":") //
				.append(queueSize) //
				.append("/") //
				.append(POINTS_QUEUE_SIZE) //
				.append((queueSize == POINTS_QUEUE_SIZE) ? " !!!BACKPRESSURE!!!" : "") //
				.toString();
	}
}
