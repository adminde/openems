package io.openems.edge.ess.hyperstrong.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.timedata.Timeout;
import io.openems.edge.common.channel.EnumWriteChannel;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.ess.hyperstrong.hypercube.HyperCube;
import io.openems.edge.ess.hyperstrong.hypercube.OperatingStatus;
import io.openems.edge.ess.hyperstrong.hypercube.RunModeTarget;
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
		}
		return State.STOPPING;
	}

	/**
	 * Requests the HyperCube to switch its work state to Stop via register 303.
	 *
	 * <p>
	 * The request is repeated every cycle while the system reports a stoppable
	 * Operating Status and stops as soon as it reports Standby.
	 *
	 * @param ess the {@link HyperCube}
	 * @throws OpenemsNamedException on write error
	 */
	private void requestStop(HyperCube ess) throws OpenemsNamedException {
		if (ess.isReadOnly()) {
			return;
		}
		EnumWriteChannel runModeTarget = ess.channel(HyperCube.ChannelId.RUN_MODE_TARGET);
		runModeTarget.setNextWriteValue(RunModeTarget.STOP);
	}
}
