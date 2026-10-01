package io.openems.edge.simulator.heat.pump.reacting.statemachine;

import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.simulator.heat.pump.reacting.statemachine.StateMachine.State;

public class StoppedHandler extends StateHandler<State, Context> {

	@Override
	protected State runAndGetNextState(Context context) {
		context.getParent().applyPowerState(0, 0, context.cop(1.0), context.supplyTemperature);
		if (context.hasStartCondition()) {
			return State.STARTING;
		}
		return State.STOPPED;
	}
}
