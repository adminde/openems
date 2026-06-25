package io.openems.edge.ess.hyperstrong.statemachine;

import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.ess.hyperstrong.hypercube.OperatingStatus;
import io.openems.edge.ess.hyperstrong.statemachine.StateMachine.State;

public class StandbyHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) {
		var ess = context.getParent();

		switch (ess.getOperationState().asEnum()) {
		case OperatingStatus.STANDBY:
		case OperatingStatus.DEBUG:
			break;

		default:
			return State.UNDEFINED;
		}

		// Mark as stopped
		ess._setStartStop(StartStop.STOP);

		return State.STANDBY;
	}
}
