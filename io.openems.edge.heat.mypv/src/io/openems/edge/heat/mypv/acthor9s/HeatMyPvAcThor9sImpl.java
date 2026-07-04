package io.openems.edge.heat.mypv.acthor9s;

import static io.openems.edge.heat.api.AsymmetricHeating.calculateAverageVoltageFromPhases;
import static io.openems.edge.heat.api.AsymmetricHeating.calculateSumCurrentFromPhases;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.heat.api.AsymmetricHeating;
import io.openems.edge.heat.api.ManagedSymmetricHeating;
import io.openems.edge.heat.api.SymmetricHeating;
import io.openems.edge.heat.element.api.HeatElement;
import io.openems.edge.heat.element.api.ManagedHeatElement;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Heat.MyPv.AcThor9s", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE)
@EventTopics({ //
		EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE //
})
public class HeatMyPvAcThor9sImpl extends AbstractOpenemsModbusComponent implements HeatMyPvAcThor9s, ModbusComponent,
		OpenemsComponent, HeatElement, ManagedHeatElement, TimedataProvider, EventHandler {

	// gets the total energy consumption in kWh
	private final CalculateEnergyFromPower totalEnergy = new CalculateEnergyFromPower(this,
			SymmetricHeating.ChannelId.ACTIVE_PRODUCTION_ENERGY);
	// gets the energy consumption in kWh per phase
	private final CalculateEnergyFromPower phaseEnergyL1 = new CalculateEnergyFromPower(this,
			AsymmetricHeating.ChannelId.ACTIVE_PRODUCTION_ENERGY_L1);
	private final CalculateEnergyFromPower phaseEnergyL2 = new CalculateEnergyFromPower(this,
			AsymmetricHeating.ChannelId.ACTIVE_PRODUCTION_ENERGY_L2);
	private final CalculateEnergyFromPower phaseEnergyL3 = new CalculateEnergyFromPower(this,
			AsymmetricHeating.ChannelId.ACTIVE_PRODUCTION_ENERGY_L3);

	private Config config = null;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata = null;

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	protected ComponentManager componentManager;

	@Reference
	private Sum sum;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public HeatMyPvAcThor9sImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				SymmetricHeating.ChannelId.values(), //
				AsymmetricHeating.ChannelId.values(), //
				ManagedSymmetricHeating.ChannelId.values(), //
				HeatElement.ChannelId.values(), //
				ManagedHeatElement.ChannelId.values(), //
				HeatMyPvAcThor9s.ChannelId.values() //
		);

		calculateSumCurrentFromPhases(this);
		calculateAverageVoltageFromPhases(this);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsNamedException {
		if (super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm,
				"Modbus", config.modbus_id())) {
			return;
		}
		this.config = config;
	}

	@Modified
	private void modified(ComponentContext context, Config config) throws OpenemsNamedException {
		if (super.modified(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm,
				"Modbus", config.modbus_id())) {
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
	protected ModbusProtocol defineModbusProtocol() {
		return new ModbusProtocol(this, new FC3ReadRegistersTask(1000, Priority.HIGH,
				m(SymmetricHeating.ChannelId.ACTIVE_POWER, new SignedWordElement(1000)),
				m(SymmetricHeating.ChannelId.TEMPERATURE, new SignedWordElement(1001)), new DummyRegisterElement(1002, 1060),
				m(AsymmetricHeating.ChannelId.VOLTAGE_L1, new SignedWordElement(1061)),
				m(AsymmetricHeating.ChannelId.CURRENT_L1, new SignedWordElement(1062)),
				new DummyRegisterElement(1063, 1066),
				m(AsymmetricHeating.ChannelId.VOLTAGE_L2, new SignedWordElement(1067)),
				m(AsymmetricHeating.ChannelId.CURRENT_L2, new SignedWordElement(1068)),
				new DummyRegisterElement(1069, 1071),
				m(AsymmetricHeating.ChannelId.VOLTAGE_L3, new SignedWordElement(1072)),
				m(AsymmetricHeating.ChannelId.CURRENT_L3, new SignedWordElement(1073)),
				m(AsymmetricHeating.ChannelId.ACTIVE_POWER_L1, new SignedWordElement(1074)),
				m(AsymmetricHeating.ChannelId.ACTIVE_POWER_L2, new SignedWordElement(1075)),
				m(AsymmetricHeating.ChannelId.ACTIVE_POWER_L3, new SignedWordElement(1076)),
				m(ManagedHeatElement.ChannelId.STATUS, new SignedWordElement(1077))));
	}

	@Override
	public String debugLog() {
		return "Status: " + this.channel(ManagedHeatElement.ChannelId.STATUS).value() //
				+ " | Read Only: " + this.config.readOnly(); //
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE -> {
			this.totalEnergy.update(this.getActivePower().orElse(0));
			this.phaseEnergyL1.update(this.getActivePowerL1().orElse(0));
			this.phaseEnergyL2.update(this.getActivePowerL2().orElse(0));
			this.phaseEnergyL3.update(this.getActivePowerL3().orElse(0));
		}
		}
	}
}
