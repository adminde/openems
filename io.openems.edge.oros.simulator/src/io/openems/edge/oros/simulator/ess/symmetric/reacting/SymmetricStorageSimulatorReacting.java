package io.openems.edge.oros.simulator.ess.symmetric.reacting;

import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.oros.common.SymmetricComponent;
import io.openems.edge.timedata.api.TimedataProvider;

public interface SymmetricStorageSimulatorReacting extends 
		ManagedSymmetricEss, SymmetricEss, SymmetricComponent, 
		OpenemsComponent, StartStoppable, ModbusSlave, TimedataProvider {

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
