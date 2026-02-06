package io.openems.edge.ess.hyperstrong.statemachine;

import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.ess.hyperstrong.statemachine.StateMachine.State;

public class UndefinedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) {
		var ess = context.getParent();
		return switch (ess.getStartStopTarget()) {
		case UNDEFINED // Stuck in UNDEFINED State
			-> State.UNDEFINED;

		case START // force START
			-> ess.hasFaults() //
					// Has Faults -> error handling
					? State.ERROR
					// No Faults -> start
					: State.STARTING;

		case STOP // force STOP
			-> State.STOPPING;
		};
	}

}
