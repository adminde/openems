package io.openems.shared.timescaledb.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Rebuilds every second whose data was not sent to the Backend from the rows of
 * the not-sent marker Channel.
 *
 * <p>
 * The resend worker merges these timestamps into ranges and cuts the ranges
 * into chunks that start and end on a timestamp. A second without a timestamp
 * inside an outage would fall between two chunks and never be resent.
 *
 * <p>
 * The Edge writes the marker like every other Channel only when it changes and
 * once per {@link ChangeFilter#SNAPSHOT_INTERVAL}. So a non-zero row marks its
 * own second and every following second until the next row of the Channel,
 * for at most {@link #HOLD_SECONDS}. A longer silence means that the Edge did
 * not run or the marker had no value, so that time is not marked. The newest
 * row marks only its own second. The resend worker adds a buffer after every
 * range, which covers the time after it.
 */
public class ResendMarkers {

	/**
	 * How long a non-zero row marks the following seconds. It is twice the
	 * snapshot interval, so that a delayed Cycle does not cut an outage apart.
	 */
	public static final long HOLD_SECONDS = 2 * ChangeFilter.SNAPSHOT_INTERVAL.toSeconds();

	private final List<Long> timestamps = new ArrayList<>();
	private Long notSentSince = null;

	/**
	 * Adds the next row of the marker Channel. Rows have to be added in
	 * ascending order of time.
	 *
	 * @param epochSecond the time of the row in epoch seconds
	 * @param notSent     whether the row marks data that was not sent
	 */
	public void add(long epochSecond, boolean notSent) {
		if (this.notSentSince != null) {
			var end = Math.min(epochSecond, this.notSentSince + HOLD_SECONDS);
			for (var second = this.notSentSince + 1; second < end; second++) {
				this.timestamps.add(second);
			}
		}
		if (!notSent) {
			this.notSentSince = null;
			return;
		}
		// Two Cycles can share one second.
		if (this.timestamps.isEmpty() || this.timestamps.getLast() != epochSecond) {
			this.timestamps.add(epochSecond);
		}
		this.notSentSince = epochSecond;
	}

	/**
	 * Gets the seconds whose data was not sent.
	 *
	 * @return the timestamps in epoch seconds, ascending
	 */
	public List<Long> getTimestamps() {
		return this.timestamps;
	}
}
