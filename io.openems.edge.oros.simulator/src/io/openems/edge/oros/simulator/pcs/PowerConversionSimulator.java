package io.openems.edge.oros.simulator.pcs;

import static io.openems.common.channel.PersistencePriority.HIGH;
import static io.openems.common.channel.Unit.WATT;
import static io.openems.common.types.OpenemsType.INTEGER;

import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.oros.pcs.api.PowerConversionSystem;

public interface PowerConversionSimulator extends PowerConversionSystem, OpenemsComponent {

	/** Efficiency factor (%) used for AC/DC conversion. */
	public static final float EFFICIENCY_FACTOR = 98F;

	/** Power-factors. */
	public static final float APPARENT_POWER_FACTOR = 1.1F;
	public static final float REACTIVE_POWER_FACTOR = 0.5F;

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/**
		 * DC Discharge Power.
		 *
		 * <ul>
		 * <li>Interface: PowerConversionSimulator
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#WATT}
		 * <li>Range: negative values for Charge; positive for Discharge
		 * </ul>
		 */
		DC_DISCHARGE_POWER(Doc.of(INTEGER)
				.unit(WATT)
				.persistencePriority(HIGH)),
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
	 * Gets the DC Discharge Power in [W]. See
	 * {@link ChannelId#DC_DISCHARGE_POWER}.
	 *
	 * @return the DC Power
	 */
	public default Integer getDcPower() {
		return this.getDcDischargePower().get();
	}

	/**
	 * Gets the Channel for {@link ChannelId#DC_DISCHARGE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getDcDischargePowerChannel() {
		return this.channel(ChannelId.DC_DISCHARGE_POWER);
	}

	/**
	 * Gets the DC Discharge Power in [W]. See
	 * {@link ChannelId#DC_DISCHARGE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getDcDischargePower() {
		return this.getDcDischargePowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#DC_DISCHARGE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDcDischargePower(Integer value) {
		this.getDcDischargePowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#DC_DISCHARGE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDcDischargePower(int value) {
		this.getDcDischargePowerChannel().setNextValue(value);
	}
}
