package io.openems.edge.oros.ess.api;

import static io.openems.common.channel.PersistencePriority.HIGH;
import static io.openems.common.channel.Unit.WATT;
import static io.openems.common.types.OpenemsType.INTEGER;
import static io.openems.edge.common.type.Phase.SingleOrAllPhase.ALL;
import static io.openems.edge.ess.power.api.Pwr.ACTIVE;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.common.utils.IntUtils;
import io.openems.edge.batteryinverter.api.HybridManagedSymmetricBatteryInverter;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveNatureTable;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.modbusslave.ModbusType;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.statemachine.AbstractStateMachine;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.ess.api.AsymmetricEss;
import io.openems.edge.ess.api.HybridEss;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.oros.common.SymmetricComponent;
import io.openems.edge.oros.pcs.api.PowerConversionProvider;

public interface EnergyStorageSystem extends
		ManagedSymmetricEss, SymmetricEss, OpenemsComponent, ModbusSlave, StartStoppable {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Sets a fixed Active Power, relative to {@link ChannelId#MAX_ACTIVE_POWER}.
		 *
		 * <ul>
		 * <li>Interface: Energy Storage System
		 * <li>Type: Integer
		 * <li>Unit: per mille of MAX_ACTIVE_POWER (1000 = 100%)
		 * <li>Range: negative for Charge; positive for Discharge.
		 * </ul>
		 */
		SET_ACTIVE_RELATIVE_POWER_EQUALS(Doc.of(INTEGER)
				.unit(Unit.THOUSANDTH)
				.accessMode(AccessMode.WRITE_ONLY)
				.text("Write command for a charge (-) or discharge power (+), per mille of maximum active power")),

		/**
		 * Sets a maximum Active Power, relative to {@link ChannelId#MAX_ACTIVE_POWER}.
		 *
		 * <ul>
		 * <li>Interface: Energy Storage System
		 * <li>Type: Integer
		 * <li>Unit: per mille of MAX_ACTIVE_POWER (1000 = 100%)
		 * <li>Range: negative for Charge; positive for Discharge
		 * </ul>
		 */
		SET_ACTIVE_RELATIVE_POWER_LESS_OR_EQUALS(Doc.of(INTEGER)
				.unit(Unit.THOUSANDTH)
				.accessMode(AccessMode.WRITE_ONLY)
				.text("Write command for a maximum discharge (+) or minimum charge power (-), per mille of maximum active power")),

		/**
		 * Sets a minimum Active Power, relative to {@link ChannelId#MAX_ACTIVE_POWER}.
		 *
		 * <ul>
		 * <li>Interface: Energy Storage System
		 * <li>Type: Integer
		 * <li>Unit: per mille of maximum active power (1000 = 100%)
		 * <li>Range: negative for Charge; positive for Discharge
		 * </ul>
		 */
		SET_ACTIVE_RELATIVE_POWER_GREATER_OR_EQUALS(Doc.of(INTEGER)
				.unit(Unit.THOUSANDTH)
				.accessMode(AccessMode.WRITE_ONLY)
				.text("Write command for a minimum discharge (+) or maximum charge power (-), per mille of maximum active power")),

		/**
		 * Holds the currently maximum possible active power. This value is commonly
		 * defined by the inverter limitations.
		 *
		 * <ul>
		 * <li>Interface: SymmetricBatteryInverter
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		MAX_ACTIVE_POWER(Doc.of(INTEGER)
				.unit(Unit.WATT)
				.persistencePriority(HIGH)),

		/**
		 * Holds the currently maximum possible reactive power. This value is commonly
		 * defined by the inverter limitations.
		 *
		 * <ul>
		 * <li>Interface: SymmetricBatteryInverter
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		MAX_REACTIVE_POWER(Doc.of(INTEGER)
				.unit(Unit.VOLT_AMPERE_REACTIVE)
				.persistencePriority(HIGH)),

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

	/**
	 * Gets the Channel for {@link ChannelId#MAX_ACTIVE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMaxActivePowerChannel() {
		return this.channel(ChannelId.MAX_ACTIVE_POWER);
	}

	/**
	 * Gets the Maximum Active Power in [W], range "&gt;= 0". See
	 * {@link ChannelId#MAX_ACTIVE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMaxActivePower() {
		return this.getMaxActivePowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MAX_ACTIVE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxActivePower(Integer value) {
		this.getMaxActivePowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MAX_ACTIVE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxActivePower(int value) {
		this.getMaxActivePowerChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MAX_REACTIVE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMaxReactivePowerChannel() {
		return this.channel(ChannelId.MAX_REACTIVE_POWER);
	}

	/**
	 * Gets the Maximum Reactive Power in [var], range "&gt;= 0". See
	 * {@link ChannelId#MAX_REACTIVE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMaxReactivePower() {
		return this.getMaxReactivePowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MAX_REACTIVE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxReactivePower(Integer value) {
		this.getMaxReactivePowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MAX_REACTIVE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxReactivePower(int value) {
		this.getMaxReactivePowerChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#AVAILABLE_CHARGE_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getAvailableChargEnergyChannel() {
		return this.channel(ChannelId.AVAILABLE_CHARGE_ENERGY);
	}

	/**
	 * Gets the Available Charge Energy in [Wh]. See 
	 * {@link ChannelId#AVAILABLE_CHARGE_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getAvailableChargEnergy() {
		return this.getAvailableChargEnergyChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#AVAILABLE_DISCHARGE_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getAvailableDischargEnergyChannel() {
		return this.channel(ChannelId.AVAILABLE_DISCHARGE_ENERGY);
	}

	/**
	 * Gets the Available Discharge Energy in [Wh]. See 
	 * {@link ChannelId#AVAILABLE_DISCHARGE_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getAvailableDischargEnergy() {
		return this.getAvailableDischargEnergyChannel().value();
	}

	@Override
	public default ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(
				Status.getModbusSlaveNatureTable(this, accessMode),
				Control.getModbusSlaveNatureTable(this, accessMode),
				Data.getModbusSlaveNatureTable(this, accessMode)
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

	public static class Status {
		public static ModbusSlaveNatureTable getModbusSlaveNatureTable(EnergyStorageSystem system, AccessMode accessMode) {
			return ModbusSlaveNatureTable.of(Status.class, accessMode, 80)
					.channel(0, OpenemsComponent.ChannelId.STATE, ModbusType.ENUM16)
					.channel(1, SymmetricEss.ChannelId.GRID_MODE, ModbusType.UINT16)
					.build();
		}
	}

	public static class Control {
		public static ModbusSlaveNatureTable getModbusSlaveNatureTable(EnergyStorageSystem system, AccessMode accessMode) {
			return ModbusSlaveNatureTable.of(Control.class, accessMode, 100)
					.int16Reserved(0, 7)

					.channel(8, ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_EQUALS, ModbusType.INT32)
					.channel(10, ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_LESS_OR_EQUALS, ModbusType.INT32)
					.channel(12, ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_GREATER_OR_EQUALS, ModbusType.INT32)
					.channel(14, EnergyStorageSystem.ChannelId.SET_ACTIVE_RELATIVE_POWER_EQUALS, ModbusType.INT16)
					.channel(15, EnergyStorageSystem.ChannelId.SET_ACTIVE_RELATIVE_POWER_LESS_OR_EQUALS, ModbusType.INT16)
					.channel(16, EnergyStorageSystem.ChannelId.SET_ACTIVE_RELATIVE_POWER_GREATER_OR_EQUALS, ModbusType.INT16)
					.int16Reserved(17, 27)

					.channel(28, ManagedSymmetricEss.ChannelId.SET_REACTIVE_POWER_EQUALS, ModbusType.INT32)
					.channel(30, ManagedSymmetricEss.ChannelId.SET_REACTIVE_POWER_LESS_OR_EQUALS, ModbusType.INT32)
					.channel(32, ManagedSymmetricEss.ChannelId.SET_REACTIVE_POWER_GREATER_OR_EQUALS, ModbusType.INT32)
					.build();
		}
	}

	public static class Data {
		public static ModbusSlaveNatureTable getModbusSlaveNatureTable(EnergyStorageSystem system, AccessMode accessMode) {
			var table = ModbusSlaveNatureTable.of(Data.class, accessMode, 820)
					.channel(0, EnergyStorageSystem.ChannelId.MAX_ACTIVE_POWER, ModbusType.UINT32)
					.channel(2, EnergyStorageSystem.ChannelId.MAX_REACTIVE_POWER, ModbusType.UINT32)
					.channel(4, SymmetricEss.ChannelId.MAX_APPARENT_POWER, ModbusType.UINT32)
					.int16Reserved(6, 7)

					.<ManagedSymmetricEss>cycleValue(8, "Available Charge Power", WATT, "", ModbusType.INT32,
							c -> c.getPower().getMinPower(c, ALL, ACTIVE))
					.<ManagedSymmetricEss>cycleValue(10, "Available Discharge Power", WATT, "", ModbusType.INT32,
							c -> c.getPower().getMaxPower(c, ALL, ACTIVE))

					.channel(12, EnergyStorageSystem.ChannelId.AVAILABLE_CHARGE_ENERGY, ModbusType.UINT32)
					.channel(14, EnergyStorageSystem.ChannelId.AVAILABLE_DISCHARGE_ENERGY, ModbusType.UINT32)
					.channel(16, SymmetricEss.ChannelId.CAPACITY, ModbusType.UINT32)
					.channel(18, SymmetricEss.ChannelId.SOC, ModbusType.UINT16)
					.int16Reserved(19, 27);

			table.channel(28, SymmetricEss.ChannelId.ACTIVE_POWER, ModbusType.INT32);
			if (system instanceof SymmetricComponent) {
				table.channel(30, SymmetricComponent.ChannelId.ACTIVE_POWER_L1, ModbusType.INT32)
						.channel(32, SymmetricComponent.ChannelId.ACTIVE_POWER_L2, ModbusType.INT32)
						.channel(34, SymmetricComponent.ChannelId.ACTIVE_POWER_L3, ModbusType.INT32)
						.int16Reserved(36, 37);
			}
			else if (system instanceof AsymmetricEss) {
				table.channel(30, AsymmetricEss.ChannelId.ACTIVE_POWER_L1, ModbusType.INT32)
						.channel(32, AsymmetricEss.ChannelId.ACTIVE_POWER_L2, ModbusType.INT32)
						.channel(34, AsymmetricEss.ChannelId.ACTIVE_POWER_L3, ModbusType.INT32)
						.int16Reserved(36, 37);
			} else {
				table.int16Reserved(30, 37);
			}
			table.channel(38, SymmetricEss.ChannelId.REACTIVE_POWER, ModbusType.INT32);
			if (system instanceof SymmetricComponent) {
				table.channel(40, SymmetricComponent.ChannelId.REACTIVE_POWER_L1, ModbusType.INT32)
						.channel(42, SymmetricComponent.ChannelId.REACTIVE_POWER_L2, ModbusType.INT32)
						.channel(44, SymmetricComponent.ChannelId.REACTIVE_POWER_L3, ModbusType.INT32)
						.int16Reserved(46, 47);
			}
			else if (system instanceof AsymmetricEss) {
				table.channel(40, AsymmetricEss.ChannelId.REACTIVE_POWER_L1, ModbusType.INT32)
						.channel(42, AsymmetricEss.ChannelId.REACTIVE_POWER_L2, ModbusType.INT32)
						.channel(44, AsymmetricEss.ChannelId.REACTIVE_POWER_L3, ModbusType.INT32)
						.int16Reserved(46, 37);
			} else {
				table.int16Reserved(40, 47);
			}
			return table.build();
		}
	}

}
