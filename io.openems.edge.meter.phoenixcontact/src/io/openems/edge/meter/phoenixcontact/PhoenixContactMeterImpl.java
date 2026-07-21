package io.openems.edge.meter.phoenixcontact;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.INVERT_IF_TRUE;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;
import static io.openems.edge.bridge.modbus.api.element.WordOrder.LSWMSW;

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

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.MeterType;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.FloatDoublewordElement;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveNatureTable;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.meter.api.ElectricityMeter;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Meter.PhoenixContact", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class PhoenixContactMeterImpl extends AbstractOpenemsModbusComponent
		implements ElectricityMeter, PhoenixContactMeter, ModbusComponent, OpenemsComponent, ModbusSlave {

	@Reference
	private ConfigurationAdmin cm;

	private MeterType type = MeterType.PRODUCTION;
	private boolean invert = false;
	private PhaseWiring phaseWiring = PhaseWiring.THREE_PHASE_FOUR_WIRE;

	public PhoenixContactMeterImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values(), //
				PhoenixContactMeter.ChannelId.values() //
		);
	}

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		this.type = config.type();
		this.invert = config.invert();
		this.phaseWiring = config.phaseWiring();

		if (super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm,
				"Modbus", config.modbus_id())) {
			return;
		}

		if (this.phaseWiring == PhaseWiring.THREE_PHASE_THREE_WIRE) {
			this.calculatePhaseVoltagesFromLineToLine();
		}
		ElectricityMeter.calculateAverageVoltageFromPhases(this);
		ElectricityMeter.calculateSumCurrentFromPhases(this);
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	/**
	 * Under 3P3W the device provides no phase (L-N) voltages. Derive them from the
	 * line-to-line voltages as L-L / √3, assuming a symmetrical system.
	 */
	private void calculatePhaseVoltagesFromLineToLine() {
		this.getVoltageL1L2Channel().onSetNextValue(v -> this._setVoltageL1(divideBySqrt3(v.get())));
		this.getVoltageL2L3Channel().onSetNextValue(v -> this._setVoltageL2(divideBySqrt3(v.get())));
		this.getVoltageL3L1Channel().onSetNextValue(v -> this._setVoltageL3(divideBySqrt3(v.get())));
	}

	private static Integer divideBySqrt3(Integer value) {
		if (value == null) {
			return null;
		}
		return (int) Math.round(value / Math.sqrt(3));
	}

	private static FloatDoublewordElement FLOAT32(int address) {
		return new FloatDoublewordElement(address).wordOrder(LSWMSW);
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		var modbusProtocol = new ModbusProtocol(this, //
				new FC4ReadInputRegistersTask(0x9306, Priority.LOW, //
						m(this.invert ? ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY
								: ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, //
								FLOAT32(0x9306)),
						new DummyRegisterElement(0x9308, 0x930D),
						m(this.invert ? ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY
								: ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, //
								FLOAT32(0x930E))
				),
				new FC4ReadInputRegistersTask(0x9350, Priority.LOW, //
						m(this.invert ? PhoenixContactMeter.ChannelId.REACTIVE_LEADING_ENERGY
								: PhoenixContactMeter.ChannelId.REACTIVE_LAGGING_ENERGY, //
								FLOAT32(0x9350)),
						m(this.invert ? PhoenixContactMeter.ChannelId.REACTIVE_LAGGING_ENERGY
								: PhoenixContactMeter.ChannelId.REACTIVE_LEADING_ENERGY, //
								FLOAT32(0x9352))
				));

		switch (this.phaseWiring) {
		case THREE_PHASE_FOUR_WIRE -> {
			modbusProtocol.addTask(new FC4ReadInputRegistersTask(0x8000, Priority.HIGH, //
					m(PhoenixContactMeter.ChannelId.VOLTAGE_L1_L2, FLOAT32(0x8000), SCALE_FACTOR_3),
					m(PhoenixContactMeter.ChannelId.VOLTAGE_L2_L3, FLOAT32(0x8002), SCALE_FACTOR_3),
					m(PhoenixContactMeter.ChannelId.VOLTAGE_L3_L1, FLOAT32(0x8004), SCALE_FACTOR_3),
					m(ElectricityMeter.ChannelId.VOLTAGE_L1, FLOAT32(0x8006), SCALE_FACTOR_3),
					m(ElectricityMeter.ChannelId.VOLTAGE_L2, FLOAT32(0x8008), SCALE_FACTOR_3),
					m(ElectricityMeter.ChannelId.VOLTAGE_L3, FLOAT32(0x800A), SCALE_FACTOR_3),
					m(ElectricityMeter.ChannelId.FREQUENCY, FLOAT32(0x800C), SCALE_FACTOR_3),
					m(ElectricityMeter.ChannelId.CURRENT_L1, FLOAT32(0x800E), SCALE_FACTOR_3),
					m(ElectricityMeter.ChannelId.CURRENT_L2, FLOAT32(0x8010), SCALE_FACTOR_3),
					m(ElectricityMeter.ChannelId.CURRENT_L3, FLOAT32(0x8012), SCALE_FACTOR_3),
					new DummyRegisterElement(0x8014, 0x8015), // Current IN
					m(ElectricityMeter.ChannelId.ACTIVE_POWER, FLOAT32(0x8016), INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.REACTIVE_POWER, FLOAT32(0x8018), INVERT_IF_TRUE(this.invert)),
					new DummyRegisterElement(0x801A, 0x801B), // Total apparent power
					m(PhoenixContactMeter.ChannelId.POWER_FACTOR, FLOAT32(0x801C)),
					m(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, FLOAT32(0x801E), INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.ACTIVE_POWER_L2, FLOAT32(0x8020), INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.ACTIVE_POWER_L3, FLOAT32(0x8022), INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.REACTIVE_POWER_L1, FLOAT32(0x8024), INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.REACTIVE_POWER_L2, FLOAT32(0x8026), INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.REACTIVE_POWER_L3, FLOAT32(0x8028), INVERT_IF_TRUE(this.invert)),
					new DummyRegisterElement(0x802A, 0x802F), // Per-phase apparent power
					m(PhoenixContactMeter.ChannelId.POWER_FACTOR_L1, FLOAT32(0x8030)),
					m(PhoenixContactMeter.ChannelId.POWER_FACTOR_L2, FLOAT32(0x8032)),
					m(PhoenixContactMeter.ChannelId.POWER_FACTOR_L3, FLOAT32(0x8034))
			));
			modbusProtocol.addTask(new FC4ReadInputRegistersTask(0x8043, Priority.LOW, //
					m(PhoenixContactMeter.ChannelId.PHASE_ANGLE, FLOAT32(0x8043)),
					m(PhoenixContactMeter.ChannelId.PHASE_ANGLE_L1, FLOAT32(0x8045)),
					m(PhoenixContactMeter.ChannelId.PHASE_ANGLE_L2, FLOAT32(0x8047)),
					m(PhoenixContactMeter.ChannelId.PHASE_ANGLE_L3, FLOAT32(0x8049))
			));
			modbusProtocol.addTask(new FC4ReadInputRegistersTask(0x8806, Priority.LOW, //
					m(PhoenixContactMeter.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L1, FLOAT32(0x8806)),
					m(PhoenixContactMeter.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L2, FLOAT32(0x8808)),
					m(PhoenixContactMeter.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L3, FLOAT32(0x880A)),
					m(PhoenixContactMeter.ChannelId.CURRENT_HARMONIC_DISTORTION_L1, FLOAT32(0x880C)),
					m(PhoenixContactMeter.ChannelId.CURRENT_HARMONIC_DISTORTION_L2, FLOAT32(0x880E)),
					m(PhoenixContactMeter.ChannelId.CURRENT_HARMONIC_DISTORTION_L3, FLOAT32(0x8810))
			));
		}
		case THREE_PHASE_THREE_WIRE -> {
			// 3P3W: no phase voltages, no per-phase powers/power-factors.
			modbusProtocol.addTask(new FC4ReadInputRegistersTask(0x8000, Priority.HIGH, //
					m(PhoenixContactMeter.ChannelId.VOLTAGE_L1_L2, FLOAT32(0x8000), SCALE_FACTOR_3),
					m(PhoenixContactMeter.ChannelId.VOLTAGE_L2_L3, FLOAT32(0x8002), SCALE_FACTOR_3),
					m(PhoenixContactMeter.ChannelId.VOLTAGE_L3_L1, FLOAT32(0x8004), SCALE_FACTOR_3),
					new DummyRegisterElement(0x8006, 0x800B), // Phase voltages
					m(ElectricityMeter.ChannelId.FREQUENCY, FLOAT32(0x800C), SCALE_FACTOR_3),
					m(ElectricityMeter.ChannelId.CURRENT_L1, FLOAT32(0x800E), SCALE_FACTOR_3),
					m(ElectricityMeter.ChannelId.CURRENT_L2, FLOAT32(0x8010), SCALE_FACTOR_3),
					m(ElectricityMeter.ChannelId.CURRENT_L3, FLOAT32(0x8012), SCALE_FACTOR_3),
					new DummyRegisterElement(0x8014, 0x8015), // Current IN
					m(ElectricityMeter.ChannelId.ACTIVE_POWER, FLOAT32(0x8016), INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.REACTIVE_POWER, FLOAT32(0x8018), INVERT_IF_TRUE(this.invert)),
					new DummyRegisterElement(0x801A, 0x801B), // Total apparent power
					m(PhoenixContactMeter.ChannelId.POWER_FACTOR, FLOAT32(0x801C))
			));
			modbusProtocol.addTask(new FC4ReadInputRegistersTask(0x8043, Priority.LOW, //
					m(PhoenixContactMeter.ChannelId.PHASE_ANGLE, FLOAT32(0x8043))
			));
			// 3P3W: THD of U31 (0x8804) and I2 (0x880E) are not provided by the device.
			modbusProtocol.addTask(new FC4ReadInputRegistersTask(0x8800, Priority.LOW, //
					m(PhoenixContactMeter.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L1_L2, FLOAT32(0x8800)),
					m(PhoenixContactMeter.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L2_L3, FLOAT32(0x8802)),
					new DummyRegisterElement(0x8804, 0x880B),
					m(PhoenixContactMeter.ChannelId.CURRENT_HARMONIC_DISTORTION_L1, FLOAT32(0x880C)),
					new DummyRegisterElement(0x880E, 0x880F),
					m(PhoenixContactMeter.ChannelId.CURRENT_HARMONIC_DISTORTION_L3, FLOAT32(0x8810))
			));
		}
		case SINGLE_PHASE_TWO_WIRE -> {
			// 1P2W (e.g. EEM-XM157): only L1 values are available.
			modbusProtocol.addTask(new FC4ReadInputRegistersTask(0x8006, Priority.HIGH, //
					m(ElectricityMeter.ChannelId.VOLTAGE_L1, FLOAT32(0x8006), SCALE_FACTOR_3),
					new DummyRegisterElement(0x8008, 0x800B), // Phase voltages L2, L3
					m(ElectricityMeter.ChannelId.FREQUENCY, FLOAT32(0x800C), SCALE_FACTOR_3),
					m(ElectricityMeter.ChannelId.CURRENT_L1, FLOAT32(0x800E), SCALE_FACTOR_3),
					new DummyRegisterElement(0x8010, 0x8015), // Currents I2, I3, IN
					m(ElectricityMeter.ChannelId.ACTIVE_POWER, FLOAT32(0x8016), INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.REACTIVE_POWER, FLOAT32(0x8018), INVERT_IF_TRUE(this.invert)),
					new DummyRegisterElement(0x801A, 0x801B), // Total apparent power
					m(PhoenixContactMeter.ChannelId.POWER_FACTOR, FLOAT32(0x801C)),
					m(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, FLOAT32(0x801E), INVERT_IF_TRUE(this.invert)),
					new DummyRegisterElement(0x8020, 0x8023), // Active power L2, L3
					m(ElectricityMeter.ChannelId.REACTIVE_POWER_L1, FLOAT32(0x8024), INVERT_IF_TRUE(this.invert)),
					new DummyRegisterElement(0x8026, 0x802F), // Reactive power L2, L3; apparent powers
					m(PhoenixContactMeter.ChannelId.POWER_FACTOR_L1, FLOAT32(0x8030))
			));
			modbusProtocol.addTask(new FC4ReadInputRegistersTask(0x8043, Priority.LOW, //
					m(PhoenixContactMeter.ChannelId.PHASE_ANGLE, FLOAT32(0x8043)),
					m(PhoenixContactMeter.ChannelId.PHASE_ANGLE_L1, FLOAT32(0x8045))
			));
			modbusProtocol.addTask(new FC4ReadInputRegistersTask(0x8806, Priority.LOW, //
					m(PhoenixContactMeter.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L1, FLOAT32(0x8806)),
					new DummyRegisterElement(0x8808, 0x880B), // THD phase voltages L2, L3
					m(PhoenixContactMeter.ChannelId.CURRENT_HARMONIC_DISTORTION_L1, FLOAT32(0x880C))
			));
		}
		}

		return modbusProtocol;
	}

	@Override
	public String debugLog() {
		return "L:" + this.getActivePower().asString();
	}

	@Override
	public MeterType getMeterType() {
		return this.type;
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(//
				OpenemsComponent.getModbusSlaveNatureTable(accessMode), //
				ElectricityMeter.getModbusSlaveNatureTable(accessMode), //
				ModbusSlaveNatureTable.of(PhoenixContactMeter.class, accessMode, 100) //
						.build());
	}
}
