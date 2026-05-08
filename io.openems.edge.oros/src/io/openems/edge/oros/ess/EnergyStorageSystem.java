package io.openems.edge.oros.ess;

import static io.openems.common.channel.PersistencePriority.HIGH;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.common.utils.IntUtils;
import io.openems.edge.batteryinverter.api.HybridManagedSymmetricBatteryInverter;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.statemachine.AbstractStateMachine;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.ess.api.HybridEss;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.oros.SymmetricComponent;
import io.openems.edge.oros.bms.BatteryManagementSystem;
import io.openems.edge.oros.ess.protection.PowerLimiter;
import io.openems.edge.oros.pcs.PowerConversionSystem;

public interface EnergyStorageSystem extends
		ManagedSymmetricEss, SymmetricEss, EnergyStorageProtection,
		SymmetricComponent, OpenemsComponent, ModbusSlave, StartStoppable {

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
				.accessMode(AccessMode.READ_WRITE)
				.persistencePriority(HIGH)),
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
				.accessMode(AccessMode.READ_WRITE)
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

	public default boolean isReadOnly() {
		return false;
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
	public BatteryManagementSystem getBatteryManagementSystem();

	/**
	 * Gets the {@link PowerConversionSystem} of this {@link EnergyStorageSystem}.
	 *
	 * @return the {@link PowerConversionSystem}
	 */
	public PowerConversionSystem getPowerConversionSystem();

	@Override
	public default ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(
				OpenemsComponent.getModbusSlaveNatureTable(accessMode),
				SymmetricEss.getModbusSlaveNatureTable(accessMode),
				ManagedSymmetricEss.getModbusSlaveNatureTable(accessMode)
		);
	}

	/**
	 * Generates a default DebugLog message for {@link EnergyStorageSystem} implementations with
	 * a State-Machine.
	 *
	 * @param ess      the {@link EnergyStorageSystem}
	 * @param stateMachine the actual StateMachine (extends
	 *                     {@link AbstractStateMachine})
	 * @return a debug log String
	 */
	public static String generateDebugLog(EnergyStorageSystem ess, AbstractStateMachine<?, ?> stateMachine) {
		var builder = new StringBuilder()
				.append(stateMachine.debugLog()).append("|");
		return _generateDebugLog(ess, builder).toString();
	}

	/**
	 * Generates a default DebugLog message for {@link EnergyStorageSystem} implementations
	 *
	 * @param ess      the {@link EnergyStorageSystem}
	 * @return a debug log String
	 */
	public static String generateDebugLog(EnergyStorageSystem ess) {
		return _generateDebugLog(ess, new StringBuilder()).toString();
	}

	private static StringBuilder _generateDebugLog(EnergyStorageSystem ess, StringBuilder builder) {
		builder.append("SoC:").append(ess.getSoc().asString())
				.append("|L:").append(ess.getActivePower().asString());

		// For hybrid systems, show the actual battery charge power and PV production power
		var pcs = ess.getPowerConversionSystem();
		if (ess instanceof HybridEss hybridEss && pcs instanceof HybridManagedSymmetricBatteryInverter hybridPcs) {
			var dcPvPower = hybridPcs.getDcPvPower();
			if (dcPvPower != null) {
				builder.append("|Battery:").append(hybridEss.getDcDischargePower().asString());
				builder.append("|PV:").append(dcPvPower).append("W");
			}
		}

		// Show max AC export/import active power:
		// minimum of MaxAllowedCharge/DischargePower and MaxApparentPower
		var allowedCharge = ess.getAllowedChargePower().get();
		var allowedDischarge = ess.getAllowedDischargePower().get();
		var maxApparent = ess.getMaxApparentPower().get();
		builder.append("|Allowed:")
				.append(IntUtils.maxInteger(allowedCharge, TypeUtils.multiply(maxApparent, -1))).append("W")
				.append(";")
				.append(IntUtils.minInteger(allowedDischarge, maxApparent)).append("W");
		return builder;
	}

}
