package io.openems.edge.controller.ess.ebx.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.controller.ess.ebx.statemachine.StateMachine.State;

public class FailSafeHandler extends StateHandler<State, Context> {

	@Override
	protected State runAndGetNextState(Context context) throws OpenemsNamedException {
		if (context.isHealthy()) {
			return State.DISPATCHING;
		}
		var lastApplied = context.lastAppliedPower;
		if (lastApplied == null || lastApplied == 0) {
			return State.IDLE_SAFE;
		}
		return State.FAIL_SAFE;
	}
}
