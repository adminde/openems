package io.openems.edge.ess.hyperstrong.hypercube;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Level;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.ess.api.EssErrorAcknowledge;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.hyperstrong.statemachine.StateMachine.State;
import io.openems.edge.oros.bms.api.BatteryManagementProvider;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;
import io.openems.edge.oros.pcs.api.PowerConversionProvider;
import io.openems.edge.timedata.api.TimedataProvider;

public interface HyperCube extends EnergyStorageSystem,
		ManagedSymmetricEss, SymmetricEss, EssErrorAcknowledge, OpenemsComponent, ModbusComponent, ModbusSlave,
		PowerConversionProvider, BatteryManagementProvider, TimedataProvider, StartStoppable {

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

		HEARTBEAT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.WRITE_ONLY)),

		STATE_MACHINE(Doc.of(State.values())
				.text("Current State of State-Machine")),
		RUN_FAILED(Doc.of(Level.FAULT)
				.text("Running the Logic failed")),
		RUN_MODE_TARGET(Doc.of(RunModeTarget.values())
				.accessMode(AccessMode.WRITE_ONLY)),
		CHARGE_MODE(Doc.of(ChargingMode.values())),
		CHARGE_CONSTRAINT(Doc.of(ChargingConstraint.values())),
		OPERATING_STATUS(Doc.of(OperatingStatus.values())),
		OPERATING_TARGET(Doc.of(OperatingTarget.values())),
		REMOTE_COMMUNICATION_ENABLED(Doc.of(OpenemsType.BOOLEAN)),
		REMOTE_COMMUNICATION_CONNECTED(Doc.of(OpenemsType.BOOLEAN)),
		REMOTE_COMMUNICATION_ABNORMAL(Doc.of(Level.WARNING)),
		REMOTE_COMMUNICATION_FAULT(Doc.of(Level.FAULT)),

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
		INITIALIZATION_FAILURE(Doc.of(Level.FAULT)),
		SMOKE_SENSOR_ALARM(Doc.of(Level.FAULT)),
		FIRE_ALARM(Doc.of(Level.FAULT)),
		WATER_LEAKAGE_ALARM(Doc.of(Level.WARNING)),
		SEVERE_HUMIDITY_ALARM(Doc.of(Level.WARNING)),
		SUBSYSTEM_ISLANDING(Doc.of(Level.FAULT)),
		CABINET_DOOR_INTERLOCK(Doc.of(Level.WARNING)),
		PCS_EMERGENCY_STOP(Doc.of(Level.FAULT)),
		QZ_CONTACTOR_RELEASE_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 2
		UPS_FAULT(Doc.of(Level.FAULT)),
		BMS_FAULT(Doc.of(Level.FAULT)),
		PCS_FAULT(Doc.of(Level.FAULT)),
		METER_ALARM(Doc.of(Level.WARNING)),
		THERMAL_MANAGEMENT_SYSTEM_FAULT(Doc.of(Level.FAULT)),
		BMS_RS485_COMMUNICATION_ABNORMAL(Doc.of(Level.WARNING)),
		PCS_RS485_COMMUNICATION_ABNORMAL(Doc.of(Level.WARNING)),
		CABINET_EMERGENCY_STOP(Doc.of(Level.FAULT)),
		INSULATION_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 3
		GRID_POWER_CUTOFF_FAULT(Doc.of(Level.FAULT)),
		SURGE_PROTECTION_FAULT(Doc.of(Level.FAULT)),

		// Alarm Value 4
		GAS_DISCHARGE(Doc.of(Level.FAULT)),
		SUBSYSTEM_SHUTDOWN_FAILURE(Doc.of(Level.FAULT)),
		PCS_POWER_CONTROL_FAILURE(Doc.of(Level.FAULT)),
		PCS_COMMUNICATION_FAILURE(Doc.of(Level.FAULT)),
		BMS_COMMUNICATION_FAILURE(Doc.of(Level.FAULT)),

		// Alarm Value 5
		SUBSYSTEM_HIGH_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		SUBSYSTEM_LOW_VOLTAGE_FAULT(Doc.of(Level.FAULT)),
		THERMAL_MANAGEMENT_COMMUNICATION_WARNING(Doc.of(Level.WARNING)),

		// Alarm Value 6
		QS_FUSE_FAULT(Doc.of(Level.FAULT)),
		QF_TRIP_FAULT(Doc.of(Level.FAULT)),
		THERMAL_MANAGEMENT_SYSTEM_ALARM(Doc.of(Level.FAULT)),

		// Alarm Value 7
		PCS_STARTUP_FAULT(Doc.of(Level.FAULT)),
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

	/**
	 * Gets the Channel for {@link ChannelId#OPERATING_STATUS}.
	 *
	 * @return the Channel
	 */
	public default Channel<OperatingStatus> getOperationStateChannel() {
		return this.channel(ChannelId.OPERATING_STATUS);
	}

	/**
	 * Gets the OperationStatus channel value for {@link ChannelId#OPERATING_STATUS}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<OperatingStatus> getOperationState() {
		return this.getOperationStateChannel().value();
	}

}
