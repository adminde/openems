package io.openems.edge.ess.hyperstrong.hypercube;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.chain;
import static io.openems.edge.common.cycle.Cycle.DEFAULT_CYCLE_TIME;
import static io.openems.edge.common.type.Phase.SingleOrAllPhase.ALL;
import static io.openems.edge.ess.hyperstrong.AlarmAnalysis.convertAlarm;
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

import io.openems.common.channel.AccessMode;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import io.openems.edge.ess.hyperstrong.thermal.ThermalManagementSystem;
import io.openems.edge.ess.hyperstrong.jsonrpc.ClearTimeoutFailure;
import io.openems.edge.ess.hyperstrong.statemachine.Context;
import io.openems.edge.ess.hyperstrong.statemachine.StateMachine;
import io.openems.edge.ess.hyperstrong.statemachine.StateMachine.State;
import io.openems.edge.ess.power.api.Constraint;
import io.openems.edge.ess.power.api.Power;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

public abstract class AbstractHyperCube extends AbstractOpenemsModbusComponent implements HyperCube,
		ManagedSymmetricEss, SymmetricEss, EssErrorAcknowledge, ThermalManagementSystem,
		OpenemsComponent, ModbusComponent, ModbusSlave, ComponentJsonApi,
		CycleProvider, TimedataProvider, EventHandler, StartStoppable {

	protected final Logger log = LoggerFactory.getLogger(AbstractHyperCube.class);
	protected final StateMachine stateMachine = new StateMachine(State.UNDEFINED);

	protected final AtomicReference<StartStop> startStopTarget = new AtomicReference<>(StartStop.UNDEFINED);

	protected final CalculateEnergyFromPower calculateChargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricEss.ChannelId.ACTIVE_CHARGE_ENERGY);
	protected final CalculateEnergyFromPower calculateDischargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricEss.ChannelId.ACTIVE_DISCHARGE_ENERGY);

	protected final Timeout heartbeatTimeout = Timeout.ofSeconds((int) Math.ceil(HyperCube.MIN_HEARTBEAT_CYCLE));
	protected volatile int heartbeatValue = 0;

	protected AllowedPowerHandler powerHandler;

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

	public AbstractHyperCube() {
		super(
				OpenemsComponent.ChannelId.values(),
				ModbusComponent.ChannelId.values(),
				StartStoppable.ChannelId.values(),
				ThermalManagementSystem.ChannelId.values(),
				EssErrorAcknowledge.ChannelId.values(),
				SymmetricEss.ChannelId.values(),
				ManagedSymmetricEss.ChannelId.values(),
				HyperInverter.ChannelId.values(),
				HyperInverter.AlarmChannelId.values(),
				HyperBattery.ChannelId.values(),
				HyperBattery.AlarmChannelId.values(),
				HyperCube.ChannelId.values(),
				HyperCube.AlarmChannelId.values()
		);
	}

	protected void activate(ComponentContext context, String id, String alias, boolean enabled,
				int unitId, String modbusReference, String modbusId) throws OpenemsException {
		if (super.activate(context, id, alias, enabled, unitId, this.cm, modbusReference, modbusId)) {
			return;
		}
		var model = this.getModel();
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

	protected abstract boolean isReadOnly();

	public abstract HyperCubeModel getModel();

	/**
	 * Retrieves StaticConstraints from {@link ManagedSymmetricEss}.
	 *
	 * {@inheritDoc}
	 */
	@Override
	public Constraint[] getStaticConstraints() throws OpenemsNamedException {
		var constraints = new ArrayList<Constraint>();

		if (this.isReadOnly()) {
			constraints.add(this.createPowerConstraint("Read-Only Mode - Active Power", ALL, ACTIVE, EQUALS, 0));
			constraints.add(this.createPowerConstraint("Read-Only Mode - Reactive Power", ALL, REACTIVE, EQUALS, 0));
		}
		else if (!this.isStarted()) {
			constraints.add(this.createPowerConstraint("ESS not Started - Active Power", ALL, ACTIVE, EQUALS, 0));
			constraints.add(this.createPowerConstraint("ESS not Started - Reactive Power", ALL, REACTIVE, EQUALS, 0));
		}
		else {
			var model = this.getModel();
			var maxActivePower = model.getMaxDischargePower();
			var minActivePower = -model.getMaxChargePower();
			var maxReactivePower = maxActivePower * HyperCube.REACTIVE_POWER_FACTOR;
			var minReactivePower = minActivePower * HyperCube.REACTIVE_POWER_FACTOR;

			constraints.add(this.createPowerConstraint("Maximum Active Power", ALL, ACTIVE, LESS_OR_EQUALS, maxActivePower));
			constraints.add(this.createPowerConstraint("Minimum Active Power", ALL, ACTIVE, GREATER_OR_EQUALS, minActivePower));
			constraints.add(this.createPowerConstraint("Maximum Reactive Power", ALL, REACTIVE, LESS_OR_EQUALS, maxReactivePower));
			constraints.add(this.createPowerConstraint("Minimum Reactive Power", ALL, REACTIVE, GREATER_OR_EQUALS, minReactivePower));
		}
		return constraints.toArray(new Constraint[constraints.size()]);
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

	/**
	 * Retrieves PowerPrecision from {@link ManagedSymmetricEss}.
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
	 * Forwards the power request to the {@link ManagedSymmetricEss}.
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
						m(HyperCube.ChannelId.RUN_MODE, new UnsignedWordElement(303))),
//						new DummyRegisterElement(304, 314),
//						m(HyperCube.ChannelId.SET_ACTIVE_POWER,
//								new SignedWordElement(315), SCALE_FACTOR_3),
//						m(HyperCube.ChannelId.SET_REACTIVE_POWER,
//								new SignedWordElement(316), SCALE_FACTOR_3)),

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
								.bit(14, HyperCube.AlarmChannelId.COOLING_SYSTEM_FAULT)
						),
						new DummyRegisterElement(129),
						m(new BitsWordElement(130, this)
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
						m(new BitsWordElement(3019, this)
								.bit(0, HyperInverter.AlarmChannelId.LOW_AC_VOLTAGE_FAULT)
								.bit(1, HyperInverter.AlarmChannelId.HIGH_AC_VOLTAGE_FAULT)
								.bit(2, HyperInverter.AlarmChannelId.LOW_FREQUENCY_FAULT)
								.bit(3, HyperInverter.AlarmChannelId.HIGH_FREQUENCY_FAULT)
								.bit(4, HyperInverter.AlarmChannelId.FAST_LOW_AC_VOLTAGE_FAULT)
								.bit(5, HyperInverter.AlarmChannelId.FAST_HIGH_AC_VOLTAGE_FAULT)
								.bit(8, HyperInverter.AlarmChannelId.PHASE_REVERSAL_FAULT)
								.bit(9, HyperInverter.AlarmChannelId.PHASE_LOSS_FAULT)
								.bit(10, HyperInverter.AlarmChannelId.OUTPUT_VOLTAGE_FAULT)
								.bit(11, HyperInverter.AlarmChannelId.OFF_GRID_STARTUP_BLOCKED_FAULT)
								.bit(12, HyperInverter.AlarmChannelId.ISLAND_PROTECTION_FAULT)
								.bit(13, HyperInverter.AlarmChannelId.AC_SHORT_CIRCUIT_FAULT)
								.bit(14, HyperInverter.AlarmChannelId.HIGH_AC_CURRENT_FAULT)
						),
						m(new BitsWordElement(3020, this)
								.bit(4, HyperInverter.AlarmChannelId.PARALLEL_OVERLOAD_TIMEOUT_FAULT)
								.bit(5, HyperInverter.AlarmChannelId.OUTPUT_OVERLOAD_TIMEOUT_FAULT)
								.bit(6, HyperInverter.AlarmChannelId.AC_POWER_ANOMALY_FAULT)
								.bit(8, HyperInverter.AlarmChannelId.VOLTAGE_L1_L2_FAULT)
								.bit(9, HyperInverter.AlarmChannelId.VOLTAGE_L2_L3_FAULT)
								.bit(10, HyperInverter.AlarmChannelId.VOLTAGE_L3_L1_FAULT)
								.bit(11, HyperInverter.AlarmChannelId.DC_SOFT_START_FAULT)
								.bit(12, HyperInverter.AlarmChannelId.DC_RELAY_CLOSE_FAULT)
								.bit(13, HyperInverter.AlarmChannelId.INDUCTOR_CURRENT_BALANCE_L1_FAULT)
								.bit(14, HyperInverter.AlarmChannelId.INDUCTOR_CURRENT_BALANCE_L2_FAULT)
								.bit(15, HyperInverter.AlarmChannelId.INDUCTOR_CURRENT_BALANCE_L3_FAULT)
						),
						m(new BitsWordElement(3021, this)
								.bit(6, HyperInverter.AlarmChannelId.BAMS_CURRENT_LIMIT_SHUTDOWN_FAULT)
								.bit(7, HyperInverter.AlarmChannelId.BAMS_POWER_LIMIT_SHUTDOWN_FAULT)
								.bit(8, HyperInverter.AlarmChannelId.BCMS_NO_CHARGE_SHUTDOWN_FAULT)
								.bit(9, HyperInverter.AlarmChannelId.BCMS_DISABLE_SHUTDOWN_FAULT)
								.bit(10, HyperInverter.AlarmChannelId.BAMS_CHARGE_DISABLED_SHUTDOWN_FAULT)
								.bit(11, HyperInverter.AlarmChannelId.BAMS_SHUTDOWN_FAULT)
								.bit(12, HyperInverter.AlarmChannelId.BCMS_CURRENT_LIMIT_SHUTDOWN_FAULT)
								.bit(13, HyperInverter.AlarmChannelId.BCMS_POWER_LIMIT_SHUTDOWN_FAULT)
								.bit(14, HyperInverter.AlarmChannelId.BATTERY_VOLTAGE_LIMIT_SHUTDOWN_FAULT)
								.bit(15, HyperInverter.AlarmChannelId.BATTERY_CURRENT_LIMIT_SHUTDOWN_FAULT)
						),
						m(new BitsWordElement(3022, this)
								.bit(0, HyperInverter.AlarmChannelId.LOW_BATTERY_VOLTAGE_FAULT)
								.bit(1, HyperInverter.AlarmChannelId.HIGH_BATTERY_VOLTAGE_FAULT)
								.bit(2, HyperInverter.AlarmChannelId.REVERSE_BATTERY_POLARITY_FAULT)
								.bit(3, HyperInverter.AlarmChannelId.HIGH_BATTERY_CURRENT_FAULT)
								.bit(4, HyperInverter.AlarmChannelId.INSULATION_FAULT)
								.bit(5, HyperInverter.AlarmChannelId.INSULATION_BATTERY_VOLTAGE_FAULT)
								.bit(7, HyperInverter.AlarmChannelId.LOW_POSITIVE_BUS_VOLTAGE_FAULT)
								.bit(8, HyperInverter.AlarmChannelId.LOW_NEGATIVE_BUS_VOLTAGE_FAULT)
								.bit(9, HyperInverter.AlarmChannelId.HIGH_DC_BUS1_VOLTAGE_FAULT)
								.bit(10, HyperInverter.AlarmChannelId.HIGH_DC_BUS2_VOLTAGE_FAULT)
								.bit(11, HyperInverter.AlarmChannelId.HIGH_DC_BUS3_VOLTAGE_FAULT)
								.bit(12, HyperInverter.AlarmChannelId.HIGH_DC_BUS4_VOLTAGE_FAULT)
								.bit(13, HyperInverter.AlarmChannelId.DC_BUS1_2_VOLTAGE_IMBALANCE_FAULT)
								.bit(14, HyperInverter.AlarmChannelId.DC_BUS3_4_VOLTAGE_IMBALANCE_FAULT)
						),
						new DummyRegisterElement(3023),
						m(new BitsWordElement(3024, this)
								.bit(0, HyperInverter.AlarmChannelId.DC_SURGE_ARRESTER_WARNING)
								.bit(1, HyperInverter.AlarmChannelId.AC_SURGE_ARRESTER_WARNING)
								.bit(10, HyperInverter.AlarmChannelId.DC_RELAY_OPEN_CIRCUIT_FAULT)
								.bit(11, HyperInverter.AlarmChannelId.DC_RELAY_SHORT_CIRCUIT_FAULT)
								.bit(13, HyperInverter.AlarmChannelId.POWER_SUPPLY_15V_FAULT)
								.bit(14, HyperInverter.AlarmChannelId.POWER_SUPPLY_24V_FAULT)
						),
						new DummyRegisterElement(3025),
						m(new BitsWordElement(3026, this)
								.bit(4, HyperInverter.AlarmChannelId.DC_BUS1_SHORT_CIRCUIT_FAULT)
								.bit(5, HyperInverter.AlarmChannelId.DC_BUS2_SHORT_CIRCUIT_FAULT)
								.bit(6, HyperInverter.AlarmChannelId.DC_BUS3_SHORT_CIRCUIT_FAULT)
								.bit(7, HyperInverter.AlarmChannelId.DC_BUS4_SHORT_CIRCUIT_FAULT)
								.bit(8, HyperInverter.AlarmChannelId.GRID_RELAY_L1_L2_SHORT_CIRCUIT_FAULT)
								.bit(9, HyperInverter.AlarmChannelId.GRID_RELAY_L2_L3_SHORT_CIRCUIT_FAULT)
								.bit(10, HyperInverter.AlarmChannelId.GRID_RELAY_L3_L1_SHORT_CIRCUIT_FAULT)
								.bit(11, HyperInverter.AlarmChannelId.GRID_RELAY_L1_L2_OPEN_CIRCUIT_FAULT)
								.bit(12, HyperInverter.AlarmChannelId.GRID_RELAY_L2_L3_OPEN_CIRCUIT_FAULT)
								.bit(13, HyperInverter.AlarmChannelId.GRID_RELAY_L3_L1_OPEN_CIRCUIT_FAULT)
						),
						new DummyRegisterElement(3027),
						m(new BitsWordElement(3028, this)
								.bit(0, HyperInverter.AlarmChannelId.BUS_VOLTAGE_IMBALANCE_FAULT)
								.bit(1, HyperInverter.AlarmChannelId.HIGH_POSITIVE_BUS_VOLTAGE_FAULT)
								.bit(2, HyperInverter.AlarmChannelId.HIGH_NEGATIVE_BUS_VOLTAGE_FAULT)
								.bit(10, HyperInverter.AlarmChannelId.LOW_EFFICIENCY_FAULT)
								.bit(11, HyperInverter.AlarmChannelId.HIGH_DC_BUS1_HARDWARE_VOLTAGE_FAULT)
								.bit(12, HyperInverter.AlarmChannelId.HIGH_DC_BUS2_HARDWARE_VOLTAGE_FAULT)
								.bit(13, HyperInverter.AlarmChannelId.HIGH_DC_BUS3_HARDWARE_VOLTAGE_FAULT)
								.bit(14, HyperInverter.AlarmChannelId.HIGH_DC_BUS4_HARDWARE_VOLTAGE_FAULT)
						),
						new DummyRegisterElement(3029),
						m(new BitsWordElement(3030, this)
								.bit(0, HyperInverter.AlarmChannelId.INVERTER_FAILURE)
								.bit(1, HyperInverter.AlarmChannelId.INVERTER_SOFT_START_COMMUNICATION_FAULT)
								.bit(8, HyperInverter.AlarmChannelId.INVERTER_COOLING_FAN_WARNING)
								.bit(9, HyperInverter.AlarmChannelId.INVERTER_IGBT_FAN_WARNING)
								.bit(12, HyperInverter.AlarmChannelId.INVERTER_VOLTAGE_L1_L2_FAULT)
								.bit(13, HyperInverter.AlarmChannelId.INVERTER_VOLTAGE_L2_L3_FAULT)
								.bit(14, HyperInverter.AlarmChannelId.INVERTER_VOLTAGE_L3_L1_FAULT)
								.bit(15, HyperInverter.AlarmChannelId.MISSING_N_LINE_FAULT)
						),
						m(new BitsWordElement(3031, this)
								.bit(0, HyperInverter.AlarmChannelId.LIMITING_N_LINE_CURRENT_WARNING)
								.bit(1, HyperInverter.AlarmChannelId.HIGH_N_LINE_CURRENT_FAULT)
								.bit(4, HyperInverter.AlarmChannelId.INDUCTOR_CURRENT_BRANCH1_L1_FAULT)
								.bit(5, HyperInverter.AlarmChannelId.INDUCTOR_CURRENT_BRANCH1_L2_FAULT)
								.bit(6, HyperInverter.AlarmChannelId.INDUCTOR_CURRENT_BRANCH1_L3_FAULT)
								.bit(7, HyperInverter.AlarmChannelId.INDUCTOR_CURRENT_BRANCH2_L1_FAULT)
								.bit(8, HyperInverter.AlarmChannelId.INDUCTOR_CURRENT_BRANCH2_L2_FAULT)
								.bit(9, HyperInverter.AlarmChannelId.INDUCTOR_CURRENT_BRANCH2_L3_FAULT)
						),
						new DummyRegisterElement(3032, 3035),
						m(new BitsWordElement(3036, this)
								.bit(0, HyperInverter.AlarmChannelId.HIGH_CABIN_TEMPERATURE_FAULT)
								.bit(1, HyperInverter.AlarmChannelId.HIGH_DISCHARGE_RESISTOR_TEMPERATURE_FAULT)
								.bit(2, HyperInverter.AlarmChannelId.HIGH_IGBT_TEMPERATURE_FAULT)
								.bit(6, HyperInverter.AlarmChannelId.CABIN_TEMPERATURE_SENSOR_WARNING)
								.bit(7, HyperInverter.AlarmChannelId.LOCAL_EPO_FAULT)
								.bit(8, HyperInverter.AlarmChannelId.HIGH_IGBT_BRANCH1_L1_TEMPERATURE_WARNING)
								.bit(9, HyperInverter.AlarmChannelId.HIGH_IGBT_BRANCH2_L1_TEMPERATURE_WARNING)
								.bit(10, HyperInverter.AlarmChannelId.HIGH_IGBT_BRANCH1_L2_TEMPERATURE_WARNING)
								.bit(11, HyperInverter.AlarmChannelId.HIGH_IGBT_BRANCH2_L2_TEMPERATURE_WARNING)
								.bit(12, HyperInverter.AlarmChannelId.HIGH_IGBT_BRANCH1_L3_TEMPERATURE_WARNING)
								.bit(13, HyperInverter.AlarmChannelId.HIGH_IGBT_BRANCH2_L3_TEMPERATURE_WARNING)
								.bit(14, HyperInverter.AlarmChannelId.REMOTE_EPO_FAULT)
								.bit(15, HyperInverter.AlarmChannelId.HIGH_SOFT_START_RESISTOR_TEMPERATURE_FAULT)
						),
						new DummyRegisterElement(3037, 3038),
						m(new BitsWordElement(3039, this)
								.bit(0, HyperInverter.ChannelId.INVERTER_COMMUNICATION_ABNORMAL)
								.bit(1, HyperInverter.ChannelId.INVERTER_COMMUNICATION_CONNECTED)
								.bit(2, HyperInverter.ChannelId.INVERTER_COMMUNICATION_ENABLED)
								.bit(3, HyperInverter.ChannelId.INVERTER_COMMUNICATION_FAULT)
						)),

				new FC4ReadInputRegistersTask(3076, Priority.LOW,
						new DummyRegisterElement(3076),
						m(new BitsWordElement(3077, this)
								.bit(0, HyperInverter.AlarmChannelId.DSP_ARM_COMMUNICATION_FAULT)
								.bit(2, HyperInverter.AlarmChannelId.CARRIER_SYNC_FAULT)
								.bit(3, HyperInverter.AlarmChannelId.POWER_FREQUENCY_SYNC_FAULT)
								.bit(4, HyperInverter.AlarmChannelId.MODULE_ID_CONFLICT_FAULT)
								.bit(7, HyperInverter.AlarmChannelId.DSP_FPGA_VERSION_MISMATCH_WARNING)
						),
						m(new BitsWordElement(3078, this)
								.bit(3, HyperInverter.AlarmChannelId.WAVE_LIMIT_BRANCH1_L1_WARNING)
								.bit(5, HyperInverter.AlarmChannelId.WAVE_LIMIT_BRANCH1_L2_WARNING)
								.bit(7, HyperInverter.AlarmChannelId.WAVE_LIMIT_BRANCH1_L3_WARNING)
								.bit(9, HyperInverter.AlarmChannelId.WAVE_LIMIT_BRANCH2_L1_WARNING)
								.bit(11, HyperInverter.AlarmChannelId.WAVE_LIMIT_BRANCH2_L2_WARNING)
								.bit(13, HyperInverter.AlarmChannelId.WAVE_LIMIT_BRANCH2_L3_WARNING)
						),
						m(new BitsWordElement(3079, this)
								.bit(0, HyperInverter.AlarmChannelId.HIGH_GRID_VOLTAGE_LEVEL1_FAULT)
								.bit(1, HyperInverter.AlarmChannelId.HIGH_GRID_VOLTAGE_LEVEL2_FAULT)
								.bit(2, HyperInverter.AlarmChannelId.HIGH_GRID_VOLTAGE_LEVEL3_FAULT)
								.bit(3, HyperInverter.AlarmChannelId.HIGH_GRID_VOLTAGE_LEVEL4_FAULT)
								.bit(4, HyperInverter.AlarmChannelId.HIGH_GRID_VOLTAGE_LEVEL5_FAULT)
								.bit(5, HyperInverter.AlarmChannelId.LOW_GRID_VOLTAGE_LEVEL1_FAULT)
								.bit(6, HyperInverter.AlarmChannelId.LOW_GRID_VOLTAGE_LEVEL2_FAULT)
								.bit(7, HyperInverter.AlarmChannelId.LOW_GRID_VOLTAGE_LEVEL3_FAULT)
								.bit(8, HyperInverter.AlarmChannelId.LOW_GRID_VOLTAGE_LEVEL4_FAULT)
								.bit(9, HyperInverter.AlarmChannelId.LOW_GRID_VOLTAGE_LEVEL5_FAULT)
								.bit(10, HyperInverter.AlarmChannelId.HIGH_GRID_FREQUENCY_LEVEL1_FAULT)
								.bit(11, HyperInverter.AlarmChannelId.HIGH_GRID_FREQUENCY_LEVEL2_FAULT)
								.bit(12, HyperInverter.AlarmChannelId.HIGH_GRID_FREQUENCY_LEVEL3_FAULT)
								.bit(13, HyperInverter.AlarmChannelId.HIGH_GRID_FREQUENCY_LEVEL4_FAULT)
								.bit(14, HyperInverter.AlarmChannelId.HIGH_GRID_FREQUENCY_LEVEL5_FAULT)
								.bit(15, HyperInverter.AlarmChannelId.LOW_GRID_FREQUENCY_LEVEL1_FAULT)
						),
						m(new BitsWordElement(3080, this)
								.bit(0, HyperInverter.AlarmChannelId.LOW_GRID_FREQUENCY_LEVEL2_FAULT)
								.bit(1, HyperInverter.AlarmChannelId.LOW_GRID_FREQUENCY_LEVEL3_FAULT)
								.bit(2, HyperInverter.AlarmChannelId.LOW_GRID_FREQUENCY_LEVEL4_FAULT)
								.bit(3, HyperInverter.AlarmChannelId.LOW_GRID_FREQUENCY_LEVEL5_FAULT)
						),
						m(new BitsWordElement(3081, this)
								.bit(0, HyperInverter.AlarmChannelId.HIGH_MEAN_VOLTAGE_FAULT)
								.bit(1, HyperInverter.AlarmChannelId.CT_PHASE_REVERSAL_WARNING)
								.bit(2, HyperInverter.AlarmChannelId.CT_DETECTION_WARNING)
								.bit(3, HyperInverter.AlarmChannelId.DETECTION_BOX_WARNING)
								.bit(13, HyperInverter.AlarmChannelId.ANTI_BACKFLOW_OVERLIMIT_FAULT)
								.bit(14, HyperInverter.AlarmChannelId.ANTI_BACKFLOW_METER_COMMUNICATION_WARNING)
								.bit(15, HyperInverter.AlarmChannelId.ANTI_BACKFLOW_METER_COMMUNICATION_FAULT)
						),
						m(new BitsWordElement(3082, this)
								.bit(0, HyperInverter.AlarmChannelId.HOST_COMMUNICATION_WARNING)
								.bit(1, HyperInverter.AlarmChannelId.BCMS_COMMUNICATION_FAULT)
								.bit(2, HyperInverter.AlarmChannelId.DSP_COMMUNICATION_FAULT)
								.bit(3, HyperInverter.AlarmChannelId.BAMS_COMMUNICATION_FAULT)
								.bit(4, HyperInverter.AlarmChannelId.ETHERNET_COMMUNICATION_FAULT)
								.bit(6, HyperInverter.AlarmChannelId.BCMS_ETH_COMMUNICATION_FAULT)
								.bit(12, HyperInverter.AlarmChannelId.MODULE_MODEL_MISMATCH_FAULT)
								.bit(13, HyperInverter.AlarmChannelId.INTERNAL_PARAMETER_MISMATCH_FAULT)
								.bit(14, HyperInverter.AlarmChannelId.FLASH_STORAGE_FAULT)
								.bit(15, HyperInverter.AlarmChannelId.RTC_INIT_WARNING)
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
						m(HyperBattery.ChannelId.BATTERY_MAX_CHARGE_POWER,
								new UnsignedWordElement(10007), SCALE_FACTOR_2),
						m(HyperBattery.ChannelId.BATTERY_MAX_DISCHARGE_POWER,
								new UnsignedWordElement(10008), SCALE_FACTOR_2)),

				new FC4ReadInputRegistersTask(10009, Priority.LOW,
						m(new BitsWordElement(10009, this)
								.bit(0, HyperBattery.ChannelId.BATTERY_POSITIVE_RELAY_STATUS)
								.bit(1, HyperBattery.ChannelId.BATTERY_NEGATIVE_RELAY_STATUS)
						),
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
						m(new UnsignedWordElement(10018)).build().onUpdateCallback(value -> {
								convertAlarm(0, value,
										this.channel(HyperBattery.AlarmChannelId.HIGH_CELL_VOLTAGE_WARNING),
										this.channel(HyperBattery.AlarmChannelId.HIGH_CELL_VOLTAGE_FAULT));
								convertAlarm(2, value,
										this.channel(HyperBattery.AlarmChannelId.LOW_CELL_VOLTAGE_WARNING),
										this.channel(HyperBattery.AlarmChannelId.LOW_CELL_VOLTAGE_FAULT));
								convertAlarm(4, value,
										this.channel(HyperBattery.AlarmChannelId.IMBALANCE_CELL_VOLTAGE_WARNING),
										this.channel(HyperBattery.AlarmChannelId.IMBALANCE_CELL_VOLTAGE_FAULT));
								convertAlarm(12, value,
										this.channel(HyperBattery.AlarmChannelId.HIGH_VOLTAGE_WARNING),
										this.channel(HyperBattery.AlarmChannelId.HIGH_VOLTAGE_FAULT));
								convertAlarm(14, value,
										this.channel(HyperBattery.AlarmChannelId.LOW_VOLTAGE_WARNING),
										this.channel(HyperBattery.AlarmChannelId.LOW_VOLTAGE_FAULT));
						}),
						new DummyRegisterElement(10019),
						m(new UnsignedWordElement(10020)).build().onUpdateCallback(value -> {
								convertAlarm(0, value, this.channel(HyperBattery.AlarmChannelId.HIGH_DISCHARGE_CURRENT_FAULT));
								convertAlarm(2, value, this.channel(HyperBattery.AlarmChannelId.HIGH_CHARGE_CURRENT_FAULT));
								convertAlarm(4, value,
										this.channel(HyperBattery.AlarmChannelId.HIGH_TEMPERATURE_WARNING),
										this.channel(HyperBattery.AlarmChannelId.HIGH_TEMPERATURE_FAULT));
								convertAlarm(6, value,
										this.channel(HyperBattery.AlarmChannelId.LOW_TEMPERATURE_WARNING),
										this.channel(HyperBattery.AlarmChannelId.LOW_TEMPERATURE_FAULT));
								convertAlarm(8, value,
										this.channel(HyperBattery.AlarmChannelId.HIGH_TEMPERATURE_DIFFERENTIAL_WARNING),
										this.channel(HyperBattery.AlarmChannelId.HIGH_TEMPERATURE_DIFFERENTIAL_FAULT));
								convertAlarm(10, value,
										this.channel(HyperBattery.AlarmChannelId.RAPID_TEMPERATURE_RISE_WARNING),
										this.channel(HyperBattery.AlarmChannelId.RAPID_TEMPERATURE_RISE_FAULT));
						}),
						new DummyRegisterElement(10021),
						m(new UnsignedWordElement(10022)).build().onUpdateCallback(value -> {
								convertAlarm(4, value,
										this.channel(HyperBattery.AlarmChannelId.HIGH_SOC_WARNING),
										this.channel(HyperBattery.AlarmChannelId.HIGH_SOC_FAULT));
								convertAlarm(6, value,
										this.channel(HyperBattery.AlarmChannelId.LOW_SOC_WARNING),
										this.channel(HyperBattery.AlarmChannelId.LOW_SOC_FAULT));
								convertAlarm(12, value,
										this.channel(HyperBattery.AlarmChannelId.HIGH_BUSBAR_TEMPERATURE_WARNING),
										this.channel(HyperBattery.AlarmChannelId.HIGH_BUSBAR_TEMPERATURE_FAULT));
								convertAlarm(14, value,
										this.channel(HyperBattery.AlarmChannelId.EXCESSIVE_BATTERY_VOLTAGE_DIFFERENTIAL_WARNING),
										this.channel(HyperBattery.AlarmChannelId.EXCESSIVE_BATTERY_VOLTAGE_DIFFERENTIAL_FAULT));
						}),
						new DummyRegisterElement(10023),
						m(new BitsWordElement(10024, this)
								.bit(7, HyperBattery.AlarmChannelId.BCMS_COMMUNICATION_FAULT)
								.bit(8, HyperBattery.AlarmChannelId.BAMS_COMMUNICATION_FAULT)
								.bit(12, HyperBattery.AlarmChannelId.EXTREME_HIGH_VOLTAGE_FAULT)
								.bit(13, HyperBattery.AlarmChannelId.EXTREME_LOW_VOLTAGE_FAULT)
								.bit(14, HyperBattery.AlarmChannelId.EXTREME_HIGH_TEMPERATURE_FAULT)
								.bit(15, HyperBattery.AlarmChannelId.EXTREME_LOW_TEMPERATURE_FAULT)
						),
						new DummyRegisterElement(10025),
						m(new UnsignedWordElement(10026)).build().onUpdateCallback(value -> {
								convertAlarm(2, value,
										this.channel(HyperBattery.AlarmChannelId.HIGH_CONTACTOR_TEMPERATURE_WARNING),
										this.channel(HyperBattery.AlarmChannelId.HIGH_CONTACTOR_TEMPERATURE_FAULT));
								convertAlarm(4, value,
										this.channel(HyperBattery.AlarmChannelId.HIGH_POWER_MODULE_TEMPERATURE_WARNING),
										this.channel(HyperBattery.AlarmChannelId.HIGH_POWER_MODULE_TEMPERATURE_FAULT));
								convertAlarm(8, value,
										this.channel(HyperBattery.AlarmChannelId.HIGH_CONNECTOR_TEMPERATURE_WARNING),
										this.channel(HyperBattery.AlarmChannelId.HIGH_CONNECTOR_TEMPERATURE_FAULT));
						}),
						new DummyRegisterElement(10027),
						m(new BitsWordElement(10028, this)
								.bit(0, HyperBattery.AlarmChannelId.INSULATION_MODULE_COMMUNICATION_WARNING)
								.bit(1, HyperBattery.AlarmChannelId.PARAMETER_CONFIGURATION_WARNING)
								.bit(2, HyperBattery.AlarmChannelId.HALL_SENSOR_OPEN_CIRCUIT_WARNING)
								.bit(3, HyperBattery.AlarmChannelId.TEMPERATURE_SENSOR_OPEN_CIRCUIT_WARNING)
								.bit(4, HyperBattery.AlarmChannelId.TEMPERATURE_SENSOR_SHORT_CIRCUIT_WARNING)
								.bit(5, HyperBattery.AlarmChannelId.MAIN_POSITIVE_CONTACTOR_FAULT)
								.bit(9, HyperBattery.AlarmChannelId.FIRE_DETECTOR_COMMUNICATION_TIMEOUT)
								.bit(10, HyperBattery.AlarmChannelId.COOLING_SYSTEM_COMMUNICATION_TIMEOUT)
								.bit(11, HyperBattery.AlarmChannelId.AEROSOL_SIGNAL_DISCONNECT_WARNING)
								.bit(12, HyperBattery.AlarmChannelId.HIGH_VOLTAGE_CALIBRATION_WARNING)
								.bit(13, HyperBattery.AlarmChannelId.CURRENT_CALIBRATION_WARNING)
								.bit(14, HyperBattery.AlarmChannelId.FIRE_DETECTOR_DISCONNECT_FAULT)
								.bit(15, HyperBattery.AlarmChannelId.MSD_DISCONNECT_FAULT)
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
						new DummyRegisterElement(10031, 10033),
						m(new UnsignedWordElement(10034)).build().onUpdateCallback(value -> {
								convertAlarm(0, value,
										this.channel(HyperBattery.AlarmChannelId.IMBALANCE_CELL_CHARGE_VOLTAGE_WARNING),
										this.channel(HyperBattery.AlarmChannelId.IMBALANCE_CELL_CHARGE_VOLTAGE_FAULT));
								convertAlarm(2, value,
										this.channel(HyperBattery.AlarmChannelId.IMBALANCE_CELL_DISCHARGE_VOLTAGE_WARNING),
										this.channel(HyperBattery.AlarmChannelId.IMBALANCE_CELL_DISCHARGE_VOLTAGE_FAULT));
								convertAlarm(4, value,
										this.channel(HyperBattery.AlarmChannelId.HIGH_PACK_VOLTAGE_WARNING),
										this.channel(HyperBattery.AlarmChannelId.HIGH_PACK_VOLTAGE_FAULT));
								convertAlarm(6, value,
										this.channel(HyperBattery.AlarmChannelId.LOW_PACK_VOLTAGE_WARNING),
										this.channel(HyperBattery.AlarmChannelId.LOW_PACK_VOLTAGE_FAULT));
								convertAlarm(8, value,
										this.channel(HyperBattery.AlarmChannelId.COOLING_SYSTEM_MANAGEMENT_WARNING),
										this.channel(HyperBattery.AlarmChannelId.COOLING_SYSTEM_MANAGEMENT_FAULT), 3);
						})),

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
						m(ThermalManagementSystem.ChannelId.COOLING_SYSTEM_MODE,
								new UnsignedWordElement(50001)),
						m(ThermalManagementSystem.ChannelId.COOLING_SYSTEM_RETURN_TEMPERATURE,
								new SignedWordElement(50002)),
						m(ThermalManagementSystem.ChannelId.COOLING_SYSTEM_SUPPLY_TEMPERATURE,
								new SignedWordElement(50003)),
						m(new BitsWordElement(50004, this)
								.bit(0, ThermalManagementSystem.ChannelId.COOLING_SYSTEM_COMMUNICATION_ABNORMAL)
								.bit(1, ThermalManagementSystem.ChannelId.COOLING_SYSTEM_COMMUNICATION_CONNECTED)
								.bit(2, ThermalManagementSystem.ChannelId.COOLING_SYSTEM_COMMUNICATION_ENABLED)
								.bit(3, ThermalManagementSystem.ChannelId.COOLING_SYSTEM_COMMUNICATION_FAULT)
						),
						m(new BitsWordElement(50005, this)
								.bit(0, ThermalManagementSystem.ChannelId.COOLING_SYSTEM_MAIN_CONTACTOR_STATE)
								.bit(1, ThermalManagementSystem.ChannelId.COOLING_SYSTEM_COMPRESSOR_STATE)
								.bit(2, ThermalManagementSystem.ChannelId.COOLING_SYSTEM_HEATING_STATE)
						)),

				new FC4ReadInputRegistersTask(50077, Priority.LOW,
						m(ThermalManagementSystem.ChannelId.COOLING_SYSTEM_FAULT_CODE,
								new UnsignedWordElement(50077)),
						new DummyRegisterElement(50078, 50098),
						m(ThermalManagementSystem.ChannelId.COOLING_SYSTEM_RETURN_PRESSURE,
								new SignedWordElement(50099), SCALE_FACTOR_1),
						m(ThermalManagementSystem.ChannelId.COOLING_SYSTEM_SUPPLY_PRESSURE,
								new SignedWordElement(50100), SCALE_FACTOR_1)),

				new FC6WriteRegisterTask(1, 
						m(HyperCube.ChannelId.HEARTBEAT, new UnsignedWordElement(1))),

				new FC6WriteRegisterTask(302, 
						m(SymmetricEss.ChannelId.GRID_MODE, new UnsignedWordElement(302))),
				new FC6WriteRegisterTask(303, 
						m(HyperCube.ChannelId.RUN_MODE, new UnsignedWordElement(303))),

				new FC16WriteRegistersTask(315,
						m(HyperCube.ChannelId.SET_ACTIVE_POWER,
								new SignedWordElement(315), SCALE_FACTOR_3),
						m(HyperCube.ChannelId.SET_REACTIVE_POWER,
								new SignedWordElement(316), SCALE_FACTOR_3)));
	}

//	private Task defineModbusCellAnalyticsTask(int startAddress, HyperCubeModel model) {
//		// TODO: Implement 260 cell voltages for HyperCube II, dynamically based on HyperCube model
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
