package io.openems.edge.ess.hyperstrong.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.timedata.Timeout;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.ess.hyperstrong.OperationState;
import io.openems.edge.ess.hyperstrong.hypercube.HyperCube;
import io.openems.edge.ess.hyperstrong.statemachine.StateMachine.State;

public class StartingHandler extends StateHandler<State, Context> {

	private final Timeout undefinedTimeout = Timeout.ofSeconds(StateMachine.WAIT_IN_UNDEFINED_STATE_SECONDS);
	private final Timeout errorTimeout = Timeout.ofSeconds(HyperCube.TIMEOUT);

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
		this.undefinedTimeout.start(context.clock);
		this.errorTimeout.start(context.clock);
	}

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var ess = context.getParent();

		switch (ess.getOperationState().asEnum()) {
		case OperationState.RUNNING:
		case OperationState.DEBUG:
			return State.RUNNING;

		case OperationState.INITIALIZED:
		case OperationState.SHUTDOWN:
		case OperationState.STANDBY:
        	// TODO: Initiate starting procedure
			// return State.STARTING;
		default:
			if (this.errorTimeout.elapsed(context.clock)) {
				ess._setTimeoutStartBatteryInverter(true);
				return State.ERROR;
			}
			if (this.undefinedTimeout.elapsed(context.clock)) {
				return State.UNDEFINED;
			}
			return State.STARTING;
		}
	}
}
