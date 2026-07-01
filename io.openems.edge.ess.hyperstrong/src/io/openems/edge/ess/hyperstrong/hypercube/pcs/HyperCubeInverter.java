package io.openems.edge.ess.hyperstrong.hypercube.pcs;

import static io.openems.common.channel.PersistencePriority.HIGH;
import static io.openems.common.channel.Unit.WATT;
import static io.openems.common.types.OpenemsType.INTEGER;

import io.openems.common.channel.Level;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.batteryinverter.api.ManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.oros.common.SymmetricComponent;
import io.openems.edge.oros.pcs.api.PowerConversionSystem;


public interface HyperCubeInverter extends PowerConversionSystem,
		ManagedSymmetricBatteryInverter, SymmetricBatteryInverter, SymmetricComponent,
		OpenemsComponent, ModbusComponent, ModbusSlave {

	/** Efficiency factor (%) used for AC/DC conversion. */
	public static final float EFFICIENCY_FACTOR = 98F;

	/** Power-precision in [W]. */
	public static final int APPARENT_POWER_PRECISION = 1000;

	/** Power-factors. */
	public static final float APPARENT_POWER_FACTOR = 1.1F;
	public static final float REACTIVE_POWER_FACTOR = 0.46F;

	/** Maximum active power of one HyperCube II PCS in [W]. */
	public static final int MAX_ACTIVE_POWER = 116_500;


	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/**
		 * DC Discharge Power.
		 *
		 * <ul>
		 * <li>Interface: HyperCubeInverter
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#WATT}
		 * <li>Range: negative values for Charge; positive for Discharge
		 * </ul>
		 */
		DC_DISCHARGE_POWER(Doc.of(INTEGER)
				.unit(WATT)
				.persistencePriority(HIGH)),

		/**
		 * IGBT Temperature L1.
		 *
		 * <ul>
		 * <li>Interface: HyperCubeInverter
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#DEZIDEGREE_CELSIUS}
		 * <li>Range: signed
		 * </ul>
		 */
		IGBT_L1_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)),
		/**
		 * IGBT Temperature L2.
		 *
		 * <ul>
		 * <li>Interface: HyperCubeInverter
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#DEZIDEGREE_CELSIUS}
		 * <li>Range: signed
		 * </ul>
		 */
		IGBT_L2_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)),
		/**
		 * IGBT Temperature L3.
		 *
		 * <ul>
		 * <li>Interface: HyperCubeInverter
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#DEZIDEGREE_CELSIUS}
		 * <li>Range: signed
		 * </ul>
		 */
		IGBT_L3_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)),

		/**
		 * Module Temperature.
		 *
		 * <ul>
		 * <li>Interface: HyperCubeInverter
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#DEZIDEGREE_CELSIUS}
		 * <li>Range: signed
		 * </ul>
		 */
		MODULE_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)),

		COMMUNICATION_ENABLED(Doc.of(OpenemsType.BOOLEAN)),
		COMMUNICATION_CONNECTED(Doc.of(OpenemsType.BOOLEAN)),
		COMMUNICATION_ABNORMAL(Doc.of(Level.INFO)),
		COMMUNICATION_FAULT(Doc.of(Level.WARNING)),

		PCS_POWER_ON_STATUS(Doc.of(OpenemsType.INTEGER)),
		PCS_RUNNING_STATUS(Doc.of(OpenemsType.INTEGER)),
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
		LOW_AC_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		HIGH_AC_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		LOW_FREQUENCY_FAULT(Doc.of(Level.FAULT)),
		HIGH_FREQUENCY_FAULT(Doc.of(Level.FAULT)),
		FAST_LOW_AC_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		FAST_HIGH_AC_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		PHASE_REVERSAL_FAULT(Doc.of(Level.FAULT)),
		PHASE_LOSS_FAULT(Doc.of(Level.FAULT)),
		OUTPUT_VOLTAGE_ANOMALY(Doc.of(Level.FAULT)),
		OFF_GRID_STARTUP_BLOCKED(Doc.of(Level.FAULT)),
		ISLAND_PROTECTION_FAULT(Doc.of(Level.FAULT)),
		AC_SHORT_CIRCUIT_FAULT(Doc.of(Level.FAULT)),
		AC_CURRENT_ABNORMAL_FAULT(Doc.of(Level.FAULT)),
		PARALLEL_OVERLOAD_TIMEOUT(Doc.of(Level.FAULT)),
		OUTPUT_OVERLOAD_TIMEOUT(Doc.of(Level.FAULT)),
		AC_POWER_ABNORMAL(Doc.of(Level.FAULT)),
		MODULE_IDENTIFICATION_FAULT(Doc.of(Level.FAULT)),
		VOLTAGE_L1_L2_FAULT(Doc.of(Level.FAULT)),
		VOLTAGE_L2_L3_FAULT(Doc.of(Level.FAULT)),
		VOLTAGE_L3_L1_FAULT(Doc.of(Level.FAULT)),
		DC_SOFT_START_FAULT(Doc.of(Level.FAULT)),
		DC_RELAY_CLOSE_FAULT(Doc.of(Level.FAULT)),
		INDUCTOR_CURRENT_BALANCE_L1_FAULT(Doc.of(Level.FAULT)),
		INDUCTOR_CURRENT_BALANCE_L2_FAULT(Doc.of(Level.FAULT)),
		INDUCTOR_CURRENT_BALANCE_L3_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 2
		BAMS_CURRENT_LIMIT_SHUTDOWN(Doc.of(Level.FAULT)),
		BAMS_POWER_LIMIT_SHUTDOWN(Doc.of(Level.FAULT)),
		BCMS_NO_CHARGE_SHUTDOWN(Doc.of(Level.FAULT)),
		BCMS_DISABLE_SHUTDOWN(Doc.of(Level.FAULT)),
		BAMS_CHARGE_DISABLED_SHUTDOWN(Doc.of(Level.FAULT)),
		BAMS_SHUTDOWN(Doc.of(Level.FAULT)),
		BCMS_CURRENT_LIMIT_SHUTDOWN(Doc.of(Level.FAULT)),
		BCMS_POWER_LIMIT_SHUTDOWN(Doc.of(Level.FAULT)),
		BATTERY_VOLTAGE_LIMIT_SHUTDOWN(Doc.of(Level.FAULT)),
		BATTERY_CURRENT_LIMIT_SHUTDOWN(Doc.of(Level.FAULT)),
		LOW_BATTERY_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		HIGH_BATTERY_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		REVERSE_BATTERY_POLARITY_FAULT(Doc.of(Level.FAULT)),
		HIGH_BATTERY_CURRENT_FAULT(Doc.of(Level.FAULT)),
		INSULATION_FAULT(Doc.of(Level.FAULT)),
		INSULATION_BATTERY_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		LOW_POSITIVE_BUS_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		LOW_NEGATIVE_BUS_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		HIGH_DC_BUS1_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		HIGH_DC_BUS2_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		HIGH_DC_BUS3_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		HIGH_DC_BUS4_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		DC_BUS1_2_VOLTAGE_IMBALANCE_FAULT(Doc.of(Level.FAULT)),
		DC_BUS3_4_VOLTAGE_IMBALANCE_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 3 – Surge arresters / DC relay / Power supplies
		DC_SURGE_ARRESTER_WARNING(Doc.of(Level.WARNING)),
		AC_SURGE_ARRESTER_WARNING(Doc.of(Level.WARNING)),
		DC_RELAY_OPEN_CIRCUIT_FAULT(Doc.of(Level.FAULT)),
		DC_RELAY_SHORT_CIRCUIT_FAULT(Doc.of(Level.FAULT)),
		POWER_SUPPLY_15V_FAULT(Doc.of(Level.FAULT)),
		POWER_SUPPLY_24V_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 4
		DC_BUS1_SHORT_CIRCUIT_FAULT(Doc.of(Level.FAULT)),
		DC_BUS2_SHORT_CIRCUIT_FAULT(Doc.of(Level.FAULT)),
		DC_BUS3_SHORT_CIRCUIT_FAULT(Doc.of(Level.FAULT)),
		DC_BUS4_SHORT_CIRCUIT_FAULT(Doc.of(Level.FAULT)),
		GRID_RELAY_L1_L2_SHORT_CIRCUIT_FAULT(Doc.of(Level.FAULT)),
		GRID_RELAY_L2_L3_SHORT_CIRCUIT_FAULT(Doc.of(Level.FAULT)),
		GRID_RELAY_L3_L1_SHORT_CIRCUIT_FAULT(Doc.of(Level.FAULT)),
		GRID_RELAY_L1_L2_OPEN_CIRCUIT_FAULT(Doc.of(Level.FAULT)),
		GRID_RELAY_L2_L3_OPEN_CIRCUIT_FAULT(Doc.of(Level.FAULT)),
		GRID_RELAY_L3_L1_OPEN_CIRCUIT_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 5
		BUS_VOLTAGE_IMBALANCE_FAULT(Doc.of(Level.FAULT)),
		HIGH_POSITIVE_BUS_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		HIGH_NEGATIVE_BUS_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		CONVERSION_EFFICIENCY_ABNORMAL(Doc.of(Level.FAULT)),
		HIGH_DC_BUS1_HARDWARE_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		HIGH_DC_BUS2_HARDWARE_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		HIGH_DC_BUS3_HARDWARE_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		HIGH_DC_BUS4_HARDWARE_VOLTAGE_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 6
		INVERTER_FAILURE(Doc.of(Level.FAULT)),
		INVERTER_SOFT_START_COMMUNICATION_FAULT(Doc.of(Level.FAULT)),
		INVERTER_COOLING_FAN_WARNING(Doc.of(Level.WARNING)),
		INVERTER_IGBT_FAN_WARNING(Doc.of(Level.WARNING)),
		INVERTER_VOLTAGE_L1_L2_FAULT(Doc.of(Level.FAULT)),
		INVERTER_VOLTAGE_L2_L3_FAULT(Doc.of(Level.FAULT)),
		INVERTER_VOLTAGE_L3_L1_FAULT(Doc.of(Level.FAULT)),
		MISSING_N_LINE_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 7
		LIMITING_N_LINE_CURRENT_WARNING(Doc.of(Level.WARNING)),
		HIGH_N_LINE_CURRENT_FAULT(Doc.of(Level.FAULT)),
		INDUCTOR_CURRENT_BRANCH1_L1_FAULT(Doc.of(Level.FAULT)),
		INDUCTOR_CURRENT_BRANCH1_L2_FAULT(Doc.of(Level.FAULT)),
		INDUCTOR_CURRENT_BRANCH1_L3_FAULT(Doc.of(Level.FAULT)),
		INDUCTOR_CURRENT_BRANCH2_L1_FAULT(Doc.of(Level.FAULT)),
		INDUCTOR_CURRENT_BRANCH2_L2_FAULT(Doc.of(Level.FAULT)),
		INDUCTOR_CURRENT_BRANCH2_L3_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 9
		CABINET_TEMPERATURE_SENSOR_ANOMALY_WARNING(Doc.of(Level.WARNING)),
		HIGH_CABINET_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),
		HIGH_DISCHARGE_RESISTOR_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),
		LOCAL_EPO_FAULT(Doc.of(Level.FAULT)),
		REMOTE_EPO_FAULT(Doc.of(Level.FAULT)),
		HIGH_IGBT_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),
		HIGH_IGBT_BRANCH1_L1_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		HIGH_IGBT_BRANCH2_L1_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		HIGH_IGBT_BRANCH1_L2_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		HIGH_IGBT_BRANCH2_L2_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		HIGH_IGBT_BRANCH1_L3_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		HIGH_IGBT_BRANCH2_L3_TEMPERATURE_WARNING(Doc.of(Level.WARNING)),
		HIGH_SOFT_START_RESISTOR_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 11
		CARRIER_SYNC_FAULT(Doc.of(Level.FAULT)),
		POWER_FREQUENCY_SYNC_FAULT(Doc.of(Level.FAULT)),
		MODULE_IDENTIFICATION_CONFLICT(Doc.of(Level.FAULT)),
		SCHEDULING_CAN_COMMUNICATION_FAULT(Doc.of(Level.FAULT)),
		POWER_CAN1_COMMUNICATION_ANOMALY(Doc.of(Level.WARNING)),
		POWER_CAN2_COMMUNICATION_ANOMALY(Doc.of(Level.FAULT)),
		DSP_ARM_COMMUNICATION_FAULT(Doc.of(Level.FAULT)),
		DSP_FPGA_VERSION_MISMATCH_WARNING(Doc.of(Level.WARNING)),

		// Alarm Value 12
		WAVE_LIMIT_BRANCH1_L1_WARNING(Doc.of(Level.WARNING)),
		WAVE_LIMIT_BRANCH1_L2_WARNING(Doc.of(Level.WARNING)),
		WAVE_LIMIT_BRANCH1_L3_WARNING(Doc.of(Level.WARNING)),
		WAVE_LIMIT_BRANCH2_L1_WARNING(Doc.of(Level.WARNING)),
		WAVE_LIMIT_BRANCH2_L2_WARNING(Doc.of(Level.WARNING)),
		WAVE_LIMIT_BRANCH2_L3_WARNING(Doc.of(Level.WARNING)),
		HIGH_GRID_VOLTAGE_LEVEL1_FAULT(Doc.of(Level.FAULT)),
		HIGH_GRID_VOLTAGE_LEVEL2_FAULT(Doc.of(Level.FAULT)),
		HIGH_GRID_VOLTAGE_LEVEL3_FAULT(Doc.of(Level.FAULT)),
		HIGH_GRID_VOLTAGE_LEVEL4_FAULT(Doc.of(Level.FAULT)),
		HIGH_GRID_VOLTAGE_LEVEL5_FAULT(Doc.of(Level.FAULT)),
		LOW_GRID_VOLTAGE_LEVEL1_FAULT(Doc.of(Level.FAULT)),
		LOW_GRID_VOLTAGE_LEVEL2_FAULT(Doc.of(Level.FAULT)),
		LOW_GRID_VOLTAGE_LEVEL3_FAULT(Doc.of(Level.FAULT)),
		LOW_GRID_VOLTAGE_LEVEL4_FAULT(Doc.of(Level.FAULT)),
		LOW_GRID_VOLTAGE_LEVEL5_FAULT(Doc.of(Level.FAULT)),
		HIGH_GRID_FREQUENCY_LEVEL1_FAULT(Doc.of(Level.FAULT)),
		HIGH_GRID_FREQUENCY_LEVEL2_FAULT(Doc.of(Level.FAULT)),
		HIGH_GRID_FREQUENCY_LEVEL3_FAULT(Doc.of(Level.FAULT)),
		HIGH_GRID_FREQUENCY_LEVEL4_FAULT(Doc.of(Level.FAULT)),
		HIGH_GRID_FREQUENCY_LEVEL5_FAULT(Doc.of(Level.FAULT)),
		LOW_GRID_FREQUENCY_LEVEL1_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 13
		LOW_GRID_FREQUENCY_LEVEL2_FAULT(Doc.of(Level.FAULT)),
		LOW_GRID_FREQUENCY_LEVEL3_FAULT(Doc.of(Level.FAULT)),
		LOW_GRID_FREQUENCY_LEVEL4_FAULT(Doc.of(Level.FAULT)),
		LOW_GRID_FREQUENCY_LEVEL5_FAULT(Doc.of(Level.FAULT)),
		HIGH_MEAN_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		CT_PHASE_REVERSAL_WARNING(Doc.of(Level.WARNING)),
		CT_DETECTION_ANOMALY_WARNING(Doc.of(Level.WARNING)),
		DETECTION_BOX_WARNING(Doc.of(Level.WARNING)),
		ANTI_BACKFLOW_OVERLIMIT_FAULT(Doc.of(Level.FAULT)),
		ANTI_BACKFLOW_METER_COMMUNICATION_WARNING(Doc.of(Level.WARNING)),
		ANTI_BACKFLOW_METER_COMMUNICATION_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 14
		HOST_COMMUNICATION_WARNING(Doc.of(Level.WARNING)),
		BCMS_COMMUNICATION_FAULT(Doc.of(Level.FAULT)),
		DSP_COMMUNICATION_FAULT(Doc.of(Level.FAULT)),
		BAMS_COMMUNICATION_FAULT(Doc.of(Level.FAULT)),
		ETHERNET_COMMUNICATION_FAULT(Doc.of(Level.FAULT)),
		BCMS_ETH_COMMUNICATION_FAULT(Doc.of(Level.FAULT)),
		MODULE_MODEL_MISMATCH_FAULT(Doc.of(Level.FAULT)),
		INTERNAL_PARAMETER_MISMATCH_FAULT(Doc.of(Level.FAULT)),
		FLASH_STORAGE_FAULT(Doc.of(Level.FAULT)),
		RTC_INIT_WARNING(Doc.of(Level.WARNING)),
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

	@Override
	public default float getEfficiencyFactor() {
		return EFFICIENCY_FACTOR;
	}

	@Override
	public default int getPowerPrecision() {
		return APPARENT_POWER_PRECISION;
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

	/**
	 * Gets the Channel for {@link ChannelId#IGBT_L1_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getIgbtL1TemperatureChannel() {
		return this.channel(ChannelId.IGBT_L1_TEMPERATURE);
	}

	/**
	 * Gets the Temperature of the IGBT on L1 in [dezi-degC]. See
	 * {@link ChannelId#IGBT_L1_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getIgbtL1Temperature() {
		return this.getIgbtL1TemperatureChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#IGBT_L1_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setIgbtL1Temperature(Integer value) {
		this.getIgbtL1TemperatureChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#IGBT_L1_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setIgbtL1Temperature(int value) {
		this.getIgbtL1TemperatureChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#IGBT_L2_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getIgbtL2TemperatureChannel() {
		return this.channel(ChannelId.IGBT_L2_TEMPERATURE);
	}

	/**
	 * Gets the Temperature of the IGBT on L2 in [dezi-degC]. See
	 * {@link ChannelId#IGBT_L2_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getIgbtL2Temperature() {
		return this.getIgbtL2TemperatureChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#IGBT_L2_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setIgbtL2Temperature(Integer value) {
		this.getIgbtL2TemperatureChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#IGBT_L2_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setIgbtL2Temperature(int value) {
		this.getIgbtL2TemperatureChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#IGBT_L3_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getIgbtL3TemperatureChannel() {
		return this.channel(ChannelId.IGBT_L3_TEMPERATURE);
	}

	/**
	 * Gets the Temperature of the IGBT on L3 in [dezi-degC]. See
	 * {@link ChannelId#IGBT_L3_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getIgbtL3Temperature() {
		return this.getIgbtL3TemperatureChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#IGBT_L3_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setIgbtL3Temperature(Integer value) {
		this.getIgbtL3TemperatureChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#IGBT_L3_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setIgbtL3Temperature(int value) {
		this.getIgbtL3TemperatureChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MODULE_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getModuleTemperatureChannel() {
		return this.channel(ChannelId.MODULE_TEMPERATURE);
	}

	/**
	 * Gets the Module Temperature in [dezi-degC]. See
	 * {@link ChannelId#MODULE_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getModuleTemperature() {
		return this.getModuleTemperatureChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MODULE_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setModuleTemperature(Integer value) {
		this.getModuleTemperatureChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MODULE_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setModuleTemperature(int value) {
		this.getModuleTemperatureChannel().setNextValue(value);
	}

}
