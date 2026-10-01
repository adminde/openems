package io.openems.edge.heat.tess.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Estimates the net thermal power of a storage from the change of its mean
 * temperature over a sliding time window.
 *
 * <p>
 * Temperature sensors resolve 0.1 K, which equals several kilowatts within a
 * single second for typical storage volumes. The window smooths this
 * quantization; no estimate is available before the window is covered for the
 * first time.
 */
public class NetThermalPowerEstimator {

	private record Sample(Instant timestamp, double temperature) {
	}

	private final Duration window;
	private final double heatCapacity;
	private final Deque<Sample> samples = new ArrayDeque<>();

	/**
	 * Creates an estimator for a water volume.
	 *
	 * @param volume the water volume in [L]
	 * @param window the length of the sliding window
	 */
	public NetThermalPowerEstimator(double volume, Duration window) {
		this.heatCapacity = volume * StratifiedTank.WATER_DENSITY * StratifiedTank.WATER_SPECIFIC_HEAT;
		this.window = window;
	}

	/**
	 * Adds the current mean temperature and estimates the net thermal power.
	 *
	 * @param now         the current time
	 * @param temperature the mean temperature in [°C]
	 * @return the net thermal power in [W], positive for charging; or null if the
	 *         window is not covered yet
	 */
	public Integer update(Instant now, double temperature) {
		this.samples.addLast(new Sample(now, temperature));
		var windowStart = now.minus(this.window);
		while (this.samples.size() > 1) {
			var iterator = this.samples.iterator();
			iterator.next();
			if (iterator.next().timestamp().isAfter(windowStart)) {
				break;
			}
			this.samples.removeFirst();
		}
		var oldest = this.samples.getFirst();
		if (oldest.timestamp().isAfter(windowStart)) {
			return null;
		}
		var seconds = Duration.between(oldest.timestamp(), now).toMillis() / 1000.0;
		return (int) Math.round(this.heatCapacity * (temperature - oldest.temperature()) / seconds);
	}

	/**
	 * Treats a step of the mean temperature as a correction of the estimate
	 * instead of a change of the stored heat, e.g. when additional temperatures
	 * become available. All samples are shifted, so the step does not appear as
	 * thermal power.
	 *
	 * @param temperature the corrected mean temperature in [°C] at the time of the
	 *                    latest sample
	 */
	public void rebase(double temperature) {
		if (this.samples.isEmpty()) {
			return;
		}
		var offset = temperature - this.samples.getLast().temperature();
		var shifted = this.samples.stream() //
				.map(sample -> new Sample(sample.timestamp(), sample.temperature() + offset)) //
				.toList();
		this.samples.clear();
		this.samples.addAll(shifted);
	}
}
