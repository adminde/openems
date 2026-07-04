package io.openems.edge.heat.pump.api;

import org.osgi.annotation.versioning.ProviderType;

import io.openems.edge.common.channel.Doc;
import io.openems.edge.heat.api.ManagedSymmetricHeating;

@ProviderType
public interface ManagedHeatPump extends HeatPump, ManagedSymmetricHeating {

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
