package io.openems.shared.timescaledb.data;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Decides which Channel values the Edge writes, so the raw hypertables grow
 * with the changes of the Channels instead of with the number of Cycles.
 *
 * <p>
 * A value is written when it differs from the value last written for its
 * Channel. In addition, every Channel is written in the first Cycle of every
 * {@link #SNAPSHOT_INTERVAL}, so every one minute bucket holds at least one row
 * of every Channel that had a value.
 *
 * <p>
 * Aggregate channels are written in every Cycle. The continuous aggregates
 * average over rows, not over time. With sparse rows, a minute in which a
 * Channel fluctuates would outweigh the minutes in which it stayed constant.
 *
 * <p>
 * All methods are called from the Cycle thread, so the class is not
 * synchronized.
 */
public class ChangeFilter {

	/**
	 * Every Channel that has a value is written at least once per interval. The
	 * intervals are aligned to the epoch, like the buckets of the one minute
	 * aggregate.
	 */
	public static final Duration SNAPSHOT_INTERVAL = Duration.ofMinutes(1);

	private final Map<Object, Object> lastValues = new HashMap<>();
	private long period = Long.MIN_VALUE;

	/**
	 * Starts a Cycle. The first Cycle of a new {@link #SNAPSHOT_INTERVAL} forgets
	 * all written values, so every Channel is written again.
	 *
	 * @param timestamp the timestamp of the Cycle in epoch milliseconds
	 */
	public void startCycle(long timestamp) {
		var period = Math.floorDiv(timestamp, SNAPSHOT_INTERVAL.toMillis());
		if (period != this.period) {
			this.period = period;
			this.lastValues.clear();
		}
	}

	/**
	 * Whether a value has to be written. The value of a Channel that is not
	 * aggregated is remembered for the comparison in later Cycles.
	 *
	 * @param channel   the Channel, used as key
	 * @param value     the value, not null
	 * @param aggregate whether the Channel is in the aggregate layer
	 * @return true if the value has to be written
	 */
	public boolean shouldWrite(Object channel, Object value, boolean aggregate) {
		if (aggregate) {
			return true;
		}
		return !value.equals(this.lastValues.put(channel, value));
	}

	/**
	 * Forgets the last written value of a Channel whose value is undefined, so
	 * its next value is written even if it equals the old one.
	 *
	 * @param channel the Channel, used as key
	 */
	public void forget(Object channel) {
		this.lastValues.remove(channel);
	}
}
