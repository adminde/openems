package io.openems.edge.oros.simulator.ess.symmetric.reacting;

import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
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

	/** Default power of the control infrastructure, drawn around the clock [W]. */
	public static final int STANDBY_POWER = 100;

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Auxiliary Power.
		 *
		 * <p>The electrical power the System draws beside its AC terminals: the control
		 * infrastructure around the clock plus the Thermal Management System of the
		 * Battery while it carries a current. The auxiliary circuit is fed separately
		 * from the terminals of the Power Conversion System, so this power is no part
		 * of {@link SymmetricEss.ChannelId#ACTIVE_POWER} and shows up at the point of
		 * connection instead.
		 *
		 * <ul>
		 * <li>Interface: SymmetricStorageSimulatorReacting
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		AUXILIARY_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * DC Discharge Power.
		 *
		 * <p>The Channel carries the name declared by HybridEss, which is what
		 * aggregating implementations look for on a system that is no HybridEss.
		 *
		 * <ul>
		 * <li>Interface: SymmetricStorageSimulatorReacting
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: negative for Charge, positive for Discharge
		 * </ul>
		 */
		DC_DISCHARGE_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH)),
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
	 * Gets the Channel for {@link ChannelId#AUXILIARY_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getAuxiliaryPowerChannel() {
		return this.channel(ChannelId.AUXILIARY_POWER);
	}

	/**
	 * Gets the Auxiliary Power in [W]. See {@link ChannelId#AUXILIARY_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getAuxiliaryPower() {
		return this.getAuxiliaryPowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#AUXILIARY_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setAuxiliaryPower(Integer value) {
		this.getAuxiliaryPowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#AUXILIARY_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setAuxiliaryPower(int value) {
		this.getAuxiliaryPowerChannel().setNextValue(value);
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
