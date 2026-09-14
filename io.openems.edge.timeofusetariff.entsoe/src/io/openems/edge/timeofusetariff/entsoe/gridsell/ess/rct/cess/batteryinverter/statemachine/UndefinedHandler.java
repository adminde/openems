package io.openems.edge.ess.rct.cess.batteryinverter.statemachine;

import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.ess.rct.cess.batteryinverter.statemachine.StateMachine.State;

public class UndefinedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) {
		var inverter = context.getParent();
		return switch (inverter.getStartStopTarget()) {
		case UNDEFINED // Stuck in UNDEFINED State
			-> State.UNDEFINED;

		case START // force START
			-> inverter.hasFaults() //
					// Has Faults -> error handling
					? State.ERROR
					// No Faults -> start
					: State.STARTING;

		case STOP // force STOP
			-> State.STOPPING;
		};
	}

}
