package io.openems.edge.heat.element.api;

import org.osgi.annotation.versioning.ProviderType;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.PersistencePriority;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.heat.api.ManagedSymmetricHeating;

@ProviderType
public interface ManagedHeatElement extends HeatElement, ManagedSymmetricHeating {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/**
		 * Current Status of the Heat element.
		 *
		 * <ul>
		 * <li>Interface: Heat
		 * <li>Type: Status
		 * </ul>
		 */
		STATUS(Doc.of(Status.values()) //
				.persistencePriority(PersistencePriority.LOW) //
				.accessMode(AccessMode.READ_ONLY)), //
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

	/**
	 * Gets the Channel for {@link ChannelId#STATUS}.
	 *
	 * @return the Channel
	 */
	public default Channel<Status> getStatusChannel() {
		return this.channel(ChannelId.STATUS);
	}

	/**
	 * Gets the Status of the Heat element. See {@link ChannelId#STATUS}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Status getStatus() {
		return this.getStatusChannel().value().asEnum();
	}
}
