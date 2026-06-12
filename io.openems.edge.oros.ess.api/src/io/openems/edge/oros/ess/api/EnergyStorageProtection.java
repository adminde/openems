package io.openems.edge.oros.ess.api;

import static io.openems.common.channel.PersistencePriority.MEDIUM;
import static io.openems.common.channel.Unit.AMPERE;
import static io.openems.common.types.OpenemsType.INTEGER;

import io.openems.common.channel.Level;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;

public interface EnergyStorageProtection extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		OVER_CHARGE_PROTECTION_CURRENT(Doc.of(INTEGER)
				.unit(AMPERE)
				.persistencePriority(MEDIUM)),
		DEEP_DISCHARGE_PROTECTION_CURRENT(Doc.of(INTEGER)
				.unit(AMPERE)
				.persistencePriority(MEDIUM)),
		OVER_CHARGE_PROTECTION(Doc.of(Level.FAULT)
				.text("Over charge protection triggered!")
				.persistencePriority(MEDIUM)),
		DEEP_DISCHARGE_PROTECTION(Doc.of(Level.FAULT)
				.text("Deep discharge protection triggered!")
				.persistencePriority(MEDIUM)),
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
	 * Gets the Channel for {@link ChannelId#OVER_CHARGE_PROTECTION_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default Channel<Integer> getOverChargeProtectionCurrentChannel() {
		return this.channel(ChannelId.OVER_CHARGE_PROTECTION_CURRENT);
	}

	/**
	 * Gets the {@link ChannelId#OVER_CHARGE_PROTECTION_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getOverChargeProtectionCurrent() {
		return this.getOverChargeProtectionCurrentChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#OVER_CHARGE_PROTECTION_CURRENT} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setOverChargeProtectionCurrent(Integer value) {
		this.getOverChargeProtectionCurrentChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#DEEP_DISCHARGE_PROTECTION_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default Channel<Integer> getDeepDischargeProtectionCurrentChannel() {
		return this.channel(ChannelId.DEEP_DISCHARGE_PROTECTION_CURRENT);
	}

	/**
	 * Gets the {@link ChannelId#DEEP_DISCHARGE_PROTECTION_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getDeepDischargeProtectionCurrent() {
		return this.getDeepDischargeProtectionCurrentChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#DEEP_DISCHARGE_PROTECTION_CURRENT} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDeepDischargeProtectionCurrent(Integer value) {
		this.getDeepDischargeProtectionCurrentChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#OVER_CHARGE_PROTECTION}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getOverChargeProtectionChannel() {
		return this.channel(ChannelId.OVER_CHARGE_PROTECTION);
	}

	/**
	 * Gets the {@link ChannelId#OVER_CHARGE_PROTECTION}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getOverChargeProtection() {
		return this.getOverChargeProtectionChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#OVER_CHARGE_PROTECTION} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setOverChargeProtection(boolean value) {
		this.getOverChargeProtectionChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#DEEP_DISCHARGE_PROTECTION}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getDeepDischargeProtectionChannel() {
		return this.channel(ChannelId.DEEP_DISCHARGE_PROTECTION);
	}

	/**
	 * Gets the {@link ChannelId#DEEP_DISCHARGE_PROTECTION}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getDeepDischargeProtection() {
		return this.getDeepDischargeProtectionChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#DEEP_DISCHARGE_PROTECTION} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDeepDischargeProtection(boolean value) {
		this.getDeepDischargeProtectionChannel().setNextValue(value);
	}
}
