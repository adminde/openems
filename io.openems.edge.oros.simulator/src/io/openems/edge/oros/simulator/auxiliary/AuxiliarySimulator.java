package io.openems.edge.oros.simulator.auxiliary;

import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.api.ElectricityMeter;

public interface AuxiliarySimulator extends ElectricityMeter, OpenemsComponent {

	/** Default power of the control infrastructure, drawn around the clock [W]. */
	public static final int STANDBY_POWER = 100;

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
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
