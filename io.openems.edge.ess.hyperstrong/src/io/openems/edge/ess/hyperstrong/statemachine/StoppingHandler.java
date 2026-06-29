package io.openems.edge.ess.hyperstrong.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.timedata.Timeout;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.ess.hyperstrong.hypercube.OperatingStatus;
import io.openems.edge.ess.hyperstrong.hypercube.HyperCube;
import io.openems.edge.ess.hyperstrong.statemachine.StateMachine.State;

public class StoppingHandler extends StateHandler<State, Context> {

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
		case OperatingStatus.STANDBY:
		case OperatingStatus.DEBUG:
			return State.STANDBY;

        case OperatingStatus.RUNNING:
        case OperatingStatus.STARTING:
        case OperatingStatus.INITIALIZED:
        	// TODO: Initiate stopping procedure
			// return State.STOPPING;
		default:
			if (this.errorTimeout.elapsed(context.clock)) {
				ess._setTimeoutStartBatteryInverter(true);
				return State.ERROR;
			}
			if (this.undefinedTimeout.elapsed(context.clock)) {
				return State.UNDEFINED;
			}
		}
		return State.STOPPING;
	}
}
