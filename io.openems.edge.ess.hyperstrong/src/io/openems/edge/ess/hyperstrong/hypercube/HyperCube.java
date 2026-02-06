package io.openems.edge.ess.hyperstrong.hypercube;

import java.util.function.Consumer;

import org.osgi.service.event.EventHandler;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Level;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.jsonapi.ComponentJsonApi;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.ess.api.EssErrorAcknowledge;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.hyperstrong.AllowedPowerHandler;
import io.openems.edge.ess.hyperstrong.ChargingMode;
import io.openems.edge.ess.hyperstrong.CoolingSystemMode;
import io.openems.edge.ess.hyperstrong.CycleProvider;
import io.openems.edge.ess.hyperstrong.HyperBattery;
import io.openems.edge.ess.hyperstrong.HyperInverter;
import io.openems.edge.ess.hyperstrong.OperationState;
import io.openems.edge.ess.hyperstrong.WorkState;
import io.openems.edge.ess.hyperstrong.statemachine.StateMachine.State;
import io.openems.edge.timedata.api.TimedataProvider;

public interface HyperCube extends HyperInverter, HyperBattery,
		ManagedSymmetricEss, SymmetricEss, EssErrorAcknowledge,
		OpenemsComponent, ModbusComponent, ModbusSlave, ComponentJsonApi,
		CycleProvider, TimedataProvider, EventHandler, StartStoppable {

	public static final int APPARENT_POWER_PRECISION = 1000; // [W]
	public static final float APPARENT_POWER_FACTOR = 1.1F;
	public static final float REACTIVE_POWER_FACTOR = 0.46F;

	/**
	 * Allow a maximum power increase per second.
	 *
	 * <p>
	 * 5 % of possible allowed charge/discharge power
	 */
	public static final float MAX_POWER_INCREASE_PERCENTAGE = 0.05F;

	/**
	 * How often the OEM EMS controller will check for a changed heartbeat value
	 */
	public static final float MIN_HEARTBEAT_CYCLE = 0.5F;

	/**
	 * After how many seconds will commands be retried to send, 
	 * e.g. for starting the battery-inverter.
	 */
	public static int TIMEOUT = 300;

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		STATE_MACHINE(Doc.of(State.values())
				.text("Current State of State-Machine")),
		RUN_FAILED(Doc.of(Level.FAULT)
				.text("Running the Logic failed")),

		HEARTBEAT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.WRITE_ONLY)),

		WORK_STATE(Doc.of(WorkState.values())),
		DEVICE_MODE(Doc.of(ChargingMode.values())),
		OPERATION_STATE(Doc.of(OperationState.values())),

		/**
		 * Sets the Active Power in [W].
		 *
		 * <ul>
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: negative values for Charge; positive for Discharge
		 * </ul>
		 */
		SET_ACTIVE_POWER(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.accessMode(AccessMode.WRITE_ONLY)),
		/**
		 * Sets the Reactive Power in [var].
		 *
		 * <ul>
		 * <li>Type: Integer
		 * <li>Unit: var
		 * <li>Range: negative values for Charge; positive for Discharge
		 * </ul>
		 */
		SET_REACTIVE_POWER(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.VOLT_AMPERE_REACTIVE)
				.accessMode(AccessMode.WRITE_ONLY)),

		COOLING_SYSTEM_MODE(Doc.of(CoolingSystemMode.values())),

		COOLING_SYSTEM_COMMUNICATION_ENABLED(Doc.of(OpenemsType.BOOLEAN)),
		COOLING_SYSTEM_COMMUNICATION_CONNECTED(Doc.of(OpenemsType.BOOLEAN)),
		COOLING_SYSTEM_COMMUNICATION_ABNORMAL(Doc.of(Level.INFO)),
		COOLING_SYSTEM_COMMUNICATION_FAULT(Doc.of(Level.WARNING)),

		COOLING_SYSTEM_MAIN_CONTACTOR_STATUS(Doc.of(OpenemsType.BOOLEAN)),
		COOLING_SYSTEM_COMPRESSOR_STATUS(Doc.of(OpenemsType.BOOLEAN)),
		COOLING_SYSTEM_HEATING_STATUS(Doc.of(OpenemsType.BOOLEAN)),

		COOLING_SYSTEM_RETURN_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)),
		COOLING_SYSTEM_SUPPLY_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)),
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
		INITIALIZATION_FAILURE(Doc.of(Level.WARNING)),
		SMOKE_SENSOR_ALARM(Doc.of(Level.WARNING)),
		FIRE_ALARM(Doc.of(Level.WARNING)),
		WATER_LEAKAGE_ALARM(Doc.of(Level.FAULT)),
		SEVERE_HUMIDITY_ALARM(Doc.of(Level.FAULT)),
		SUBSYSTEM_ISLANDING(Doc.of(Level.WARNING)),
		INTERLOCK(Doc.of(Level.FAULT)),
		PCS_EMERGENCY_STOP(Doc.of(Level.WARNING)),
		QZ_CONTACTOR_RELEASE_FAULT(Doc.of(Level.WARNING)),

		// Alarm Value 2
		UPS_FAULT(Doc.of(Level.WARNING)),
		BMS_FAULT(Doc.of(Level.WARNING)),
		PCS_FAULT(Doc.of(Level.WARNING)),
		METER_FAULT(Doc.of(Level.FAULT)),
		COOLING_SYSTEM_WARNING(Doc.of(Level.WARNING)),
		BMS_RS485_COMMUNICATION_ABNORMAL(Doc.of(Level.FAULT)),
		PCS_RS485_COMMUNICATION_ABNORMAL(Doc.of(Level.FAULT)),
		CABINET_EMERGENCY_STOP(Doc.of(Level.WARNING)),
		INSULATION_FAULT(Doc.of(Level.WARNING)),

		// Alarm Value 3
		GRID_POWER_CUTOFF_WARNING(Doc.of(Level.WARNING)),
		SURGE_PROTECTION_WARNING(Doc.of(Level.WARNING)),

		// Alarm Value 4
		GAS_DISCHARGE(Doc.of(Level.WARNING)),
		SUBSYSTEM_SHUTDOWN_FAILURE(Doc.of(Level.WARNING)),
		PCS_POWER_CONTROL_FAILURE(Doc.of(Level.WARNING)),
		PCS_COMMUNICATION_FAILURE(Doc.of(Level.WARNING)),
		BMS_COMMUNICATION_FAILURE(Doc.of(Level.WARNING)),

		// Alarm Value 5
		SUBSYSTEM_HIGH_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		SUBSYSTEM_LOW_VOLTAGE_WARNING(Doc.of(Level.WARNING)),
		COOLING_SYSTEM_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 6
		QS_FUSE_FAULT(Doc.of(Level.WARNING)),
		QF_TRIP_FAULT(Doc.of(Level.WARNING)),
		COOLING_SYSTEM_ALARM(Doc.of(Level.WARNING)),
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

	public HyperCubeModel getModel();

	/**
	 * Gets the Channel for {@link ChannelId#OPERATION_STATE}.
	 *
	 * @return the Channel
	 */
	public default Channel<OperationState> getOperationStateChannel() {
		return this.channel(ChannelId.OPERATION_STATE);
	}

	/**
	 * Gets the OperationStatus channel value for {@link ChannelId#OPERATION_STATE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<OperationState> getOperationState() {
		return this.getOperationStateChannel().value();
	}

	public static void activateAllowedPowerHandler(HyperCube ess, ClockProvider clock,
			AllowedPowerHandler allowedPower) {

		final Consumer<Value<Integer>> accept = ignore -> {
			allowedPower.accept(clock);
		};
		ess.getStartStopChannel().onChange((ignore0, ignore1) -> {
			allowedPower.accept(clock);
		});
		ess.getBatteryDischargeMaxPowerChannel().onSetNextValue(accept);
		ess.getBatteryChargeMaxPowerChannel().onSetNextValue(accept);
	}

}
