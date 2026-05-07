package io.openems.edge.oros.bms;

import static io.openems.common.channel.PersistencePriority.MEDIUM;
import static io.openems.common.channel.Unit.VOLT;
import static io.openems.common.types.OpenemsType.INTEGER;

import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;

public interface BatteryProtection extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		OVER_CHARGE_PROTECTION_VOLTAGE(Doc.of(INTEGER)
				.unit(VOLT)
				.persistencePriority(MEDIUM)),
		DEEP_DISCHARGE_PROTECTION_VOLTAGE(Doc.of(INTEGER)
				.unit(VOLT)
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
	 * Gets the Channel for {@link ChannelId#OVER_CHARGE_PROTECTION_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default Channel<Integer> getOverChargeProtectionVoltageChannel() {
		return this.channel(ChannelId.OVER_CHARGE_PROTECTION_VOLTAGE);
	}

	/**
	 * Gets the {@link ChannelId#OVER_CHARGE_PROTECTION_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getOverChargeProtectionVoltage() {
		return this.getOverChargeProtectionVoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#OVER_CHARGE_PROTECTION_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setOverChargeProtectionVoltage(Integer value) {
		this.getOverChargeProtectionVoltageChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#DEEP_DISCHARGE_PROTECTION_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default Channel<Integer> getDeepDischargeProtectionVoltageChannel() {
		return this.channel(ChannelId.DEEP_DISCHARGE_PROTECTION_VOLTAGE);
	}

	/**
	 * Gets the {@link ChannelId#DEEP_DISCHARGE_PROTECTION_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getDeepDischargeProtectionVoltage() {
		return this.getDeepDischargeProtectionVoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#DEEP_DISCHARGE_PROTECTION_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDeepDischargeProtectionVoltage(Integer value) {
		this.getDeepDischargeProtectionVoltageChannel().setNextValue(value);
	}

}
