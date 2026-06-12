package io.openems.edge.ess.hyperstrong.hypercube.pcs;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.chain;
import static io.openems.edge.common.type.Phase.SingleOrAllPhase.ALL;
import static io.openems.edge.ess.power.api.Pwr.ACTIVE;
import static io.openems.edge.ess.power.api.Pwr.REACTIVE;
import static io.openems.edge.ess.power.api.Relationship.GREATER_OR_EQUALS;
import static io.openems.edge.ess.power.api.Relationship.LESS_OR_EQUALS;

import java.util.ArrayList;

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
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
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
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.hyperstrong.ConverterUtils;
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
public class HyperCubeInverterImpl extends AbstractOpenemsModbusComponent implements
		HyperCubeInverter, PowerConversionSystem, SymmetricBatteryInverter,
		SymmetricComponent, OpenemsComponent, ModbusComponent, ModbusSlave, TimedataProvider {

	private final CalculateEnergyFromPower calculateChargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricEss.ChannelId.ACTIVE_CHARGE_ENERGY);
	private final CalculateEnergyFromPower calculateDischargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricEss.ChannelId.ACTIVE_DISCHARGE_ENERGY);

	@Reference
	private ConfigurationAdmin cm;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata = null;

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
		// Calculate the Energy values from ActivePower.
		this.calculateEnergy();
	}

	private void calculateEnergy() {
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
								chain(ConverterUtils.CONVERT_FLOAT, SCALE_FACTOR_MINUS_3)),
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
						m(new BitsWordElement(3019, this)
								.bit(0, HyperCubeInverter.AlarmChannelId.LOW_AC_VOLTAGE_FAULT)
								.bit(1, HyperCubeInverter.AlarmChannelId.HIGH_AC_VOLTAGE_FAULT)
								.bit(2, HyperCubeInverter.AlarmChannelId.LOW_FREQUENCY_FAULT)
								.bit(3, HyperCubeInverter.AlarmChannelId.HIGH_FREQUENCY_FAULT)
								.bit(4, HyperCubeInverter.AlarmChannelId.FAST_LOW_AC_VOLTAGE_FAULT)
								.bit(5, HyperCubeInverter.AlarmChannelId.FAST_HIGH_AC_VOLTAGE_FAULT)
								.bit(8, HyperCubeInverter.AlarmChannelId.PHASE_REVERSAL_FAULT)
								.bit(9, HyperCubeInverter.AlarmChannelId.PHASE_LOSS_FAULT)
								.bit(10, HyperCubeInverter.AlarmChannelId.OUTPUT_VOLTAGE_FAULT)
								.bit(11, HyperCubeInverter.AlarmChannelId.OFF_GRID_STARTUP_BLOCKED_FAULT)
								.bit(12, HyperCubeInverter.AlarmChannelId.ISLAND_PROTECTION_FAULT)
								.bit(13, HyperCubeInverter.AlarmChannelId.AC_SHORT_CIRCUIT_FAULT)
								.bit(14, HyperCubeInverter.AlarmChannelId.HIGH_AC_CURRENT_FAULT)
						),
						m(new BitsWordElement(3020, this)
								.bit(4, HyperCubeInverter.AlarmChannelId.PARALLEL_OVERLOAD_TIMEOUT_FAULT)
								.bit(5, HyperCubeInverter.AlarmChannelId.OUTPUT_OVERLOAD_TIMEOUT_FAULT)
								.bit(6, HyperCubeInverter.AlarmChannelId.AC_POWER_ANOMALY_FAULT)
								.bit(8, HyperCubeInverter.AlarmChannelId.VOLTAGE_L1_L2_FAULT)
								.bit(9, HyperCubeInverter.AlarmChannelId.VOLTAGE_L2_L3_FAULT)
								.bit(10, HyperCubeInverter.AlarmChannelId.VOLTAGE_L3_L1_FAULT)
								.bit(11, HyperCubeInverter.AlarmChannelId.DC_SOFT_START_FAULT)
								.bit(12, HyperCubeInverter.AlarmChannelId.DC_RELAY_CLOSE_FAULT)
								.bit(13, HyperCubeInverter.AlarmChannelId.INDUCTOR_CURRENT_BALANCE_L1_FAULT)
								.bit(14, HyperCubeInverter.AlarmChannelId.INDUCTOR_CURRENT_BALANCE_L2_FAULT)
								.bit(15, HyperCubeInverter.AlarmChannelId.INDUCTOR_CURRENT_BALANCE_L3_FAULT)
						),
						m(new BitsWordElement(3021, this)
								.bit(6, HyperCubeInverter.AlarmChannelId.BAMS_CURRENT_LIMIT_SHUTDOWN_FAULT)
								.bit(7, HyperCubeInverter.AlarmChannelId.BAMS_POWER_LIMIT_SHUTDOWN_FAULT)
								.bit(8, HyperCubeInverter.AlarmChannelId.BCMS_NO_CHARGE_SHUTDOWN_FAULT)
								.bit(9, HyperCubeInverter.AlarmChannelId.BCMS_DISABLE_SHUTDOWN_FAULT)
								.bit(10, HyperCubeInverter.AlarmChannelId.BAMS_CHARGE_DISABLED_SHUTDOWN_FAULT)
								.bit(11, HyperCubeInverter.AlarmChannelId.BAMS_SHUTDOWN_FAULT)
								.bit(12, HyperCubeInverter.AlarmChannelId.BCMS_CURRENT_LIMIT_SHUTDOWN_FAULT)
								.bit(13, HyperCubeInverter.AlarmChannelId.BCMS_POWER_LIMIT_SHUTDOWN_FAULT)
								.bit(14, HyperCubeInverter.AlarmChannelId.BATTERY_VOLTAGE_LIMIT_SHUTDOWN_FAULT)
								.bit(15, HyperCubeInverter.AlarmChannelId.BATTERY_CURRENT_LIMIT_SHUTDOWN_FAULT)
						),
						m(new BitsWordElement(3022, this)
								.bit(0, HyperCubeInverter.AlarmChannelId.LOW_BATTERY_VOLTAGE_FAULT)
								.bit(1, HyperCubeInverter.AlarmChannelId.HIGH_BATTERY_VOLTAGE_FAULT)
								.bit(2, HyperCubeInverter.AlarmChannelId.REVERSE_BATTERY_POLARITY_FAULT)
								.bit(3, HyperCubeInverter.AlarmChannelId.HIGH_BATTERY_CURRENT_FAULT)
								.bit(4, HyperCubeInverter.AlarmChannelId.INSULATION_FAULT)
								.bit(5, HyperCubeInverter.AlarmChannelId.INSULATION_BATTERY_VOLTAGE_FAULT)
								.bit(7, HyperCubeInverter.AlarmChannelId.LOW_POSITIVE_BUS_VOLTAGE_FAULT)
								.bit(8, HyperCubeInverter.AlarmChannelId.LOW_NEGATIVE_BUS_VOLTAGE_FAULT)
								.bit(9, HyperCubeInverter.AlarmChannelId.HIGH_DC_BUS1_VOLTAGE_FAULT)
								.bit(10, HyperCubeInverter.AlarmChannelId.HIGH_DC_BUS2_VOLTAGE_FAULT)
								.bit(11, HyperCubeInverter.AlarmChannelId.HIGH_DC_BUS3_VOLTAGE_FAULT)
								.bit(12, HyperCubeInverter.AlarmChannelId.HIGH_DC_BUS4_VOLTAGE_FAULT)
								.bit(13, HyperCubeInverter.AlarmChannelId.DC_BUS1_2_VOLTAGE_IMBALANCE_FAULT)
								.bit(14, HyperCubeInverter.AlarmChannelId.DC_BUS3_4_VOLTAGE_IMBALANCE_FAULT)
						),
						new DummyRegisterElement(3023),
						m(new BitsWordElement(3024, this)
								.bit(0, HyperCubeInverter.AlarmChannelId.DC_SURGE_ARRESTER_WARNING)
								.bit(1, HyperCubeInverter.AlarmChannelId.AC_SURGE_ARRESTER_WARNING)
								.bit(10, HyperCubeInverter.AlarmChannelId.DC_RELAY_OPEN_CIRCUIT_FAULT)
								.bit(11, HyperCubeInverter.AlarmChannelId.DC_RELAY_SHORT_CIRCUIT_FAULT)
								.bit(13, HyperCubeInverter.AlarmChannelId.POWER_SUPPLY_15V_FAULT)
								.bit(14, HyperCubeInverter.AlarmChannelId.POWER_SUPPLY_24V_FAULT)
						),
						new DummyRegisterElement(3025),
						m(new BitsWordElement(3026, this)
								.bit(4, HyperCubeInverter.AlarmChannelId.DC_BUS1_SHORT_CIRCUIT_FAULT)
								.bit(5, HyperCubeInverter.AlarmChannelId.DC_BUS2_SHORT_CIRCUIT_FAULT)
								.bit(6, HyperCubeInverter.AlarmChannelId.DC_BUS3_SHORT_CIRCUIT_FAULT)
								.bit(7, HyperCubeInverter.AlarmChannelId.DC_BUS4_SHORT_CIRCUIT_FAULT)
								.bit(8, HyperCubeInverter.AlarmChannelId.GRID_RELAY_L1_L2_SHORT_CIRCUIT_FAULT)
								.bit(9, HyperCubeInverter.AlarmChannelId.GRID_RELAY_L2_L3_SHORT_CIRCUIT_FAULT)
								.bit(10, HyperCubeInverter.AlarmChannelId.GRID_RELAY_L3_L1_SHORT_CIRCUIT_FAULT)
								.bit(11, HyperCubeInverter.AlarmChannelId.GRID_RELAY_L1_L2_OPEN_CIRCUIT_FAULT)
								.bit(12, HyperCubeInverter.AlarmChannelId.GRID_RELAY_L2_L3_OPEN_CIRCUIT_FAULT)
								.bit(13, HyperCubeInverter.AlarmChannelId.GRID_RELAY_L3_L1_OPEN_CIRCUIT_FAULT)
						),
						new DummyRegisterElement(3027),
						m(new BitsWordElement(3028, this)
								.bit(0, HyperCubeInverter.AlarmChannelId.BUS_VOLTAGE_IMBALANCE_FAULT)
								.bit(1, HyperCubeInverter.AlarmChannelId.HIGH_POSITIVE_BUS_VOLTAGE_FAULT)
								.bit(2, HyperCubeInverter.AlarmChannelId.HIGH_NEGATIVE_BUS_VOLTAGE_FAULT)
								.bit(10, HyperCubeInverter.AlarmChannelId.LOW_EFFICIENCY_FAULT)
								.bit(11, HyperCubeInverter.AlarmChannelId.HIGH_DC_BUS1_HARDWARE_VOLTAGE_FAULT)
								.bit(12, HyperCubeInverter.AlarmChannelId.HIGH_DC_BUS2_HARDWARE_VOLTAGE_FAULT)
								.bit(13, HyperCubeInverter.AlarmChannelId.HIGH_DC_BUS3_HARDWARE_VOLTAGE_FAULT)
								.bit(14, HyperCubeInverter.AlarmChannelId.HIGH_DC_BUS4_HARDWARE_VOLTAGE_FAULT)
						),
						new DummyRegisterElement(3029),
						m(new BitsWordElement(3030, this)
								.bit(0, HyperCubeInverter.AlarmChannelId.INVERTER_FAILURE)
								.bit(1, HyperCubeInverter.AlarmChannelId.INVERTER_SOFT_START_COMMUNICATION_FAULT)
								.bit(8, HyperCubeInverter.AlarmChannelId.INVERTER_COOLING_FAN_WARNING)
								.bit(9, HyperCubeInverter.AlarmChannelId.INVERTER_IGBT_FAN_WARNING)
								.bit(12, HyperCubeInverter.AlarmChannelId.INVERTER_VOLTAGE_L1_L2_FAULT)
								.bit(13, HyperCubeInverter.AlarmChannelId.INVERTER_VOLTAGE_L2_L3_FAULT)
								.bit(14, HyperCubeInverter.AlarmChannelId.INVERTER_VOLTAGE_L3_L1_FAULT)
								.bit(15, HyperCubeInverter.AlarmChannelId.MISSING_N_LINE_FAULT)
						),
						m(new BitsWordElement(3031, this)
								.bit(0, HyperCubeInverter.AlarmChannelId.LIMITING_N_LINE_CURRENT_WARNING)
								.bit(1, HyperCubeInverter.AlarmChannelId.HIGH_N_LINE_CURRENT_FAULT)
								.bit(4, HyperCubeInverter.AlarmChannelId.INDUCTOR_CURRENT_BRANCH1_L1_FAULT)
								.bit(5, HyperCubeInverter.AlarmChannelId.INDUCTOR_CURRENT_BRANCH1_L2_FAULT)
								.bit(6, HyperCubeInverter.AlarmChannelId.INDUCTOR_CURRENT_BRANCH1_L3_FAULT)
								.bit(7, HyperCubeInverter.AlarmChannelId.INDUCTOR_CURRENT_BRANCH2_L1_FAULT)
								.bit(8, HyperCubeInverter.AlarmChannelId.INDUCTOR_CURRENT_BRANCH2_L2_FAULT)
								.bit(9, HyperCubeInverter.AlarmChannelId.INDUCTOR_CURRENT_BRANCH2_L3_FAULT)
						),
						new DummyRegisterElement(3032, 3035),
						m(new BitsWordElement(3036, this)
								.bit(0, HyperCubeInverter.AlarmChannelId.HIGH_CABIN_TEMPERATURE_FAULT)
								.bit(1, HyperCubeInverter.AlarmChannelId.HIGH_DISCHARGE_RESISTOR_TEMPERATURE_FAULT)
								.bit(2, HyperCubeInverter.AlarmChannelId.HIGH_IGBT_TEMPERATURE_FAULT)
								.bit(6, HyperCubeInverter.AlarmChannelId.CABIN_TEMPERATURE_SENSOR_WARNING)
								.bit(7, HyperCubeInverter.AlarmChannelId.LOCAL_EPO_FAULT)
								.bit(8, HyperCubeInverter.AlarmChannelId.HIGH_IGBT_BRANCH1_L1_TEMPERATURE_WARNING)
								.bit(9, HyperCubeInverter.AlarmChannelId.HIGH_IGBT_BRANCH2_L1_TEMPERATURE_WARNING)
								.bit(10, HyperCubeInverter.AlarmChannelId.HIGH_IGBT_BRANCH1_L2_TEMPERATURE_WARNING)
								.bit(11, HyperCubeInverter.AlarmChannelId.HIGH_IGBT_BRANCH2_L2_TEMPERATURE_WARNING)
								.bit(12, HyperCubeInverter.AlarmChannelId.HIGH_IGBT_BRANCH1_L3_TEMPERATURE_WARNING)
								.bit(13, HyperCubeInverter.AlarmChannelId.HIGH_IGBT_BRANCH2_L3_TEMPERATURE_WARNING)
								.bit(14, HyperCubeInverter.AlarmChannelId.REMOTE_EPO_FAULT)
								.bit(15, HyperCubeInverter.AlarmChannelId.HIGH_SOFT_START_RESISTOR_TEMPERATURE_FAULT)
						),
						new DummyRegisterElement(3037, 3038),
						m(new BitsWordElement(3039, this)
								.bit(0, HyperCubeInverter.ChannelId.COMMUNICATION_ABNORMAL)
								.bit(1, HyperCubeInverter.ChannelId.COMMUNICATION_CONNECTED)
								.bit(2, HyperCubeInverter.ChannelId.COMMUNICATION_ENABLED)
								.bit(3, HyperCubeInverter.ChannelId.COMMUNICATION_FAULT)
						)),

				new FC4ReadInputRegistersTask(3076, Priority.LOW,
						new DummyRegisterElement(3076),
						m(new BitsWordElement(3077, this)
								.bit(0, HyperCubeInverter.AlarmChannelId.DSP_ARM_COMMUNICATION_FAULT)
								.bit(2, HyperCubeInverter.AlarmChannelId.CARRIER_SYNC_FAULT)
								.bit(3, HyperCubeInverter.AlarmChannelId.POWER_FREQUENCY_SYNC_FAULT)
								.bit(4, HyperCubeInverter.AlarmChannelId.MODULE_ID_CONFLICT_FAULT)
								.bit(7, HyperCubeInverter.AlarmChannelId.DSP_FPGA_VERSION_MISMATCH_WARNING)
						),
						m(new BitsWordElement(3078, this)
								.bit(3, HyperCubeInverter.AlarmChannelId.WAVE_LIMIT_BRANCH1_L1_WARNING)
								.bit(5, HyperCubeInverter.AlarmChannelId.WAVE_LIMIT_BRANCH1_L2_WARNING)
								.bit(7, HyperCubeInverter.AlarmChannelId.WAVE_LIMIT_BRANCH1_L3_WARNING)
								.bit(9, HyperCubeInverter.AlarmChannelId.WAVE_LIMIT_BRANCH2_L1_WARNING)
								.bit(11, HyperCubeInverter.AlarmChannelId.WAVE_LIMIT_BRANCH2_L2_WARNING)
								.bit(13, HyperCubeInverter.AlarmChannelId.WAVE_LIMIT_BRANCH2_L3_WARNING)
						),
						m(new BitsWordElement(3079, this)
								.bit(0, HyperCubeInverter.AlarmChannelId.HIGH_GRID_VOLTAGE_LEVEL1_FAULT)
								.bit(1, HyperCubeInverter.AlarmChannelId.HIGH_GRID_VOLTAGE_LEVEL2_FAULT)
								.bit(2, HyperCubeInverter.AlarmChannelId.HIGH_GRID_VOLTAGE_LEVEL3_FAULT)
								.bit(3, HyperCubeInverter.AlarmChannelId.HIGH_GRID_VOLTAGE_LEVEL4_FAULT)
								.bit(4, HyperCubeInverter.AlarmChannelId.HIGH_GRID_VOLTAGE_LEVEL5_FAULT)
								.bit(5, HyperCubeInverter.AlarmChannelId.LOW_GRID_VOLTAGE_LEVEL1_FAULT)
								.bit(6, HyperCubeInverter.AlarmChannelId.LOW_GRID_VOLTAGE_LEVEL2_FAULT)
								.bit(7, HyperCubeInverter.AlarmChannelId.LOW_GRID_VOLTAGE_LEVEL3_FAULT)
								.bit(8, HyperCubeInverter.AlarmChannelId.LOW_GRID_VOLTAGE_LEVEL4_FAULT)
								.bit(9, HyperCubeInverter.AlarmChannelId.LOW_GRID_VOLTAGE_LEVEL5_FAULT)
								.bit(10, HyperCubeInverter.AlarmChannelId.HIGH_GRID_FREQUENCY_LEVEL1_FAULT)
								.bit(11, HyperCubeInverter.AlarmChannelId.HIGH_GRID_FREQUENCY_LEVEL2_FAULT)
								.bit(12, HyperCubeInverter.AlarmChannelId.HIGH_GRID_FREQUENCY_LEVEL3_FAULT)
								.bit(13, HyperCubeInverter.AlarmChannelId.HIGH_GRID_FREQUENCY_LEVEL4_FAULT)
								.bit(14, HyperCubeInverter.AlarmChannelId.HIGH_GRID_FREQUENCY_LEVEL5_FAULT)
								.bit(15, HyperCubeInverter.AlarmChannelId.LOW_GRID_FREQUENCY_LEVEL1_FAULT)
						),
						m(new BitsWordElement(3080, this)
								.bit(0, HyperCubeInverter.AlarmChannelId.LOW_GRID_FREQUENCY_LEVEL2_FAULT)
								.bit(1, HyperCubeInverter.AlarmChannelId.LOW_GRID_FREQUENCY_LEVEL3_FAULT)
								.bit(2, HyperCubeInverter.AlarmChannelId.LOW_GRID_FREQUENCY_LEVEL4_FAULT)
								.bit(3, HyperCubeInverter.AlarmChannelId.LOW_GRID_FREQUENCY_LEVEL5_FAULT)
						),
						m(new BitsWordElement(3081, this)
								.bit(0, HyperCubeInverter.AlarmChannelId.HIGH_MEAN_VOLTAGE_FAULT)
								.bit(1, HyperCubeInverter.AlarmChannelId.CT_PHASE_REVERSAL_WARNING)
								.bit(2, HyperCubeInverter.AlarmChannelId.CT_DETECTION_WARNING)
								.bit(3, HyperCubeInverter.AlarmChannelId.DETECTION_BOX_WARNING)
								.bit(13, HyperCubeInverter.AlarmChannelId.ANTI_BACKFLOW_OVERLIMIT_FAULT)
								.bit(14, HyperCubeInverter.AlarmChannelId.ANTI_BACKFLOW_METER_COMMUNICATION_WARNING)
								.bit(15, HyperCubeInverter.AlarmChannelId.ANTI_BACKFLOW_METER_COMMUNICATION_FAULT)
						),
						m(new BitsWordElement(3082, this)
								.bit(0, HyperCubeInverter.AlarmChannelId.HOST_COMMUNICATION_WARNING)
								.bit(1, HyperCubeInverter.AlarmChannelId.BCMS_COMMUNICATION_FAULT)
								.bit(2, HyperCubeInverter.AlarmChannelId.DSP_COMMUNICATION_FAULT)
								.bit(3, HyperCubeInverter.AlarmChannelId.BAMS_COMMUNICATION_FAULT)
								.bit(4, HyperCubeInverter.AlarmChannelId.ETHERNET_COMMUNICATION_FAULT)
								.bit(6, HyperCubeInverter.AlarmChannelId.BCMS_ETH_COMMUNICATION_FAULT)
								.bit(12, HyperCubeInverter.AlarmChannelId.MODULE_MODEL_MISMATCH_FAULT)
								.bit(13, HyperCubeInverter.AlarmChannelId.INTERNAL_PARAMETER_MISMATCH_FAULT)
								.bit(14, HyperCubeInverter.AlarmChannelId.FLASH_STORAGE_FAULT)
								.bit(15, HyperCubeInverter.AlarmChannelId.RTC_INIT_WARNING)
						))
		);
	}

	@Override
	public String debugLog() {
		return PowerConversionSystem.generateDebugLog(this);
	}

}
