package io.openems.edge.simulator.heat.pump.reacting.statemachine;

import java.time.Instant;

import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.heat.tess.api.ThermalEss;
import io.openems.edge.simulator.heat.pump.reacting.statemachine.StateMachine.State;

/**
 * The compressor is running: follows a requested electrical power within the
 * modulation limits, or heats at full power on autonomous demand. Once
 * started, the compressor keeps running for its minimum runtime.
 */
public class RunningHandler extends StateHandler<State, Context> {

	private ThermalEss servedStorage = null;
	private Instant startedAt = null;

	@Override
	protected void onEntry(Context context) {
		// The diverter valve has settled: the compressor starts producing heat
		// within the same cycle.
		this.servedStorage = context.storage;
		this.startedAt = context.now;
		this.applyRunningPower(context);
	}

	@Override
	protected State runAndGetNextState(Context context) {
		if (context.hasDemand() && context.storage != this.servedStorage) {
			// The diverter valve must reposition to the newly served storage.
			context.getParent().applyPowerState(0, 0, context.cop(1.0), context.supplyTemperature);
			return State.STARTING;
		}
		if (!context.hasDemand() || !this.applyRunningPower(context)) {
			return this.holdOrStop(context);
		}
		return State.RUNNING;
	}

	/**
	 * Computes and applies the operating point of the running compressor.
	 *
	 * @param context the {@link Context}
	 * @return false if the requested power cannot keep the compressor running
	 */
	private boolean applyRunningPower(Context context) {
		var parent = context.getParent();
		if (context.modulating) {
			final int activePower;
			if (context.requestedPower != null) {
				// A hysteresis below the minimum modulation avoids cycling.
				if (context.requestedPower < context.minActivePower - context.minActivePowerHysteresis) {
					return false;
				}
				activePower = Math.max(context.minActivePower,
						Math.min(context.requestedPower, context.nominalActivePower));
			} else {
				// Comfort or frost protection: run at nominal power to the off point.
				activePower = context.nominalActivePower;
			}
			var partLoadRatio = context.nominalActivePower > 0 //
					? (double) activePower / context.nominalActivePower //
					: 1.0;
			var cop = context.cop(partLoadRatio);
			parent.applyPowerState(activePower, Math.round(activePower * cop), cop, context.supplyTemperature);
			return true;
		}

		// Constant thermal output; the electrical power rises as the COP drops. An
		// on/off device follows a request only while it covers the full electrical
		// consumption.
		var cop = context.cop(1.0);
		var electricalDraw = context.electricalDraw();
		if (context.requestedPower != null && context.requestedPower < electricalDraw) {
			return false;
		}
		parent.applyPowerState(electricalDraw, context.nominalThermalPower, cop, context.supplyTemperature);
		return true;
	}

	/**
	 * Stops the compressor once its minimum runtime has elapsed; before that it
	 * keeps serving the last storage at the lowest dispatchable power, if needed
	 * beyond the storage's off point up to the hardware temperature limits.
	 *
	 * @param context the {@link Context}
	 * @return the next State
	 */
	private State holdOrStop(Context context) {
		var parent = context.getParent();
		if (context.minRuntimeElapsed(this.startedAt) || !context.canTakeHeat(this.servedStorage)) {
			parent.applyPowerState(0, 0, context.cop(1.0), context.supplyTemperature);
			return State.STOPPING;
		}
		var supplyTemperature = context.supplyTemperatureOf(this.servedStorage);
		if (context.modulating) {
			var partLoadRatio = context.nominalActivePower > 0 //
					? (double) context.minActivePower / context.nominalActivePower //
					: 1.0;
			var cop = context.cop(supplyTemperature, partLoadRatio);
			parent.applyPowerState(context.minActivePower, Math.round(context.minActivePower * cop), cop,
					supplyTemperature);
		} else {
			var cop = context.cop(supplyTemperature, 1.0);
			parent.applyPowerState(Math.round(context.nominalThermalPower / cop), context.nominalThermalPower, cop,
					supplyTemperature);
		}
		parent.setActiveThermalStorage(this.servedStorage);
		return State.RUNNING;
	}
}
