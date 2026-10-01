package io.openems.edge.simulator.heat.pump.reacting;

import org.osgi.service.event.EventHandler;

import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.heat.pump.api.ManagedHeatPump;
import io.openems.edge.simulator.heat.pump.reacting.statemachine.StateMachine.State;

public interface SimulatorHeatPumpReacting extends ManagedHeatPump, OpenemsComponent, EventHandler {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Current State of the State-Machine.
		 *
		 * <ul>
		 * <li>Interface: SimulatorHeatPumpReacting
		 * <li>Type: State
		 * </ul>
		 */
		STATE_MACHINE(Doc.of(State.values()) //
				.text("Current State of State-Machine")), //
		;

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}
}
