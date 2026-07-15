package io.openems.edge.oros.simulator.pcs;

import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.oros.pcs.api.PowerConversionSystem;

public interface PowerConversionSimulator extends PowerConversionSystem, OpenemsComponent {

	/** Efficiency factor (%) used for AC/DC conversion. */
	public static final float EFFICIENCY_FACTOR = 98F;

	/** Power-factors. */
	public static final float APPARENT_POWER_FACTOR = 1.1F;
	public static final float REACTIVE_POWER_FACTOR = 0.5F;

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
