package io.openems.edge.ess.hyperstrong.hypercube;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.INVERT;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.chain;
import static io.openems.edge.ess.hyperstrong.AlarmAnalysis.decodeAlarm;
import static io.openems.edge.ess.hyperstrong.ModbusUtils.defineModbusAlarmRegister;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.BitsWordElement;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC16WriteRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;
import io.openems.edge.common.channel.ChannelId.ChannelIdImpl;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.cycle.Cycle;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.ess.api.EssErrorAcknowledge;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.hyperstrong.statemachine.Context;
import io.openems.edge.ess.hyperstrong.statemachine.StateMachine;
import io.openems.edge.ess.hyperstrong.statemachine.StateMachine.State;
import io.openems.edge.ess.hyperstrong.thermal.ThermalManagementSystem;
import io.openems.edge.ess.power.api.Power;
import io.openems.edge.oros.bms.api.BatteryManagementProvider;
import io.openems.edge.oros.bms.api.BatteryManagementSystem;
import io.openems.edge.oros.common.SymmetricComponent;
import io.openems.edge.oros.ess.api.EnergyStorageProtection;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;
import io.openems.edge.oros.ess.core.AbstractModbusEss;
import io.openems.edge.oros.ess.core.RuntimeChannels;
import io.openems.edge.oros.pcs.api.PowerConversionProvider;
import io.openems.edge.oros.pcs.api.PowerConversionSystem;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;


@Designate(ocd = Config.class, factory = true)
@Component(
		name = "Ess.HyperStrong.HyperCube.II",
		immediate = true,
		configurationPolicy = ConfigurationPolicy.REQUIRE
)
@EventTopics({
		EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE,
})
public class HyperCubeImpl extends AbstractModbusEss implements HyperCube,
		EnergyStorageSystem, ManagedSymmetricEss, SymmetricEss, SymmetricComponent, 
		EnergyStorageProtection, EssErrorAcknowledge, OpenemsComponent, ModbusComponent, ModbusSlave, RuntimeChannels,
		ThermalManagementSystem, PowerConversionProvider, BatteryManagementProvider, TimedataProvider, EventHandler, StartStoppable {

	private final Logger log = LoggerFactory.getLogger(HyperCubeImpl.class);
	private final StateMachine stateMachine = new StateMachine(State.UNDEFINED);

	private final Duration heartbeatTimeout = Duration.ofMillis((long) (HyperCube.MIN_HEARTBEAT_CYCLE * 1000));
	private volatile Instant heartbeatTimestamp = Instant.MIN;
	private volatile int heartbeatValue = 0;

	private Config config;

	@Reference
	private Cycle cycle;

	@Reference
	private Power power;

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private ComponentManager componentManager;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata;

	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY)
	private volatile PowerConversionSystem pcs;

	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY)
	private volatile BatteryManagementSystem bms;

	@Override
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public HyperCubeImpl() {
		super(
				OpenemsComponent.ChannelId.values(),
				ModbusComponent.ChannelId.values(),
				StartStoppable.ChannelId.values(),
				SymmetricComponent.ChannelId.values(),
				SymmetricEss.ChannelId.values(),
				ManagedSymmetricEss.ChannelId.values(),
				EnergyStorageSystem.ChannelId.values(),
				EnergyStorageProtection.ChannelId.values(),
				EssErrorAcknowledge.ChannelId.values(),
				RuntimeChannels.ChannelId.values(),
				HyperCube.ChannelId.values(),
				HyperCube.AlarmChannelId.values(),
				ThermalManagementSystem.ChannelId.values()
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		if (super.activate(context, config.id(), config.alias(), config.enabled(), this.cm, 1,
				config.modbus_id(), config.pcs_id(), config.bms_id(), config.startStop())) {
			return;
		}
		this.config = config;
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void executeErrorAcknowledge() {
		try {
			this.stateMachine.forceNextState(State.UNDEFINED);

		} catch (Exception e) {
			this.logError(this.log, e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	@Override
	protected void handleStateMachine() {
		var clock = this.componentManager.getClock();

		// Store the current State
		this._setStateMachine(this.stateMachine.getCurrentState());

		// Initialize 'Start-Stop' Channel
		this._setStartStop(StartStop.UNDEFINED);

		this.handleHeartbeat(clock);

		// Prepare Context
		var context = new Context(this, clock);

		// Call the StateMachine
		try {
			this.stateMachine.run(context);
			this._setRunFailed(false);

		} catch (OpenemsNamedException e) {
			this._setRunFailed(true);
			this.logError(this.log, "StateMachine failed: " + e.getMessage());
		}
	}

	protected void handleHeartbeat(Clock clock) {
		if (this.isReadOnly() || this.getStartStopTarget() != StartStop.START) {
			return;
		}
		var heartbeatTimestamp = Instant.now(clock);
		if (heartbeatTimestamp.isAfter(this.heartbeatTimestamp.plus(this.heartbeatTimeout))) {
			int heartbeat = heartbeatValue == 0 ? 1 : 0;
			this.setHeartbeat(heartbeat);

			this.heartbeatValue = heartbeat;
			this.heartbeatTimestamp = heartbeatTimestamp;
		}
	}

	protected void setHeartbeat(int heartbeat) {
		try {
			IntegerWriteChannel heartbeatChannel = this.channel(HyperCube.ChannelId.HEARTBEAT);
			heartbeatChannel.setNextWriteValue(heartbeat);

		} catch (IllegalArgumentException | OpenemsNamedException e) {
			this.logError(this.log, "Setting Heartbeat failed: " + e.getMessage());
		}
	}

	@Override
	public void setStartStop(StartStop value) {
		if (this.startStopTarget.getAndSet(value) != value) {
			this.stateMachine.forceNextState(State.UNDEFINED);
		}
	}

	@Override
	public void applyPower(int activePower, int reactivePower) throws OpenemsNamedException {
		super.applyPower(activePower, reactivePower);

		if (this.isReadOnly()) {
			return;
		}
		IntegerWriteChannel setActivePowerChannel = this.channel(HyperCube.ChannelId.SET_ACTIVE_POWER);
		setActivePowerChannel.setNextWriteValue(activePower);
		IntegerWriteChannel setReactivePowerChannel = this.channel(HyperCube.ChannelId.SET_REACTIVE_POWER);
		setReactivePowerChannel.setNextWriteValue(reactivePower);
	}

	protected ComponentManager getComponentManager() {
		return this.componentManager;
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public Power getPower() {
		return this.power;
	}

	@Override
	protected float getMaxPowerIncreasePercentage() {
		return this.config.maxPowerIncreasePercentage();
	}

	@Override
	public boolean isReadOnly() {
		return this.config.readOnly();
	}

	@Override
	public PowerConversionSystem getPowerConversionSystem() {
		return this.pcs;
	}

	@Override
	public BatteryManagementSystem getBatteryManagementSystem() {
		return this.bms;
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		return new ModbusProtocol(this,
				new FC4ReadInputRegistersTask(101, Priority.LOW,
						m(HyperCube.ChannelId.CHARGE_MODE, new UnsignedWordElement(101)),
						m(HyperCube.ChannelId.OPERATING_STATUS, new UnsignedWordElement(102)),
						new DummyRegisterElement(103, 115),
						m(ManagedSymmetricEss.ChannelId.ALLOWED_CHARGE_POWER,
								new UnsignedWordElement(116), chain(SCALE_FACTOR_2, INVERT)),
						m(ManagedSymmetricEss.ChannelId.ALLOWED_DISCHARGE_POWER,
								new UnsignedWordElement(117), SCALE_FACTOR_2),
						m(EnergyStorageSystem.ChannelId.AVAILABLE_DISCHARGE_ENERGY,
								new UnsignedWordElement(118), SCALE_FACTOR_2),
						m(EnergyStorageSystem.ChannelId.AVAILABLE_CHARGE_ENERGY,
								new UnsignedWordElement(119), SCALE_FACTOR_2),
						defineModbusAlarmRegister(this, 1, 120, this::addModbusAlarmChannel, value -> {
							decodeAlarm(0, value, this.channel(AlarmChannelId.INITIALIZATION_FAILURE));
							decodeAlarm(2, value, this.channel(AlarmChannelId.SMOKE_SENSOR_ALARM));
							decodeAlarm(3, value, this.channel(AlarmChannelId.FIRE_ALARM));
							decodeAlarm(4, value, this.channel(AlarmChannelId.WATER_LEAKAGE_ALARM));
							decodeAlarm(6, value, this.channel(AlarmChannelId.SEVERE_HUMIDITY_ALARM));
							decodeAlarm(7, value, this.channel(AlarmChannelId.SUBSYSTEM_ISLANDING));
							decodeAlarm(8, value, this.channel(AlarmChannelId.CABINET_DOOR_INTERLOCK));
							decodeAlarm(9, value, this.channel(AlarmChannelId.PCS_EMERGENCY_STOP));
							decodeAlarm(10, value, this.channel(AlarmChannelId.QZ_CONTACTOR_RELEASE_FAULT));
						}),
						defineModbusAlarmRegister(this, 2, 122, this::addModbusAlarmChannel, value -> {
							decodeAlarm(0, value, this.channel(AlarmChannelId.UPS_FAULT));
							decodeAlarm(1, value, this.channel(AlarmChannelId.BMS_FAULT));
							decodeAlarm(2, value, this.channel(AlarmChannelId.PCS_FAULT));
							decodeAlarm(3, value, this.channel(AlarmChannelId.METER_ALARM));
							decodeAlarm(4, value, this.channel(AlarmChannelId.THERMAL_MANAGEMENT_SYSTEM_FAULT));
							decodeAlarm(5, value, this.channel(AlarmChannelId.BMS_RS485_COMMUNICATION_ABNORMAL));
							decodeAlarm(6, value, this.channel(AlarmChannelId.PCS_RS485_COMMUNICATION_ABNORMAL));
							decodeAlarm(7, value, this.channel(AlarmChannelId.CABINET_EMERGENCY_STOP));
							decodeAlarm(13, value, this.channel(AlarmChannelId.INSULATION_FAULT));
						}),
						defineModbusAlarmRegister(this, 3, 124, this::addModbusAlarmChannel, value -> {
							decodeAlarm(5, value, this.channel(AlarmChannelId.GRID_POWER_CUTOFF_FAULT));
							decodeAlarm(14, value, this.channel(AlarmChannelId.SURGE_PROTECTION_FAULT));
						}),
						defineModbusAlarmRegister(this, 4, 126, this::addModbusAlarmChannel, value -> {
							decodeAlarm(0, value, this.channel(AlarmChannelId.GAS_DISCHARGE));
							decodeAlarm(9, value, this.channel(AlarmChannelId.SUBSYSTEM_SHUTDOWN_FAILURE));
							decodeAlarm(12, value, this.channel(AlarmChannelId.PCS_POWER_CONTROL_FAILURE));
							decodeAlarm(13, value, this.channel(AlarmChannelId.PCS_COMMUNICATION_FAILURE));
							decodeAlarm(14, value, this.channel(AlarmChannelId.BMS_COMMUNICATION_FAILURE));
						}),
						defineModbusAlarmRegister(this, 5, 128, this::addModbusAlarmChannel, value -> {
							decodeAlarm(0, value, this.channel(AlarmChannelId.SUBSYSTEM_HIGH_VOLTAGE_FAULT));
							decodeAlarm(1, value, this.channel(AlarmChannelId.SUBSYSTEM_LOW_VOLTAGE_FAULT));
							decodeAlarm(14, value, this.channel(AlarmChannelId.THERMAL_MANAGEMENT_COMMUNICATION_WARNING));
						}),
						defineModbusAlarmRegister(this, 6, 130, this::addModbusAlarmChannel, value -> {
							decodeAlarm(0, value, this.channel(AlarmChannelId.QS_FUSE_FAULT));
							decodeAlarm(3, value, this.channel(AlarmChannelId.QF_TRIP_FAULT));
							decodeAlarm(4, value, this.channel(AlarmChannelId.THERMAL_MANAGEMENT_SYSTEM_ALARM));
						}),
						defineModbusAlarmRegister(this, 7, 132, this::addModbusAlarmChannel, value -> {
							decodeAlarm(11, value, this.channel(AlarmChannelId.PCS_STARTUP_FAULT));
						}),
						new DummyRegisterElement(131, 140),
						m(HyperCube.ChannelId.CHARGE_CONSTRAINT, new UnsignedWordElement(141)),
						m(new BitsWordElement(142, this)
								.bit(0, HyperCube.ChannelId.REMOTE_COMMUNICATION_ABNORMAL)
								.bit(1, HyperCube.ChannelId.REMOTE_COMMUNICATION_CONNECTED)
								.bit(2, HyperCube.ChannelId.REMOTE_COMMUNICATION_ENABLED)
								.bit(3, HyperCube.ChannelId.REMOTE_COMMUNICATION_FAULT)
						),
						m(HyperCube.ChannelId.OPERATING_TARGET, new UnsignedWordElement(143))),

				// FIXME: Reading appears to not work correctly. Validate this with future firmware update.
				// Channels were set to WRITE_ONLY to reflect this.
				// new DummyRegisterElement(304, 314),
				// m(HyperCube.ChannelId.SET_ACTIVE_POWER,
				// 		new SignedWordElement(315), SCALE_FACTOR_3),
				// m(HyperCube.ChannelId.SET_REACTIVE_POWER,
				// 		new SignedWordElement(316), SCALE_FACTOR_3)),

				new FC4ReadInputRegistersTask(50001, Priority.LOW,
						m(ThermalManagementSystem.ChannelId.THERMAL_MANAGEMENT_SYSTEM_RUN_MODE,
								new UnsignedWordElement(50001)),
						m(ThermalManagementSystem.ChannelId.THERMAL_MANAGEMENT_SYSTEM_RETURN_TEMPERATURE,
								new SignedWordElement(50002)),
						m(ThermalManagementSystem.ChannelId.THERMAL_MANAGEMENT_SYSTEM_SUPPLY_TEMPERATURE,
								new SignedWordElement(50003)),
						m(new BitsWordElement(50004, this)
								.bit(0, ThermalManagementSystem.ChannelId.THERMAL_MANAGEMENT_COMMUNICATION_ABNORMAL)
								.bit(1, ThermalManagementSystem.ChannelId.THERMAL_MANAGEMENT_COMMUNICATION_CONNECTED)
								.bit(2, ThermalManagementSystem.ChannelId.THERMAL_MANAGEMENT_COMMUNICATION_ENABLED)
								.bit(3, ThermalManagementSystem.ChannelId.THERMAL_MANAGEMENT_COMMUNICATION_FAULT)
						),
						m(new BitsWordElement(50005, this)
								.bit(0, ThermalManagementSystem.ChannelId.THERMAL_MANAGEMENT_MAIN_CONTACTOR_STATE)
								.bit(1, ThermalManagementSystem.ChannelId.THERMAL_MANAGEMENT_COMPRESSOR_STATE)
								.bit(2, ThermalManagementSystem.ChannelId.THERMAL_MANAGEMENT_HEATING_STATE)
						),
						new DummyRegisterElement(50006, 50073),
						m(ThermalManagementSystem.ChannelId.THERMAL_MANAGEMENT_SYSTEM_RUN_MODE_TARGET,
								new UnsignedWordElement(50074)),
						new DummyRegisterElement(50075, 50076),
						m(ThermalManagementSystem.ChannelId.THERMAL_MANAGEMENT_SYSTEM_FAULT_CODE,
								new UnsignedWordElement(50077)),
						new DummyRegisterElement(50078, 50098),
						m(ThermalManagementSystem.ChannelId.THERMAL_MANAGEMENT_RETURN_PRESSURE,
								new SignedWordElement(50099), SCALE_FACTOR_1),
						m(ThermalManagementSystem.ChannelId.THERMAL_MANAGEMENT_SUPPLY_PRESSURE,
								new SignedWordElement(50100), SCALE_FACTOR_1)),

				new FC6WriteRegisterTask(1, 
						m(HyperCube.ChannelId.HEARTBEAT, new UnsignedWordElement(1))),

				new FC6WriteRegisterTask(302, 
						m(SymmetricEss.ChannelId.GRID_MODE, new UnsignedWordElement(302))),
				new FC6WriteRegisterTask(303, 
						m(HyperCube.ChannelId.RUN_MODE_TARGET, new UnsignedWordElement(303))),

				new FC16WriteRegistersTask(315,
						m(HyperCube.ChannelId.SET_ACTIVE_POWER,
								new SignedWordElement(315), SCALE_FACTOR_3),
						m(HyperCube.ChannelId.SET_REACTIVE_POWER,
								new SignedWordElement(316), SCALE_FACTOR_3)));
	}

	private io.openems.edge.common.channel.ChannelId addModbusAlarmChannel(int number) {
		var channelId = new ChannelIdImpl(String.format("%s_%02d", "ALARM", number), Doc.of(OpenemsType.LONG));
		this.addChannel(channelId);
		return channelId;
	}

	@Override
	public String debugLog() {
		return EnergyStorageSystem.generateDebugLog(this, this.stateMachine);
	}

}
