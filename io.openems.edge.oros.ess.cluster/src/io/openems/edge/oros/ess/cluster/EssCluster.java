package io.openems.edge.oros.ess.cluster;

import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.LongReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.ess.api.AsymmetricEss;
import io.openems.edge.ess.api.HybridEss;
import io.openems.edge.ess.api.ManagedAsymmetricEss;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.MetaEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;

public interface EssCluster extends EnergyStorageSystem,
		ManagedAsymmetricEss, AsymmetricEss, ManagedSymmetricEss, SymmetricEss, MetaEss,
		OpenemsComponent, ModbusSlave, StartStoppable {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * DC Discharge Power of the Cluster.
		 *
		 * <ul>
		 * <li>Interface: EssCluster
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: negative for Charge, positive for Discharge
		 * </ul>
		 */
		DC_DISCHARGE_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Actual AC-side battery discharge power of Energy Storage Cluster. " //
						+ "Negative values for charge; positive for discharge")),
		/**
		 * DC Charge Energy.
		 *
		 * <ul>
		 * <li>Interface: EssCluster
		 * <li>Type: Long
		 * <li>Unit: Wh
		 * </ul>
		 */
		DC_CHARGE_ENERGY(Doc.of(OpenemsType.LONG) //
				.unit(Unit.CUMULATED_WATT_HOURS) //
				.persistencePriority(PersistencePriority.HIGH)), //
		/**
		 * DC Discharge Energy.
		 *
		 * <ul>
		 * <li>Interface: EssCluster
		 * <li>Type: Long
		 * <li>Unit: Wh
		 * </ul>
		 */
		DC_DISCHARGE_ENERGY(Doc.of(OpenemsType.LONG) //
				.unit(Unit.CUMULATED_WATT_HOURS) //
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
	 * Gets the Channel for {@link ChannelId#DC_DISCHARGE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getDcDischargePowerChannel() {
		return this.channel(ChannelId.DC_DISCHARGE_POWER);
	}

	/**
	 * Gets the DC discharge power of the cluster in [W]. See
	 * {@link ChannelId#DC_DISCHARGE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	@Override
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
	 * Gets the Channel for {@link ChannelId#DC_CHARGE_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getDcChargeEnergyChannel() {
		return this.channel(ChannelId.DC_CHARGE_ENERGY);
	}

	/**
	 * Gets the DC Charge Energy in [Wh_Σ]. See {@link ChannelId#DC_CHARGE_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getDcChargeEnergy() {
		return this.getDcChargeEnergyChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#DC_CHARGE_ENERGY}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDcChargeEnergy(Long value) {
		this.getDcChargeEnergyChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#DC_CHARGE_ENERGY}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDcChargeEnergy(long value) {
		this.getDcChargeEnergyChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#DC_DISCHARGE_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getDcDischargeEnergyChannel() {
		return this.channel(ChannelId.DC_DISCHARGE_ENERGY);
	}

	/**
	 * Gets the DC Discharge Energy in [Wh_Σ]. See
	 * {@link ChannelId#DC_DISCHARGE_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getDcDischargeEnergy() {
		return this.getDcDischargeEnergyChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#DC_DISCHARGE_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDcDischargeEnergy(Long value) {
		this.getDcDischargeEnergyChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#DC_DISCHARGE_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDcDischargeEnergy(long value) {
		this.getDcDischargeEnergyChannel().setNextValue(value);
	}
}
