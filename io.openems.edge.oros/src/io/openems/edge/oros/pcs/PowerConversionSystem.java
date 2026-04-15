package io.openems.edge.oros.pcs;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.batteryinverter.api.BatteryInverterErrorAcknowledge;
import io.openems.edge.batteryinverter.api.ManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.oros.SymmetricComponent;

public interface PowerConversionSystem extends
		ManagedSymmetricBatteryInverter, SymmetricBatteryInverter, BatteryInverterErrorAcknowledge,
		ModbusSlave, SymmetricComponent, OpenemsComponent  {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		DC_VOLTAGE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),

		AIR_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)
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
	 * Gets the efficiency factor for AC/DC conversion in [%]. Applied to discharge
	 * power to account for inverter losses.
	 *
	 * @return the efficiency factor (e.g. 98 for 98%)
	 */
	public float getEfficiencyFactor();

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

	@Override
	public default ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(
				OpenemsComponent.getModbusSlaveNatureTable(accessMode),
				SymmetricBatteryInverter.getModbusSlaveNatureTable(accessMode),
				ManagedSymmetricBatteryInverter.getModbusSlaveNatureTable(accessMode)
		);
	}

}
