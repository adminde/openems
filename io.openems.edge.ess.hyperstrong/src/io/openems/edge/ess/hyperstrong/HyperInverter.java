package io.openems.edge.ess.hyperstrong;

import java.util.function.Function;

import io.openems.common.channel.Level;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.FloatReadChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;

public interface HyperInverter extends OpenemsComponent {

	/**
	 * Efficiency factor to calculate AC Charge/Discharge limits from DC. Used at
	 * {@link AllowedPowerHandler}.
	 */
	public static final float EFFICIENCY_FACTOR = 0.98F;

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Active Power L1.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#WATT}
		 * </ul>
		 */
		ACTIVE_POWER_L1(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Active Power L2.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#WATT}
		 * </ul>
		 */
		ACTIVE_POWER_L2(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Active Power L3.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#WATT}
		 * </ul>
		 */
		ACTIVE_POWER_L3(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Reactive Power L1.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#VOLT_AMPERE_REACTIVE}
		 * </ul>
		 */
		REACTIVE_POWER_L1(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.VOLT_AMPERE_REACTIVE)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Reactive Power L2.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#VOLT_AMPERE_REACTIVE}
		 * </ul>
		 */
		REACTIVE_POWER_L2(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.VOLT_AMPERE_REACTIVE)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Reactive Power L3.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#VOLT_AMPERE_REACTIVE}
		 * </ul>
		 */
		REACTIVE_POWER_L3(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.VOLT_AMPERE_REACTIVE)
				.persistencePriority(PersistencePriority.HIGH)),

		POWER_FACTOR(Doc.of(OpenemsType.FLOAT)
				.unit(Unit.NONE)
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Voltage L1-L2.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIVOLT}
		 * <li>Range: only positive values
		 * </ul>
		 */
		VOLTAGE_L1_L2(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Voltage L2-3.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIVOLT}
		 * <li>Range: only positive values
		 * </ul>
		 */
		VOLTAGE_L2_L3(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Voltage L3-L1.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIVOLT}
		 * <li>Range: only positive values
		 * </ul>
		 */
		VOLTAGE_L3_L1(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Voltage L1.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIVOLT}
		 * <li>Range: only positive values
		 * </ul>
		 */
		VOLTAGE_L1(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Voltage L2.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIVOLT}
		 * <li>Range: only positive values
		 * </ul>
		 */
		VOLTAGE_L2(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Voltage L3.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIVOLT}
		 * <li>Range: only positive values
		 * </ul>
		 */
		VOLTAGE_L3(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Current L1.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIAMPERE}
		 * </ul>
		 */
		CURRENT_L1(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Current L2.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIAMPERE}
		 * </ul>
		 */
		CURRENT_L2(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Current L3.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIAMPERE}
		 * </ul>
		 */
		CURRENT_L3(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Frequency.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIHERTZ}
		 * <li>Range: only positive values
		 * </ul>
		 */
		FREQUENCY(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIHERTZ)
				.persistencePriority(PersistencePriority.HIGH)),

		DC_VOLTAGE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),

		MODULE_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)
				.persistencePriority(PersistencePriority.HIGH)),
		AIR_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)
				.persistencePriority(PersistencePriority.HIGH)),
		IGBT_L1_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)
				.persistencePriority(PersistencePriority.HIGH)),
		IGBT_L2_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)
				.persistencePriority(PersistencePriority.HIGH)),
		IGBT_L3_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)
				.persistencePriority(PersistencePriority.HIGH)),

		INVERTER_COMMUNICATION_ENABLED(Doc.of(OpenemsType.BOOLEAN)),
		INVERTER_COMMUNICATION_CONNECTED(Doc.of(OpenemsType.BOOLEAN)),
		INVERTER_COMMUNICATION_ABNORMAL(Doc.of(Level.INFO)),
		INVERTER_COMMUNICATION_FAULT(Doc.of(Level.WARNING)),
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
		OUTPUT_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		OFF_GRID_STARTUP_BLOCKED_FAULT(Doc.of(Level.FAULT)),
		ISLAND_PROTECTION_FAULT(Doc.of(Level.FAULT)),
		AC_SHORT_CIRCUIT_FAULT(Doc.of(Level.FAULT)),
		HIGH_AC_CURRENT_FAULT(Doc.of(Level.FAULT)),
		PARALLEL_OVERLOAD_TIMEOUT_FAULT(Doc.of(Level.FAULT)),
		OUTPUT_OVERLOAD_TIMEOUT_FAULT(Doc.of(Level.FAULT)),
		AC_POWER_ANOMALY_FAULT(Doc.of(Level.FAULT)),
		VOLTAGE_L1_L2_FAULT(Doc.of(Level.FAULT)),
		VOLTAGE_L2_L3_FAULT(Doc.of(Level.FAULT)),
		VOLTAGE_L3_L1_FAULT(Doc.of(Level.FAULT)),
		DC_SOFT_START_FAULT(Doc.of(Level.FAULT)),
		DC_RELAY_CLOSE_FAULT(Doc.of(Level.FAULT)),
		INDUCTOR_CURRENT_BALANCE_L1_FAULT(Doc.of(Level.FAULT)),
		INDUCTOR_CURRENT_BALANCE_L2_FAULT(Doc.of(Level.FAULT)),
		INDUCTOR_CURRENT_BALANCE_L3_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 2
		BAMS_CURRENT_LIMIT_SHUTDOWN_FAULT(Doc.of(Level.FAULT)),
		BAMS_POWER_LIMIT_SHUTDOWN_FAULT(Doc.of(Level.FAULT)),
		BCMS_NO_CHARGE_SHUTDOWN_FAULT(Doc.of(Level.FAULT)),
		BCMS_DISABLE_SHUTDOWN_FAULT(Doc.of(Level.FAULT)),
		BAMS_CHARGE_DISABLED_SHUTDOWN_FAULT(Doc.of(Level.FAULT)),
		BAMS_SHUTDOWN_FAULT(Doc.of(Level.FAULT)),
		BCMS_CURRENT_LIMIT_SHUTDOWN_FAULT(Doc.of(Level.FAULT)),
		BCMS_POWER_LIMIT_SHUTDOWN_FAULT(Doc.of(Level.FAULT)),
		BATTERY_VOLTAGE_LIMIT_SHUTDOWN_FAULT(Doc.of(Level.FAULT)),
		BATTERY_CURRENT_LIMIT_SHUTDOWN_FAULT(Doc.of(Level.FAULT)),
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
		LOW_EFFICIENCY_FAULT(Doc.of(Level.FAULT)),
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
		CABIN_TEMPERATURE_SENSOR_WARNING(Doc.of(Level.WARNING)),
		HIGH_CABIN_TEMPERATURE_FAULT(Doc.of(Level.FAULT)),
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
		MODULE_ID_CONFLICT_FAULT(Doc.of(Level.FAULT)),
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
		CT_DETECTION_WARNING(Doc.of(Level.WARNING)),
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

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_POWER_L1}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getActivePowerL1Channel() {
		return this.channel(ChannelId.ACTIVE_POWER_L1);
	}

	/**
	 * Gets the Active Power on L1 in [W]. Negative values for Consumption (power
	 * that is 'leaving the system', e.g. feed-to-grid); positive for Production
	 * (power that is 'entering the system'). See {@link ChannelId#ACTIVE_POWER_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getActivePowerL1() {
		return this.getActivePowerL1Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL1(Integer value) {
		this.getActivePowerL1Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL1(int value) {
		this.getActivePowerL1Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_POWER_L2}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getActivePowerL2Channel() {
		return this.channel(ChannelId.ACTIVE_POWER_L2);
	}

	/**
	 * Gets the Active Power on L2 in [W]. Negative values for Consumption (power
	 * that is 'leaving the system', e.g. feed-to-grid); positive for Production
	 * (power that is 'entering the system'). See {@link ChannelId#ACTIVE_POWER_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getActivePowerL2() {
		return this.getActivePowerL2Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL2(Integer value) {
		this.getActivePowerL2Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL2(int value) {
		this.getActivePowerL2Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_POWER_L3}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getActivePowerL3Channel() {
		return this.channel(ChannelId.ACTIVE_POWER_L3);
	}

	/**
	 * Gets the Active Power on L3 in [W]. Negative values for Consumption (power
	 * that is 'leaving the system', e.g. feed-to-grid); positive for Production
	 * (power that is 'entering the system'). See {@link ChannelId#ACTIVE_POWER_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getActivePowerL3() {
		return this.getActivePowerL3Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL3(Integer value) {
		this.getActivePowerL3Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL3(int value) {
		this.getActivePowerL3Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#REACTIVE_POWER_L1}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getReactivePowerL1Channel() {
		return this.channel(ChannelId.REACTIVE_POWER_L1);
	}

	/**
	 * Gets the Reactive Power on L1 in [var]. See
	 * {@link ChannelId#REACTIVE_POWER_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getReactivePowerL1() {
		return this.getReactivePowerL1Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#REACTIVE_POWER_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReactivePowerL1(Integer value) {
		this.getReactivePowerL1Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#REACTIVE_POWER_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReactivePowerL1(int value) {
		this.getReactivePowerL1Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#REACTIVE_POWER_L2}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getReactivePowerL2Channel() {
		return this.channel(ChannelId.REACTIVE_POWER_L2);
	}

	/**
	 * Gets the Reactive Power on L2 in [var]. See
	 * {@link ChannelId#REACTIVE_POWER_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getReactivePowerL2() {
		return this.getReactivePowerL2Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#REACTIVE_POWER_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReactivePowerL2(Integer value) {
		this.getReactivePowerL2Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#REACTIVE_POWER_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReactivePowerL2(int value) {
		this.getReactivePowerL2Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#REACTIVE_POWER_L3}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getReactivePowerL3Channel() {
		return this.channel(ChannelId.REACTIVE_POWER_L3);
	}

	/**
	 * Gets the Reactive Power on L3 in [var]. See
	 * {@link ChannelId#REACTIVE_POWER_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getReactivePowerL3() {
		return this.getReactivePowerL3Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#REACTIVE_POWER_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReactivePowerL3(Integer value) {
		this.getReactivePowerL3Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#REACTIVE_POWER_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReactivePowerL3(int value) {
		this.getReactivePowerL3Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#POWER_FACTOR}.
	 *
	 * @return the Channel for the voltage ratio
	 */
	public default FloatReadChannel getPowerFactorChannel() {
		return this.channel(ChannelId.POWER_FACTOR);
	}

	/**
	 * Gets the Power Factor.
	 *
	 * @return the Channel {@link Value} containing the power factor
	 */
	public default Value<Float> getPowerFactor() {
		return this.getPowerFactorChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactor(Float value) {
		this.getPowerFactorChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactor(float value) {
		this.getPowerFactorChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L1_L2}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL1L2Channel() {
		return this.channel(ChannelId.VOLTAGE_L1_L2);
	}

	/**
	 * Gets the Voltage L1-L2 in [mV]. See
	 * {@link ChannelId#VOLTAGE_L1_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL1L2() {
		return this.getVoltageL1L2Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L2_L3}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL2L3Channel() {
		return this.channel(ChannelId.VOLTAGE_L2_L3);
	}

	/**
	 * Gets the Voltage L2-L3 in [mV]. See
	 * {@link ChannelId#VOLTAGE_L2_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL2L3() {
		return this.getVoltageL2L3Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L3_L1}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL3L1Channel() {
		return this.channel(ChannelId.VOLTAGE_L3_L1);
	}

	/**
	 * Gets the Voltage L3-L1 in [mV]. See
	 * {@link ChannelId#VOLTAGE_L3_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL3L1() {
		return this.getVoltageL3L1Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L1}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL1Channel() {
		return this.channel(ChannelId.VOLTAGE_L1);
	}

	/**
	 * Gets the Voltage on L1 in [mV]. See {@link ChannelId#VOLTAGE_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL1() {
		return this.getVoltageL1Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL1(Integer value) {
		this.getVoltageL1Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL1(int value) {
		this.getVoltageL1Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L2}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL2Channel() {
		return this.channel(ChannelId.VOLTAGE_L2);
	}

	/**
	 * Gets the Voltage on L2 in [mV]. See {@link ChannelId#VOLTAGE_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL2() {
		return this.getVoltageL2Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL2(Integer value) {
		this.getVoltageL2Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL2(int value) {
		this.getVoltageL2Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L3}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL3Channel() {
		return this.channel(ChannelId.VOLTAGE_L3);
	}

	/**
	 * Gets the Voltage on L3 in [mV]. See {@link ChannelId#VOLTAGE_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL3() {
		return this.getVoltageL3Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL3(Integer value) {
		this.getVoltageL3Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL3(int value) {
		this.getVoltageL3Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#CURRENT_L1}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getCurrentL1Channel() {
		return this.channel(ChannelId.CURRENT_L1);
	}

	/**
	 * Gets the Current L1 in [mA]. See
	 * {@link ChannelId#CURRENT_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getCurrentL1() {
		return this.getCurrentL1Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#CURRENT_L2}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getCurrentL2Channel() {
		return this.channel(ChannelId.CURRENT_L2);
	}

	/**
	 * Gets the Current L2 in [mA]. See
	 * {@link ChannelId#CURRENT_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getCurrentL2() {
		return this.getCurrentL2Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#CURRENT_L3}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getCurrentL3Channel() {
		return this.channel(ChannelId.CURRENT_L3);
	}

	/**
	 * Gets the Current L3 in [mA]. See
	 * {@link ChannelId#CURRENT_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getCurrentL3() {
		return this.getCurrentL3Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#FREQUENCY}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getFrequencyChannel() {
		return this.channel(ChannelId.FREQUENCY);
	}

	/**
	 * Gets the Frequency in [mHz]. See
	 * {@link ChannelId#FREQUENCY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getFrequency() {
		return this.getFrequencyChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#DC_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getDcVoltageChannel() {
		return this.channel(ChannelId.DC_VOLTAGE);
	}

	/**
	 * Gets the DC Voltage in [V]. See
	 * {@link ChannelId#DC_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getDcVoltage() {
		return this.getDcVoltageChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#DC_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getDcCurrentChannel() {
		return this.channel(ChannelId.DC_CURRENT);
	}

	/**
	 * Gets the DC Current in [A]. See
	 * {@link ChannelId#DC_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getDcCurrent() {
		return this.getDcCurrentChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#DC_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getDcPowerChannel() {
		return this.channel(ChannelId.DC_POWER);
	}

	/**
	 * Gets the DC Power in [W]. See
	 * {@link ChannelId#DC_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getDcPower() {
		return this.getDcPowerChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#AIR_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getAirTemperatureChannel() {
		return this.channel(ChannelId.AIR_TEMPERATURE);
	}

	/**
	 * Gets the Temperature of the Air in [°C]. See
	 * {@link ChannelId#AIR_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getAirTemperature() {
		return this.getAirTemperatureChannel().value();
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
	 * Gets the Temperature of the IGBT in [°C]. See
	 * {@link ChannelId#IGBT_L1_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getIgbtL1Temperature() {
		return this.getIgbtL1TemperatureChannel().value();
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
	 * Gets the Temperature of the IGBT in [°C]. See
	 * {@link ChannelId#IGBT_L2_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getIgbtL2Temperature() {
		return this.getIgbtL2TemperatureChannel().value();
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
	 * Gets the Temperature of the IGBT in [°C]. See
	 * {@link ChannelId#IGBT_L3_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getIgbtL3Temperature() {
		return this.getIgbtL3TemperatureChannel().value();
	}

	public static void calculatePhaseVoltages(HyperInverter inverter) {
		inverter.getVoltageL1L2Channel().onSetNextValue(value -> {
			inverter._setVoltageL1(calculatePhaseVoltage(value.get()));
		});
		inverter.getVoltageL2L3Channel().onSetNextValue(value -> {
			inverter._setVoltageL2(calculatePhaseVoltage(value.get()));
		});
		inverter.getVoltageL3L1Channel().onSetNextValue(value -> {
			inverter._setVoltageL3(calculatePhaseVoltage(value.get()));
		});
	}

	private static Integer calculatePhaseVoltage(Integer phaseToPhaseVoltage) {
		if (phaseToPhaseVoltage == null) {
			return null;
		}
		return (int) Math.round(phaseToPhaseVoltage / Math.sqrt(3));
	}

	public static void calculatePhasePowersFromVoltageAndCurrent(HyperInverter meter) {
		var powerFactor = meter.getPowerFactorChannel();

		calculateL1PowersFromVoltageAndCurrent(meter, powerFactor);
		calculateL2PowersFromVoltageAndCurrent(meter, powerFactor);
		calculateL3PowersFromVoltageAndCurrent(meter, powerFactor);
	}

	private static void calculateL1PowersFromVoltageAndCurrent(HyperInverter meter, FloatReadChannel powerFactorL1) {
		_calculatePhasePowersFromVoltageAndCurrent(meter, powerFactorL1,
				HyperInverter::getActivePowerL1Channel,
	            HyperInverter::getReactivePowerL1Channel,
	            HyperInverter::getVoltageL1Channel,
	            HyperInverter::getCurrentL1Channel
	    );
	}

	private static void calculateL2PowersFromVoltageAndCurrent(HyperInverter meter, FloatReadChannel powerFactorL2) {
		_calculatePhasePowersFromVoltageAndCurrent(meter, powerFactorL2,
				HyperInverter::getActivePowerL2Channel,
				HyperInverter::getReactivePowerL2Channel,
				HyperInverter::getVoltageL2Channel,
				HyperInverter::getCurrentL2Channel
	    );
	}

	private static void calculateL3PowersFromVoltageAndCurrent(HyperInverter meter, FloatReadChannel powerFactorL3) {
		_calculatePhasePowersFromVoltageAndCurrent(meter, powerFactorL3,
				HyperInverter::getActivePowerL3Channel,
				HyperInverter::getReactivePowerL3Channel,
				HyperInverter::getVoltageL3Channel,
	            HyperInverter::getCurrentL3Channel
	    );
	}

	public static <M> void _calculatePhasePowersFromVoltageAndCurrent(
	        M meter,
	        FloatReadChannel powerFactorChannel,
	        Function<M, IntegerReadChannel> activePower,
	        Function<M, IntegerReadChannel> reactivePower,
	        Function<M, IntegerReadChannel> voltage,
	        Function<M, IntegerReadChannel> current
	) {
		IntegerReadChannel activePowerChannel = activePower.apply(meter);
		IntegerReadChannel reactivePowerChannel = reactivePower.apply(meter);
		IntegerReadChannel voltageChannel = voltage.apply(meter);
		IntegerReadChannel currentChannel = current.apply(meter);

	    powerFactorChannel.onSetNextValue(value -> {
	        calculatePhasePowersFromVoltageAndCurrent(activePowerChannel, reactivePowerChannel,
	        		voltageChannel.value().get(), currentChannel.value().get(), value.get());
	    });
	    voltageChannel.onSetNextValue(value -> {
	        calculatePhasePowersFromVoltageAndCurrent(activePowerChannel, reactivePowerChannel,
	        		value.get(), currentChannel.value().get(), powerFactorChannel.value().get());
	    });
	    currentChannel.onSetNextValue(value -> {
	        calculatePhasePowersFromVoltageAndCurrent(activePowerChannel, reactivePowerChannel,
	        		voltageChannel.value().get(), value.get(), powerFactorChannel.value().get());
	    });
	}

    private static void calculatePhasePowersFromVoltageAndCurrent(IntegerReadChannel activePower, IntegerReadChannel reactivePower,
    		Integer voltage, Integer current, Float PowerFactor) {
    	if (voltage == null || current == null || PowerFactor == null) {
    		return;
    	}
    	var apparentPower = calculateApparentPhasePowerFromVoltageAndCurrent(voltage, current);

    	activePower.setNextValue(calculateActivePhasePowerFromApparentPower(apparentPower, PowerFactor));
    	reactivePower.setNextValue(calculateReactivePhasePowerFromApparentPower(apparentPower, PowerFactor));
    }

	private static int calculateApparentPhasePowerFromVoltageAndCurrent(int voltage, int current) {
		return (int) (((double) voltage / 1000.0) * ((double) current / 1000.0));
	}

	private static int calculateActivePhasePowerFromApparentPower(int apparentPower, float powerFactor) {
		return (int) (apparentPower * powerFactor);
	}

	private static int calculateReactivePhasePowerFromApparentPower(int apparentPower, float powerFactor) {
		double phi = Math.acos(powerFactor);
		return (int) (apparentPower * Math.sin(phi));
	}

}
