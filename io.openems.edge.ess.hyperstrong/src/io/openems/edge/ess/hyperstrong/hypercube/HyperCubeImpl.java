package io.openems.edge.ess.hyperstrong.hypercube;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.time.Clock;

import io.openems.edge.battery.api.Battery;
import io.openems.edge.batteryinverter.api.ManagedSymmetricBatteryInverter;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.oros.bms.BatteryManagementSystem;
import io.openems.edge.oros.pcs.PowerConversionSystem;
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
import io.openems.common.timedata.Timeout;
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
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.cycle.Cycle;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.ess.api.EssErrorAcknowledge;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.hyperstrong.statemachine.Context;
import io.openems.edge.ess.hyperstrong.statemachine.StateMachine;
import io.openems.edge.ess.hyperstrong.statemachine.StateMachine.State;
import io.openems.edge.ess.hyperstrong.thermal.ThermalManagementSystem;
import io.openems.edge.ess.power.api.Power;
import io.openems.edge.oros.SymmetricComponent;
import io.openems.edge.oros.ess.AbstractStorageSystem;
import io.openems.edge.oros.ess.EnergyStorageSystem;
import io.openems.edge.oros.ess.protection.VoltageProtection;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;


@Designate(ocd = Config.class, factory = true)
@Component(
		name = "ESS.HyperStrong.HyperCube.II",
		immediate = true,
		configurationPolicy = ConfigurationPolicy.REQUIRE
)
@EventTopics({
		EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE,
})
public class HyperCubeImpl extends AbstractStorageSystem implements HyperCube,
		EnergyStorageSystem, ManagedSymmetricEss, SymmetricEss, SymmetricComponent, EssErrorAcknowledge,
		ThermalManagementSystem, VoltageProtection, OpenemsComponent, ModbusComponent, ModbusSlave,
		TimedataProvider, EventHandler, StartStoppable {

	private final Logger log = LoggerFactory.getLogger(HyperCubeImpl.class);
	private final StateMachine stateMachine = new StateMachine(State.UNDEFINED);

	private final Timeout heartbeatTimeout = Timeout.ofSeconds((int) Math.ceil(HyperCube.MIN_HEARTBEAT_CYCLE));
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
	private volatile Timedata timedata = null;

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
				ThermalManagementSystem.ChannelId.values(),
				EssErrorAcknowledge.ChannelId.values(),
				SymmetricEss.ChannelId.values(),
				ManagedSymmetricEss.ChannelId.values(),
				HyperCube.ChannelId.values(),
				HyperCube.AlarmChannelId.values()
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		super.activate(context, config.id(), config.alias(), config.enabled(), this.cm, 1,
				config.modbus_id(), config.pcs_id(), config.bms_id(), config.startStop());
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
		if (this.getStartStopTarget() == StartStop.START) {
			int heartbeat = heartbeatValue == 0 ? 1 : 0;
			setHeartbeat(clock, heartbeat);
		}
	}

	protected void setHeartbeat(Clock clock, int heartbeat) {
		try {
			IntegerWriteChannel heartbeatChannel = this.channel(HyperCube.ChannelId.HEARTBEAT);
			heartbeatChannel.setNextWriteValue(this.heartbeatValue);

		} catch (IllegalArgumentException | OpenemsNamedException e) {
			this.logError(this.log, "Setting Heartbeat failed: " + e.getMessage());
			e.printStackTrace();
			return;
		}
		this.heartbeatValue = heartbeat;
		this.heartbeatTimeout.start(clock);
	}

	@Override
	public void setStartStop(StartStop value) {
		if (this.startStopTarget.getAndSet(value) != value) {
			this.stateMachine.forceNextState(State.UNDEFINED);
		}
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
	public float getMaxPowerIncreasePercentage() {
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
				new FC3ReadRegistersTask(302, Priority.LOW,
						m(SymmetricEss.ChannelId.GRID_MODE, new UnsignedWordElement(302),
								new ElementToChannelConverter(value -> {
									var intValue = TypeUtils.<Integer>getAsType(OpenemsType.INTEGER, value);
									if (intValue != null) {
										switch (intValue) {
										case 1:
											return GridMode.OFF_GRID;
										case 2:
											return GridMode.ON_GRID;
										}
									}
									return GridMode.UNDEFINED;
								})),
						m(HyperCube.ChannelId.RUN_MODE, new UnsignedWordElement(303)),
						new DummyRegisterElement(304, 314),
						m(EnergyStorageSystem.ChannelId.SET_ACTIVE_POWER,
								new SignedWordElement(315), SCALE_FACTOR_3),
						m(EnergyStorageSystem.ChannelId.SET_REACTIVE_POWER,
								new SignedWordElement(316), SCALE_FACTOR_3)),

				new FC4ReadInputRegistersTask(101, Priority.LOW,
						m(HyperCube.ChannelId.DEVICE_MODE, new UnsignedWordElement(101)),
						m(HyperCube.ChannelId.OPERATING_STATUS, new UnsignedWordElement(102)),
						new DummyRegisterElement(103, 119),
						m(new BitsWordElement(120, this)
								.bit(0, HyperCube.AlarmChannelId.INITIALIZATION_FAILURE)
								.bit(2, HyperCube.AlarmChannelId.SMOKE_SENSOR_ALARM)
								.bit(3, HyperCube.AlarmChannelId.FIRE_ALARM)
								.bit(4, HyperCube.AlarmChannelId.WATER_LEAKAGE_ALARM)
								.bit(6, HyperCube.AlarmChannelId.SEVERE_HUMIDITY_ALARM)
								.bit(7, HyperCube.AlarmChannelId.SUBSYSTEM_ISLANDING)
								.bit(8, HyperCube.AlarmChannelId.INTERLOCK)
								.bit(9, HyperCube.AlarmChannelId.PCS_EMERGENCY_STOP)
								.bit(10, HyperCube.AlarmChannelId.QZ_CONTACTOR_RELEASE_FAULT)
						),
						new DummyRegisterElement(121),
						m(new BitsWordElement(122, this)
								.bit(0, HyperCube.AlarmChannelId.UPS_FAULT)
								.bit(1, HyperCube.AlarmChannelId.BMS_FAULT)
								.bit(2, HyperCube.AlarmChannelId.PCS_FAULT)
								.bit(3, HyperCube.AlarmChannelId.METER_FAULT)
								.bit(4, HyperCube.AlarmChannelId.THERMAL_MANAGEMENT_SYSTEM_WARNING)
								.bit(5, HyperCube.AlarmChannelId.BMS_RS485_COMMUNICATION_ABNORMAL)
								.bit(6, HyperCube.AlarmChannelId.PCS_RS485_COMMUNICATION_ABNORMAL)
								.bit(7, HyperCube.AlarmChannelId.CABINET_EMERGENCY_STOP)
								.bit(13, HyperCube.AlarmChannelId.INSULATION_FAULT)
						),
						new DummyRegisterElement(123),
						m(new BitsWordElement(124, this)
								.bit(5, HyperCube.AlarmChannelId.GRID_POWER_CUTOFF_WARNING)
								.bit(14, HyperCube.AlarmChannelId.SURGE_PROTECTION_WARNING)
						),
						new DummyRegisterElement(125),
						m(new BitsWordElement(126, this)
								.bit(0, HyperCube.AlarmChannelId.GAS_DISCHARGE)
								.bit(9, HyperCube.AlarmChannelId.SUBSYSTEM_SHUTDOWN_FAILURE)
								.bit(12, HyperCube.AlarmChannelId.PCS_POWER_CONTROL_FAILURE)
								.bit(13, HyperCube.AlarmChannelId.PCS_COMMUNICATION_FAILURE)
								.bit(14, HyperCube.AlarmChannelId.BMS_COMMUNICATION_FAILURE)
						),
						new DummyRegisterElement(127),
						m(new BitsWordElement(128, this)
								.bit(0, HyperCube.AlarmChannelId.SUBSYSTEM_HIGH_VOLTAGE_WARNING)
								.bit(1, HyperCube.AlarmChannelId.SUBSYSTEM_LOW_VOLTAGE_WARNING)
								.bit(14, HyperCube.AlarmChannelId.THERMAL_MANAGEMENT_SYSTEM_FAULT)
						),
						new DummyRegisterElement(129),
						m(new BitsWordElement(130, this)
								.bit(0, HyperCube.AlarmChannelId.QS_FUSE_FAULT)
								.bit(3, HyperCube.AlarmChannelId.QF_TRIP_FAULT)
								.bit(4, HyperCube.AlarmChannelId.THERMAL_MANAGEMENT_SYSTEM_ALARM)
						)),

				new FC4ReadInputRegistersTask(50001, Priority.LOW,
						m(ThermalManagementSystem.ChannelId.THERMAL_MANAGEMENT_SYSTEM_MODE,
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
						)),

				new FC4ReadInputRegistersTask(50077, Priority.LOW,
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
						m(HyperCube.ChannelId.RUN_MODE, new UnsignedWordElement(303))),

				new FC16WriteRegistersTask(315,
						m(EnergyStorageSystem.ChannelId.SET_ACTIVE_POWER,
								new SignedWordElement(315), SCALE_FACTOR_3),
						m(EnergyStorageSystem.ChannelId.SET_REACTIVE_POWER,
								new SignedWordElement(316), SCALE_FACTOR_3)));
	}

	@Override
	public String debugLog() {
		return EnergyStorageSystem.generateDebugLog(this, this.stateMachine);
	}

}
