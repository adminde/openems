package io.openems.edge.oros.simulator.ess.symmetric.reacting;

import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
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
