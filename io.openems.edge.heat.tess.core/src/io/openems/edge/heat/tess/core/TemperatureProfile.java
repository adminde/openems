package io.openems.edge.heat.tess.core;

import java.util.Map;
import java.util.TreeMap;

/**
 * Estimates the vertical temperature profile of a storage tank from
 * temperatures known at certain heights.
 *
 * <p>
 * Heights are relative to the tank height, from 0 at the bottom to 1 at the
 * top. Between two support points the temperature is interpolated linearly;
 * above the highest and below the lowest support point it continues
 * constantly. A single support point therefore describes a fully mixed tank.
 */
public class TemperatureProfile {

	private final TreeMap<Double, Double> points = new TreeMap<>();

	/**
	 * Adds a support point. A support point at the same height is replaced.
	 *
	 * @param height      the relative height, clamped to 0..1
	 * @param temperature the temperature in [°C]
	 * @return myself
	 */
	public TemperatureProfile add(double height, double temperature) {
		this.points.put(Math.max(0, Math.min(1, height)), temperature);
		return this;
	}

	/**
	 * Gets the mean temperature over the tank height, i.e. the volume-weighted
	 * mean temperature of a tank with constant cross-section.
	 *
	 * @return the mean temperature in [°C], or null without support points
	 */
	public Double getMeanTemperature() {
		if (this.points.isEmpty()) {
			return null;
		}
		var lowest = this.points.firstEntry();
		var highest = this.points.lastEntry();
		var integral = lowest.getValue() * lowest.getKey() + highest.getValue() * (1 - highest.getKey());
		Map.Entry<Double, Double> previous = null;
		for (var point : this.points.entrySet()) {
			if (previous != null) {
				integral += (point.getKey() - previous.getKey()) * (point.getValue() + previous.getValue()) / 2;
			}
			previous = point;
		}
		return integral;
	}

	/**
	 * Gets the highest temperature of the profile.
	 *
	 * @return the temperature in [°C], or null without support points
	 */
	public Double getMaxTemperature() {
		return this.points.values().stream() //
				.max(Double::compare) //
				.orElse(null);
	}
}
