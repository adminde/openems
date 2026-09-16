package io.openems.edge.oros.simulator.bms;

import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.oros.bms.api.BatteryManagementSystem;

public interface BatteryManagementSimulator extends
		BatteryManagementSystem, Battery, OpenemsComponent, ModbusSlave, StartStoppable {

	/** Default charge cut-off voltage of the Rack [V]. */
	public static final float MAX_CHARGE_VOLTAGE = 936F;

	/** Default discharge cut-off voltage of the Rack [V]. */
	public static final float MIN_DISCHARGE_VOLTAGE = 728F;

	/** Default internal resistance of the Rack [mOhm]. */
	public static final int INTERNAL_RESISTANCE = 120;

	/** Default Coefficient of Performance of the Thermal Management System. */
	public static final float THERMAL_COEFFICIENT_OF_PERFORMANCE = 2.5F;

	/** Default power of pump and controls while the Rack carries a current [W]. */
	public static final int THERMAL_MANAGEMENT_POWER = 5;

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Electrical power drawn by the Thermal Management System.
		 *
		 * <p>Covers the draw of pump and controls plus the work the heat pump spends on
		 * removing the loss in the internal resistance. Both only arise while the Rack
		 * carries a current, so a Battery at rest draws nothing. The power is supplied
		 * from the AC side, so it is no part of the power at the Battery terminals.
		 *
		 * <ul>
		 * <li>Interface: BatteryManagementSimulator
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		THERMAL_MANAGEMENT_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
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
	 * Gets the Channel for {@link ChannelId#THERMAL_MANAGEMENT_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getThermalManagementPowerChannel() {
		return this.channel(ChannelId.THERMAL_MANAGEMENT_POWER);
	}

	/**
	 * Gets the electrical power of the Thermal Management System in [W]. See
	 * {@link ChannelId#THERMAL_MANAGEMENT_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getThermalManagementPower() {
		return this.getThermalManagementPowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#THERMAL_MANAGEMENT_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setThermalManagementPower(Integer value) {
		this.getThermalManagementPowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#THERMAL_MANAGEMENT_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setThermalManagementPower(int value) {
		this.getThermalManagementPowerChannel().setNextValue(value);
	}

	/**
	 * Runs the simulation of one Cycle.
	 *
	 * @param setPower the power at the Battery terminals in [W], positive for discharge
	 */
	public void run(int setPower);
}
