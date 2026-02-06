package io.openems.edge.ess.hyperstrong.hypercube;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.chain;
import static io.openems.edge.common.cycle.Cycle.DEFAULT_CYCLE_TIME;
import static io.openems.edge.common.type.Phase.SingleOrAllPhase.ALL;
import static io.openems.edge.ess.power.api.Pwr.ACTIVE;
import static io.openems.edge.ess.power.api.Pwr.REACTIVE;
import static io.openems.edge.ess.power.api.Relationship.EQUALS;
import static io.openems.edge.ess.power.api.Relationship.GREATER_OR_EQUALS;
import static io.openems.edge.ess.power.api.Relationship.LESS_OR_EQUALS;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.serialization.EmptyObject;
import io.openems.common.session.Role;
import io.openems.common.timedata.Timeout;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.BitsWordElement;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.ModbusElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC16WriteRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;
import io.openems.edge.bridge.modbus.api.task.Task;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.cycle.Cycle;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.jsonapi.ComponentJsonApi;
import io.openems.edge.common.jsonapi.EdgeGuards;
import io.openems.edge.common.jsonapi.JsonApiBuilder;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveNatureTable;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.ess.api.EssErrorAcknowledge;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.hyperstrong.AllowedPowerHandler;
import io.openems.edge.ess.hyperstrong.CycleProvider;
import io.openems.edge.ess.hyperstrong.HyperBattery;
import io.openems.edge.ess.hyperstrong.HyperInverter;
import io.openems.edge.ess.hyperstrong.jsonrpc.ClearTimeoutFailure;
import io.openems.edge.ess.hyperstrong.statemachine.Context;
import io.openems.edge.ess.hyperstrong.statemachine.StateMachine;
import io.openems.edge.ess.hyperstrong.statemachine.StateMachine.State;
import io.openems.edge.ess.power.api.Constraint;
import io.openems.edge.ess.power.api.Power;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "ESS.HyperStrong.HyperCube",
		immediate = true,
		configurationPolicy = ConfigurationPolicy.REQUIRE
)
@EventTopics({
		EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE,
})
public class HyperCubeImpl extends AbstractOpenemsModbusComponent implements
		HyperCube, ManagedSymmetricEss, SymmetricEss, EssErrorAcknowledge,
		OpenemsComponent, ModbusComponent, ModbusSlave, ComponentJsonApi,
		CycleProvider, TimedataProvider, EventHandler, StartStoppable {

	private final Logger log = LoggerFactory.getLogger(HyperCubeImpl.class);
	private final StateMachine stateMachine = new StateMachine(State.UNDEFINED);

	private final AtomicReference<StartStop> startStopTarget = new AtomicReference<>(StartStop.UNDEFINED);

	private final CalculateEnergyFromPower calculateChargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricEss.ChannelId.ACTIVE_CHARGE_ENERGY);
	private final CalculateEnergyFromPower calculateDischargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricEss.ChannelId.ACTIVE_DISCHARGE_ENERGY);

	private final Timeout heartbeatTimeout = Timeout.ofSeconds((int) Math.ceil(HyperCube.MIN_HEARTBEAT_CYCLE));
	private volatile int heartbeatValue = 0;

	private AllowedPowerHandler powerHandler;

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
				EssErrorAcknowledge.ChannelId.values(),
				SymmetricEss.ChannelId.values(),
				ManagedSymmetricEss.ChannelId.values(),
				HyperInverter.ChannelId.values(),
				HyperBattery.ChannelId.values(),
				HyperBattery.AlarmChannelId.values(),
				HyperCube.ChannelId.values(),
				HyperCube.AlarmChannelId.values()
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		if (super.activate(context, config.id(), config.alias(), config.enabled(), 1, this.cm,
				"Modbus", config.modbus_id())) {
			return;
		}
		this.config = config;
		var model = config.model();
		this._setCapacity(model.getCapacity());
		this._setMaxApparentPower((int) Math.floor(model.getMaxChargePower() * HyperCube.APPARENT_POWER_FACTOR));

		this.powerHandler = new AllowedPowerHandler(this);

		// Calculate the Phase Voltages from Phase to Phase Voltages
		HyperInverter.calculatePhaseVoltages(this);

		// Calculate the Phase Powers from Voltage, Current and Power Factor
		HyperInverter.calculatePhasePowersFromVoltageAndCurrent(this);

		// Update the the SoC, limited by Charge and Discharge Power Boundaries
		HyperBattery.activateSocUpdate(this);

		HyperCube.activateAllowedPowerHandler(this, this.getComponentManager(), powerHandler);
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
	public void buildJsonApiRoutes(JsonApiBuilder builder) {
		builder.handleRequest(new ClearTimeoutFailure(), endpoint -> {
			endpoint.setGuards(EdgeGuards.roleIsAtleast(Role.ADMIN));
		}, call -> {
			this.executeErrorAcknowledge();
			return EmptyObject.INSTANCE;
		});
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE:
			this.handleStateMachine();
			break;
		}
	}

	protected void handleStateMachine() {
		var clock = this.componentManager.getClock();

		// Store the current State
		this._setStateMachine(this.stateMachine.getCurrentState());

		// Initialize 'Start-Stop' Channel
		this._setStartStop(StartStop.UNDEFINED);

		this.handleHeartbeat(clock);

		// Calculate the Energy values from DC Discharge Power.
		this.calculateDcEnergy();

		// Prepare Context
		var context = new Context(this, this.config, clock);

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
		if (this.startStopTarget.get() == StartStop.START) {
			int heartbeat = heartbeatValue == 0 ? 1 : 0;
			try {
				IntegerWriteChannel heartbeatChannel = this.channel(HyperCube.ChannelId.HEARTBEAT);
				heartbeatChannel.setNextWriteValue(heartbeat);
				heartbeatTimeout.start(clock);
				heartbeatValue = heartbeat;

			} catch (IllegalArgumentException | OpenemsNamedException e) {
				this.logError(this.log, "Setting Heartbeat failed: " + e.getMessage());
				e.printStackTrace();
			}
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

	private void calculateDcEnergy() {
		var activePower = this.getActivePowerChannel().getNextValue().get();
		if (activePower == null) {
			// Not available
			this.calculateChargeEnergy.update(null);
			this.calculateDischargeEnergy.update(null);
		} else if (activePower > 0) {
			// Discharge
			this.calculateChargeEnergy.update(0);
			this.calculateDischargeEnergy.update(activePower);
		} else {
			// Charge
			this.calculateChargeEnergy.update(activePower * -1);
			this.calculateDischargeEnergy.update(0);
		}
	}

	@Override
	public void setStartStop(StartStop value) {
		if (this.startStopTarget.getAndSet(value) != value) {
			this.stateMachine.forceNextState(State.UNDEFINED);
		}
	}

	@Override
	public StartStop getStartStopTarget() {
		return switch (this.config.startStop()) {
			case AUTO -> this.startStopTarget.get(); // read StartStop-Channel
			case START -> StartStop.START; // force START
			case STOP -> StartStop.STOP; // force STOP
		};
	}

	/**
	 * Retrieves StaticConstraints from {@link SymmetricBatteryInverter}.
	 *
	 * {@inheritDoc}
	 */
	@Override
	public Constraint[] getStaticConstraints() throws OpenemsNamedException {
		var constaints = new ArrayList<Constraint>();

		if (this.config.readOnly()) {
			constaints.add(this.createPowerConstraint("Read-Only Mode - Active Power", ALL, ACTIVE, EQUALS, 0));
			constaints.add(this.createPowerConstraint("Read-Only Mode - Reactive Power", ALL, REACTIVE, EQUALS, 0));
		}
		else if (!this.isStarted()) {
			constaints.add(this.createPowerConstraint("ESS not Started - Active Power", ALL, ACTIVE, EQUALS, 0));
			constaints.add(this.createPowerConstraint("ESS not Started - Reactive Power", ALL, REACTIVE, EQUALS, 0));
		}
		else {
			var model = config.model();
			var maxActivePower = model.getMaxDischargePower();
			var minActivePower = -model.getMaxChargePower();
			var maxReactivePower = maxActivePower * HyperCube.REACTIVE_POWER_FACTOR;
			var minReactivePower = minActivePower * HyperCube.REACTIVE_POWER_FACTOR;

			constaints.add(this.createPowerConstraint("Maximum Active Power", ALL, ACTIVE, LESS_OR_EQUALS, maxActivePower));
			constaints.add(this.createPowerConstraint("Minimum Active Power", ALL, ACTIVE, GREATER_OR_EQUALS, minActivePower));
			constaints.add(this.createPowerConstraint("Maximum Reactive Power", ALL, REACTIVE, LESS_OR_EQUALS, maxReactivePower));
			constaints.add(this.createPowerConstraint("Minimum Reactive Power", ALL, REACTIVE, GREATER_OR_EQUALS, minReactivePower));
		}
		return constaints.toArray(new Constraint[constaints.size()]);
	}

	protected ComponentManager getComponentManager() {
		return this.componentManager;
	}

	@Override
	public int getCycleTime() {
		return this.cycle != null ? this.cycle.getCycleTime() : DEFAULT_CYCLE_TIME;
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	public HyperCubeModel getModel() {
		return this.config.model();
	}

	/**
	 * Retrieves PowerPrecision from {@link SymmetricBatteryInverter}.
	 *
	 * {@inheritDoc}
	 */
	@Override
	public int getPowerPrecision() {
		return APPARENT_POWER_PRECISION;
	}

	@Override
	public Power getPower() {
		return this.power;
	}

	/**
	 * Forwards the power request to the {@link SymmetricBatteryInverter}.
	 *
	 * {@inheritDoc}
	 */
	@Override
	public void applyPower(int activePower, int reactivePower) throws OpenemsNamedException {
		IntegerWriteChannel setActivePowerChannel = this.channel(HyperCube.ChannelId.SET_ACTIVE_POWER);
		setActivePowerChannel.setNextWriteValue(activePower);
		IntegerWriteChannel setReactivePowerChannel = this.channel(HyperCube.ChannelId.SET_REACTIVE_POWER);
		setReactivePowerChannel.setNextWriteValue(reactivePower);
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
						m(HyperCube.ChannelId.WORK_STATE, new UnsignedWordElement(303)),
						new DummyRegisterElement(304, 314),
						m(HyperCube.ChannelId.SET_ACTIVE_POWER,
								new SignedWordElement(315), SCALE_FACTOR_3),
						m(HyperCube.ChannelId.SET_REACTIVE_POWER,
								new SignedWordElement(316), SCALE_FACTOR_3)),

				new FC4ReadInputRegistersTask(101, Priority.LOW,
						m(HyperCube.ChannelId.DEVICE_MODE, new UnsignedWordElement(101)),
						m(HyperCube.ChannelId.OPERATION_STATE, new UnsignedWordElement(102)),
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
								.bit(4, HyperCube.AlarmChannelId.COOLING_SYSTEM_WARNING)
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
								.bit(0, HyperCube.AlarmChannelId.SUBSYSTEM_HIGH_VOLTAGE_WARNING)
								.bit(1, HyperCube.AlarmChannelId.SUBSYSTEM_LOW_VOLTAGE_WARNING)
								.bit(14, HyperCube.AlarmChannelId.COOLING_SYSTEM_FAULT)
						),
						new DummyRegisterElement(127),
						m(new BitsWordElement(128, this)
								.bit(0, HyperCube.AlarmChannelId.QS_FUSE_FAULT)
								.bit(3, HyperCube.AlarmChannelId.QF_TRIP_FAULT)
								.bit(4, HyperCube.AlarmChannelId.COOLING_SYSTEM_ALARM)
						)),

				new FC4ReadInputRegistersTask(3001, Priority.HIGH,
						m(HyperInverter.ChannelId.VOLTAGE_L1_L2,
								new SignedWordElement(3001), SCALE_FACTOR_2),
						m(HyperInverter.ChannelId.VOLTAGE_L2_L3,
								new SignedWordElement(3002), SCALE_FACTOR_2),
						m(HyperInverter.ChannelId.VOLTAGE_L3_L1,
								new SignedWordElement(3003), SCALE_FACTOR_2),
						m(HyperInverter.ChannelId.CURRENT_L1,
								new SignedWordElement(3004), SCALE_FACTOR_2),
						m(HyperInverter.ChannelId.CURRENT_L2,
								new SignedWordElement(3005), SCALE_FACTOR_2),
						m(HyperInverter.ChannelId.CURRENT_L3,
								new SignedWordElement(3006), SCALE_FACTOR_2),
						m(HyperInverter.ChannelId.FREQUENCY,
								new SignedWordElement(3007), SCALE_FACTOR_2),
						m(SymmetricEss.ChannelId.ACTIVE_POWER,
								new SignedWordElement(3008), SCALE_FACTOR_2),
						m(SymmetricEss.ChannelId.REACTIVE_POWER,
								new SignedWordElement(3009), SCALE_FACTOR_2),
						m(HyperInverter.ChannelId.POWER_FACTOR,
								new SignedWordElement(3010),
                                chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_3)),
						m(HyperInverter.ChannelId.DC_VOLTAGE,
								new SignedWordElement(3011), SCALE_FACTOR_2),
						m(HyperInverter.ChannelId.DC_CURRENT,
								new SignedWordElement(3012), SCALE_FACTOR_2),
						m(HyperInverter.ChannelId.DC_POWER,
								new SignedWordElement(3013), SCALE_FACTOR_2)),

				new FC4ReadInputRegistersTask(3014, Priority.LOW,
						m(HyperInverter.ChannelId.MODULE_TEMPERATURE, new SignedWordElement(3014)),
						m(HyperInverter.ChannelId.AIR_TEMPERATURE, new SignedWordElement(3015)),
						m(HyperInverter.ChannelId.IGBT_L1_TEMPERATURE, new SignedWordElement(3016)),
						m(HyperInverter.ChannelId.IGBT_L2_TEMPERATURE, new SignedWordElement(3017)),
						m(HyperInverter.ChannelId.IGBT_L3_TEMPERATURE, new SignedWordElement(3018)),
						new DummyRegisterElement(3019, 3038),
						m(new BitsWordElement(3039, this)
								.bit(0, HyperInverter.ChannelId.INVERTER_COMMUNICATION_ABNORMAL)
								.bit(1, HyperInverter.ChannelId.INVERTER_COMMUNICATION_CONNECTED)
								.bit(2, HyperInverter.ChannelId.INVERTER_COMMUNICATION_ENABLED)
								.bit(3, HyperInverter.ChannelId.INVERTER_COMMUNICATION_FAULT)
						)),

				new FC4ReadInputRegistersTask(10001, Priority.HIGH,
						m(HyperBattery.ChannelId.BATTERY_VOLTAGE,
								new SignedWordElement(10001), SCALE_FACTOR_2),
						m(HyperBattery.ChannelId.BATTERY_CURRENT,
								new SignedWordElement(10002), SCALE_FACTOR_2),
						m(HyperBattery.ChannelId.BATTERY_POWER,
								new SignedWordElement(10003), SCALE_FACTOR_2),
						m(HyperBattery.ChannelId.BATTERY_SOC,
								new UnsignedWordElement(10004),
                                chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_1)),
						m(HyperBattery.ChannelId.SOE,
								new UnsignedWordElement(10005),
                                chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_1)),
						m(HyperBattery.ChannelId.SOH,
								new UnsignedWordElement(10006),
                                chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_1)),
						m(HyperBattery.ChannelId.BATTERY_CHARGE_MAX_POWER,
								new SignedWordElement(10007), SCALE_FACTOR_2),
						m(HyperBattery.ChannelId.RACK_DISCHARGE_MAX_POWER,
								new SignedWordElement(10008), SCALE_FACTOR_2)),

				new FC4ReadInputRegistersTask(10009, Priority.LOW,
						m(HyperBattery.ChannelId.BATTERY_RELAY_STATUS,
								new UnsignedWordElement(10009)),
						m(SymmetricEss.ChannelId.MAX_CELL_VOLTAGE,
								new UnsignedWordElement(10010)),
						m(SymmetricEss.ChannelId.MIN_CELL_VOLTAGE,
								new UnsignedWordElement(10011)),
						m(SymmetricEss.ChannelId.MAX_CELL_TEMPERATURE,
								new SignedWordElement(10012)),
						m(SymmetricEss.ChannelId.MIN_CELL_TEMPERATURE,
								new SignedWordElement(10013)),
						m(HyperBattery.ChannelId.MAX_CELL_VOLTAGE_INDEX,
								new UnsignedWordElement(10014)),
						m(HyperBattery.ChannelId.MIN_CELL_VOLTAGE_INDEX,
								new UnsignedWordElement(10015)),
						m(HyperBattery.ChannelId.MAX_CELL_TEMPERATURE_INDEX,
								new UnsignedWordElement(10016)),
						m(HyperBattery.ChannelId.MIN_CELL_TEMPERATURE_INDEX,
								new UnsignedWordElement(10017)),
						m(new BitsWordElement(10018, this)
								.bit(0, HyperBattery.AlarmChannelId.HIGH_CELL_VOLTAGE_WARNING)
								.bit(1, HyperBattery.AlarmChannelId.HIGH_CELL_VOLTAGE_FAULT)
								.bit(2, HyperBattery.AlarmChannelId.LOW_CELL_VOLTAGE_WARNING)
								.bit(3, HyperBattery.AlarmChannelId.LOW_CELL_VOLTAGE_FAULT)
								.bit(4, HyperBattery.AlarmChannelId.IMBALANCE_CELL_VOLTAGE_WARNING)
								.bit(5, HyperBattery.AlarmChannelId.IMBALANCE_CELL_VOLTAGE_FAULT)
								.bit(12, HyperBattery.AlarmChannelId.HIGH_PACK_VOLTAGE_WARNING)
								.bit(13, HyperBattery.AlarmChannelId.HIGH_PACK_VOLTAGE_FAULT)
								.bit(14, HyperBattery.AlarmChannelId.LOW_PACK_VOLTAGE_WARNING)
								.bit(15, HyperBattery.AlarmChannelId.LOW_PACK_VOLTAGE_FAULT)
						),
						new DummyRegisterElement(10019),
						m(new BitsWordElement(10020, this)
								.bit(0, HyperBattery.AlarmChannelId.EXCESSIVE_DISCHARGE_CURRENT_WARNING)
								.bit(2, HyperBattery.AlarmChannelId.EXCESSIVE_CHARGE_CURRENT_WARNING)
								.bit(4, HyperBattery.AlarmChannelId.EXTREME_HIGH_TEMPERATURE_WARNING)
								.bit(5, HyperBattery.AlarmChannelId.EXTREME_HIGH_TEMPERATURE_FAULT)
								.bit(6, HyperBattery.AlarmChannelId.EXTREME_LOW_TEMPERATURE_WARNING)
								.bit(7, HyperBattery.AlarmChannelId.EXTREME_LOW_TEMPERATURE_FAULT)
								.bit(8, HyperBattery.AlarmChannelId.EXCESSIVE_TEMPERATURE_DIFFERENTIAL_WARNING)
								.bit(9, HyperBattery.AlarmChannelId.EXCESSIVE_TEMPERATURE_DIFFERENTIAL_FAULT)
								.bit(10, HyperBattery.AlarmChannelId.RAPID_TEMPERATURE_RISE_WARNING)
								.bit(11, HyperBattery.AlarmChannelId.RAPID_TEMPERATURE_RISE_FAULT)
						),
						new DummyRegisterElement(10021),
						m(new BitsWordElement(10022, this)
								.bit(4, HyperBattery.AlarmChannelId.HIGH_SOC_WARNING)
								.bit(5, HyperBattery.AlarmChannelId.HIGH_SOC_FAULT)
								.bit(6, HyperBattery.AlarmChannelId.LOW_SOC_WARNING)
								.bit(7, HyperBattery.AlarmChannelId.LOW_SOC_FAULT)
								.bit(14, HyperBattery.AlarmChannelId.EXCESSIVE_BATTERY_VOLTAGE_DIFFERENTIAL_WARNING)
								.bit(15, HyperBattery.AlarmChannelId.EXCESSIVE_BATTERY_VOLTAGE_DIFFERENTIAL_FAULT)
						),
						new DummyRegisterElement(10023),
						m(new BitsWordElement(10024, this)
								.bit(7, HyperBattery.AlarmChannelId.BCMS_COMMUNICATION_WARNING)
								.bit(8, HyperBattery.AlarmChannelId.BAMS_COMMUNICATION_WARNING)
								.bit(12, HyperBattery.AlarmChannelId.EXTREME_HIGH_VOLTAGE_WARNING)
								.bit(13, HyperBattery.AlarmChannelId.EXTREME_LOW_VOLTAGE_WARNING)
						),
						new DummyRegisterElement(10025),
						m(new BitsWordElement(10026, this)
								.bit(2, HyperBattery.AlarmChannelId.HIGH_CONTACTOR_TEMPERATURE_WARNING)
								.bit(3, HyperBattery.AlarmChannelId.HIGH_CONTACTOR_TEMPERATURE_FAULT)
								.bit(4, HyperBattery.AlarmChannelId.HIGH_POWER_MODULE_TEMPERATURE_WARNING)
								.bit(5, HyperBattery.AlarmChannelId.HIGH_POWER_MODULE_TEMPERATURE_FAULT)
								.bit(6, HyperBattery.AlarmChannelId.HIGH_BUSBAR_TEMPERATURE_WARNING)
								.bit(7, HyperBattery.AlarmChannelId.HIGH_BUSBAR_TEMPERATURE_FAULT)
								.bit(8, HyperBattery.AlarmChannelId.HIGH_CONNECTOR_TEMPERATURE_WARNING)
								.bit(9, HyperBattery.AlarmChannelId.HIGH_CONNECTOR_TEMPERATURE_FAULT)
						),
						new DummyRegisterElement(10027),
						m(new BitsWordElement(10028, this)
								.bit(0, HyperBattery.AlarmChannelId.INSULATION_MODULE_COMMUNICATION_FAULT)
								.bit(1, HyperBattery.AlarmChannelId.PARAMETER_CONFIGURATION_FAULT)
								.bit(2, HyperBattery.AlarmChannelId.HALL_SENSOR_OPEN_CIRCUIT_FAULT)
								.bit(3, HyperBattery.AlarmChannelId.TEMPERATURE_SENSOR_OPEN_CIRCUIT_FAULT)
								.bit(4, HyperBattery.AlarmChannelId.TEMPERATURE_SENSOR_SHORT_CIRCUIT_FAULT)
								.bit(5, HyperBattery.AlarmChannelId.MAIN_POSITIVE_CONTACTOR_WARNING)
								.bit(9, HyperBattery.AlarmChannelId.FIRE_DETECTOR_COMMUNICATION_TIMEOUT)
								.bit(10, HyperBattery.AlarmChannelId.COOLING_SYSTEM_COMMUNICATION_TIMEOUT)
								.bit(11, HyperBattery.AlarmChannelId.AEROSOL_SIGNAL_DISCONNECT_FAULT)
								.bit(12, HyperBattery.AlarmChannelId.HIGH_VOLTAGE_CALIBRATION_FAULT)
								.bit(13, HyperBattery.AlarmChannelId.CURRENT_CALIBRATION_FAULT)
								.bit(14, HyperBattery.AlarmChannelId.FIRE_DETECTOR_DISCONNECT_FAULT)
								.bit(15, HyperBattery.AlarmChannelId.MSD_DISCONNECT_WARNING)
						),
						new DummyRegisterElement(10029),
						m(new BitsWordElement(10030, this)
								.bit(0, HyperBattery.AlarmChannelId.FIRE_DETECTOR_1_FAULT)
								.bit(2, HyperBattery.AlarmChannelId.FIRE_DETECTOR_2_FAULT)
								.bit(4, HyperBattery.AlarmChannelId.FIRE_DETECTOR_3_FAULT)
								.bit(6, HyperBattery.AlarmChannelId.FIRE_DETECTOR_4_FAULT)
								.bit(8, HyperBattery.AlarmChannelId.FIRE_DETECTOR_5_FAULT)
								.bit(10, HyperBattery.AlarmChannelId.FIRE_DETECTOR_6_FAULT)
						),
						new DummyRegisterElement(10031),
						m(new BitsWordElement(10032, this)
								.bit(0, HyperBattery.AlarmChannelId.IMBALANCE_CELL_CHARGE_VOLTAGE_WARNING)
								.bit(1, HyperBattery.AlarmChannelId.IMBALANCE_CELL_CHARGE_VOLTAGE_FAULT)
								.bit(2, HyperBattery.AlarmChannelId.IMBALANCE_CELL_DISCHARGE_VOLTAGE_WARNING)
								.bit(3, HyperBattery.AlarmChannelId.IMBALANCE_CELL_DISCHARGE_VOLTAGE_FAULT)
								//.bit(4, HyperBattery.AlarmChannelId.HIGH_PACK_VOLTAGE_WARNING)
								//.bit(5, HyperBattery.AlarmChannelId.HIGH_PACK_VOLTAGE_FAULT)
								//.bit(6, HyperBattery.AlarmChannelId.LOW_PACK_VOLTAGE_WARNING)
								//.bit(7, HyperBattery.AlarmChannelId.LOW_PACK_VOLTAGE_FAULT)
								.bit(8, HyperBattery.AlarmChannelId.COOLING_SYSTEM_MANAGEMENT_WARNING)
								.bit(9, HyperBattery.AlarmChannelId.COOLING_SYSTEM_MANAGEMENT_FAULT)
						)),

				//defineModbusCellAnalyticsTask(10057),
				defineModbusConnectorAnalyticsTask(10772),

				new FC4ReadInputRegistersTask(10778, Priority.LOW,
						m(HyperBattery.ChannelId.INSULATION_RESISTANCE,
								new UnsignedWordElement(10778)),
						m(HyperBattery.ChannelId.PRECHARGE_VOLTAGE,
								new SignedWordElement(10779), SCALE_FACTOR_2),
						m(new BitsWordElement(10780, this)
								.bit(0, HyperBattery.ChannelId.BATTERY_COMMUNICATION_ABNORMAL)
								.bit(1, HyperBattery.ChannelId.BATTERY_COMMUNICATION_CONNECTED)
								.bit(2, HyperBattery.ChannelId.BATTERY_COMMUNICATION_ENABLED)
								.bit(3, HyperBattery.ChannelId.BATTERY_COMMUNICATION_FAULT)
						)),

				defineModbusBusBarAnalyticsTask(10781),

				new FC4ReadInputRegistersTask(50001, Priority.LOW,
						m(HyperCube.ChannelId.COOLING_SYSTEM_MODE,
								new UnsignedWordElement(50001)),
						m(HyperCube.ChannelId.COOLING_SYSTEM_RETURN_TEMPERATURE,
								new SignedWordElement(50002)),
						m(HyperCube.ChannelId.COOLING_SYSTEM_SUPPLY_TEMPERATURE,
								new SignedWordElement(50003)),
						m(new BitsWordElement(50004, this)
								.bit(0, HyperCube.ChannelId.COOLING_SYSTEM_COMMUNICATION_ABNORMAL)
								.bit(1, HyperCube.ChannelId.COOLING_SYSTEM_COMMUNICATION_CONNECTED)
								.bit(2, HyperCube.ChannelId.COOLING_SYSTEM_COMMUNICATION_ENABLED)
								.bit(3, HyperCube.ChannelId.COOLING_SYSTEM_COMMUNICATION_FAULT)
						),
						m(new BitsWordElement(50005, this)
								.bit(0, HyperCube.ChannelId.COOLING_SYSTEM_MAIN_CONTACTOR_STATUS)
								.bit(1, HyperCube.ChannelId.COOLING_SYSTEM_COMPRESSOR_STATUS)
								.bit(2, HyperCube.ChannelId.COOLING_SYSTEM_HEATING_STATUS)
						)),

				new FC6WriteRegisterTask(1, 
						m(HyperCube.ChannelId.HEARTBEAT, new UnsignedWordElement(1))),

				new FC6WriteRegisterTask(302, 
						m(SymmetricEss.ChannelId.GRID_MODE, new UnsignedWordElement(302))),
				new FC6WriteRegisterTask(303, 
						m(HyperCube.ChannelId.WORK_STATE, new UnsignedWordElement(303))),

				new FC16WriteRegistersTask(315,
						m(HyperCube.ChannelId.SET_ACTIVE_POWER,
								new SignedWordElement(315), SCALE_FACTOR_3),
						m(HyperCube.ChannelId.SET_REACTIVE_POWER,
								new SignedWordElement(316), SCALE_FACTOR_3)));
	}
	
//	private Task defineModbusCellAnalyticsTask(int startAddress, HyperCubeModel model) {
//		
//	}

	private Task defineModbusConnectorAnalyticsTask(int startAddress) { //, HyperCubeModel model) {
		List<ModbusElement> elements = new ArrayList<ModbusElement>(Arrays.asList(
				m(HyperBattery.ChannelId.MAX_CONNECTOR_TEMPERATURE,
						new SignedWordElement(10772)),
				m(HyperBattery.ChannelId.MIN_CONNECTOR_TEMPERATURE,
						new SignedWordElement(10773)),
				m(HyperBattery.ChannelId.MAX_CONNECTOR_TEMPERATURE_INDEX,
						new UnsignedWordElement(10774)),
				m(HyperBattery.ChannelId.MIN_CONNECTOR_TEMPERATURE_INDEX,
						new UnsignedWordElement(10775))
		));
		return new FC4ReadInputRegistersTask(10772, Priority.LOW, 
				elements.toArray(size -> new ModbusElement[size]));
	}

	private Task defineModbusBusBarAnalyticsTask(int startAddress) { //, HyperCubeModel model) {
		List<ModbusElement> elements = new ArrayList<ModbusElement>(Arrays.asList(
				m(HyperBattery.ChannelId.MAX_BUSBAR_TEMPERATURE,
						new SignedWordElement(10881)),
				m(HyperBattery.ChannelId.MIN_BUSBAR_TEMPERATURE,
						new SignedWordElement(10882)),
				m(HyperBattery.ChannelId.MAX_BUSBAR_TEMPERATURE_INDEX,
						new UnsignedWordElement(10883)),
				m(HyperBattery.ChannelId.MIN_BUSBAR_TEMPERATURE_INDEX,
						new UnsignedWordElement(10884))
		));
		return new FC4ReadInputRegistersTask(10881, Priority.LOW, 
				elements.toArray(size -> new ModbusElement[size]));
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(
				OpenemsComponent.getModbusSlaveNatureTable(accessMode),
				SymmetricEss.getModbusSlaveNatureTable(accessMode),
				ManagedSymmetricEss.getModbusSlaveNatureTable(accessMode),
				ModbusSlaveNatureTable.of(HyperCube.class, accessMode, 100)
						.build()
		);
	}

	private static final ElementToChannelConverter CONVERT_FLOAT = new ElementToChannelConverter(v -> {
		if (v == null) {
			return null;
		}
		if (v instanceof Number n) {
			return n.floatValue();
		}
		if (v instanceof String s) {
			return Float.valueOf(s);
		}
		throw new IllegalArgumentException(
			"Type [" + v.getClass().getName() + "] not supported by float converter");
	});

	@Override
	public String debugLog() {
		var builder = new StringBuilder(this.stateMachine.debugLog());

		builder.append("|SoC:").append(this.getSoc().asString())
				.append("|L:").append(this.getActivePower().asString());

		// Show max AC export/import active power:
		// Minimum of MaxAllowedCharge/DischargePower and MaxApparentPower
		builder.append("|Allowed:")
				.append(TypeUtils.max(
						this.getAllowedChargePower().get(), TypeUtils.multiply(this.getMaxApparentPower().get(), -1)))
				.append(";")
				.append(TypeUtils.min(
						this.getAllowedDischargePower().get(), this.getMaxApparentPower().get()));

		builder.append("|").append(this.getGridModeChannel().value().asOptionString());
		
		return builder.toString();
	}

	@Override
	public String toString() {
		return toStringHelper(this)
				.addValue(this.id())
				.toString();
	}

}
