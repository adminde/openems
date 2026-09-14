package io.openems.edge.ess.hyperstrong.hypercube.tms;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_1;

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

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.BitsWordElement;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.taskmanager.Priority;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "Ess.HyperStrong.HyperCube.II.TMS",
		immediate = true,
		configurationPolicy = ConfigurationPolicy.REQUIRE
)
public class ThermalManagementSystemImpl extends AbstractOpenemsModbusComponent implements
		ThermalManagementSystem, OpenemsComponent, ModbusComponent {

	@Reference
	private ConfigurationAdmin cm;

	@Override
	@Reference(policy = ReferencePolicy.STATIC,
			policyOption = ReferencePolicyOption.GREEDY,
			cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public ThermalManagementSystemImpl() {
		super(OpenemsComponent.ChannelId.values(),
				ModbusComponent.ChannelId.values(),
				ThermalManagementSystem.ChannelId.values());
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		if (super.activate(context, config.id(), config.alias(), config.enabled(),
				config.modbusUnitId(), this.cm, "Modbus", config.modbus_id())) {
			return;
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		return new ModbusProtocol(this,
				new FC4ReadInputRegistersTask(50001, Priority.LOW,
						m(ThermalManagementSystem.ChannelId.RUN_MODE,
								new UnsignedWordElement(50001)),
						m(ThermalManagementSystem.ChannelId.RETURN_TEMPERATURE,
								new SignedWordElement(50002)),
						m(ThermalManagementSystem.ChannelId.SUPPLY_TEMPERATURE,
								new SignedWordElement(50003)),
						m(new BitsWordElement(50004, this)
								.bit(0, ThermalManagementSystem.ChannelId.COMMUNICATION_ABNORMAL)
								.bit(1, ThermalManagementSystem.ChannelId.COMMUNICATION_CONNECTED)
								.bit(2, ThermalManagementSystem.ChannelId.COMMUNICATION_ENABLED)
								.bit(3, ThermalManagementSystem.ChannelId.COMMUNICATION_FAULT)
						),
						m(new BitsWordElement(50005, this)
								.bit(0, ThermalManagementSystem.ChannelId.MAIN_CONTACTOR_STATE)
								.bit(1, ThermalManagementSystem.ChannelId.COMPRESSOR_STATE)
								.bit(2, ThermalManagementSystem.ChannelId.HEATING_STATE)
						),
						new DummyRegisterElement(50006, 50073),
						m(ThermalManagementSystem.ChannelId.RUN_MODE_TARGET,
								new UnsignedWordElement(50074)),
						new DummyRegisterElement(50075, 50076),
						m(ThermalManagementSystem.ChannelId.FAULT_CODE,
								new UnsignedWordElement(50077)),
						new DummyRegisterElement(50078, 50098),
						m(ThermalManagementSystem.ChannelId.RETURN_PRESSURE,
								new SignedWordElement(50099), SCALE_FACTOR_1),
						m(ThermalManagementSystem.ChannelId.SUPPLY_PRESSURE,
								new SignedWordElement(50100), SCALE_FACTOR_1)));
	}

}
