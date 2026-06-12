package io.openems.edge.ess.hyperstrong.hypercube.bms;

import java.util.function.Consumer;

import io.openems.common.channel.Level;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.oros.bms.api.BatteryManagementSystem;

public interface HyperCubeBattery extends BatteryManagementSystem, Battery,
		OpenemsComponent, ModbusComponent, ModbusSlave, StartStoppable {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		POSITIVE_RELAY_STATUS(Doc.of(OpenemsType.BOOLEAN)
				.persistencePriority(PersistencePriority.HIGH)),
		NEGATIVE_RELAY_STATUS(Doc.of(OpenemsType.BOOLEAN)
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Max Aviation Connector Voltage.
		 *
		 * <ul>
		 * <li>Interface: HyperBattery
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * <li>Range: > 0
		 * </ul>
		 */
		MAX_CONNECTOR_VOLTAGE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)
				.persistencePriority(PersistencePriority.MEDIUM)
				.text("Maximum aviation connector voltage")),
		MAX_CONNECTOR_VOLTAGE_INDEX(Doc.of(OpenemsType.INTEGER)
				.persistencePriority(PersistencePriority.MEDIUM)),
		/**
		 * Min Aviation Connector Voltage.
		 *
		 * <ul>
		 * <li>Interface: HyperBattery
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * <li>Range: > 0
		 * </ul>
		 */
		MIN_CONNECTOR_VOLTAGE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)
				.persistencePriority(PersistencePriority.MEDIUM)
				.text("Minimum aviation connector voltage")),
		MIN_CONNECTOR_VOLTAGE_INDEX(Doc.of(OpenemsType.INTEGER)
				.persistencePriority(PersistencePriority.MEDIUM)),

		/**
		 * Max Aviation Connector Temperature.
		 *
		 * <ul>
		 * <li>Interface: Ess Symmetric
		 * <li>Type: Integer
		 * <li>Unit: °C
		 * <li>Range: -273 to positive infinity
		 * </ul>
		 */
		MAX_CONNECTOR_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)
				.persistencePriority(PersistencePriority.MEDIUM)
				.text("Maximum aviation connector temperature")),
		MAX_CONNECTOR_TEMPERATURE_INDEX(Doc.of(OpenemsType.INTEGER)
				.persistencePriority(PersistencePriority.MEDIUM)),
		/**
		 * Min Aviation Connector Temperature.
		 *
		 * <ul>
		 * <li>Interface: HyperBattery
		 * <li>Type: Integer
		 * <li>Unit: °C
		 * <li>Range: -273 to positive infinity
		 * </ul>
		 */
		MIN_CONNECTOR_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)
				.persistencePriority(PersistencePriority.MEDIUM)
				.text("Minimum aviation connector temperature")),
		MIN_CONNECTOR_TEMPERATURE_INDEX(Doc.of(OpenemsType.INTEGER)
				.persistencePriority(PersistencePriority.MEDIUM)),

		/**
		 * Max Copper Bar Temperature.
		 *
		 * <ul>
		 * <li>Interface: Ess Symmetric
		 * <li>Type: Integer
		 * <li>Unit: °C
		 * <li>Range: -273 to positive infinity
		 * </ul>
		 */
		MAX_BUSBAR_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)
				.persistencePriority(PersistencePriority.MEDIUM)
				.text("Maximum busbar temperature")),
		MAX_BUSBAR_TEMPERATURE_INDEX(Doc.of(OpenemsType.INTEGER)
				.persistencePriority(PersistencePriority.MEDIUM)),
		/**
		 * Min Copper Bar Temperature.
		 *
		 * <ul>
		 * <li>Interface: HyperBattery
		 * <li>Type: Integer
		 * <li>Unit: °C
		 * <li>Range: -273 to positive infinity
		 * </ul>
		 */
		MIN_BUSBAR_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)
				.persistencePriority(PersistencePriority.MEDIUM)
				.text("Minimum busbar temperature")),
		MIN_BUSBAR_TEMPERATURE_INDEX(Doc.of(OpenemsType.INTEGER)
				.persistencePriority(PersistencePriority.MEDIUM)),

		INSULATION_RESISTANCE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.KILOOHM)
				.persistencePriority(PersistencePriority.HIGH)),

		PRECHARGE_VOLTAGE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)
				.persistencePriority(PersistencePriority.HIGH)),

		COMMUNICATION_ENABLED(Doc.of(OpenemsType.BOOLEAN)),
		COMMUNICATION_CONNECTED(Doc.of(OpenemsType.BOOLEAN)),
		COMMUNICATION_ABNORMAL(Doc.of(Level.INFO)),
		COMMUNICATION_FAULT(Doc.of(Level.WARNING)),
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

	public enum AlarmChannelId implements io.openems.edge.common.channel.ChannelId {
		// Alarm Value 1
		HIGH_CELL_VOLTAGE_INFO(Doc.of(Level.INFO)),
		HIGH_CELL_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		HIGH_CELL_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		LOW_CELL_VOLTAGE_INFO(Doc.of(Level.INFO)),
		LOW_CELL_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		LOW_CELL_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		IMBALANCE_CELL_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		IMBALANCE_CELL_VOLTAGE_SEVERE_WARNING(Doc.of(Level.WARNING)),
		IMBALANCE_CELL_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		HIGH_VOLTAGE_INFO(Doc.of(Level.INFO)),
		HIGH_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		HIGH_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		LOW_VOLTAGE_INFO(Doc.of(Level.INFO)),
		LOW_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		LOW_VOLTAGE_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 2
		HIGH_DISCHARGE_CURRENT_FAULT(Doc.of(Level.FAULT)),
		HIGH_DISCHARGE_CURRENT_SEVERE_FAULT(Doc.of(Level.FAULT)),
		HIGH_DISCHARGE_CURRENT_CRITICAL_FAULT(Doc.of(Level.FAULT)),
		HIGH_CHARGE_CURRENT_FAULT(Doc.of(Level.FAULT)),
		HIGH_CHARGE_CURRENT_SEVERE_FAULT(Doc.of(Level.FAULT)),
		HIGH_CHARGE_CURRENT_CRITICAL_FAULT(Doc.of(Level.FAULT)),
		HIGH_TEMPERATURE_INFO(Doc.of(Level.INFO)),
		HIGH_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		HIGH_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),
		LOW_TEMPERATURE_INFO(Doc.of(Level.INFO)),
		LOW_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		LOW_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),
		HIGH_TEMPERATURE_DIFFERENTIAL_INFO(Doc.of(Level.INFO)),
		HIGH_TEMPERATURE_DIFFERENTIAL_WARNING(Doc.of(Level.WARNING)),
		HIGH_TEMPERATURE_DIFFERENTIAL_FAULT(Doc.of(Level.FAULT)),
		RAPID_TEMPERATURE_RISE_WARNING(Doc.of(Level.WARNING)),
		RAPID_TEMPERATURE_RISE_SEVERE_WARNING(Doc.of(Level.WARNING)),
		RAPID_TEMPERATURE_RISE_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 3
		HIGH_SOC_INFO(Doc.of(Level.INFO)),
		HIGH_SOC_WARNING(Doc.of(Level.WARNING)),
		HIGH_SOC_FAULT(Doc.of(Level.FAULT)),
		LOW_SOC_INFO(Doc.of(Level.INFO)),
		LOW_SOC_WARNING(Doc.of(Level.WARNING)),
		LOW_SOC_FAULT(Doc.of(Level.FAULT)),
		HIGH_BUSBAR_TEMPERATURE_INFO(Doc.of(Level.INFO)),
		HIGH_BUSBAR_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		HIGH_BUSBAR_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),
		EXCESSIVE_BATTERY_VOLTAGE_DIFFERENTIAL_WARNING(Doc.of(Level.WARNING)),
		EXCESSIVE_BATTERY_VOLTAGE_DIFFERENTIAL_SEVERE_WARNING(Doc.of(Level.WARNING)),
		EXCESSIVE_BATTERY_VOLTAGE_DIFFERENTIAL_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 4
		BCMS_COMMUNICATION_FAULT(Doc.of(Level.FAULT)),
		BAMS_COMMUNICATION_FAULT(Doc.of(Level.FAULT)),
		EXTREME_HIGH_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		EXTREME_LOW_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		EXTREME_HIGH_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),
		EXTREME_LOW_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 5
		HIGH_CONTACTOR_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		HIGH_CONTACTOR_TEMPERATURE_SEVERE_WARNING(Doc.of(Level.WARNING)),
		HIGH_CONTACTOR_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),
		HIGH_POWER_MODULE_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		HIGH_POWER_MODULE_TEMPERATURE_SEVERE_WARNING(Doc.of(Level.WARNING)),
		HIGH_POWER_MODULE_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),
        // Reserved bits for backward compatibility
        //HIGH_BUSBAR_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
        //HIGH_BUSBAR_TEMPERATURE_SEVERE_WARNING(Doc.of(Level.WARNING)),
        //HIGH_BUSBAR_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),
		HIGH_CONNECTOR_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		HIGH_CONNECTOR_TEMPERATURE_SEVERE_WARNING(Doc.of(Level.WARNING)),
		HIGH_CONNECTOR_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 6
		MSD_DISCONNECT_FAULT(Doc.of(Level.FAULT)),
		MAIN_POSITIVE_CONTACTOR_FAULT(Doc.of(Level.FAULT)),
		INSULATION_MODULE_COMMUNICATION_WARNING(Doc.of(Level.WARNING)),
		PARAMETER_CONFIGURATION_WARNING(Doc.of(Level.WARNING)),
		HALL_SENSOR_OPEN_CIRCUIT_WARNING(Doc.of(Level.WARNING)),
		TEMPERATURE_SENSOR_OPEN_CIRCUIT_WARNING(Doc.of(Level.WARNING)),
		TEMPERATURE_SENSOR_SHORT_CIRCUIT_WARNING(Doc.of(Level.WARNING)),
		THERMAL_MANAGEMENT_COMMUNICATION_TIMEOUT(Doc.of(Level.WARNING)),
		AEROSOL_SIGNAL_DISCONNECT_WARNING(Doc.of(Level.WARNING)),
		HIGH_VOLTAGE_CALIBRATION_WARNING(Doc.of(Level.WARNING)),
		CURRENT_CALIBRATION_WARNING(Doc.of(Level.WARNING)),
		FIRE_DETECTOR_COMMUNICATION_TIMEOUT(Doc.of(Level.WARNING)),
		FIRE_DETECTOR_DISCONNECT_FAULT(Doc.of(Level.WARNING)),

		// Alarm Value 7
		FIRE_DETECTOR_1_FAULT(Doc.of(Level.FAULT)),
		FIRE_DETECTOR_2_FAULT(Doc.of(Level.FAULT)),
		FIRE_DETECTOR_3_FAULT(Doc.of(Level.FAULT)),
		FIRE_DETECTOR_4_FAULT(Doc.of(Level.FAULT)),
		FIRE_DETECTOR_5_FAULT(Doc.of(Level.FAULT)),
		FIRE_DETECTOR_6_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 9
		IMBALANCE_CELL_CHARGE_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		IMBALANCE_CELL_CHARGE_VOLTAGE_SEVERE_WARNING(Doc.of(Level.WARNING)),
		IMBALANCE_CELL_CHARGE_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		IMBALANCE_CELL_DISCHARGE_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		IMBALANCE_CELL_DISCHARGE_VOLTAGE_SEVERE_WARNING(Doc.of(Level.WARNING)),
		IMBALANCE_CELL_DISCHARGE_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		HIGH_PACK_VOLTAGE_INFO(Doc.of(Level.INFO)),
		HIGH_PACK_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		HIGH_PACK_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		LOW_PACK_VOLTAGE_INFO(Doc.of(Level.INFO)),
		LOW_PACK_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		LOW_PACK_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		THERMAL_MANAGEMENT_SYSTEM_WARNING(Doc.of(Level.WARNING)),
		THERMAL_MANAGEMENT_SYSTEM_FAULT(Doc.of(Level.FAULT)),
		THERMAL_MANAGEMENT_SYSTEM_SEVERE_FAULT(Doc.of(Level.FAULT)),
		;

		private final Doc doc;

		private AlarmChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	/**
	 * Gets the Channel for {@link ChannelId#INSULATION_RESISTANCE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getInsulationResistanceChannel() {
		return this.channel(ChannelId.INSULATION_RESISTANCE);
	}

	/**
	 * Gets the insulation resistance in [kOhm].
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getInsulationResistance() {
		return this.getInsulationResistanceChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#PRECHARGE_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getPrechargeVoltageChannel() {
		return this.channel(ChannelId.PRECHARGE_VOLTAGE);
	}

	/**
	 * Gets the precharge voltage in [mV].
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getPrechargeVoltage() {
		return this.getPrechargeVoltageChannel().value();
	}

	public static void mirrorOpenCircuitVoltageFromPrecharge(HyperCubeBattery battery) {
		final Consumer<Value<Integer>> accept = value -> {
			if (value.isDefined()) {
				battery._setOpenCircuitVoltage(value.get());
			}
		};
		battery.getPrechargeVoltageChannel().onSetNextValue(accept);
	}

}
