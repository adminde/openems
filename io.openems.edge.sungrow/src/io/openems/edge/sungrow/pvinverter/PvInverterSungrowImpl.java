package io.openems.edge.sungrow.pvinverter;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SET_NULL_FOR_DEFAULT;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.chain;
import static io.openems.edge.bridge.modbus.api.element.WordOrder.LSWMSW;
import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.referencetarget.GenerateTargetsFromReferences;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ChannelMetaInfoReadAndWrite;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.SignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC16WriteRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "PV-Inverter.Sungrow", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = { //
				"type=PRODUCTION" //
		})
@EventTopics({ //
		TOPIC_CYCLE_EXECUTE_WRITE //
})
@GenerateTargetsFromReferences("Modbus")
public class PvInverterSungrowImpl extends AbstractOpenemsModbusComponent implements PvInverterSungrow,
		ManagedSymmetricPvInverter, ElectricityMeter, ModbusComponent, OpenemsComponent, EventHandler, ModbusSlave {

	/**
	 * The protocol documents register addresses starting at 1 while Modbus
	 * transmits them starting at 0.
	 */
	private static final int OFFSET = 1;

	private final SetPvLimitHandler setPvLimitHandler = new SetPvLimitHandler(this);

	private Config config;

	@Override
	@Reference(//
			policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY, //
			target = "(&(id=${config.modbus_id})(enabled=true))")
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public PvInverterSungrowImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values(), //
				ManagedSymmetricPvInverter.ChannelId.values(), //
				PvInverterSungrow.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		this.config = config;
		super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId());

		// The inverter reports only its rated active power, which is used as rated
		// apparent power as well.
		this.getMaxActivePowerChannel().onSetNextValue(value -> this._setMaxApparentPower(value.get()));
		if (config.phaseWiring() == PhaseWiring.THREE_PHASE_THREE_WIRE) {
			PvInverterSungrow.calculatePhaseVoltagesFromLineVoltages(this);
		}
		this.getOutputTypeChannel().onSetNextValue(value -> this.detectWrongPhaseWiring(value.asEnum()));
		ElectricityMeter.calculatePhasesFromActivePower(this);
		ElectricityMeter.calculateSumCurrentFromPhases(this);
		ElectricityMeter.calculateAverageVoltageFromPhases(this);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	private void detectWrongPhaseWiring(OutputType outputType) {
		var wrongPhaseWiring = switch (outputType) {
		case THREE_PHASE_FOUR_LINE -> this.config.phaseWiring() != PhaseWiring.THREE_PHASE_FOUR_WIRE;
		case THREE_PHASE_THREE_LINE -> this.config.phaseWiring() != PhaseWiring.THREE_PHASE_THREE_WIRE;
		default -> false;
		};
		this._setWrongPhaseWiringConfigured(wrongPhaseWiring);
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		if (event.getTopic().equals(TOPIC_CYCLE_EXECUTE_WRITE)) {
			this.applyActivePowerLimit();
		}
	}

	private void applyActivePowerLimit() {
		var activePowerLimit = this.getActivePowerLimitChannel().getNextWriteValueAndReset();
		this._setReadOnlyModePvLimitFailed(this.config.readOnly() && activePowerLimit.isPresent());
		if (this.config.readOnly()) {
			return;
		}
		try {
			this.setPvLimitHandler.accept(activePowerLimit);
			this._setPvLimitFailed(false);
		} catch (OpenemsNamedException e) {
			this._setPvLimitFailed(true);
		}
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		final io.openems.edge.common.channel.ChannelId voltage1;
		final io.openems.edge.common.channel.ChannelId voltage2;
		final io.openems.edge.common.channel.ChannelId voltage3;
		if (this.config.phaseWiring() == PhaseWiring.THREE_PHASE_THREE_WIRE) {
			voltage1 = PvInverterSungrow.ChannelId.VOLTAGE_L1_L2;
			voltage2 = PvInverterSungrow.ChannelId.VOLTAGE_L2_L3;
			voltage3 = PvInverterSungrow.ChannelId.VOLTAGE_L3_L1;
		} else {
			voltage1 = ElectricityMeter.ChannelId.VOLTAGE_L1;
			voltage2 = ElectricityMeter.ChannelId.VOLTAGE_L2;
			voltage3 = ElectricityMeter.ChannelId.VOLTAGE_L3;
		}
		var protocol = new ModbusProtocol(this, //
				new FC4ReadInputRegistersTask(5000 - OFFSET, Priority.HIGH, //
						m(PvInverterSungrow.ChannelId.DEVICE_TYPE_CODE, new UnsignedWordElement(5000 - OFFSET)),
						m(ManagedSymmetricPvInverter.ChannelId.MAX_ACTIVE_POWER, new UnsignedWordElement(5001 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.OUTPUT_TYPE, new UnsignedWordElement(5002 - OFFSET)),
						m(PvInverterSungrow.ChannelId.DAILY_PRODUCTION_ENERGY, new UnsignedWordElement(5003 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, uint32(5004 - OFFSET),
								U32_SCALE_FACTOR_3_OR_NULL),
						m(PvInverterSungrow.ChannelId.TOTAL_RUNNING_TIME, uint32(5006 - OFFSET), U32_OR_NULL),
						m(PvInverterSungrow.ChannelId.INTERNAL_TEMPERATURE, new SignedWordElement(5008 - OFFSET),
								S16_OR_NULL),
						m(PvInverterSungrow.ChannelId.APPARENT_POWER, uint32(5009 - OFFSET), U32_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_1_VOLTAGE, new UnsignedWordElement(5011 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_1_CURRENT, new UnsignedWordElement(5012 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_2_VOLTAGE, new UnsignedWordElement(5013 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_2_CURRENT, new UnsignedWordElement(5014 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_3_VOLTAGE, new UnsignedWordElement(5015 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_3_CURRENT, new UnsignedWordElement(5016 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.DC_POWER, uint32(5017 - OFFSET), U32_OR_NULL),
						m(voltage1, new UnsignedWordElement(5019 - OFFSET), U16_SCALE_FACTOR_2_OR_NULL),
						m(voltage2, new UnsignedWordElement(5020 - OFFSET), U16_SCALE_FACTOR_2_OR_NULL),
						m(voltage3, new UnsignedWordElement(5021 - OFFSET), U16_SCALE_FACTOR_2_OR_NULL),
						m(ElectricityMeter.ChannelId.CURRENT_L1, new UnsignedWordElement(5022 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(ElectricityMeter.ChannelId.CURRENT_L2, new UnsignedWordElement(5023 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(ElectricityMeter.ChannelId.CURRENT_L3, new UnsignedWordElement(5024 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						new DummyRegisterElement(5025 - OFFSET, 5030 - OFFSET),
						m(ElectricityMeter.ChannelId.ACTIVE_POWER, uint32(5031 - OFFSET), U32_OR_NULL),
						m(ElectricityMeter.ChannelId.REACTIVE_POWER, int32(5033 - OFFSET), S32_OR_NULL),
						m(PvInverterSungrow.ChannelId.POWER_FACTOR, new SignedWordElement(5035 - OFFSET),
								S16_POWER_FACTOR_OR_NULL),
						m(ElectricityMeter.ChannelId.FREQUENCY, new UnsignedWordElement(5036 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						new DummyRegisterElement(5037 - OFFSET),
						m(PvInverterSungrow.ChannelId.WORK_STATE, new UnsignedWordElement(5038 - OFFSET))),
				new FC4ReadInputRegistersTask(5049 - OFFSET, Priority.LOW, //
						m(ManagedSymmetricPvInverter.ChannelId.MAX_REACTIVE_POWER,
								new UnsignedWordElement(5049 - OFFSET), U16_SCALE_FACTOR_2_OR_NULL)),
				new FC4ReadInputRegistersTask(5115 - OFFSET, Priority.LOW, //
						m(PvInverterSungrow.ChannelId.MPPT_4_VOLTAGE, new UnsignedWordElement(5115 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_4_CURRENT, new UnsignedWordElement(5116 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_5_VOLTAGE, new UnsignedWordElement(5117 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_5_CURRENT, new UnsignedWordElement(5118 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_6_VOLTAGE, new UnsignedWordElement(5119 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_6_CURRENT, new UnsignedWordElement(5120 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_7_VOLTAGE, new UnsignedWordElement(5121 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_7_CURRENT, new UnsignedWordElement(5122 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_8_VOLTAGE, new UnsignedWordElement(5123 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_8_CURRENT, new UnsignedWordElement(5124 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						new DummyRegisterElement(5125 - OFFSET, 5127 - OFFSET),
						m(PvInverterSungrow.ChannelId.MONTHLY_PRODUCTION_ENERGY, uint32(5128 - OFFSET),
								U32_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_9_VOLTAGE, new UnsignedWordElement(5130 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_9_CURRENT, new UnsignedWordElement(5131 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_10_VOLTAGE, new UnsignedWordElement(5132 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_10_CURRENT, new UnsignedWordElement(5133 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_11_VOLTAGE, new UnsignedWordElement(5134 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_11_CURRENT, new UnsignedWordElement(5135 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_12_VOLTAGE, new UnsignedWordElement(5136 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL),
						m(PvInverterSungrow.ChannelId.MPPT_12_CURRENT, new UnsignedWordElement(5137 - OFFSET),
								U16_SCALE_FACTOR_2_OR_NULL)));

		if (!this.config.readOnly()) {
			final ChannelMetaInfoReadAndWrite powerLimitationSwitchMeta = new ChannelMetaInfoReadAndWrite(
					5007 - OFFSET, 5007 - OFFSET);
			final ChannelMetaInfoReadAndWrite powerLimitationSettingsMeta = new ChannelMetaInfoReadAndWrite(
					5008 - OFFSET, 5008 - OFFSET);

			protocol.addTask(new FC3ReadRegistersTask(5007 - OFFSET, Priority.LOW, //
					m(PvInverterSungrow.ChannelId.POWER_LIMITATION_SWITCH, new UnsignedWordElement(5007 - OFFSET),
							powerLimitationSwitchMeta),
					m(PvInverterSungrow.ChannelId.POWER_LIMITATION_SETTING, new UnsignedWordElement(5008 - OFFSET),
							powerLimitationSettingsMeta)));

			protocol.addTask(new FC16WriteRegistersTask(5007 - OFFSET, //
					m(PvInverterSungrow.ChannelId.POWER_LIMITATION_SWITCH, new UnsignedWordElement(5007 - OFFSET),
							powerLimitationSwitchMeta),
					m(PvInverterSungrow.ChannelId.POWER_LIMITATION_SETTING, new UnsignedWordElement(5008 - OFFSET),
							powerLimitationSettingsMeta)));
		}
		return protocol;
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(//
				OpenemsComponent.getModbusSlaveNatureTable(accessMode), //
				ElectricityMeter.getModbusSlaveNatureTable(accessMode), //
				ManagedSymmetricPvInverter.getModbusSlaveNatureTable(accessMode));
	}

	@Override
	public String debugLog() {
		return "L:" + this.getActivePower().asString();
	}

	/*
	 * Registers that are not supported by the inverter model are reported with all
	 * bits set for unsigned and with the maximum positive value for signed data
	 * types.
	 */
	private static final ElementToChannelConverter U16_OR_NULL = SET_NULL_FOR_DEFAULT(0xFFFF);
	private static final ElementToChannelConverter S16_OR_NULL = SET_NULL_FOR_DEFAULT(0x7FFF);
	private static final ElementToChannelConverter U32_OR_NULL = SET_NULL_FOR_DEFAULT(0xFFFFFFFFL);
	private static final ElementToChannelConverter S32_OR_NULL = SET_NULL_FOR_DEFAULT(0x7FFFFFFF);
	private static final ElementToChannelConverter U16_SCALE_FACTOR_2_OR_NULL = chain(U16_OR_NULL, SCALE_FACTOR_2);
	private static final ElementToChannelConverter U32_SCALE_FACTOR_2_OR_NULL = chain(U32_OR_NULL, SCALE_FACTOR_2);
	private static final ElementToChannelConverter U32_SCALE_FACTOR_3_OR_NULL = chain(U32_OR_NULL, SCALE_FACTOR_3);

	/**
	 * The power factor register holds the value multiplied by 1000. Scale factor
	 * converters keep the integer type of the register, so the conversion to a
	 * fraction is done explicitly.
	 */
	private static final ElementToChannelConverter S16_POWER_FACTOR_OR_NULL = chain(S16_OR_NULL,
			new ElementToChannelConverter(value -> {
				var v = TypeUtils.<Integer>getAsType(OpenemsType.INTEGER, value);
				return v == null ? null : v / 1000F;
			}));

	private static UnsignedDoublewordElement uint32(int address) {
		return new UnsignedDoublewordElement(address).wordOrder(LSWMSW);
	}

	private static SignedDoublewordElement int32(int address) {
		return new SignedDoublewordElement(address).wordOrder(LSWMSW);
	}
}
