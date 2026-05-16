package io.openems.edge.oros.bms;

import java.util.function.Consumer;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.common.utils.IntUtils;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerDoc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.statemachine.AbstractStateMachine;

public interface BatteryManagementSystem extends
		Battery, OpenemsComponent, ModbusSlave, StartStoppable {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/**
		 * Rack State of Charge in Thousandths [‰].
		 *
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * <li>Unit: %
		 * <li>Range: 0..1000
		 * </ul>
		 */
		RACK_SOC(new IntegerDoc()
				.unit(Unit.THOUSANDTH)
				.persistencePriority(PersistencePriority.HIGH)
				.onChannelSetNextValue((self, value) -> {
					value.ifPresent(newValue -> {
						Channel<Integer> socChannel = self.channel(Battery.ChannelId.SOC);
						socChannel.setNextValue(IntUtils.roundToPrecision(newValue / 10.0,
								IntUtils.Round.HALF_UP, 1));
					});
				})),
		/**
		 * Rack State of Health in Thousandths [‰].
		 *
		 * <ul>
		 * <li>Interface: BatteryManagementSystem
		 * <li>Type: Integer
		 * <li>Unit: ‰
		 * <li>Range: 0..1000
		 * <li>Implementation Note: mirrored to {@link Battery.ChannelId#SOH} as percent.
		 * </ul>
		 */
		RACK_SOH(new IntegerDoc()
				.unit(Unit.THOUSANDTH)
				.persistencePriority(PersistencePriority.HIGH)
				.onChannelSetNextValue((self, value) -> {
					value.ifPresent(newValue -> {
						Channel<Integer> sohChannel = self.channel(Battery.ChannelId.SOH);
						sohChannel.setNextValue(IntUtils.roundToPrecision(newValue / 10.0,
								IntUtils.Round.HALF_UP, 1));
					});
				})),
		/**
		 * Rack State of Energy in Thousandths [‰].
		 *
		 * <ul>
		 * <li>Interface: BatteryManagementSystem
		 * <li>Type: Integer
		 * <li>Unit: ‰
		 * <li>Range: 0..1000
		 * </ul>
		 */
		RACK_SOE(Doc.of(OpenemsType.FLOAT)
				.unit(Unit.THOUSANDTH)
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Rack Voltage in Millivolts [mV].
		 *
		 * <ul>
		 * <li>Interface: BatteryManagementSystem
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * <li>Implementation Note: mirrored to {@link Battery.ChannelId#VOLTAGE} as Volts.
		 * </ul>
		 */
		RACK_VOLTAGE(new IntegerDoc()
				.unit(Unit.MILLIVOLT)
				.persistencePriority(PersistencePriority.HIGH)
				.onChannelSetNextValue((self, value) -> {
					value.ifPresent(newValue -> {
						Channel<Integer> voltageChannel = self.channel(Battery.ChannelId.VOLTAGE);
						voltageChannel.setNextValue(IntUtils.roundToPrecision(newValue / 1000.0,
								IntUtils.Round.HALF_UP, 1));
					});
				})),

		/**
		 * Rack Current in Milliamperes [mA].
		 *
		 * <ul>
		 * <li>Interface: BatteryManagementSystem
		 * <li>Type: Integer
		 * <li>Unit: mA
		 * <li>Implementation Note: mirrored to {@link Battery.ChannelId#CURRENT} as Amperes.
		 * </ul>
		 */
		RACK_CURRENT(new IntegerDoc()
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)
				.onChannelSetNextValue((self, value) -> {
					value.ifPresent(newValue -> {
						Channel<Integer> currentChannel = self.channel(Battery.ChannelId.CURRENT);
						currentChannel.setNextValue(IntUtils.roundToPrecision(newValue / 1000.0,
								IntUtils.Round.HALF_UP, 1));
					});
				})),

		/**
		 * Rack Power in Watts [W].
		 *
		 * <ul>
		 * <li>Interface: BatteryManagementSystem
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		RACK_POWER(new IntegerDoc()
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Open Circuit Voltage.
		 *
		 * <ul>
		 * <li>Interface: BatteryManagementSystem
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
		 * <li>Interface: BatteryManagementSystem
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
		 * <li>Interface: BatteryManagementSystem
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
	 * Gets the Channel for {@link ChannelId#RACK_SOC}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getRackSocChannel() {
		return this.channel(ChannelId.RACK_SOC);
	}

	/**
	 * Gets the Rack State of Charge in [‰]. See {@link ChannelId#RACK_SOC}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getRackSoc() {
		return this.getRackSocChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#RACK_SOC} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setRackSoc(Integer value) {
		this.getRackSocChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#RACK_SOC} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setRackSoc(int value) {
		this.getRackSocChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#RACK_SOH}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getRackSohChannel() {
		return this.channel(ChannelId.RACK_SOH);
	}

	/**
	 * Gets the Rack State of Health in [‰]. See {@link ChannelId#RACK_SOH}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getRackSoh() {
		return this.getRackSohChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#RACK_SOH} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setRackSoh(Integer value) {
		this.getRackSohChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#RACK_SOH} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setRackSoh(int value) {
		this.getRackSohChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#RACK_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getRackVoltageChannel() {
		return this.channel(ChannelId.RACK_VOLTAGE);
	}

	/**
	 * Gets the Rack Voltage in [mV]. See {@link ChannelId#RACK_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getRackVoltage() {
		return this.getRackVoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#RACK_VOLTAGE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setRackVoltage(Integer value) {
		this.getRackVoltageChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#RACK_VOLTAGE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setRackVoltage(int value) {
		this.getRackVoltageChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#RACK_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getRackCurrentChannel() {
		return this.channel(ChannelId.RACK_CURRENT);
	}

	/**
	 * Gets the Rack Current in [mA]. See {@link ChannelId#RACK_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getRackCurrent() {
		return this.getRackCurrentChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#RACK_CURRENT} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setRackCurrent(Integer value) {
		this.getRackCurrentChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#RACK_CURRENT} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setRackCurrent(int value) {
		this.getRackCurrentChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#RACK_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getRackPowerChannel() {
		return this.channel(ChannelId.RACK_POWER);
	}

	/**
	 * Gets the Power in [W]. See
	 * {@link ChannelId#RACK_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getRackPower() {
		return this.getRackPowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#RACK_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setRackPower(Integer value) {
		this.getRackPowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#RACK_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setRackPower(int value) {
		this.getRackPowerChannel().setNextValue(value);
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
	 * Generates a default DebugLog message for {@link BatteryManagementSystem} implementations with
	 * a State-Machine.
	 *
	 * @param battery      the {@link Battery}
	 * @param stateMachine the actual StateMachine (extends
	 *                     {@link AbstractStateMachine})
	 * @return a debug log String
	 */
	public static String generateDebugLog(Battery battery, AbstractStateMachine<?, ?> stateMachine) {
		var builder = new StringBuilder()
				.append(stateMachine.debugLog()).append("|");
		return _generateDebugLog(battery, builder).toString();
	}

	/**
	 * Generates a default DebugLog message for {@link BatteryManagementSystem} implementations
	 *
	 * @param battery      the {@link Battery}
	 * @return a debug log String
	 */
	public static String generateDebugLog(Battery battery) {
		return _generateDebugLog(battery, new StringBuilder()).toString();
	}

	private static StringBuilder _generateDebugLog(Battery battery, StringBuilder builder) {
		return builder
				.append("SoC:").append(battery.getSoc())
				.append("|IV:").append(battery.getCurrent())
				.append(";").append(battery.getVoltage())
				.append("|Charge:").append(battery.getChargeMaxCurrent())
				.append(";").append(battery.getChargeMaxVoltage())
				.append("|Discharge:").append(battery.getDischargeMaxCurrent())
				.append(";").append(battery.getDischargeMinVoltage());
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

	public static void calculateRackPowerFromVoltageAndCurrent(BatteryManagementSystem battery) {
		final Consumer<Value<Integer>> calculate = ignore -> {
			var voltage = battery.getRackVoltage();
			var current = battery.getRackCurrent();
			if (!current.isDefined() || !voltage.isDefined()) {
				return;
			}
			battery._setRackPower(Math.round((voltage.get() / 1000F) * (current.get() / 1000F)));
		};
		battery.getVoltageChannel().onSetNextValue(calculate);
		battery.getCurrentChannel().onSetNextValue(calculate);
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
