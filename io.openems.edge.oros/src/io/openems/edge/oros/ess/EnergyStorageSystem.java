package io.openems.edge.oros.ess;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.jsonapi.ComponentJsonApi;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.ess.api.EssErrorAcknowledge;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.oros.CycleProvider;
import io.openems.edge.oros.OrosModel;
import io.openems.edge.oros.SymmetricComponent;
import io.openems.edge.oros.bms.BatteryManagementSystem;
import io.openems.edge.oros.ess.protection.PowerLimiter;
import io.openems.edge.oros.ess.protection.VoltageProtection;
import io.openems.edge.oros.pcs.PowerConversionSystem;

public interface EnergyStorageSystem extends
		ManagedSymmetricEss, SymmetricEss, EssErrorAcknowledge, VoltageProtection,
		SymmetricComponent, OpenemsComponent, ComponentJsonApi,
		ModbusSlave, CycleProvider, StartStoppable {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/**
		 * Sets the Active Power in [W].
		 *
		 * <ul>
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: negative values for Charge; positive for Discharge
		 * </ul>
		 */
		SET_ACTIVE_POWER(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.accessMode(AccessMode.WRITE_ONLY)),
		/**
		 * Sets the Reactive Power in [var].
		 *
		 * <ul>
		 * <li>Type: Integer
		 * <li>Unit: var
		 * <li>Range: negative values for Charge; positive for Discharge
		 * </ul>
		 */
		SET_REACTIVE_POWER(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.VOLT_AMPERE_REACTIVE)
				.accessMode(AccessMode.WRITE_ONLY)),
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
	 * Gets the maximum allowed power increase percentage per second. Used by
	 * {@link PowerLimiter} to ramp charge/discharge power.
	 *
	 * @return the max power increase percentage (e.g. 5 for 5%)
	 */
	public float getMaxPowerIncreasePercentage();

	/**
	 * Gets the {@link BatteryManagementSystem} of this {@link EnergyStorageSystem}.
	 *
	 * @return the {@link BatteryManagementSystem}
	 */
	public BatteryManagementSystem getBattery();

	/**
	 * Gets the {@link PowerConversionSystem} of this {@link EnergyStorageSystem}.
	 *
	 * @return the {@link PowerConversionSystem}
	 */
	public PowerConversionSystem getPowerConversionSystem();

	/**
	 * Gets the target Start/Stop mode from config or StartStop-Channel.
	 *
	 * @return {@link StartStop}
	 */
	public StartStop getStartStopTarget();

	public OrosModel getModel();

	@Override
	public default ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(
				OpenemsComponent.getModbusSlaveNatureTable(accessMode),
				SymmetricEss.getModbusSlaveNatureTable(accessMode),
				ManagedSymmetricEss.getModbusSlaveNatureTable(accessMode)
		);
	}
}
