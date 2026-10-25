package io.openems.shared.timescaledb.data;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * log message. So is a value that cannot be converted to the stored value
 * type of its channel.
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

	/** Channels already reported with a value that does not fit their type, to log each one once. */
	private final Set<String> invalidValueWarned = new HashSet<>();

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
			var value = this.toStoredType(point, info);
			if (value == null) {
				continue; // dropped, already logged
			}
			this.mergeWorkers.get(info.type())
					.put(new DataRow(point.timestamp(), info.channelId(), info.aggregate(), value));
		}
	}

	/**
	 * Converts the value of a point to the stored value type of its channel,
	 * which wins when the two disagree. A value that cannot be converted, like an
	 * empty string for an INTEGER channel, is dropped here, because it would fail
	 * the whole batch it is written with.
	 *
	 * @param point the {@link DataPoint}
	 * @param info  the resolved channel
	 * @return the converted value, or {@code null} if the point has to be dropped
	 */
	private Object toStoredType(DataPoint point, ChannelInfo info) {
		if (point.type() == info.type()) {
			return point.value();
		}
		try {
			return info.type().coerce(point.value());
		} catch (NumberFormatException e) {
			var channel = this.channelManager.encodeChannelKey(point);
			if (this.invalidValueWarned.add(channel)) {
				this.log.warn("Dropping values of [{}]: the channel is stored as {}, but got the {} value \"{}\". "
						+ "Check channel_def.type of this channel.", channel, info.type(), point.type(), point.value());
			}
			return null;
		}
	}

	/**
	 * Resolves a channel via {@code get_or_create_channel_id}. Retries with
	 * backoff on connection errors while the pipeline is running; returns
	 * {@code null} (point is dropped) on a data error.
	 *
	 * @param point the {@link DataPoint} whose channel to resolve
	 * @return the resolved {@link ChannelInfo}, or {@code null} if the point has
	 *         to be dropped
	 * @throws InterruptedException if interrupted while backing off
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
