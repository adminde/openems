package io.openems.edge.heat.tess.core;

import java.util.Arrays;

/**
 * One-dimensional multi-node model of a stratified water storage tank.
 *
 * <p>
 * The tank is divided into nodes of equal height and mass, indexed from the
 * bottom (0) to the top. Heights are relative to the tank height, from 0 at
 * the bottom to 1 at the top. Water entering and leaving through connections
 * moves the nodes in between as plug flow; heat exchangers and heating
 * elements transfer heat into nodes without exchanging water. After every
 * step, temperature inversions are mixed out, which approximates buoyancy.
 */
public class StratifiedTank {

	/** Specific heat capacity of water in [J/(kg·K)]. */
	public static final double WATER_SPECIFIC_HEAT = 4181.3;

	/** Density of water in [kg/L]. */
	public static final double WATER_DENSITY = 1.0;

	/**
	 * Largest share of a node's mass exchanged within one integration step. Keeps
	 * the explicit plug-flow scheme stable for long time steps.
	 */
	private static final double MAX_EXCHANGE_SHARE = 0.5;

	/**
	 * Smallest temperature difference between inlet and outlet in [K] that still
	 * drives a heat transfer by flow.
	 */
	private static final double MIN_FLOW_TEMPERATURE_DIFFERENCE = 0.1;

	private static final double INVERSION_TOLERANCE = 1e-9;

	private final double[] temperatures;
	private final double nodeMass;

	/**
	 * Creates a tank at a uniform temperature.
	 *
	 * @param volume             the water volume in [L], positive
	 * @param nodes              the number of nodes, at least 1
	 * @param initialTemperature the initial temperature in [°C]
	 */
	public StratifiedTank(double volume, int nodes, double initialTemperature) {
		if (volume <= 0) {
			throw new IllegalArgumentException("Volume must be positive, but was " + volume);
		}
		if (nodes < 1) {
			throw new IllegalArgumentException("Nodes must be at least 1, but was " + nodes);
		}
		this.temperatures = new double[nodes];
		Arrays.fill(this.temperatures, initialTemperature);
		this.nodeMass = volume * WATER_DENSITY / nodes;
	}

	/**
	 * Gets the temperature of the node at a relative height.
	 *
	 * @param height the relative height, clamped to 0..1
	 * @return the temperature in [°C]
	 */
	public double getTemperatureAt(double height) {
		return this.temperatures[this.nodeAt(height)];
	}

	/**
	 * Gets the mean temperature of the tank.
	 *
	 * @return the temperature in [°C]
	 */
	public double getMeanTemperature() {
		return Arrays.stream(this.temperatures).average().orElse(0);
	}

	/**
	 * Gets the stored heat relative to a reference temperature.
	 *
	 * @param referenceTemperature the reference temperature in [°C]
	 * @return the energy in [Wh]
	 */
	public double getEnergy(double referenceTemperature) {
		var mass = this.nodeMass * this.temperatures.length;
		return (this.getMeanTemperature() - referenceTemperature) * mass * WATER_SPECIFIC_HEAT / 3600.0;
	}

	/**
	 * Transfers heat by plug flow: water at the inlet temperature enters at the
	 * inlet height while the same mass leaves at the outlet height. The mass flow
	 * follows from the power and the temperature difference between inlet and
	 * outlet. The tank gains heat if the inlet is warmer than the outlet and loses
	 * heat otherwise; the transfer stops once inlet and outlet have the same
	 * temperature.
	 *
	 * @param power            the transferred thermal power in [W]
	 * @param inletTemperature the temperature of the entering water in [°C]
	 * @param inletHeight      the relative height of the inlet
	 * @param outletHeight     the relative height of the outlet
	 * @param seconds          the duration in [s]
	 */
	public void flow(double power, double inletTemperature, double inletHeight, double outletHeight,
			double seconds) {
		if (power <= 0 || seconds <= 0) {
			return;
		}
		var inlet = this.nodeAt(inletHeight);
		var outlet = this.nodeAt(outletHeight);
		var remaining = seconds;
		while (remaining > 0) {
			var difference = Math.abs(inletTemperature - this.temperatures[outlet]);
			if (difference < MIN_FLOW_TEMPERATURE_DIFFERENCE) {
				return;
			}
			var massFlow = power / (WATER_SPECIFIC_HEAT * difference);
			var step = Math.min(remaining, MAX_EXCHANGE_SHARE * this.nodeMass / massFlow);
			this.exchange(massFlow * step / this.nodeMass, inletTemperature, inlet, outlet);
			this.mixInversions();
			remaining -= step;
		}
	}

	/**
	 * Transfers heat without exchanging water, evenly distributed over the nodes
	 * between two heights, e.g. by a heat exchanger coil or a heating element.
	 * Negative power extracts heat.
	 *
	 * @param power      the thermal power in [W]
	 * @param fromHeight the relative height where the heat transfer begins
	 * @param toHeight   the relative height where the heat transfer ends
	 * @param seconds    the duration in [s]
	 */
	public void heat(double power, double fromHeight, double toHeight, double seconds) {
		if (power == 0 || seconds <= 0) {
			return;
		}
		var lower = this.nodeAt(Math.min(fromHeight, toHeight));
		var upper = this.nodeAt(Math.max(fromHeight, toHeight));
		var deltaTemperature = power * seconds / ((upper - lower + 1) * this.nodeMass * WATER_SPECIFIC_HEAT);
		for (var node = lower; node <= upper; node++) {
			this.temperatures[node] += deltaTemperature;
		}
		this.mixInversions();
	}

	/**
	 * Moves the given share of a node mass from the inlet towards the outlet.
	 *
	 * @param share            the exchanged mass relative to the node mass
	 * @param inletTemperature the temperature of the entering water in [°C]
	 * @param inlet            the inlet node
	 * @param outlet           the outlet node
	 */
	private void exchange(double share, double inletTemperature, int inlet, int outlet) {
		var previous = this.temperatures.clone();
		if (inlet >= outlet) {
			for (var node = outlet; node < inlet; node++) {
				this.temperatures[node] += share * (previous[node + 1] - previous[node]);
			}
		} else {
			for (var node = inlet + 1; node <= outlet; node++) {
				this.temperatures[node] += share * (previous[node - 1] - previous[node]);
			}
		}
		this.temperatures[inlet] += share * (inletTemperature - previous[inlet]);
	}

	/**
	 * Mixes neighboring nodes until no node is warmer than the node above.
	 */
	private void mixInversions() {
		boolean mixed;
		do {
			mixed = false;
			for (var node = 0; node < this.temperatures.length - 1; node++) {
				if (this.temperatures[node] > this.temperatures[node + 1] + INVERSION_TOLERANCE) {
					var mean = (this.temperatures[node] + this.temperatures[node + 1]) / 2;
					this.temperatures[node] = mean;
					this.temperatures[node + 1] = mean;
					mixed = true;
				}
			}
		} while (mixed);
	}

	private int nodeAt(double height) {
		var node = (int) Math.floor(height * this.temperatures.length);
		return Math.max(0, Math.min(this.temperatures.length - 1, node));
	}
}
