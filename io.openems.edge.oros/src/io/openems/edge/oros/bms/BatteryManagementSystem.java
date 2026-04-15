package io.openems.edge.oros.bms;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveNatureTable;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;

import java.util.function.Consumer;

public interface BatteryManagementSystem extends
		Battery, OpenemsComponent, ModbusSlave {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/**
		 * Open Circuit Voltage.
		 *
		 * <ul>
		 * <li>Interface: OrosBattery
		 * <li>Type: Integer
		 * <li>Unit: V
		 * </ul>
		 */
		OPEN_CIRCUIT_VOLTAGE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.VOLT)
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Maximum power for charging.
		 *
		 * <ul>
		 * <li>Interface: OrosBattery
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		CHARGE_MAX_POWER(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Maximum power for discharging.
		 *
		 * <ul>
		 * <li>Interface: OrosBattery
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		DISCHARGE_MAX_POWER(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),

		MAX_CELL_TEMPERATURE_INDEX(Doc.of(OpenemsType.INTEGER)
				.persistencePriority(PersistencePriority.HIGH)),
		MAX_CELL_VOLTAGE_INDEX(Doc.of(OpenemsType.INTEGER)
				.persistencePriority(PersistencePriority.HIGH)),

		MIN_CELL_TEMPERATURE_INDEX(Doc.of(OpenemsType.INTEGER)
				.persistencePriority(PersistencePriority.HIGH)),
		MIN_CELL_VOLTAGE_INDEX(Doc.of(OpenemsType.INTEGER)
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
	 * Gets the Channel for {@link ChannelId#OPEN_CIRCUIT_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getOpenCircuitVoltageChannel() {
		return this.channel(ChannelId.OPEN_CIRCUIT_VOLTAGE);
	}

	/**
	 * Gets the Open Circuit Voltage in [V]. See
	 * {@link ChannelId#OPEN_CIRCUIT_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getOpenCircuitVoltage() {
		return this.getOpenCircuitVoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#OPEN_CIRCUIT_VOLTAGE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setOpenCircuitVoltage(Integer value) {
		this.getOpenCircuitVoltageChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#OPEN_CIRCUIT_VOLTAGE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setOpenCircuitVoltage(int value) {
		this.getOpenCircuitVoltageChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#CHARGE_MAX_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getChargeMaxPowerChannel() {
		return this.channel(ChannelId.CHARGE_MAX_POWER);
	}

	/**
	 * Gets the Charge Max Power in [W]. See
	 * {@link ChannelId#CHARGE_MAX_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getChargeMaxPower() {
		return this.getChargeMaxPowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#CHARGE_MAX_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setChargeMaxPower(Integer value) {
		this.getChargeMaxPowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#CHARGE_MAX_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setChargeMaxPower(int value) {
		this.getChargeMaxPowerChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#DISCHARGE_MAX_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getDischargeMaxPowerChannel() {
		return this.channel(ChannelId.DISCHARGE_MAX_POWER);
	}

	/**
	 * Gets the Discharge Max Power in [W]. See
	 * {@link ChannelId#DISCHARGE_MAX_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getDischargeMaxPower() {
		return this.getDischargeMaxPowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#DISCHARGE_MAX_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDischargeMaxPower(Integer value) {
		this.getDischargeMaxPowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#DISCHARGE_MAX_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDischargeMaxPower(int value) {
		this.getDischargeMaxPowerChannel().setNextValue(value);
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

	@Override
	public default ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(
				OpenemsComponent.getModbusSlaveNatureTable(accessMode),
				Battery.getModbusSlaveNatureTable(accessMode));
	}

	/**
	 * Calculates {@link Battery.ChannelId#INNER_RESISTANCE} from
	 * {@link BatteryManagementSystem.ChannelId#OPEN_CIRCUIT_VOLTAGE} (Open Circuit Voltage),
	 * {@link Battery.ChannelId#VOLTAGE} (Terminal Voltage) and
	 * {@link Battery.ChannelId#CURRENT}.
	 *
	 * <p>Formula: Ri = (V_OCV - V_terminal) / I
	 *
	 * <p>Registers onSetNextValue listeners on precharge voltage, voltage and
	 * current channels.
	 *
	 * @param battery the {@link BatteryManagementSystem}
	 */
	public static void calculateInnerResistance(BatteryManagementSystem battery) {
		final Consumer<Value<Integer>> accept = ignore -> {
			var value = calculateResistance(
					battery.getOpenCircuitVoltageChannel().getNextValue().get(),
					battery.getVoltageChannel().getNextValue().get(),
					battery.getCurrentChannel().getNextValue().get());
			if (value != null) {
				battery._setInnerResistance(value);
			}
		};
		battery.getOpenCircuitVoltageChannel().onSetNextValue(accept);
		battery.getVoltageChannel().onSetNextValue(accept);
		battery.getCurrentChannel().onSetNextValue(accept);
	}

	/**
	 * Calculates inner resistance in [mOhm] from open circuit voltage, terminal
	 * voltage and current.
	 *
	 * @param prechargeVoltage the open circuit voltage in [V]
	 * @param voltage          the terminal voltage in [V]
	 * @param current          the current in [A]
	 * @return the inner resistance in [mOhm], or null if inputs are invalid
	 */
	private static Integer calculateResistance(Integer prechargeVoltage, Integer voltage, Integer current) {
		if (prechargeVoltage == null || voltage == null || current == null || current == 0) {
			return null;
		}
		// Ri = (V_OCV - V_terminal) / I, converted from Ohm to mOhm (* 1000)
		return Math.abs((prechargeVoltage - voltage) * 1000 / current);
	}

	public static void calculateMaxPowerFromCurrentAndVoltage(BatteryManagementSystem battery) {
		battery.getChargeMaxCurrentChannel().onSetNextValue(value -> {
			battery._setChargeMaxPower(
					calculatePower(value.get(), battery.getVoltageChannel().getNextValue().get()));
		});
		battery.getDischargeMaxCurrentChannel().onSetNextValue(value -> {
			battery._setDischargeMaxPower(
					calculatePower(value.get(), battery.getVoltageChannel().getNextValue().get()));
		});
		battery.getVoltageChannel().onSetNextValue(value -> {
			battery._setChargeMaxPower(
					calculatePower(battery.getChargeMaxCurrentChannel().getNextValue().get(), value.get()));
			battery._setDischargeMaxPower(
					calculatePower(battery.getDischargeMaxCurrentChannel().getNextValue().get(), value.get()));
		});
	}

	public static void calculateMaxCurrentFromPowerAndVoltage(BatteryManagementSystem battery) {
		battery.getChargeMaxPowerChannel().onSetNextValue(value -> {
			battery._setChargeMaxCurrent(
					calculateCurrent(value.get(), battery.getVoltageChannel().getNextValue().get()));
		});
		battery.getDischargeMaxPowerChannel().onSetNextValue(value -> {
			battery._setDischargeMaxCurrent(
					calculateCurrent(value.get(), battery.getVoltageChannel().getNextValue().get()));
		});
		battery.getVoltageChannel().onSetNextValue(value -> {
			battery._setChargeMaxCurrent(
					calculateCurrent(battery.getChargeMaxPowerChannel().getNextValue().get(), value.get()));
			battery._setDischargeMaxCurrent(
					calculateCurrent(battery.getDischargeMaxPowerChannel().getNextValue().get(), value.get()));
		});
	}

	private static Integer calculatePower(Integer current, Integer voltage) {
		if (current == null || voltage == null) {
			return null;
		}
		return current * voltage;
	}

	private static Integer calculateCurrent(Integer power, Integer voltage) {
		if (power == null || voltage == null || voltage == 0) {
			return null;
		}
		return power / voltage;
	}

}
