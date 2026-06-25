package io.openems.edge.ess.hyperstrong.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.ess.hyperstrong.hypercube.OperatingStatus;
import io.openems.edge.ess.hyperstrong.statemachine.StateMachine.State;

public class RunningHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var ess = context.getParent();

		if (ess.hasFaults()) {
			return State.UNDEFINED;
		}
		switch (ess.getOperationState().asEnum()) {
		case OperatingStatus.RUNNING:
		case OperatingStatus.DEBUG:
			break;

		default:
			return State.UNDEFINED;
		}

		// Mark as started
		ess._setStartStop(StartStop.START);

		return State.RUNNING;
	}

}
