package io.openems.edge.heat.element.api;

import org.osgi.annotation.versioning.ProviderType;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerWriteChannel;
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
		STATUS(Doc.of(Status.values())//
				.persistencePriority(PersistencePriority.LOW)//
				.accessMode(AccessMode.READ_ONLY)), //

		/**
		 * Grid-referenced Active Power target. Some heat elements interpret the target
		 * relative to the current grid power (e.g. negative values requesting
		 * additional consumption) instead of the absolute {@link
		 * ManagedSymmetricHeating.ChannelId#TARGET_ACTIVE_POWER}.
		 *
		 * <ul>
		 * <li>Interface: ManagedHeatElement
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		TARGET_GRID_ACTIVE_POWER(Doc.of(OpenemsType.INTEGER)//
				.unit(Unit.WATT)//
				.accessMode(AccessMode.READ_WRITE)//
				.persistencePriority(PersistencePriority.HIGH)), //
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

	/**
	 * Gets the Channel for {@link ChannelId#TARGET_GRID_ACTIVE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerWriteChannel getTargetGridActivePowerChannel() {
		return this.channel(ChannelId.TARGET_GRID_ACTIVE_POWER);
	}

	/**
	 * Gets the grid-referenced Active Power target in [W]. See
	 * {@link ChannelId#TARGET_GRID_ACTIVE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getTargetGridActivePower() {
		return this.getTargetGridActivePowerChannel().value();
	}

	/**
	 * Sets the grid-referenced Active Power target in [W]. See
	 * {@link ChannelId#TARGET_GRID_ACTIVE_POWER}.
	 *
	 * @param value the next write value
	 * @throws OpenemsNamedException on error
	 */
	public default void _setTargetGridActivePower(Integer value) throws OpenemsNamedException {
		this.getTargetGridActivePowerChannel().setNextWriteValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#TARGET_GRID_ACTIVE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTargetGridActivePower(int value) {
		this.getTargetGridActivePowerChannel().setNextValue(value);
	}

}
