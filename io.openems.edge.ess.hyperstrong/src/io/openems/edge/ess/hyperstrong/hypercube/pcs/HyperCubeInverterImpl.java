package io.openems.edge.ess.hyperstrong.hypercube.pcs;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.chain;
import static io.openems.edge.common.type.Phase.SingleOrAllPhase.ALL;
import static io.openems.edge.ess.hyperstrong.AlarmAnalysis.decodeAlarm;
import static io.openems.edge.ess.hyperstrong.ModbusUtils.defineModbusAlarmRegister;
import static io.openems.edge.ess.power.api.Pwr.ACTIVE;
import static io.openems.edge.ess.power.api.Pwr.REACTIVE;
import static io.openems.edge.ess.power.api.Relationship.GREATER_OR_EQUALS;
import static io.openems.edge.ess.power.api.Relationship.LESS_OR_EQUALS;

import java.util.ArrayList;

import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.common.type.TypeUtils;
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

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.OpenemsType;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.batteryinverter.api.BatteryInverterConstraint;
import io.openems.edge.batteryinverter.api.ManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.BitsWordElement;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.common.channel.ChannelId.ChannelIdImpl;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.hyperstrong.ModbusUtils;
import io.openems.edge.oros.common.SymmetricComponent;
import io.openems.edge.oros.pcs.api.PowerConversionSystem;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "Ess.HyperStrong.HyperCube.II.PCS",
		immediate = true,
		configurationPolicy = ConfigurationPolicy.REQUIRE
)
@EventTopics({
	EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE,
})
public class HyperCubeInverterImpl extends AbstractOpenemsModbusComponent implements
		HyperCubeInverter, PowerConversionSystem, SymmetricBatteryInverter,
		SymmetricComponent, OpenemsComponent, ModbusComponent, ModbusSlave,
		TimedataProvider, EventHandler {

	private final CalculateEnergyFromPower calculateChargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricEss.ChannelId.ACTIVE_CHARGE_ENERGY);
	private final CalculateEnergyFromPower calculateDischargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricEss.ChannelId.ACTIVE_DISCHARGE_ENERGY);

	@Reference
	private ConfigurationAdmin cm;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata;

	@Override
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public HyperCubeInverterImpl() {
		super(OpenemsComponent.ChannelId.values(),
				ModbusComponent.ChannelId.values(),
				StartStoppable.ChannelId.values(),
				SymmetricComponent.ChannelId.values(),
				SymmetricBatteryInverter.ChannelId.values(),
				ManagedSymmetricBatteryInverter.ChannelId.values(),
				PowerConversionSystem.ChannelId.values(),
				HyperCubeInverter.ChannelId.values(),
				HyperCubeInverter.AlarmChannelId.values());
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		if (super.activate(context, config.id(), config.alias(), config.enabled(),
				config.modbusUnitId(), this.cm, "Modbus", config.modbus_id())) {
			return;
		}
		this._setMaxApparentPower((int) Math.floor(
				HyperCubeInverter.MAX_CHARGE_POWER * HyperCubeInverter.APPARENT_POWER_FACTOR));

		// Calculate the Phase Voltages from Phase to Phase Voltages
		SymmetricComponent.calculatePhaseVoltages(this);

		// Calculate the Phase Power Factors for symmetric phases
		SymmetricComponent.calculatePhasePowerFactorsFromSymmetry(this);

		// Calculate the Phase Powers from Voltage, Current and Power Factor
		SymmetricComponent.calculatePhasePowersFromVoltageAndCurrent(this);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run(Battery battery, int setActivePower, int setReactivePower) {
		// Unnecessary for this implementation
	}

	@Override
	public void handleEvent(Event event) {
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE:
			this.calculateAcEnergy();
			break;
		}
	}

	private void calculateAcEnergy() {
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
		// Unnecessary for this implementation
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public BatteryInverterConstraint[] getStaticConstraints() throws OpenemsNamedException {
		var constraints = new ArrayList<BatteryInverterConstraint>();

		var maxActivePower = this.getDischargeMaxPower();
		var minActivePower = -1 * this.getChargeMaxPower();
		constraints.add(new BatteryInverterConstraint("HyperCube II maximum Active Power",
				ALL, ACTIVE, LESS_OR_EQUALS, maxActivePower));
		constraints.add(new BatteryInverterConstraint("HyperCube II minimum Active Power",
				ALL, ACTIVE, GREATER_OR_EQUALS, minActivePower));

		var maxReactivePower = (int) Math.floor(maxActivePower * HyperCubeInverter.REACTIVE_POWER_FACTOR);
		var minReactivePower = (int) Math.ceil(minActivePower * HyperCubeInverter.REACTIVE_POWER_FACTOR);
		constraints.add(new BatteryInverterConstraint("HyperCube II maximum Reactive Power",
				ALL, REACTIVE, LESS_OR_EQUALS, maxReactivePower));
		constraints.add(new BatteryInverterConstraint("HyperCube II minimum Reactive Power",
				ALL, REACTIVE, GREATER_OR_EQUALS, minReactivePower));

		return constraints.toArray(new BatteryInverterConstraint[constraints.size()]);
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		return new ModbusProtocol(this,
				new FC4ReadInputRegistersTask(3001, Priority.HIGH,
						m(SymmetricComponent.ChannelId.VOLTAGE_L1_L2,
								new SignedWordElement(3001), SCALE_FACTOR_2),
						m(SymmetricComponent.ChannelId.VOLTAGE_L2_L3,
								new SignedWordElement(3002), SCALE_FACTOR_2),
						m(SymmetricComponent.ChannelId.VOLTAGE_L3_L1,
								new SignedWordElement(3003), SCALE_FACTOR_2),
						m(SymmetricComponent.ChannelId.CURRENT_L1,
								new SignedWordElement(3004), SCALE_FACTOR_2),
						m(SymmetricComponent.ChannelId.CURRENT_L2,
								new SignedWordElement(3005), SCALE_FACTOR_2),
						m(SymmetricComponent.ChannelId.CURRENT_L3,
								new SignedWordElement(3006), SCALE_FACTOR_2),
						m(SymmetricComponent.ChannelId.FREQUENCY,
								new SignedWordElement(3007), SCALE_FACTOR_2),
						m(SymmetricBatteryInverter.ChannelId.ACTIVE_POWER,
								new SignedWordElement(3008), SCALE_FACTOR_2),
						m(SymmetricBatteryInverter.ChannelId.REACTIVE_POWER,
								new SignedWordElement(3009), SCALE_FACTOR_2),
						m(SymmetricComponent.ChannelId.POWER_FACTOR,
								new SignedWordElement(3010),
								chain(ModbusUtils.CONVERT_FLOAT, SCALE_FACTOR_MINUS_3)),
						m(PowerConversionSystem.ChannelId.DC_VOLTAGE,
								new SignedWordElement(3011), SCALE_FACTOR_2),
						m(PowerConversionSystem.ChannelId.DC_CURRENT,
								new SignedWordElement(3012), SCALE_FACTOR_2),
						m(PowerConversionSystem.ChannelId.DC_POWER,
								new SignedWordElement(3013), SCALE_FACTOR_2)),

				new FC4ReadInputRegistersTask(3014, Priority.LOW,
						m(HyperCubeInverter.ChannelId.MODULE_TEMPERATURE, new SignedWordElement(3014)),
						m(PowerConversionSystem.ChannelId.AIR_TEMPERATURE, new SignedWordElement(3015)),
						m(HyperCubeInverter.ChannelId.IGBT_L1_TEMPERATURE, new SignedWordElement(3016)),
						m(HyperCubeInverter.ChannelId.IGBT_L2_TEMPERATURE, new SignedWordElement(3017)),
						m(HyperCubeInverter.ChannelId.IGBT_L3_TEMPERATURE, new SignedWordElement(3018)),
						defineModbusAlarmRegister(this, 1, 3019, this::addModbusAlarmChannel, value -> {
							decodeAlarm(0, value, this.channel(AlarmChannelId.LOW_AC_VOLTAGE_FAULT));
							decodeAlarm(1, value, this.channel(AlarmChannelId.HIGH_AC_VOLTAGE_FAULT));
							decodeAlarm(2, value, this.channel(AlarmChannelId.LOW_FREQUENCY_FAULT));
							decodeAlarm(3, value, this.channel(AlarmChannelId.HIGH_FREQUENCY_FAULT));
							decodeAlarm(4, value, this.channel(AlarmChannelId.FAST_LOW_AC_VOLTAGE_FAULT));
							decodeAlarm(5, value, this.channel(AlarmChannelId.FAST_HIGH_AC_VOLTAGE_FAULT));
							decodeAlarm(8, value, this.channel(AlarmChannelId.PHASE_REVERSAL_FAULT));
							decodeAlarm(9, value, this.channel(AlarmChannelId.PHASE_LOSS_FAULT));
							decodeAlarm(10, value, this.channel(AlarmChannelId.OUTPUT_VOLTAGE_ANOMALY));
							decodeAlarm(11, value, this.channel(AlarmChannelId.OFF_GRID_STARTUP_BLOCKED));
							decodeAlarm(12, value, this.channel(AlarmChannelId.ISLAND_PROTECTION_FAULT));
							decodeAlarm(13, value, this.channel(AlarmChannelId.AC_SHORT_CIRCUIT_FAULT));
							decodeAlarm(14, value, this.channel(AlarmChannelId.AC_CURRENT_ABNORMAL_FAULT));
							decodeAlarm(20, value, this.channel(AlarmChannelId.PARALLEL_OVERLOAD_TIMEOUT));
							decodeAlarm(21, value, this.channel(AlarmChannelId.OUTPUT_OVERLOAD_TIMEOUT));
							decodeAlarm(22, value, this.channel(AlarmChannelId.AC_POWER_ABNORMAL));
							decodeAlarm(23, value, this.channel(AlarmChannelId.MODULE_IDENTIFICATION_FAULT));
							decodeAlarm(24, value, this.channel(AlarmChannelId.VOLTAGE_L1_L2_FAULT));
							decodeAlarm(25, value, this.channel(AlarmChannelId.VOLTAGE_L2_L3_FAULT));
							decodeAlarm(26, value, this.channel(AlarmChannelId.VOLTAGE_L3_L1_FAULT));
							decodeAlarm(27, value, this.channel(AlarmChannelId.DC_SOFT_START_FAULT));
							decodeAlarm(28, value, this.channel(AlarmChannelId.DC_RELAY_CLOSE_FAULT));
							decodeAlarm(29, value, this.channel(AlarmChannelId.INDUCTOR_CURRENT_BALANCE_L1_FAULT));
							decodeAlarm(30, value, this.channel(AlarmChannelId.INDUCTOR_CURRENT_BALANCE_L2_FAULT));
							decodeAlarm(31, value, this.channel(AlarmChannelId.INDUCTOR_CURRENT_BALANCE_L3_FAULT));
						}),
						defineModbusAlarmRegister(this, 2, 3021, this::addModbusAlarmChannel, value -> {
							decodeAlarm(6, value, this.channel(AlarmChannelId.BAMS_CURRENT_LIMIT_SHUTDOWN));
							decodeAlarm(7, value, this.channel(AlarmChannelId.BAMS_POWER_LIMIT_SHUTDOWN));
							decodeAlarm(8, value, this.channel(AlarmChannelId.BCMS_NO_CHARGE_SHUTDOWN));
							decodeAlarm(9, value, this.channel(AlarmChannelId.BCMS_DISABLE_SHUTDOWN));
							decodeAlarm(10, value, this.channel(AlarmChannelId.BAMS_CHARGE_DISABLED_SHUTDOWN));
							decodeAlarm(11, value, this.channel(AlarmChannelId.BAMS_SHUTDOWN));
							decodeAlarm(12, value, this.channel(AlarmChannelId.BCMS_CURRENT_LIMIT_SHUTDOWN));
							decodeAlarm(13, value, this.channel(AlarmChannelId.BCMS_POWER_LIMIT_SHUTDOWN));
							decodeAlarm(14, value, this.channel(AlarmChannelId.BATTERY_VOLTAGE_LIMIT_SHUTDOWN));
							decodeAlarm(15, value, this.channel(AlarmChannelId.BATTERY_CURRENT_LIMIT_SHUTDOWN));
							decodeAlarm(16, value, this.channel(AlarmChannelId.LOW_BATTERY_VOLTAGE_FAULT));
							decodeAlarm(17, value, this.channel(AlarmChannelId.HIGH_BATTERY_VOLTAGE_FAULT));
							decodeAlarm(18, value, this.channel(AlarmChannelId.REVERSE_BATTERY_POLARITY_FAULT));
							decodeAlarm(19, value, this.channel(AlarmChannelId.HIGH_BATTERY_CURRENT_FAULT));
							decodeAlarm(20, value, this.channel(AlarmChannelId.INSULATION_FAULT));
							decodeAlarm(21, value, this.channel(AlarmChannelId.INSULATION_BATTERY_VOLTAGE_FAULT));
							decodeAlarm(23, value, this.channel(AlarmChannelId.LOW_POSITIVE_BUS_VOLTAGE_FAULT));
							decodeAlarm(24, value, this.channel(AlarmChannelId.LOW_NEGATIVE_BUS_VOLTAGE_FAULT));
							decodeAlarm(25, value, this.channel(AlarmChannelId.HIGH_DC_BUS1_VOLTAGE_FAULT));
							decodeAlarm(26, value, this.channel(AlarmChannelId.HIGH_DC_BUS2_VOLTAGE_FAULT));
							decodeAlarm(27, value, this.channel(AlarmChannelId.HIGH_DC_BUS3_VOLTAGE_FAULT));
							decodeAlarm(28, value, this.channel(AlarmChannelId.HIGH_DC_BUS4_VOLTAGE_FAULT));
							decodeAlarm(29, value, this.channel(AlarmChannelId.DC_BUS1_2_VOLTAGE_IMBALANCE_FAULT));
							decodeAlarm(30, value, this.channel(AlarmChannelId.DC_BUS3_4_VOLTAGE_IMBALANCE_FAULT));
						}),
						defineModbusAlarmRegister(this, 3, 3023, this::addModbusAlarmChannel, value -> {
							decodeAlarm(16, value, this.channel(AlarmChannelId.DC_SURGE_ARRESTER_WARNING));
							decodeAlarm(17, value, this.channel(AlarmChannelId.AC_SURGE_ARRESTER_WARNING));
							decodeAlarm(26, value, this.channel(AlarmChannelId.DC_RELAY_OPEN_CIRCUIT_FAULT));
							decodeAlarm(27, value, this.channel(AlarmChannelId.DC_RELAY_SHORT_CIRCUIT_FAULT));
							decodeAlarm(29, value, this.channel(AlarmChannelId.POWER_SUPPLY_15V_FAULT));
							decodeAlarm(30, value, this.channel(AlarmChannelId.POWER_SUPPLY_24V_FAULT));
						}),
						defineModbusAlarmRegister(this, 4, 3025, this::addModbusAlarmChannel, value -> {
							decodeAlarm(20, value, this.channel(AlarmChannelId.DC_BUS1_SHORT_CIRCUIT_FAULT));
							decodeAlarm(21, value, this.channel(AlarmChannelId.DC_BUS2_SHORT_CIRCUIT_FAULT));
							decodeAlarm(22, value, this.channel(AlarmChannelId.DC_BUS3_SHORT_CIRCUIT_FAULT));
							decodeAlarm(23, value, this.channel(AlarmChannelId.DC_BUS4_SHORT_CIRCUIT_FAULT));
							decodeAlarm(24, value, this.channel(AlarmChannelId.GRID_RELAY_L1_L2_SHORT_CIRCUIT_FAULT));
							decodeAlarm(25, value, this.channel(AlarmChannelId.GRID_RELAY_L2_L3_SHORT_CIRCUIT_FAULT));
							decodeAlarm(26, value, this.channel(AlarmChannelId.GRID_RELAY_L3_L1_SHORT_CIRCUIT_FAULT));
							decodeAlarm(27, value, this.channel(AlarmChannelId.GRID_RELAY_L1_L2_OPEN_CIRCUIT_FAULT));
							decodeAlarm(28, value, this.channel(AlarmChannelId.GRID_RELAY_L2_L3_OPEN_CIRCUIT_FAULT));
							decodeAlarm(29, value, this.channel(AlarmChannelId.GRID_RELAY_L3_L1_OPEN_CIRCUIT_FAULT));
						}),
						defineModbusAlarmRegister(this, 5, 3027, this::addModbusAlarmChannel, value -> {
							decodeAlarm(16, value, this.channel(AlarmChannelId.BUS_VOLTAGE_IMBALANCE_FAULT));
							decodeAlarm(17, value, this.channel(AlarmChannelId.HIGH_POSITIVE_BUS_VOLTAGE_FAULT));
							decodeAlarm(18, value, this.channel(AlarmChannelId.HIGH_NEGATIVE_BUS_VOLTAGE_FAULT));
							decodeAlarm(26, value, this.channel(AlarmChannelId.CONVERSION_EFFICIENCY_ABNORMAL));
							decodeAlarm(27, value, this.channel(AlarmChannelId.HIGH_DC_BUS1_HARDWARE_VOLTAGE_FAULT));
							decodeAlarm(28, value, this.channel(AlarmChannelId.HIGH_DC_BUS2_HARDWARE_VOLTAGE_FAULT));
							decodeAlarm(29, value, this.channel(AlarmChannelId.HIGH_DC_BUS3_HARDWARE_VOLTAGE_FAULT));
							decodeAlarm(30, value, this.channel(AlarmChannelId.HIGH_DC_BUS4_HARDWARE_VOLTAGE_FAULT));
						}),
						defineModbusAlarmRegister(this, 6, 3029, this::addModbusAlarmChannel, value -> {
							decodeAlarm(16, value, this.channel(AlarmChannelId.INVERTER_FAILURE));
							decodeAlarm(17, value, this.channel(AlarmChannelId.INVERTER_SOFT_START_COMMUNICATION_FAULT));
							decodeAlarm(24, value, this.channel(AlarmChannelId.INVERTER_COOLING_FAN_WARNING));
							decodeAlarm(25, value, this.channel(AlarmChannelId.INVERTER_IGBT_FAN_WARNING));
							decodeAlarm(28, value, this.channel(AlarmChannelId.INVERTER_VOLTAGE_L1_L2_FAULT));
							decodeAlarm(29, value, this.channel(AlarmChannelId.INVERTER_VOLTAGE_L2_L3_FAULT));
							decodeAlarm(30, value, this.channel(AlarmChannelId.INVERTER_VOLTAGE_L3_L1_FAULT));
							decodeAlarm(31, value, this.channel(AlarmChannelId.MISSING_N_LINE_FAULT));
						}),
						defineModbusAlarmRegister(this, 7, 3031, this::addModbusAlarmChannel, value -> {
							decodeAlarm(0, value, this.channel(AlarmChannelId.LIMITING_N_LINE_CURRENT_WARNING));
							decodeAlarm(1, value, this.channel(AlarmChannelId.HIGH_N_LINE_CURRENT_FAULT));
							decodeAlarm(4, value, this.channel(AlarmChannelId.INDUCTOR_CURRENT_BRANCH1_L1_FAULT));
							decodeAlarm(5, value, this.channel(AlarmChannelId.INDUCTOR_CURRENT_BRANCH1_L2_FAULT));
							decodeAlarm(6, value, this.channel(AlarmChannelId.INDUCTOR_CURRENT_BRANCH1_L3_FAULT));
							decodeAlarm(7, value, this.channel(AlarmChannelId.INDUCTOR_CURRENT_BRANCH2_L1_FAULT));
							decodeAlarm(8, value, this.channel(AlarmChannelId.INDUCTOR_CURRENT_BRANCH2_L2_FAULT));
							decodeAlarm(9, value, this.channel(AlarmChannelId.INDUCTOR_CURRENT_BRANCH2_L3_FAULT));
						}),
						defineModbusAlarmRegister(this, 8, 3033, this::addModbusAlarmChannel),
						defineModbusAlarmRegister(this, 9, 3035, this::addModbusAlarmChannel, value -> {
							decodeAlarm(16, value, this.channel(AlarmChannelId.HIGH_CABINET_TEMPERATURE_FAULT));
							decodeAlarm(17, value, this.channel(AlarmChannelId.HIGH_DISCHARGE_RESISTOR_TEMPERATURE_FAULT));
							decodeAlarm(18, value, this.channel(AlarmChannelId.HIGH_IGBT_TEMPERATURE_FAULT));
							decodeAlarm(22, value, this.channel(AlarmChannelId.CABINET_TEMPERATURE_SENSOR_ANOMALY_WARNING));
							decodeAlarm(23, value, this.channel(AlarmChannelId.LOCAL_EPO_FAULT));
							decodeAlarm(24, value, this.channel(AlarmChannelId.HIGH_IGBT_BRANCH1_L1_TEMPERATURE_WARNING));
							decodeAlarm(25, value, this.channel(AlarmChannelId.HIGH_IGBT_BRANCH2_L1_TEMPERATURE_WARNING));
							decodeAlarm(26, value, this.channel(AlarmChannelId.HIGH_IGBT_BRANCH1_L2_TEMPERATURE_WARNING));
							decodeAlarm(27, value, this.channel(AlarmChannelId.HIGH_IGBT_BRANCH2_L2_TEMPERATURE_WARNING));
							decodeAlarm(28, value, this.channel(AlarmChannelId.HIGH_IGBT_BRANCH1_L3_TEMPERATURE_WARNING));
							decodeAlarm(29, value, this.channel(AlarmChannelId.HIGH_IGBT_BRANCH2_L3_TEMPERATURE_WARNING));
							decodeAlarm(30, value, this.channel(AlarmChannelId.REMOTE_EPO_FAULT));
							decodeAlarm(31, value, this.channel(AlarmChannelId.HIGH_SOFT_START_RESISTOR_TEMPERATURE_FAULT));
						}),
						defineModbusAlarmRegister(this, 10, 3037, this::addModbusAlarmChannel),
						m(HyperCubeInverter.ChannelId.PCS_POWER_ON_STATUS, new UnsignedWordElement(3039)),
						m(new BitsWordElement(3040, this)
								.bit(0, HyperCubeInverter.ChannelId.COMMUNICATION_ABNORMAL)
								.bit(1, HyperCubeInverter.ChannelId.COMMUNICATION_CONNECTED)
								.bit(2, HyperCubeInverter.ChannelId.COMMUNICATION_ENABLED)
								.bit(3, HyperCubeInverter.ChannelId.COMMUNICATION_FAULT)
						),
						new DummyRegisterElement(3041, 3043),
						m(SymmetricBatteryInverter.ChannelId.GRID_MODE, new UnsignedWordElement(3044),
								new ElementToChannelConverter(value -> {
									var intValue = TypeUtils.<Integer>getAsType(OpenemsType.INTEGER, value);
									if (intValue != null) {
										switch (intValue) {
											case 0:
												return GridMode.ON_GRID;
											case 1:
												return GridMode.OFF_GRID;
										}
									}
									return GridMode.UNDEFINED;
								})),
						new DummyRegisterElement(3045, 3073),
						m(HyperCubeInverter.ChannelId.PCS_RUNNING_STATUS, new UnsignedWordElement(3074)),
						new DummyRegisterElement(3075, 3075),
						defineModbusAlarmRegister(this, 11, 3076, this::addModbusAlarmChannel, value -> {
							decodeAlarm(16, value, this.channel(AlarmChannelId.DSP_ARM_COMMUNICATION_FAULT));
							decodeAlarm(17, value, this.channel(AlarmChannelId.SCHEDULING_CAN_COMMUNICATION_FAULT));
							decodeAlarm(18, value, this.channel(AlarmChannelId.CARRIER_SYNC_FAULT));
							decodeAlarm(19, value, this.channel(AlarmChannelId.POWER_FREQUENCY_SYNC_FAULT));
							decodeAlarm(20, value, this.channel(AlarmChannelId.MODULE_IDENTIFICATION_CONFLICT));
							decodeAlarm(21, value, this.channel(AlarmChannelId.POWER_CAN1_COMMUNICATION_ANOMALY));
							decodeAlarm(22, value, this.channel(AlarmChannelId.POWER_CAN2_COMMUNICATION_ANOMALY));
							decodeAlarm(23, value, this.channel(AlarmChannelId.DSP_FPGA_VERSION_MISMATCH_WARNING));
						}),
						defineModbusAlarmRegister(this, 12, 3078, this::addModbusAlarmChannel, value -> {
							decodeAlarm(3, value, this.channel(AlarmChannelId.WAVE_LIMIT_BRANCH1_L1_WARNING));
							decodeAlarm(5, value, this.channel(AlarmChannelId.WAVE_LIMIT_BRANCH1_L2_WARNING));
							decodeAlarm(7, value, this.channel(AlarmChannelId.WAVE_LIMIT_BRANCH1_L3_WARNING));
							decodeAlarm(9, value, this.channel(AlarmChannelId.WAVE_LIMIT_BRANCH2_L1_WARNING));
							decodeAlarm(11, value, this.channel(AlarmChannelId.WAVE_LIMIT_BRANCH2_L2_WARNING));
							decodeAlarm(13, value, this.channel(AlarmChannelId.WAVE_LIMIT_BRANCH2_L3_WARNING));
							decodeAlarm(16, value, this.channel(AlarmChannelId.HIGH_GRID_VOLTAGE_LEVEL1_FAULT));
							decodeAlarm(17, value, this.channel(AlarmChannelId.HIGH_GRID_VOLTAGE_LEVEL2_FAULT));
							decodeAlarm(18, value, this.channel(AlarmChannelId.HIGH_GRID_VOLTAGE_LEVEL3_FAULT));
							decodeAlarm(19, value, this.channel(AlarmChannelId.HIGH_GRID_VOLTAGE_LEVEL4_FAULT));
							decodeAlarm(20, value, this.channel(AlarmChannelId.HIGH_GRID_VOLTAGE_LEVEL5_FAULT));
							decodeAlarm(21, value, this.channel(AlarmChannelId.LOW_GRID_VOLTAGE_LEVEL1_FAULT));
							decodeAlarm(22, value, this.channel(AlarmChannelId.LOW_GRID_VOLTAGE_LEVEL2_FAULT));
							decodeAlarm(23, value, this.channel(AlarmChannelId.LOW_GRID_VOLTAGE_LEVEL3_FAULT));
							decodeAlarm(24, value, this.channel(AlarmChannelId.LOW_GRID_VOLTAGE_LEVEL4_FAULT));
							decodeAlarm(25, value, this.channel(AlarmChannelId.LOW_GRID_VOLTAGE_LEVEL5_FAULT));
							decodeAlarm(26, value, this.channel(AlarmChannelId.HIGH_GRID_FREQUENCY_LEVEL1_FAULT));
							decodeAlarm(27, value, this.channel(AlarmChannelId.HIGH_GRID_FREQUENCY_LEVEL2_FAULT));
							decodeAlarm(28, value, this.channel(AlarmChannelId.HIGH_GRID_FREQUENCY_LEVEL3_FAULT));
							decodeAlarm(29, value, this.channel(AlarmChannelId.HIGH_GRID_FREQUENCY_LEVEL4_FAULT));
							decodeAlarm(30, value, this.channel(AlarmChannelId.HIGH_GRID_FREQUENCY_LEVEL5_FAULT));
							decodeAlarm(31, value, this.channel(AlarmChannelId.LOW_GRID_FREQUENCY_LEVEL1_FAULT));
						}),
						defineModbusAlarmRegister(this, 13, 3080, this::addModbusAlarmChannel, value -> {
							decodeAlarm(0, value, this.channel(AlarmChannelId.LOW_GRID_FREQUENCY_LEVEL2_FAULT));
							decodeAlarm(1, value, this.channel(AlarmChannelId.LOW_GRID_FREQUENCY_LEVEL3_FAULT));
							decodeAlarm(2, value, this.channel(AlarmChannelId.LOW_GRID_FREQUENCY_LEVEL4_FAULT));
							decodeAlarm(3, value, this.channel(AlarmChannelId.LOW_GRID_FREQUENCY_LEVEL5_FAULT));
							decodeAlarm(16, value, this.channel(AlarmChannelId.HIGH_MEAN_VOLTAGE_FAULT));
							decodeAlarm(17, value, this.channel(AlarmChannelId.CT_PHASE_REVERSAL_WARNING));
							decodeAlarm(18, value, this.channel(AlarmChannelId.CT_DETECTION_ANOMALY_WARNING));
							decodeAlarm(19, value, this.channel(AlarmChannelId.DETECTION_BOX_WARNING));
							decodeAlarm(29, value, this.channel(AlarmChannelId.ANTI_BACKFLOW_OVERLIMIT_FAULT));
							decodeAlarm(30, value, this.channel(AlarmChannelId.ANTI_BACKFLOW_METER_COMMUNICATION_WARNING));
							decodeAlarm(31, value, this.channel(AlarmChannelId.ANTI_BACKFLOW_METER_COMMUNICATION_FAULT));
						}),
						defineModbusAlarmRegister(this, 14, 3082, this::addModbusAlarmChannel, value -> {
							decodeAlarm(0, value, this.channel(AlarmChannelId.HOST_COMMUNICATION_WARNING));
							decodeAlarm(1, value, this.channel(AlarmChannelId.BCMS_COMMUNICATION_FAULT));
							decodeAlarm(2, value, this.channel(AlarmChannelId.DSP_COMMUNICATION_FAULT));
							decodeAlarm(3, value, this.channel(AlarmChannelId.BAMS_COMMUNICATION_FAULT));
							decodeAlarm(4, value, this.channel(AlarmChannelId.ETHERNET_COMMUNICATION_FAULT));
							decodeAlarm(6, value, this.channel(AlarmChannelId.BCMS_ETH_COMMUNICATION_FAULT));
							decodeAlarm(12, value, this.channel(AlarmChannelId.MODULE_MODEL_MISMATCH_FAULT));
							decodeAlarm(13, value, this.channel(AlarmChannelId.INTERNAL_PARAMETER_MISMATCH_FAULT));
							decodeAlarm(14, value, this.channel(AlarmChannelId.FLASH_STORAGE_FAULT));
							decodeAlarm(15, value, this.channel(AlarmChannelId.RTC_INIT_WARNING));
						}),
						defineModbusAlarmRegister(this, 15, 3084, this::addModbusAlarmChannel))
		);
	}

	private io.openems.edge.common.channel.ChannelId addModbusAlarmChannel(int number) {
		var channelId = new ChannelIdImpl(String.format("%s_%02d", "ALARM", number), Doc.of(OpenemsType.LONG));
		this.addChannel(channelId);
		return channelId;
	}

	@Override
	public String debugLog() {
		return PowerConversionSystem.generateDebugLog(this);
	}

}
