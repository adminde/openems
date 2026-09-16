package io.openems.edge.ess.hyperstrong.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.timedata.Timeout;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.ess.hyperstrong.hypercube.HyperCube;
import io.openems.edge.ess.hyperstrong.hypercube.OperatingStatus;
import io.openems.edge.ess.hyperstrong.hypercube.OperatingTarget;
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
			this.requestStop(ess);
			// After requesting Stop, fall through to the timeout handling below.
		default:
			if (this.errorTimeout.elapsed(context.clock)) {
				ess._setTimeoutStartBatteryInverter(true);
				return State.ERROR;
			}
			if (this.undefinedTimeout.elapsed(context.clock)) {
				return State.UNDEFINED;
			}
			return State.STOPPING;
		}
	}

	/**
	 * Requests the HyperCube to switch its operating mode to Stop.
	 *
	 * <p>
	 * The request is repeated every cycle until the HyperCube echoes Stop as its
	 * accepted Operating Target.
	 *
	 * @param ess the {@link HyperCube}
	 * @throws OpenemsNamedException on write error
	 */
	private void requestStop(HyperCube ess) throws OpenemsNamedException {
		if (ess.isReadOnly() || ess.getOperatingTarget() == OperatingTarget.STOP) {
			return;
		}
		ess.setOperatingTarget(OperatingTarget.STOP);
	}
}
