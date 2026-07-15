package io.openems.edge.oros.pcs.api;

import static io.openems.common.channel.PersistencePriority.HIGH;
import static io.openems.common.channel.Unit.WATT;
import static io.openems.common.types.OpenemsType.INTEGER;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.batteryinverter.api.HybridManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.ManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.statemachine.AbstractStateMachine;
import io.openems.edge.oros.common.SymmetricComponent;

public interface PowerConversionSystem extends ManagedSymmetricBatteryInverter, SymmetricBatteryInverter, ModbusSlave,
		SymmetricComponent, OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Holds the currently maximum possible active power. This value is commonly
		 * defined by the inverter limitations.
		 *
		 * <ul>
		 * <li>Interface: PowerConversionSystem
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		MAX_ACTIVE_POWER(Doc.of(INTEGER).unit(Unit.WATT).persistencePriority(HIGH)),

		/**
		 * Holds the currently maximum possible reactive power. This value is commonly
		 * defined by the inverter limitations.
		 *
		 * <ul>
		 * <li>Interface: PowerConversionSystem
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		MAX_REACTIVE_POWER(Doc.of(INTEGER).unit(Unit.VOLT_AMPERE_REACTIVE).persistencePriority(HIGH)),

		/**
		 * DC Power.
		 *
		 * <ul>
		 * <li>Interface: PowerConversionSystem
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#WATT}
		 * <li>Range: negative values for Charge; positive for Discharge
		 * <li>This is the total DC-link power of the inverter, i.e. battery power plus
		 * DC-coupled PV production. The battery-only share is provided by
		 * {@link HybridManagedSymmetricBatteryInverter.ChannelId#DC_DISCHARGE_POWER}.
		 * </ul>
		 */
		DC_POWER(Doc.of(INTEGER).unit(WATT).persistencePriority(HIGH)),

		DC_VOLTAGE(Doc.of(OpenemsType.INTEGER).unit(Unit.MILLIVOLT).persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT(Doc.of(OpenemsType.INTEGER).unit(Unit.MILLIAMPERE).persistencePriority(PersistencePriority.HIGH)),

		AIR_TEMPERATURE(Doc.of(OpenemsType.INTEGER).unit(Unit.DEZIDEGREE_CELSIUS)
				.persistencePriority(PersistencePriority.HIGH)),;

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
	 * Gets the efficiency factor for AC/DC conversion in [%]. Applied to discharge
	 * power to account for inverter losses.
	 *
	 * @return the efficiency factor (e.g. 98 for 98%)
	 */
	public float getEfficiencyFactor();

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
	 * Internal method to set the 'nextValue' on {@link ChannelId#MAX_ACTIVE_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxActivePower(Integer value) {
		this.getMaxActivePowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MAX_ACTIVE_POWER}
	 * Channel.
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
	 * Gets the Channel for {@link ChannelId#DC_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getDcPowerChannel() {
		return this.channel(ChannelId.DC_POWER);
	}

	/**
	 * Gets the total DC Power in [W]. See {@link ChannelId#DC_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getDcPower() {
		return this.getDcPowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#DC_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDcPower(Integer value) {
		this.getDcPowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#DC_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDcPower(int value) {
		this.getDcPowerChannel().setNextValue(value);
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
	 * Gets the DC Voltage in [V]. See {@link ChannelId#DC_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getDcVoltage() {
		return this.getDcVoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#DC_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value in [mV]
	 */
	public default void _setDcVoltage(Integer value) {
		this.getDcVoltageChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#DC_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value in [mV]
	 */
	public default void _setDcVoltage(int value) {
		this.getDcVoltageChannel().setNextValue(value);
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
	 * Gets the DC Current in [A]. See {@link ChannelId#DC_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getDcCurrent() {
		return this.getDcCurrentChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#DC_CURRENT}
	 * Channel.
	 *
	 * @param value the next value in [mA]
	 */
	public default void _setDcCurrent(Integer value) {
		this.getDcCurrentChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#DC_CURRENT}
	 * Channel.
	 *
	 * @param value the next value in [mA]
	 */
	public default void _setDcCurrent(int value) {
		this.getDcCurrentChannel().setNextValue(value);
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
	 * Internal method to set the 'nextValue' on {@link ChannelId#AIR_TEMPERATURE}
	 * Channel.
	 *
	 * @param value the next value in [dezi-°C]
	 */
	public default void _setAirTemperature(Integer value) {
		this.getAirTemperatureChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#AIR_TEMPERATURE}
	 * Channel.
	 *
	 * @param value the next value in [dezi-°C]
	 */
	public default void _setAirTemperature(int value) {
		this.getAirTemperatureChannel().setNextValue(value);
	}

	@Override
	public default ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(OpenemsComponent.getModbusSlaveNatureTable(accessMode),
				SymmetricBatteryInverter.getModbusSlaveNatureTable(accessMode),
				ManagedSymmetricBatteryInverter.getModbusSlaveNatureTable(accessMode));
	}

	/**
	 * Generates a default DebugLog message for {@link PowerConversionSystem}
	 * implementations with a State-Machine.
	 *
	 * @param inverter     the {@link SymmetricBatteryInverter}
	 * @param stateMachine the actual StateMachine (extends
	 *                     {@link AbstractStateMachine})
	 * @return a debug log String
	 */
	public static String generateDebugLog(SymmetricBatteryInverter inverter, AbstractStateMachine<?, ?> stateMachine) {
		var builder = new StringBuilder().append(stateMachine.debugLog()).append("|");
		return _generateDebugLog(inverter, builder).toString();
	}

	/**
	 * Generates a default DebugLog message for {@link PowerConversionSystem}
	 * implementations
	 *
	 * @param inverter the {@link SymmetricBatteryInverter}
	 * @return a debug log String
	 */
	public static String generateDebugLog(SymmetricBatteryInverter inverter) {
		return _generateDebugLog(inverter, new StringBuilder()).toString();
	}

	private static StringBuilder _generateDebugLog(SymmetricBatteryInverter inverter, StringBuilder builder) {
		return builder.append("Grid:").append(inverter.getGridModeChannel().value().asOptionString());
	}

}
