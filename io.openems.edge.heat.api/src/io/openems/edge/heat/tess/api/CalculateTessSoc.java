package io.openems.edge.heat.tess.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to calculate the overall State-of-Charge for Thermal Energy
 * Storage Systems using a capacity-weighted mean.
 *
 * <p>
 * The averaging method is selected via {@link SocAveragingMethod}:
 * <ul>
 * <li>{@link SocAveragingMethod#ARITHMETIC} — capacity-weighted arithmetic
 * mean
 * <li>{@link SocAveragingMethod#GEOMETRIC} — capacity-weighted geometric mean;
 * if any single TESS has a SOC of zero the overall result is zero, ensuring
 * that an empty storage always dominates the aggregate
 * </ul>
 *
 * <p>
 * If any capacity is unavailable, the calculation falls back to the unweighted
 * mean of the selected method.
 */
public class CalculateTessSoc {

	private static record Tess(int soc, Integer capacity) {
	}

	private final SocAveragingMethod method;
	private final List<Tess> tessComponents = new ArrayList<>();

	public CalculateTessSoc(SocAveragingMethod method) {
		this.method = method;
	}

	/**
	 * Adds a {@link ThermalEss}.
	 *
	 * @param tess the {@link ThermalEss}
	 */
	public synchronized void add(ThermalEss tess) {
		var soc = tess.getSoc().get();
		var capacity = tess.getCapacity().get();
		if (soc == null) {
			return;
		}
		this.tessComponents.add(new Tess(soc, capacity));
	}

	/**
	 * Calculates the overall State-of-Charge using the configured
	 * {@link SocAveragingMethod}, capacity-weighted if all capacities are
	 * available.
	 *
	 * @return the SoC value (0..100) or null if no values are available
	 */
	public synchronized Integer calculate() {
		var result = calculateWeightedMean(this.method, this.tessComponents);
		if (result != null) {
			return result;
		}
		return calculateUnweightedMean(this.method, this.tessComponents);
	}

	/**
	 * Calculate the SoC as a mean weighted by capacity.
	 *
	 * @param method     the {@link SocAveragingMethod}
	 * @param components the list of {@link Tess}
	 * @return the SoC or null if the list is empty or any capacity is missing
	 */
	private static Integer calculateWeightedMean(SocAveragingMethod method, List<Tess> components) {
		if (components.isEmpty()) {
			return null;
		}
		if (components.stream().anyMatch(t -> t.capacity() == null)) {
			return null;
		}

		var totalCapacity = components.stream().mapToDouble(Tess::capacity).sum();
		if (totalCapacity == 0) {
			return null;
		}

		return switch (method) {
		case ARITHMETIC -> {
			var sum = components.stream()
					.mapToDouble(t -> (t.capacity() / totalCapacity) * t.soc())
					.sum();
			yield (int) Math.round(sum);
		}
		case GEOMETRIC -> {
			if (components.stream().anyMatch(t -> t.soc() == 0)) {
				yield 0;
			}
			var logSum = components.stream()
					.mapToDouble(t -> (t.capacity() / totalCapacity) * Math.log(t.soc()))
					.sum();
			yield (int) Math.round(Math.exp(logSum));
		}
		};
	}

	/**
	 * Calculate the SoC as a simple (unweighted) mean. Used as fallback when
	 * capacity values are not available.
	 *
	 * @param method     the {@link SocAveragingMethod}
	 * @param components the list of {@link Tess}
	 * @return the SoC or null if the list is empty
	 */
	private static Integer calculateUnweightedMean(SocAveragingMethod method, List<Tess> components) {
		if (components.isEmpty()) {
			return null;
		}

		return switch (method) {
		case ARITHMETIC -> {
			var average = components.stream()
					.mapToDouble(Tess::soc)
					.average();
			if (average.isEmpty()) {
				yield null;
			}
			yield (int) Math.round(average.getAsDouble());
		}
		case GEOMETRIC -> {
			if (components.stream().anyMatch(t -> t.soc() == 0)) {
				yield 0;
			}
			var logSum = components.stream()
					.mapToDouble(t -> Math.log(t.soc()))
					.average();
			if (logSum.isEmpty()) {
				yield null;
			}
			yield (int) Math.round(Math.exp(logSum.getAsDouble()));
		}
		};
	}
}
