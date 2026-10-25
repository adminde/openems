package io.openems.edge.simulator.heat.pump.reacting.statemachine;

import java.time.Duration;
import java.time.Instant;

import io.openems.edge.common.statemachine.AbstractContext;
import io.openems.edge.heat.pump.core.HeatPumpPerformanceEstimator;
import io.openems.edge.heat.tess.api.ThermalEss;
import io.openems.edge.simulator.heat.pump.reacting.SimulatorHeatPumpReactingImpl;

/**
 * The arbitrated heat demand of one cycle plus the operating limits of the
 * heat pump.
 */
public class Context extends AbstractContext<SimulatorHeatPumpReactingImpl> {

	/** Whether the heat pump modulates; an on/off device otherwise. */
	protected final boolean modulating;
	/** Nominal thermal output in [W]. */
	protected final int nominalThermalPower;
	/** Nominal electrical power at the rating point in [W]. */
	protected final int nominalActivePower;
	/** Lowest dispatchable electrical power when modulating in [W]. */
	protected final int minActivePower;
	/** Electrical hysteresis below the minimum modulation in [W]. */
	protected final int minActivePowerHysteresis;
	/** Minimum runtime of the compressor once started. */
	protected final Duration minRuntime;
	/** The current simulation time. */
	protected final Instant now;
	/** Hardware maximum temperature of the heat pump in [deci-°C], or null. */
	protected final Integer heatPumpMaxTemperature;
	/** Temperature difference between supply and return while charging in [K]. */
	protected final double spread;
	/** The storage to serve in this cycle, or null if none. */
	protected final ThermalEss storage;
	/** The switching temperature of the served storage in [deci-°C], or null. */
	protected final Integer storageTemperature;
	/** An explicitly requested electrical power in [W], or null. */
	protected final Integer requestedPower;
	/** Autonomous two-point demand: comfort or frost protection. */
	protected final boolean autonomousDemand;
	/** The flow temperature while charging the served storage in [°C]. */
	protected final double supplyTemperature;

	private final HeatPumpPerformanceEstimator copEstimator;

	public Context(SimulatorHeatPumpReactingImpl parent, boolean modulating, int nominalThermalPower,
			int nominalActivePower, int minActivePower, int minActivePowerHysteresis, Duration minRuntime,
			Instant now, Integer heatPumpMaxTemperature, HeatPumpPerformanceEstimator copEstimator, double spread,
			ThermalEss storage, Integer requestedPower, boolean autonomousDemand) {
		super(parent);
		this.modulating = modulating;
		this.nominalThermalPower = nominalThermalPower;
		this.nominalActivePower = nominalActivePower;
		this.minActivePower = minActivePower;
		this.minActivePowerHysteresis = minActivePowerHysteresis;
		this.minRuntime = minRuntime;
		this.now = now;
		this.heatPumpMaxTemperature = heatPumpMaxTemperature;
		this.copEstimator = copEstimator;
		this.spread = spread;
		this.storage = storage;
		this.storageTemperature = storage != null ? parent.switchingTemperatureOf(storage) : null;
		this.requestedPower = requestedPower;
		this.autonomousDemand = autonomousDemand;
		this.supplyTemperature = this.supplyTemperatureOf(storage);
	}

	/**
	 * Gets the switching temperature of the served storage.
	 *
	 * @return the temperature in [deci-°C], or null if no storage is served or
	 *         its temperature is unknown
	 */
	public Integer getStorageTemperature() {
		return this.storageTemperature;
	}

	/**
	 * Estimates the COP at the flow temperature of the served storage.
	 *
	 * @param partLoadRatio the electrical part-load ratio (0..1)
	 * @return the estimated COP
	 */
	public float cop(double partLoadRatio) {
		return this.cop(this.supplyTemperature, partLoadRatio);
	}

	/**
	 * Estimates the COP at an explicit flow temperature.
	 *
	 * @param supplyTemperature the flow temperature in [°C]
	 * @param partLoadRatio     the electrical part-load ratio (0..1)
	 * @return the estimated COP
	 */
	public float cop(double supplyTemperature, double partLoadRatio) {
		return (float) this.copEstimator.estimateCop(supplyTemperature, partLoadRatio);
	}

	/**
	 * Gets the full-load electrical consumption at the flow temperature of the
	 * served storage. Rises as the COP drops.
	 *
	 * @return the electrical power in [W]
	 */
	public int electricalDraw() {
		return Math.round(this.nominalThermalPower / this.cop(1.0));
	}

	/**
	 * Gets the flow temperature while charging the given storage: the spread above
	 * the water returning from the storage, or the standard rating point if
	 * unknown.
	 *
	 * @param storage the {@link ThermalEss}, or null
	 * @return the flow temperature in [°C]
	 */
	public double supplyTemperatureOf(ThermalEss storage) {
		var temperature = storage != null ? this.getParent().returnTemperatureOf(storage) : null;
		return temperature != null //
				? temperature / 10.0 + this.spread //
				: HeatPumpPerformanceEstimator.STANDARD_RATING_SINK_TEMPERATURE;
	}

	/**
	 * Whether the given storage can still take heat, i.e. its hottest point is
	 * below the hardware maximum temperatures of both the storage and the heat
	 * pump.
	 *
	 * @param storage the {@link ThermalEss}, or null for no temperature limits
	 * @return true if heat can be delivered
	 */
	public boolean canTakeHeat(ThermalEss storage) {
		return storage == null || this.getParent().canTakeHeat(storage, this.heatPumpMaxTemperature);
	}

	/**
	 * Whether the minimum runtime of the compressor has elapsed.
	 *
	 * @param startedAt the time the compressor started, or null if unknown
	 * @return true if the compressor may stop
	 */
	public boolean minRuntimeElapsed(Instant startedAt) {
		if (startedAt == null) {
			return true;
		}
		return Duration.between(startedAt, this.now).compareTo(this.minRuntime) >= 0;
	}

	/**
	 * Whether there is any heat demand in this cycle, autonomous or requested.
	 *
	 * @return true on demand
	 */
	protected boolean hasDemand() {
		return this.autonomousDemand || this.requestedPower != null;
	}

	/**
	 * Whether the compressor is to run in this cycle: on autonomous demand, or on
	 * an acceptable power request — a modulating device accepts requests down to
	 * its minimum modulation, an on/off device only requests covering its full
	 * electrical consumption.
	 *
	 * @return true if the compressor is to run
	 */
	protected boolean hasStartCondition() {
		if (this.autonomousDemand) {
			return true;
		}
		if (this.requestedPower == null) {
			return false;
		}
		if (this.modulating) {
			return this.requestedPower >= this.minActivePower;
		}
		return this.requestedPower >= this.electricalDraw();
	}
}
