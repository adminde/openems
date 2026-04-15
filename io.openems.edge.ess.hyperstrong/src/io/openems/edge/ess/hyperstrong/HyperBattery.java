package io.openems.edge.ess.hyperstrong;

import java.util.function.Consumer;

import io.openems.common.channel.Level;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.ess.api.SymmetricEss;

public interface HyperBattery extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Voltage of the battery.
		 *
		 * <ul>
		 * <li>Interface: HyperBattery
		 * <li>Type: Integer
		 * <li>Unit: V
		 * </ul>
		 */
		BATTERY_VOLTAGE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Current of the battery.
		 *
		 * <ul>
		 * <li>Interface: HyperBattery
		 * <li>Type: Integer
		 * <li>Unit: A
		 * </ul>
		 */
		BATTERY_CURRENT(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Power of the battery.
		 *
		 * <ul>
		 * <li>Interface: HyperBattery
		 * <li>Type: Integer
		 * <li>Unit: Watt
		 * </ul>
		 */
		BATTERY_POWER(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Maximum power for charging.
		 *
		 * <ul>
		 * <li>Interface: HyperBattery
		 * <li>Type: Integer
		 * <li>Unit: Watt
		 * </ul>
		 */
		BATTERY_MAX_CHARGE_POWER(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Maximum power for discharging.
		 *
		 * <ul>
		 * <li>Interface: HyperBattery
		 * <li>Type: Integer
		 * <li>Unit: Watt
		 * </ul>
		 */
		BATTERY_MAX_DISCHARGE_POWER(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),

		BATTERY_POSITIVE_RELAY_STATUS(Doc.of(OpenemsType.BOOLEAN)
				.persistencePriority(PersistencePriority.HIGH)),
		BATTERY_NEGATIVE_RELAY_STATUS(Doc.of(OpenemsType.BOOLEAN)
				.persistencePriority(PersistencePriority.HIGH)),

		BATTERY_SOC(Doc.of(OpenemsType.FLOAT)
				.unit(Unit.PERCENT)
				.persistencePriority(PersistencePriority.MEDIUM)),

		SOE(Doc.of(OpenemsType.FLOAT)
				.unit(Unit.PERCENT)
				.persistencePriority(PersistencePriority.HIGH)),
		SOH(Doc.of(OpenemsType.FLOAT)
				.unit(Unit.PERCENT)
				.persistencePriority(PersistencePriority.HIGH)),

		MAX_CELL_TEMPERATURE_INDEX(Doc.of(OpenemsType.INTEGER)
				.persistencePriority(PersistencePriority.HIGH)),
		MAX_CELL_VOLTAGE_INDEX(Doc.of(OpenemsType.INTEGER)
				.persistencePriority(PersistencePriority.HIGH)),

		MIN_CELL_TEMPERATURE_INDEX(Doc.of(OpenemsType.INTEGER)
				.persistencePriority(PersistencePriority.HIGH)),
		MIN_CELL_VOLTAGE_INDEX(Doc.of(OpenemsType.INTEGER)
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

		BATTERY_COMMUNICATION_ENABLED(Doc.of(OpenemsType.BOOLEAN)),
		BATTERY_COMMUNICATION_CONNECTED(Doc.of(OpenemsType.BOOLEAN)),
		BATTERY_COMMUNICATION_ABNORMAL(Doc.of(Level.INFO)),
		BATTERY_COMMUNICATION_FAULT(Doc.of(Level.WARNING)),
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
		HIGH_CELL_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		HIGH_CELL_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		LOW_CELL_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		LOW_CELL_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		IMBALANCE_CELL_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		IMBALANCE_CELL_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		HIGH_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		HIGH_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		LOW_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		LOW_VOLTAGE_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 2
		HIGH_DISCHARGE_CURRENT_FAULT(Doc.of(Level.FAULT)),
		HIGH_CHARGE_CURRENT_FAULT(Doc.of(Level.FAULT)),
		HIGH_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		HIGH_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),
		LOW_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		LOW_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),
		HIGH_TEMPERATURE_DIFFERENTIAL_WARNING(Doc.of(Level.WARNING)),
		HIGH_TEMPERATURE_DIFFERENTIAL_FAULT(Doc.of(Level.FAULT)),
		RAPID_TEMPERATURE_RISE_WARNING(Doc.of(Level.WARNING)),
		RAPID_TEMPERATURE_RISE_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 3
		HIGH_SOC_WARNING(Doc.of(Level.WARNING)),
		HIGH_SOC_FAULT(Doc.of(Level.FAULT)),
		LOW_SOC_WARNING(Doc.of(Level.WARNING)),
		LOW_SOC_FAULT(Doc.of(Level.FAULT)),
		HIGH_BUSBAR_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		HIGH_BUSBAR_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),
		EXCESSIVE_BATTERY_VOLTAGE_DIFFERENTIAL_WARNING(Doc.of(Level.WARNING)),
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
		HIGH_CONTACTOR_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),
		HIGH_POWER_MODULE_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		HIGH_POWER_MODULE_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),
		// Reserved bits for backward compatibility
		//HIGH_BUSBAR_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		//HIGH_BUSBAR_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),
		HIGH_CONNECTOR_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		HIGH_CONNECTOR_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 6
		MSD_DISCONNECT_FAULT(Doc.of(Level.FAULT)),
		MAIN_POSITIVE_CONTACTOR_FAULT(Doc.of(Level.FAULT)),
		INSULATION_MODULE_COMMUNICATION_WARNING(Doc.of(Level.WARNING)),
		PARAMETER_CONFIGURATION_WARNING(Doc.of(Level.WARNING)),
		HALL_SENSOR_OPEN_CIRCUIT_WARNING(Doc.of(Level.WARNING)),
		TEMPERATURE_SENSOR_OPEN_CIRCUIT_WARNING(Doc.of(Level.WARNING)),
		TEMPERATURE_SENSOR_SHORT_CIRCUIT_WARNING(Doc.of(Level.WARNING)),
		COOLING_SYSTEM_COMMUNICATION_TIMEOUT(Doc.of(Level.WARNING)),
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
		IMBALANCE_CELL_CHARGE_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		IMBALANCE_CELL_DISCHARGE_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		IMBALANCE_CELL_DISCHARGE_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		HIGH_PACK_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		HIGH_PACK_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		LOW_PACK_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		LOW_PACK_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		COOLING_SYSTEM_MANAGEMENT_WARNING(Doc.of(Level.WARNING)),
		COOLING_SYSTEM_MANAGEMENT_FAULT(Doc.of(Level.FAULT)),
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
	 * Gets the Channel for {@link ChannelId#BATTERY_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getBatteryVoltageChannel() {
		return this.channel(ChannelId.BATTERY_VOLTAGE);
	}

	/**
	 * Gets the Battery Rack Voltage in [mV]. See {@link ChannelId#BATTERY_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getBatteryVoltage() {
		return this.getBatteryVoltageChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#BATTERY_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getBatteryCurrentChannel() {
		return this.channel(ChannelId.BATTERY_CURRENT);
	}

	/**
	 * Gets the Battery Rack Current in [mA]. See {@link ChannelId#BATTERY_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getBatteryCurrent() {
		return this.getBatteryCurrentChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#BATTERY_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getBatteryPowerChannel() {
		return this.channel(ChannelId.BATTERY_POWER);
	}

	/**
	 * Gets the Battery Rack Power in [W]. See {@link ChannelId#BATTERY_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getBatteryPower() {
		return this.getBatteryPowerChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#BATTERY_MAX_CHARGE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getBatteryChargeMaxPowerChannel() {
		return this.channel(ChannelId.BATTERY_MAX_CHARGE_POWER);
	}

	/**
	 * Gets the Battery Rack Charge Maximum Power in [W].
	 * See {@link ChannelId#BATTERY_MAX_CHARGE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getBatteryChargeMaxPower() {
		return this.getBatteryChargeMaxPowerChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#BATTERY_MAX_DISCHARGE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getBatteryDischargeMaxPowerChannel() {
		return this.channel(ChannelId.BATTERY_MAX_DISCHARGE_POWER);
	}

	/**
	 * Gets the Battery Rack Discharge Maximum Power in [W].
	 * See {@link ChannelId#BATTERY_MAX_DISCHARGE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getBatteryDischargeMaxPower() {
		return this.getBatteryDischargeMaxPowerChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#MAX_CELL_TEMPERATURE_INDEX}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMaxCellTemperatureIndexChannel() {
		return this.channel(ChannelId.MAX_CELL_TEMPERATURE_INDEX);
	}

	/**
	 * Gets the Max Cell Temperature Index. See
	 * {@link ChannelId#MAX_CELL_TEMPERATURE_INDEX}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMaxCellTemperatureIndex() {
		return this.getMaxCellTemperatureIndexChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#MAX_CELL_VOLTAGE_INDEX}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMaxCellVoltageIndexChannel() {
		return this.channel(ChannelId.MAX_CELL_VOLTAGE_INDEX);
	}

	/**
	 * Gets the Max Cell Voltage Index. See
	 * {@link ChannelId#MAX_CELL_VOLTAGE_INDEX}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMaxCellVoltageIndex() {
		return this.getMaxCellVoltageIndexChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#MIN_CELL_TEMPERATURE_INDEX}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMinCellTemperatureIndexChannel() {
		return this.channel(ChannelId.MIN_CELL_TEMPERATURE_INDEX);
	}

	/**
	 * Gets the Min Cell Temperature Index. See
	 * {@link ChannelId#MIN_CELL_TEMPERATURE_INDEX}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMinCellTemperatureIndex() {
		return this.getMinCellTemperatureIndexChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#MIN_CELL_VOLTAGE_INDEX}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMinCellVoltageIndexChannel() {
		return this.channel(ChannelId.MIN_CELL_VOLTAGE_INDEX);
	}

	/**
	 * Gets the Min Cell Voltage Index. See
	 * {@link ChannelId#MIN_CELL_VOLTAGE_INDEX}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMinCellVoltageIndex() {
		return this.getMinCellVoltageIndexChannel().value();
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
	 * Gets the insulation resistance in [mOhm]. See
	 * {@link ChannelId#INSULATION_RESISTANCE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getInsulationResistance() {
		return this.getInsulationResistanceChannel().value();
	}

	public static void activateSocUpdate(HyperBattery battery) {
		Channel<Integer> batterySocChannel = battery.channel(HyperBattery.ChannelId.BATTERY_SOC);

		final Consumer<Value<Integer>> calculate = ignore -> {
			var batterySoc = batterySocChannel.value();
			var batteryChargeMaxPower = battery.getBatteryChargeMaxPower();
			var batteryDischargeMaxPower = battery.getBatteryDischargeMaxPower();
			final Integer soc;
			if (batterySoc.isDefined()) {
				if (batteryDischargeMaxPower.isDefined()
						&& batterySoc.get() <= 2
						&& batteryDischargeMaxPower.get() <= 100) {
					// Set the SoC to 0 if it is less than 3 %
					soc = 0;

				} else if (batteryChargeMaxPower.isDefined()
						&& batterySoc.get() >= 98
						&& batteryChargeMaxPower.get() <= 100) {
					// Set the SoC to 100 if it is more than 97 %
					soc = 100;

				} else {
					// Apply the normal SoC if it not in the above ranges.
					soc = batterySoc.get();
				}

			} else {
				// Original Battery-SoC is undefined
				soc = null;
			}
			switch (battery) {
//			case Battery battery -> {
//				battery._setSoc(soc);
//			}
			case SymmetricEss ess -> {
				ess._setSoc(soc);
			}
			default -> {
				throw new IllegalArgumentException("Unexpected battery type: " + battery.getClass().getSimpleName());
			}
			}
		};
		batterySocChannel.onSetNextValue(calculate);
		battery.getBatteryChargeMaxPowerChannel().onSetNextValue(calculate);
		battery.getBatteryDischargeMaxPowerChannel().onSetNextValue(calculate);
	}

}