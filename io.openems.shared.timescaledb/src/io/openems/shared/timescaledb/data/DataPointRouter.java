package io.openems.shared.timescaledb.data;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

import io.openems.common.worker.AbstractImmediateWorker;
import io.openems.shared.timescaledb.Type;
import io.openems.shared.timescaledb.schema.ChannelInfo;
import io.openems.shared.timescaledb.schema.ChannelManager;

/**
 * Drains raw {@link DataPoint}s from the source queue, resolves each channel
 * to its database identity via the {@link ChannelManager}, and routes the
 * resulting {@link DataRow}s into the per-{@link Type} queues of
 * the {@link MergePointsWorker}s.
 *
 * <p>
 * Resolution happens inline in this single thread: with the cache warmed up,
 * a database round-trip only occurs for genuinely new channels. Connection
 * errors during a resolve are retried with backoff (the point is preserved);
 * a point whose channel cannot be resolved for data reasons is dropped with a
 * log message.
 */
public class DataPointRouter extends AbstractImmediateWorker {

	private static final int MAX_CHUNK = 10_000;
	private static final long MAX_BACKOFF_MS = 10_000;

	private final Logger log = LoggerFactory.getLogger(DataPointRouter.class);

	private final HikariDataSource dataSource;
	private final ChannelManager channelManager;
	private final BlockingQueue<DataPoint> sourceQueue;
	private final Map<Type, MergePointsWorker> mergeWorkers;
	private final BooleanSupplier isRunning;

	public DataPointRouter(HikariDataSource dataSource, ChannelManager channelManager,
	                       BlockingQueue<DataPoint> sourceQueue, Map<Type, MergePointsWorker> mergeWorkers,
	                       BooleanSupplier isRunning) {
		this.dataSource = dataSource;
		this.channelManager = channelManager;
		this.sourceQueue = sourceQueue;
		this.mergeWorkers = mergeWorkers;
		this.isRunning = isRunning;
	}

	/**
	 * Activates the worker thread.
	 */
	public void activate() {
		this.activate("TimescaleDB-Router");
	}

	@Override
	protected void forever() throws InterruptedException {
		var first = this.sourceQueue.poll(1, TimeUnit.SECONDS);
		if (first == null) {
			return;
		}
		List<DataPoint> chunk = new ArrayList<>();
		chunk.add(first);
		this.sourceQueue.drainTo(chunk, MAX_CHUNK - 1);

		for (var point : chunk) {
			var info = this.channelManager.peekResolved(point);
			if (info == null) {
				info = this.resolve(point);
				if (info == null) {
					continue; // dropped, already logged
				}
			}
			this.mergeWorkers.get(info.type())
					.put(new DataRow(point.timestamp(), info.channelId(), info.aggregate(), point.value()));
		}
	}

	/**
	 * Resolves a channel via {@code get_or_create_channel_id}. Retries with
	 * backoff on connection errors while the pipeline is running; returns
	 * {@code null} (point is dropped) on a data error.
	 */
	private ChannelInfo resolve(DataPoint point) throws InterruptedException {
		long backoffMs = 1000;
		while (this.isRunning.getAsBoolean()) {
			try (var connection = this.dataSource.getConnection()) {
				return this.channelManager.resolveChannel(connection, point);
			} catch (SQLException | RuntimeException e) {
				if (BulkWriter.isConnectionError(e)) {
					this.log.warn("Database unavailable while resolving [{}]; retrying: {}",
							this.channelManager.encodeChannelKey(point), e.getMessage());
					Thread.sleep(backoffMs);
					if (backoffMs < MAX_BACKOFF_MS) {
						backoffMs *= 2;
					}
				} else {
					this.log.error("Unable to resolve channel for [{}]; dropping point",
							this.channelManager.encodeChannelKey(point), e);
					return null;
				}
			}
		}
		return null;
	}
}
