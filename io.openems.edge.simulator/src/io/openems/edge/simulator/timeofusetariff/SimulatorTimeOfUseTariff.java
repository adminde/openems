package io.openems.edge.simulator.timeofusetariff;

import io.openems.common.channel.Level;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.timeofusetariff.api.TimeOfUseTariff;

public interface SimulatorTimeOfUseTariff extends OpenemsComponent, TimeOfUseTariff {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		UNABLE_TO_UPDATE_PRICES(Doc.of(Level.WARNING)//
				.text("Unable to read prices from the data source")), //
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
