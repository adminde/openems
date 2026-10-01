package io.openems.edge.simulator.heat.pump.reacting.statemachine;

import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.simulator.heat.pump.reacting.statemachine.StateMachine.State;

/**
 * The start-up phase of the heat pump: the diverter valve positions towards
 * the served storage and the compressor ramps up; no heat is produced yet.
 */
public class StartingHandler extends StateHandler<State, Context> {

	@Override
	protected State runAndGetNextState(Context context) {
		context.getParent().applyPowerState(0, 0, context.cop(1.0), context.supplyTemperature);
		if (context.hasStartCondition()) {
			return State.RUNNING;
		}
		// The demand disappeared while starting up.
		return State.STOPPED;
	}
}
