package io.openems.edge.simulator.heat.pump.reacting.statemachine;

import io.openems.common.types.OptionsEnum;
import io.openems.edge.common.statemachine.AbstractStateMachine;
import io.openems.edge.common.statemachine.StateHandler;

public class StateMachine extends AbstractStateMachine<StateMachine.State, Context> {

	public enum State implements io.openems.edge.common.statemachine.State<State>, OptionsEnum {
		UNDEFINED(-1), //

		/**
		 * The compressor is off; the heat pump waits for heat demand or an
		 * acceptable power request.
		 */
		STOPPED(10), //

		/**
		 * The start-up phase: e.g. the diverter valve positions towards the served
		 * storage; the compressor does not produce heat yet.
		 */
		STARTING(11), //

		/**
		 * The compressor is running and produces heat.
		 */
		RUNNING(12), //

		/**
		 * The shut-down phase: the compressor is off, the circulation pump runs
		 * down.
		 */
		STOPPING(13), //
		;

		private final int value;

		private State(int value) {
			this.value = value;
		}

		@Override
		public int getValue() {
			return this.value;
		}

		@Override
		public String getName() {
			return this.name();
		}

		@Override
		public OptionsEnum getUndefined() {
			return UNDEFINED;
		}

		@Override
		public State[] getStates() {
			return State.values();
		}
	}

	public StateMachine(State initialState) {
		super(initialState);
	}

	@Override
	public StateHandler<State, Context> getStateHandler(State state) {
		return switch (state) {
		case UNDEFINED -> new UndefinedHandler();
		case STOPPED -> new StoppedHandler();
		case STARTING -> new StartingHandler();
		case RUNNING -> new RunningHandler();
		case STOPPING -> new StoppingHandler();
		};
	}
}
