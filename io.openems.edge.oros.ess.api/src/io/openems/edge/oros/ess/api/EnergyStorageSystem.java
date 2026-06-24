package io.openems.edge.oros.ess.api;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.PersistencePriority;
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
import io.openems.edge.oros.pcs.api.PowerConversionProvider;

public interface EnergyStorageSystem extends
		ManagedSymmetricEss, SymmetricEss, EnergyStorageProtection,
		OpenemsComponent, ModbusSlave, StartStoppable {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/**
		 * Available Charge Energy.
		 *
		 * <ul>
		 * <li>Interface: Energy Management System
		 * <li>Type: Integer
		 * <li>Unit: Wh
		 * </ul>
		 */
		AVAILABLE_CHARGE_ENERGY(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT_HOURS)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Available Discharge Energy.
		 *
		 * <ul>
		 * <li>Interface: Energy Management System
		 * <li>Type: Integer
		 * <li>Unit: Wh
		 * </ul>
		 */
		AVAILABLE_DISCHARGE_ENERGY(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT_HOURS)
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

	public default boolean isReadOnly() {
		return false;
	}

	@Override
	public default ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(
				OpenemsComponent.getModbusSlaveNatureTable(accessMode),
				SymmetricEss.getModbusSlaveNatureTable(accessMode),
				ManagedSymmetricEss.getModbusSlaveNatureTable(accessMode)
		);
	}

	/**
	 * Generates a default DebugLog message for {@link EnergyStorageSystem}
	 * implementations with a State-Machine.
	 *
	 * @param ess          the {@link EnergyStorageSystem}
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
	 * Generates a default DebugLog message for {@link EnergyStorageSystem}
	 * implementations.
	 *
	 * @param ess the {@link EnergyStorageSystem}
	 * @return a debug log String
	 */
	public static String generateDebugLog(EnergyStorageSystem ess) {
		return _generateDebugLog(ess, new StringBuilder()).toString();
	}

	private static StringBuilder _generateDebugLog(EnergyStorageSystem ess, StringBuilder builder) {
		builder.append("SoC:").append(ess.getSoc().asString())
				.append("|L:").append(ess.getActivePower().asString());

		// For hybrid systems, show the actual battery charge power and PV production power
		if (ess instanceof HybridEss hybridEss && ess instanceof PowerConversionProvider provider
				&& provider.getPowerConversionSystem() instanceof HybridManagedSymmetricBatteryInverter hybridPcs) {
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
