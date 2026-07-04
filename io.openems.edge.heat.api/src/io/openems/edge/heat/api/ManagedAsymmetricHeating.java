package io.openems.edge.heat.api;

import org.osgi.annotation.versioning.ProviderType;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.value.Value;

@ProviderType
public interface ManagedAsymmetricHeating extends ManagedSymmetricHeating, AsymmetricHeating {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * The Active Power target for electrical consumption of the heating on L1.
		 *
		 * <ul>
		 * <li>Interface: ManagedAsymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		TARGET_ACTIVE_POWER_L1(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.accessMode(AccessMode.READ_WRITE) //
				.persistencePriority(PersistencePriority.HIGH)), //
		/**
		 * The Active Power target for electrical consumption of the heating on L2.
		 *
		 * <ul>
		 * <li>Interface: ManagedAsymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		TARGET_ACTIVE_POWER_L2(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.accessMode(AccessMode.READ_WRITE) //
				.persistencePriority(PersistencePriority.HIGH)), //
		/**
		 * The Active Power target for electrical consumption of the heating on L3.
		 *
		 * <ul>
		 * <li>Interface: ManagedAsymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		TARGET_ACTIVE_POWER_L3(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.accessMode(AccessMode.READ_WRITE) //
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
	 * Gets the Channel for {@link ChannelId#TARGET_ACTIVE_POWER_L1}.
	 *
	 * @return the Channel
	 */
	public default IntegerWriteChannel getTargetActivePowerL1Channel() {
		return this.channel(ChannelId.TARGET_ACTIVE_POWER_L1);
	}

	/**
	 * Gets the Active Power Target on L1 in [W]. See
	 * {@link ChannelId#TARGET_ACTIVE_POWER_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getTargetActivePowerL1() {
		return this.getTargetActivePowerL1Channel().value();
	}

	/**
	 * Sets the Active Power Target on L1 in [W]. See
	 * {@link ChannelId#TARGET_ACTIVE_POWER_L1}.
	 *
	 * @param value the next write value
	 * @throws OpenemsNamedException on error
	 */
	public default void _setTargetActivePowerL1(Integer value) throws OpenemsNamedException {
		this.getTargetActivePowerL1Channel().setNextWriteValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#TARGET_ACTIVE_POWER_L1} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTargetActivePowerL1(int value) {
		this.getTargetActivePowerL1Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#TARGET_ACTIVE_POWER_L2}.
	 *
	 * @return the Channel
	 */
	public default IntegerWriteChannel getTargetActivePowerL2Channel() {
		return this.channel(ChannelId.TARGET_ACTIVE_POWER_L2);
	}

	/**
	 * Gets the Active Power Target on L2 in [W]. See
	 * {@link ChannelId#TARGET_ACTIVE_POWER_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getTargetActivePowerL2() {
		return this.getTargetActivePowerL2Channel().value();
	}

	/**
	 * Sets the Active Power Target on L2 in [W]. See
	 * {@link ChannelId#TARGET_ACTIVE_POWER_L2}.
	 *
	 * @param value the next write value
	 * @throws OpenemsNamedException on error
	 */
	public default void _setTargetActivePowerL2(Integer value) throws OpenemsNamedException {
		this.getTargetActivePowerL2Channel().setNextWriteValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#TARGET_ACTIVE_POWER_L2} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTargetActivePowerL2(int value) {
		this.getTargetActivePowerL2Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#TARGET_ACTIVE_POWER_L3}.
	 *
	 * @return the Channel
	 */
	public default IntegerWriteChannel getTargetActivePowerL3Channel() {
		return this.channel(ChannelId.TARGET_ACTIVE_POWER_L3);
	}

	/**
	 * Gets the Active Power Target on L3 in [W]. See
	 * {@link ChannelId#TARGET_ACTIVE_POWER_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getTargetActivePowerL3() {
		return this.getTargetActivePowerL3Channel().value();
	}

	/**
	 * Sets the Active Power Target on L3 in [W]. See
	 * {@link ChannelId#TARGET_ACTIVE_POWER_L3}.
	 *
	 * @param value the next write value
	 * @throws OpenemsNamedException on error
	 */
	public default void _setTargetActivePowerL3(Integer value) throws OpenemsNamedException {
		this.getTargetActivePowerL3Channel().setNextWriteValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#TARGET_ACTIVE_POWER_L3} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTargetActivePowerL3(int value) {
		this.getTargetActivePowerL3Channel().setNextValue(value);
	}

}
