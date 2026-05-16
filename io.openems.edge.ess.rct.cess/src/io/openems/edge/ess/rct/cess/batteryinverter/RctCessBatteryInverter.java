package io.openems.edge.ess.rct.cess.batteryinverter;

import org.osgi.service.event.EventHandler;

import io.openems.common.channel.Level;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.batteryinverter.api.BatteryInverterErrorAcknowledge;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.ess.rct.cess.batteryinverter.enums.RunState;
import io.openems.edge.ess.rct.cess.batteryinverter.statemachine.StateMachine.State;
import io.openems.edge.oros.pcs.PowerConversionSystem;
import io.openems.edge.timedata.api.TimedataProvider;

public interface RctCessBatteryInverter extends
		PowerConversionSystem, BatteryInverterErrorAcknowledge, ModbusComponent,
		TimedataProvider, EventHandler {

	/** Efficiency factor (%) used for AC/DC conversion. */
	public static final float EFFICIENCY_FACTOR = 97F;

	public static final int MAX_APPARENT_POWER = 100_000; // [W]

	public static final int APPARENT_POWER_PRECISION = 100; // [W]

	public static final int DC_MIN_VOLTAGE = 200;
	public static final int DC_MAX_VOLTAGE = 950;

	/**
	 * After how many seconds will commands be retried to send, 
	 * e.g. for starting the battery-inverter.
	 */
	public static int TIMEOUT = 300;

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		STATE_MACHINE(Doc.of(State.values())
				.text("Current State of State-Machine")),
		RUN_FAILED(Doc.of(Level.WARNING)
				.text("Running the Logic failed")),

		RUN_STATE(Doc.of(RunState.values())
				.persistencePriority(PersistencePriority.HIGH)),

		IGBT_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEGREE_CELSIUS)
				.persistencePriority(PersistencePriority.HIGH)),

		// Alarm 1
		EP0_FAULT(Doc.of(Level.WARNING)),
		IGBT_CURRENT_HIGH_FAULT(Doc.of(Level.WARNING)),
		BUSBAR_VOLTAGE_HIGH_FAULT(Doc.of(Level.WARNING)),
		POWER_MODULE_CURRENT_LIMIT_FAULT(Doc.of(Level.WARNING)),
		BALANCE_MODULE_CURRENT_HIGH_FAULT(Doc.of(Level.WARNING)),

		// Alarm 2
		VOLTAGE_24_FAULT(Doc.of(Level.WARNING)),
		FAN_FAULT(Doc.of(Level.WARNING)),
		CONNECTION_FAULT(Doc.of(Level.WARNING)),
		SPD_FAULT(Doc.of(Level.WARNING)),
		POWER_MODULE_TEMPERATURE_HIGH_FAULT(Doc.of(Level.WARNING)),
		BALANCE_MODULE_TEMPERATURE_HIGH_FAULT(Doc.of(Level.WARNING)),
		VOLTAGE_15_FAULT(Doc.of(Level.WARNING)),
		FIRE_SYSTEM_ALARM(Doc.of(Level.WARNING)),
		BATTERY_DRY_FAULT(Doc.of(Level.WARNING)),
		OVERLOAD_FAULT(Doc.of(Level.WARNING)),

		// Alarm 3
		VOLTAGE_HIGH_L1(Doc.of(Level.INFO)),
		VOLTAGE_HIGH_L2(Doc.of(Level.INFO)),
		VOLTAGE_HIGH_L3(Doc.of(Level.INFO)),
		VOLTAGE_LOW_L1(Doc.of(Level.INFO)),
		VOLTAGE_LOW_L2(Doc.of(Level.INFO)),
		VOLTAGE_LOW_L3(Doc.of(Level.INFO)),
		GRID_FREQUENCY_HIGH(Doc.of(Level.INFO)),
		GRID_FREQUENCY_LOW(Doc.of(Level.INFO)),
		GRID_PHASE_SEQUENCE_FAULT(Doc.of(Level.INFO)),
		SOFT_WORK_CURRENT_HIGH_L1(Doc.of(Level.INFO)),
		SOFT_WORK_CURRENT_HIGH_L2(Doc.of(Level.INFO)),
		SOFT_WORK_CURRENT_HIGH_L3(Doc.of(Level.INFO)),
		GRID_VOLTAGE_UNBALANCE(Doc.of(Level.INFO)),
		GRID_CURRENT_UNBALANCE(Doc.of(Level.INFO)),
		GRID_LOSS_PHASE(Doc.of(Level.INFO)),
		N_CURRENT_HIGH(Doc.of(Level.INFO)),

		// Alarm 4
		PRE_CHARGE_BUS_VOLTAGE_HIGH(Doc.of(Level.INFO)),
		PRE_CHARGE_BUS_VOLTAGE_LOW(Doc.of(Level.INFO)),
		UNCONTROLLED_RECTIFIER_BUS_VOLTAGE_HIGH(Doc.of(Level.INFO)),
		UNCONTROLLED_RECTIFIER_BUS_VOLTAGE_LOW(Doc.of(Level.INFO)),
		RUN_BUS_VOLTAGE_HIGH(Doc.of(Level.INFO)),
		RUN_BUS_VOLTAGE_LOW(Doc.of(Level.INFO)),
		POSITIVE_NEGATIVE_BUS_UNBALANCE(Doc.of(Level.INFO)),
		CURRENT_MODE_BUS_VOLTAGE_LOW(Doc.of(Level.INFO)),
		CELL_VOLTAGE_LOW(Doc.of(Level.INFO)),
		CELL_VOLTAGE_HIGH(Doc.of(Level.INFO)),
		AC_PRE_CHARGE_CURRENT_HIGH(Doc.of(Level.INFO)),
		AC_CURRENT_HIGH(Doc.of(Level.INFO)),
		BALANCE_MODULE_SOFTWARE_CURRENT_HIGH(Doc.of(Level.INFO)),
		BATTERY_REVERSE(Doc.of(Level.INFO)),

		// Alarm 5
		PRE_CHARGE_TIMEOUT(Doc.of(Level.INFO)),
		PRE_CHARGE_CURRENT_HIGH_L1(Doc.of(Level.INFO)),
		PRE_CHARGE_CURRENT_HIGH_L2(Doc.of(Level.INFO)),
		PRE_CHARGE_CURRENT_HIGH_L3(Doc.of(Level.INFO)),

		// Alarm 6
		AD_NULL_SHIFT_FAULT(Doc.of(Level.WARNING)),
		BMS_CELL_FAULT(Doc.of(Level.WARNING)),
		STS_COMMUNICATION_FAULT(Doc.of(Level.WARNING)),
		BMS_CONNECTION_FAIL(Doc.of(Level.WARNING)),
		CAN_CONNECTION_FAULT(Doc.of(Level.WARNING)),
		EMS_CONNECTION_FAULT(Doc.of(Level.WARNING)),

		// Alarm 7
		PRE_CHARGE_RELAY_OPEN_FAULT(Doc.of(Level.WARNING)),
		PRE_CHARGE_RELAY_CLOSE_FAULT(Doc.of(Level.WARNING)),
		PRE_CHARGE_RELAY_OPEN_STATUS_FAULT(Doc.of(Level.WARNING)),
		PRE_CHARGE_RELAY_CLOSE_STATUS_FAULT(Doc.of(Level.WARNING)),
		MAIN_RELAY_OPEN_FAULT(Doc.of(Level.WARNING)),
		MAIN_RELAY_CLOSE_FAULT(Doc.of(Level.WARNING)),
		MAIN_RELAY_OPEN_STATUS_FAULT(Doc.of(Level.WARNING)),
		MAIN_RELAY_CLOSE_STATUS_FAULT(Doc.of(Level.WARNING)),
		AC_MAIN_RELAY_ADHESIVE_FAULT(Doc.of(Level.WARNING)),
		DC_RELAY_OPEN_FAULT(Doc.of(Level.WARNING)),

		// Alarm 8
		INVERTER_VOLTAGE_HIGH_L1_FAULT(Doc.of(Level.WARNING)),
		INVERTER_VOLTAGE_HIGH_L2_FAULT(Doc.of(Level.WARNING)),
		INVERTER_VOLTAGE_HIGH_L3_FAULT(Doc.of(Level.WARNING)),
		ISLAND_ENABLE_FAULT(Doc.of(Level.WARNING)),
		SYSTEM_RESONANCE_FAULT(Doc.of(Level.WARNING)),
		SOFT_WORK_VOLTAGE_HIGH_CURRENT_HIGH_FAULT(Doc.of(Level.WARNING)),
		MODULE_DIAL_UP_ADDRESS_FAULT(Doc.of(Level.WARNING)),
		INVERTER_VOLTAGE_LOW_L1_FAULT(Doc.of(Level.WARNING)),
		INVERTER_VOLTAGE_LOW_L2_FAULT(Doc.of(Level.WARNING)),
		INVERTER_VOLTAGE_LOW_L3_FAULT(Doc.of(Level.WARNING)),
		OFFGRID_NO_SYNCHRONIZATION_SIGNAL_FAULT(Doc.of(Level.WARNING)),
		OFFGRID_SHORT_CIRCUIT_FAULT(Doc.of(Level.WARNING)),
		VOLTAGE_LOW_CROSS_OVER_TIME_FAULT(Doc.of(Level.WARNING)),
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
	 * Gets the Channel for {@link ChannelId#STATE_MACHINE}.
	 *
	 * @return the Channel
	 */
	public default Channel<State> getStateMachineChannel() {
		return this.channel(ChannelId.STATE_MACHINE);
	}

	/**
	 * Gets the StateMachine channel value for {@link ChannelId#STATE_MACHINE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<State> getStateMachine() {
		return this.getStateMachineChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#STATE_MACHINE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setStateMachine(State value) {
		this.getStateMachineChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#RUN_FAILED}.
	 *
	 * @return the Channel
	 */
	public default Channel<Boolean> getRunFailedChannel() {
		return this.channel(ChannelId.RUN_FAILED);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#RUN_FAILED}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setRunFailed(boolean value) {
		this.getRunFailedChannel().setNextValue(value);
	}

	/**
	 * Gets the target Start/Stop mode from config or StartStop-Channel.
	 *
	 * @return {@link StartStop}
	 */
	public StartStop getStartStopTarget();

	/**
	 * Gets the Channel for {@link ChannelId#RUN_STATE}.
	 *
	 * @return the Channel {@link Channel}
	 */
	public default Channel<RunState> getRunStateChannel() {
		return this.channel(ChannelId.RUN_STATE);
	}

	/**
	 * Gets the {@link RunState}, see {@link ChannelId#RUN_STATE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<RunState> getRunState() {
		return this.getRunStateChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#IGBT_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getIgbtTemperatureChannel() {
		return this.channel(ChannelId.IGBT_TEMPERATURE);
	}

	/**
	 * Gets the Temperature of the IGBT in [°C]. See
	 * {@link ChannelId#IGBT_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getIgbtTemperature() {
		return this.getIgbtTemperatureChannel().value();
	}

}