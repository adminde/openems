package io.openems.edge.meter.acrel.adl400;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3_AND_INVERT_IF_TRUE;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_3;
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
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveNatureTable;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.meter.api.ElectricityMeter;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Meter.Acrel.Adl400", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class AcrelAdl400MeterImpl extends AbstractOpenemsModbusComponent
		implements ElectricityMeter, AcrelAdl400Meter, ModbusComponent, OpenemsComponent, ModbusSlave {

	@Reference
	private ConfigurationAdmin cm;

	private MeterType type = MeterType.PRODUCTION;
	private boolean invert = false;
	private PhaseWiring phaseWiring = PhaseWiring.THREE_PHASE_FOUR_WIRE;

	public AcrelAdl400MeterImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values(), //
				AcrelAdl400Meter.ChannelId.values() //
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
			AcrelAdl400Meter.calculatePhaseVoltages(this);
		}
		ElectricityMeter.calculateAverageVoltageFromPhases(this);
		ElectricityMeter.calculateSumCurrentFromPhases(this);
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public MeterType getMeterType() {
		return this.type;
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		var modbusProtocol = new ModbusProtocol(this, //
				new FC3ReadRegistersTask(0x05DD, Priority.LOW, //
						m(AcrelAdl400Meter.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L1, new UnsignedWordElement(0x05DD),
								SCALE_FACTOR_MINUS_3),
						m(AcrelAdl400Meter.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L2, new UnsignedWordElement(0x05DE),
								SCALE_FACTOR_MINUS_3),
						m(AcrelAdl400Meter.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L3, new UnsignedWordElement(0x05DF),
								SCALE_FACTOR_MINUS_3),
						m(AcrelAdl400Meter.ChannelId.CURRENT_HARMONIC_DISTORTION_L1, new UnsignedWordElement(0x05E0),
								SCALE_FACTOR_MINUS_3),
						m(AcrelAdl400Meter.ChannelId.CURRENT_HARMONIC_DISTORTION_L2, new UnsignedWordElement(0x05E1),
								SCALE_FACTOR_MINUS_3),
						m(AcrelAdl400Meter.ChannelId.CURRENT_HARMONIC_DISTORTION_L3, new UnsignedWordElement(0x05E2),
								SCALE_FACTOR_MINUS_3)
				),
				new FC3ReadRegistersTask(0x084C, Priority.LOW, //
						m(this.invert ? ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY
								: ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY,
								UINT32(0x084C), SCALE_FACTOR_2),
						new DummyRegisterElement(0x084E, 0x0855),
						m(this.invert ? ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY
								: ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY,
								UINT32(0x0856), SCALE_FACTOR_2),
						new DummyRegisterElement(0x0858, 0x0869),
						m(this.invert ? AcrelAdl400Meter.ChannelId.REACTIVE_LEADING_ENERGY
								: AcrelAdl400Meter.ChannelId.REACTIVE_LAGGING_ENERGY,
								UINT32(0x086A), SCALE_FACTOR_2),
						new DummyRegisterElement(0x086C, 0x0873),
						m(this.invert ? AcrelAdl400Meter.ChannelId.REACTIVE_LAGGING_ENERGY
								: AcrelAdl400Meter.ChannelId.REACTIVE_LEADING_ENERGY,
								UINT32(0x0874), SCALE_FACTOR_2)
				)
		);
		if (this.phaseWiring == PhaseWiring.THREE_PHASE_FOUR_WIRE) {
			modbusProtocol.addTask(new FC3ReadRegistersTask(0x0800, Priority.HIGH, //
					m(ElectricityMeter.ChannelId.VOLTAGE_L1, FLOAT32(0x0800), SCALE_FACTOR_3),
					m(ElectricityMeter.ChannelId.VOLTAGE_L2, FLOAT32(0x0802), SCALE_FACTOR_3),
					m(ElectricityMeter.ChannelId.VOLTAGE_L3, FLOAT32(0x0804), SCALE_FACTOR_3),
					m(AcrelAdl400Meter.ChannelId.VOLTAGE_L1_L2, FLOAT32(0x0806), SCALE_FACTOR_3),
					m(AcrelAdl400Meter.ChannelId.VOLTAGE_L2_L3, FLOAT32(0x0808), SCALE_FACTOR_3),
					m(AcrelAdl400Meter.ChannelId.VOLTAGE_L3_L1, FLOAT32(0x080A), SCALE_FACTOR_3),
					m(ElectricityMeter.ChannelId.CURRENT_L1, FLOAT32(0x080C),
							SCALE_FACTOR_3_AND_INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.CURRENT_L2, FLOAT32(0x080E),
							SCALE_FACTOR_3_AND_INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.CURRENT_L3, FLOAT32(0x0810),
							SCALE_FACTOR_3_AND_INVERT_IF_TRUE(this.invert)),
					new DummyRegisterElement(0x0812, 0x0813),
					m(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, FLOAT32(0x0814),
							SCALE_FACTOR_3_AND_INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.ACTIVE_POWER_L2, FLOAT32(0x0816),
							SCALE_FACTOR_3_AND_INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.ACTIVE_POWER_L3, FLOAT32(0x0818),
							SCALE_FACTOR_3_AND_INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.ACTIVE_POWER, FLOAT32(0x081A),
							SCALE_FACTOR_3_AND_INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.REACTIVE_POWER_L1, FLOAT32(0x081C),
							SCALE_FACTOR_3_AND_INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.REACTIVE_POWER_L2, FLOAT32(0x081E),
							SCALE_FACTOR_3_AND_INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.REACTIVE_POWER_L3, FLOAT32(0x0820),
							SCALE_FACTOR_3_AND_INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.REACTIVE_POWER, FLOAT32(0x0822),
							SCALE_FACTOR_3_AND_INVERT_IF_TRUE(this.invert)),
					new DummyRegisterElement(0x0824, 0x082B),
					m(AcrelAdl400Meter.ChannelId.POWER_FACTOR_L1, FLOAT32(0x082C)),
					m(AcrelAdl400Meter.ChannelId.POWER_FACTOR_L2, FLOAT32(0x082E)),
					m(AcrelAdl400Meter.ChannelId.POWER_FACTOR_L3, FLOAT32(0x0830)),
					m(AcrelAdl400Meter.ChannelId.POWER_FACTOR, FLOAT32(0x0832)),
					m(ElectricityMeter.ChannelId.FREQUENCY, FLOAT32(0x0834), SCALE_FACTOR_3)
			));
		} else {
			// 3P3W: No per-phase voltage, -power or power-factors. Only line-to-line voltage and totals.
			modbusProtocol.addTask(new FC3ReadRegistersTask(0x0800, Priority.HIGH, //
					new DummyRegisterElement(0x0800, 0x0805),
					m(AcrelAdl400Meter.ChannelId.VOLTAGE_L1_L2, FLOAT32(0x0806), SCALE_FACTOR_3),
					m(AcrelAdl400Meter.ChannelId.VOLTAGE_L2_L3, FLOAT32(0x0808), SCALE_FACTOR_3),
					m(AcrelAdl400Meter.ChannelId.VOLTAGE_L3_L1, FLOAT32(0x080A), SCALE_FACTOR_3),
					m(ElectricityMeter.ChannelId.CURRENT_L1, FLOAT32(0x080C),
							SCALE_FACTOR_3_AND_INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.CURRENT_L2, FLOAT32(0x080E),
							SCALE_FACTOR_3_AND_INVERT_IF_TRUE(this.invert)),
					m(ElectricityMeter.ChannelId.CURRENT_L3, FLOAT32(0x0810),
							SCALE_FACTOR_3_AND_INVERT_IF_TRUE(this.invert)),
					new DummyRegisterElement(0x0812, 0x0819),
					m(ElectricityMeter.ChannelId.ACTIVE_POWER, FLOAT32(0x081A),
							SCALE_FACTOR_3_AND_INVERT_IF_TRUE(this.invert)),
					new DummyRegisterElement(0x081C, 0x0821),
					m(ElectricityMeter.ChannelId.REACTIVE_POWER, FLOAT32(0x0822),
							SCALE_FACTOR_3_AND_INVERT_IF_TRUE(this.invert)),
					new DummyRegisterElement(0x0824, 0x0833),
					m(ElectricityMeter.ChannelId.FREQUENCY, FLOAT32(0x0834), SCALE_FACTOR_3)
			));
		}
		return modbusProtocol;
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(//
				OpenemsComponent.getModbusSlaveNatureTable(accessMode), //
				ElectricityMeter.getModbusSlaveNatureTable(accessMode), //
				ModbusSlaveNatureTable.of(AcrelAdl400Meter.class, accessMode, 100).build() //
		);
	}

	@Override
	public String debugLog() {
		return "L:" + this.getActivePower().asString();
	}

	private static FloatDoublewordElement FLOAT32(int address) {
		return new FloatDoublewordElement(address).wordOrder(LSWMSW);
	}

	private static UnsignedDoublewordElement UINT32(int address) {
		return new UnsignedDoublewordElement(address).wordOrder(LSWMSW);
	}
}
