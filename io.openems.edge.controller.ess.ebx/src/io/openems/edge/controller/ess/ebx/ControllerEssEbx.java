package io.openems.edge.controller.ess.ebx;

import static io.openems.common.channel.PersistencePriority.HIGH;
import static io.openems.common.channel.Unit.WATT;
import static io.openems.common.types.OpenemsType.INTEGER;

import java.time.Instant;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Level;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.EnumReadChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.LongReadChannel;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.controller.ess.ebx.statemachine.StateMachine.State;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;

/**
 * Executes the aggregate active-power dispatch of the EBX virtual power plant
 * on an energy storage system.
 *
 * <p>
 * This interface is transport neutral. A transport bundle hands over already
 * validated commands via {@link #applyCommand(int, Instant)}, reports the
 * heartbeat of the EBX side via {@link #reportHeartbeat(Instant)} and reads
 * everything it publishes from {@link #getEnergyStorageSystem()},
 * {@link #getMeter()} and the asset-status and available-power channels.
 * Duplicate and ordering checks, frame decoding and unit conversion are the
 * transport's duty before these calls.
 */
public interface ControllerEssEbx extends Controller, OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Current state of the fail-safe state machine.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssEbx
		 * <li>Type: State
		 * </ul>
		 */
		STATE_MACHINE(Doc.of(State.values()) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Current State of the Fail-Safe State Machine")),

		/**
		 * Asset status as reported to EBX.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssEbx
		 * <li>Type: AssetStatus
		 * <li>Implementation Note: the derivation is binary between no and full
		 * availability, deriving restricted availability requires a component-fault
		 * source that is not yet connected
		 * </ul>
		 */
		ASSET_STATUS(Doc.of(AssetStatus.values()) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Asset Status")),

		/**
		 * Second-of-minute watchdog for the Modbus access.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssEbx
		 * <li>Type: Integer
		 * <li>Range: 0 to 59
		 * <li>Implementation Note: reserved for the future ModbusSlave nature, where a
		 * change of the written value is a heartbeat event. Not used by the MQTT
		 * transport.
		 * </ul>
		 */
		HEARTBEAT(Doc.of(OpenemsType.INTEGER) //
				.accessMode(AccessMode.WRITE_ONLY) //
				.text("Second-of-minute watchdog, a change of value is a heartbeat event")),

		/**
		 * Age of the newest heartbeat event.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssEbx
		 * <li>Type: Integer
		 * <li>Unit: ms
		 * <li>Range: zero or positive value, undefined before the first heartbeat
		 * event
		 * </ul>
		 */
		HEARTBEAT_TIME(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLISECONDS) //
				.text("Age of the newest heartbeat event")),

		/**
		 * The heartbeat exceeded the configured timeout.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssEbx
		 * <li>Type: State
		 * <li>Level: WARNING
		 * </ul>
		 */
		HEARTBEAT_STALE(Doc.of(Level.WARNING) //
				.text("Heartbeat exceeded the timeout")),

		/**
		 * Local clock minus the source timestamp of the last heartbeat event.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssEbx
		 * <li>Type: Long
		 * <li>Unit: ms
		 * </ul>
		 */
		CLOCK_OFFSET(Doc.of(OpenemsType.LONG) //
				.unit(Unit.MILLISECONDS) //
				.text("Local clock minus the source timestamp of the last heartbeat event")),

		/**
		 * Clock offset exceeds half the configured heartbeat timeout.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssEbx
		 * <li>Type: State
		 * <li>Level: WARNING
		 * </ul>
		 */
		CLOCK_DRIFT_HIGH(Doc.of(Level.WARNING) //
				.text("Clock offset exceeds half the configured heartbeat timeout")),

		/**
		 * Active power actually written after clamping and ramping.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssEbx
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: negative for charge, positive for discharge, undefined while the
		 * ESS is released
		 * </ul>
		 */
		SETPOINT_ACTIVE_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Active power actually written after clamping and ramping")),

		/**
		 * Expiry of the held command.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssEbx
		 * <li>Type: Long
		 * <li>Unit: ms
		 * <li>Range: Unix epoch milliseconds
		 * </ul>
		 */
		SETPOINT_VALID_UNTIL(Doc.of(OpenemsType.LONG) //
				.unit(Unit.MILLISECONDS) //
				.text("Expiry of the held command as Unix epoch milliseconds")),

		/**
		 * Held command has passed its expiry.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssEbx
		 * <li>Type: State
		 * <li>Level: WARNING
		 * </ul>
		 */
		SETPOINT_EXPIRED(Doc.of(Level.WARNING) //
				.text("Held command has passed its expiry")),

		/**
		 * Received target was outside the achievable range.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssEbx
		 * <li>Type: State
		 * <li>Level: INFO
		 * </ul>
		 */
		SETPOINT_CLAMPED(Doc.of(Level.INFO) //
				.text("Received target was outside the achievable range")),

		/**
		 * Holds the currently maximum available charge power. This value is commonly
		 * defined by current battery limitations and grid constraints.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssEbx
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: zero or positive value
		 * <li>Implementation Note: captured from the power solver before this
		 * controller applies its own setpoint constraint, because afterwards the
		 * solver range collapses to the setpoint itself
		 * </ul>
		 */
		AVAILABLE_CHARGE_POWER(Doc.of(INTEGER)//
				.unit(WATT)//
				.persistencePriority(HIGH)//
				.text("Currently maximum available charge power")), //
		/**
		 * Holds the currently maximum available discharge power. This value is commonly
		 * defined by current battery limitations and grid constraints.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssEbx
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: zero or positive value
		 * <li>Implementation Note: captured from the power solver before this
		 * controller applies its own setpoint constraint, because afterwards the
		 * solver range collapses to the setpoint itself
		 * </ul>
		 */
		AVAILABLE_DISCHARGE_POWER(Doc.of(INTEGER)//
				.unit(WATT)//
				.persistencePriority(HIGH)//
				.text("Currently maximum available discharge power")), //
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
	 * Gets the current state of the fail-safe state machine. See
	 * {@link ChannelId#STATE_MACHINE}.
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
	 * Gets the Channel for {@link ChannelId#HEARTBEAT_TIME}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getHeartbeatTimeChannel() {
		return this.channel(ChannelId.HEARTBEAT_TIME);
	}

	/**
	 * Gets the age of the newest heartbeat event in [ms]. See
	 * {@link ChannelId#HEARTBEAT_TIME}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getHeartbeatTime() {
		return this.getHeartbeatTimeChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#HEARTBEAT_TIME}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setHeartbeatTime(Integer value) {
		this.getHeartbeatTimeChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#HEARTBEAT_STALE}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getHeartbeatStaleChannel() {
		return this.channel(ChannelId.HEARTBEAT_STALE);
	}

	/**
	 * Gets whether the heartbeat exceeded the timeout. See
	 * {@link ChannelId#HEARTBEAT_STALE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getHeartbeatStale() {
		return this.getHeartbeatStaleChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#HEARTBEAT_STALE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setHeartbeatStale(boolean value) {
		this.getHeartbeatStaleChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#CLOCK_OFFSET}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getClockOffsetChannel() {
		return this.channel(ChannelId.CLOCK_OFFSET);
	}

	/**
	 * Gets the local clock minus the source timestamp of the last heartbeat event
	 * in [ms]. See {@link ChannelId#CLOCK_OFFSET}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getClockOffset() {
		return this.getClockOffsetChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#CLOCK_OFFSET}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setClockOffset(Long value) {
		this.getClockOffsetChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#CLOCK_DRIFT_HIGH}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getClockDriftHighChannel() {
		return this.channel(ChannelId.CLOCK_DRIFT_HIGH);
	}

	/**
	 * Gets whether the clock offset exceeds half the configured heartbeat timeout.
	 * See {@link ChannelId#CLOCK_DRIFT_HIGH}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getClockDriftHigh() {
		return this.getClockDriftHighChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#CLOCK_DRIFT_HIGH}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setClockDriftHigh(boolean value) {
		this.getClockDriftHighChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#SETPOINT_ACTIVE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getSetpointActivePowerChannel() {
		return this.channel(ChannelId.SETPOINT_ACTIVE_POWER);
	}

	/**
	 * Gets the active power actually written after clamping and ramping in [W].
	 * See {@link ChannelId#SETPOINT_ACTIVE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getSetpointActivePower() {
		return this.getSetpointActivePowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#SETPOINT_ACTIVE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setSetpointActivePower(Integer value) {
		this.getSetpointActivePowerChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#SETPOINT_VALID_UNTIL}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getSetpointValidUntilChannel() {
		return this.channel(ChannelId.SETPOINT_VALID_UNTIL);
	}

	/**
	 * Gets the expiry of the held command as Unix epoch milliseconds. See
	 * {@link ChannelId#SETPOINT_VALID_UNTIL}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getSetpointValidUntil() {
		return this.getSetpointValidUntilChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#SETPOINT_VALID_UNTIL} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setSetpointValidUntil(Long value) {
		this.getSetpointValidUntilChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#SETPOINT_EXPIRED}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getSetpointExpiredChannel() {
		return this.channel(ChannelId.SETPOINT_EXPIRED);
	}

	/**
	 * Gets whether the held command has passed its expiry. See
	 * {@link ChannelId#SETPOINT_EXPIRED}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getSetpointExpired() {
		return this.getSetpointExpiredChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#SETPOINT_EXPIRED}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setSetpointExpired(boolean value) {
		this.getSetpointExpiredChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#SETPOINT_CLAMPED}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getSetpointClampedChannel() {
		return this.channel(ChannelId.SETPOINT_CLAMPED);
	}

	/**
	 * Gets whether the received target was outside the achievable range. See
	 * {@link ChannelId#SETPOINT_CLAMPED}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getSetpointClamped() {
		return this.getSetpointClampedChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#SETPOINT_CLAMPED}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setSetpointClamped(boolean value) {
		this.getSetpointClampedChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#HEARTBEAT}.
	 *
	 * @return the Channel
	 */
	public default IntegerWriteChannel getHeartbeatChannel() {
		return this.channel(ChannelId.HEARTBEAT);
	}

	/**
	 * Gets the Channel for {@link ChannelId#ASSET_STATUS}.
	 *
	 * @return the Channel
	 */
	public default EnumReadChannel getAssetStatusChannel() {
		return this.channel(ChannelId.ASSET_STATUS);
	}

	/**
	 * Gets the asset status as reported to EBX. See
	 * {@link ChannelId#ASSET_STATUS}.
	 *
	 * @return the {@link AssetStatus}
	 */
	public default AssetStatus getAssetStatus() {
		return this.getAssetStatusChannel().value().asEnum();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ASSET_STATUS}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setAssetStatus(AssetStatus value) {
		this.getAssetStatusChannel().setNextValue(value);
	}

	/**
	 * Gets the controlled Energy Storage System.
	 *
	 * <p>
	 * The power and energy telemetry is read from the nature channels of the ESS
	 * directly, it is deliberately not mirrored on this controller.
	 *
	 * @return the {@link EnergyStorageSystem}
	 */
	public EnergyStorageSystem getEnergyStorageSystem();

	/**
	 * Gets the point-of-connection meter the actual power refers to.
	 *
	 * <p>
	 * Null when the reference point is the ESS itself. The meter follows the
	 * grid-meter sign convention, positive for import.
	 *
	 * @return the {@link ElectricityMeter}, or null
	 */
	public ElectricityMeter getMeter();

	/**
	 * Gets the Channel for {@link ChannelId#AVAILABLE_CHARGE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getAvailableChargePowerChannel() {
		return this.channel(ChannelId.AVAILABLE_CHARGE_POWER);
	}

	/**
	 * Gets the currently maximum available charge power in [W]. See
	 * {@link ChannelId#AVAILABLE_CHARGE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getAvailableChargePower() {
		return this.getAvailableChargePowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#AVAILABLE_CHARGE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setAvailableChargePower(Integer value) {
		this.getAvailableChargePowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#AVAILABLE_CHARGE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setAvailableChargePower(int value) {
		this.getAvailableChargePowerChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#AVAILABLE_DISCHARGE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getAvailableDischargePowerChannel() {
		return this.channel(ChannelId.AVAILABLE_DISCHARGE_POWER);
	}

	/**
	 * Gets the currently maximum available discharge power in [W]. See
	 * {@link ChannelId#AVAILABLE_DISCHARGE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getAvailableDischargePower() {
		return this.getAvailableDischargePowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#AVAILABLE_DISCHARGE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setAvailableDischargePower(Integer value) {
		this.getAvailableDischargePowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#AVAILABLE_DISCHARGE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setAvailableDischargePower(int value) {
		this.getAvailableDischargePowerChannel().setNextValue(value);
	}

	/**
	 * Hands over an already validated dispatch command.
	 *
	 * <p>
	 * Duplicate and ordering checks, frame decoding and unit conversion are the
	 * transport's duty before this call. A call does not refresh the heartbeat,
	 * the transport reports that separately via {@link #reportHeartbeat(Instant)}.
	 *
	 * @param power      the aggregate active-power target in [W], positive for
	 *                   discharge
	 * @param validUntil the hard execution expiry of the command
	 */
	public void applyCommand(int power, Instant validUntil);

	/**
	 * Reports a heartbeat event, meaning the EBX side is alive.
	 *
	 * <p>
	 * The staleness timestamp is taken internally on a monotonic clock. For the
	 * MQTT transport the trigger is every accepted command frame, for the future
	 * Modbus access a change of the {@link ChannelId#HEARTBEAT} watchdog value.
	 *
	 * @param sourceTimestamp the source timestamp of the triggering frame, used
	 *                        for clock drift diagnostics, may be null when the
	 *                        transport carries no timestamp
	 */
	public void reportHeartbeat(Instant sourceTimestamp);
}
