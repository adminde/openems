package io.openems.edge.simulator.heat.pump.reacting.statemachine;

import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.simulator.heat.pump.reacting.statemachine.StateMachine.State;

/**
 * The shut-down phase of the heat pump: the compressor is off, the
 * circulation pump runs down; no heat is produced anymore.
 */
public class StoppingHandler extends StateHandler<State, Context> {

	@Override
	protected State runAndGetNextState(Context context) {
		context.getParent().applyPowerState(0, 0, context.cop(1.0), context.supplyTemperature);
		return State.STOPPED;
	}
}
