package io.openems.edge.oros.simulator.auxiliary;

import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.referencetarget.GenerateTargetsFromReferences;
import io.openems.common.types.MeterType;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.oros.simulator.bms.BatteryManagementSimulator;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "Simulator.Auxiliary.OROS",
		immediate = true,
		configurationPolicy = REQUIRE)
@EventTopics({
		EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE,
		EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE
})
@GenerateTargetsFromReferences("bms")
public class AuxiliarySimulatorImpl extends AbstractOpenemsComponent implements
		AuxiliarySimulator, ElectricityMeter, OpenemsComponent, TimedataProvider, EventHandler {

	private final CalculateEnergyFromPower calculateConsumptionEnergy = new CalculateEnergyFromPower(this,
			ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY);

	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY, //
			target = "(&(id=${config.bms_id})(enabled=true))")
	private volatile BatteryManagementSimulator bms;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = OPTIONAL)
	private volatile Timedata timedata = null;

	private int standbyPower = STANDBY_POWER;

	public AuxiliarySimulatorImpl() {
		super(
				OpenemsComponent.ChannelId.values(),
				ElectricityMeter.ChannelId.values(),
				AuxiliarySimulator.ChannelId.values()
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.standbyPower = config.standbyPower();
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public MeterType getMeterType() {
		return MeterType.CONSUMPTION_METERED;
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE:
			this.calculateActivePower();
			break;
		case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE:
			this.calculateEnergy();
			break;
		}
	}

	/**
	 * Sums the power the System draws beside the terminals of the Power Conversion
	 * System: the control infrastructure around the clock plus the Thermal
	 * Management System of the Battery, which only runs while the Battery carries a
	 * current. The auxiliary circuit is fed separately, so this consumption shows up
	 * at the point of connection and is no part of the power of the Energy Storage
	 * System.
	 */
	private void calculateActivePower() {
		var thermalManagement = this.bms.getThermalManagementPowerChannel().getNextValue();
		var activePower = this.standbyPower + thermalManagement.orElse(0);

		this._setActivePower(activePower);
		var activePowerByThree = TypeUtils.divide(activePower, 3);
		this._setActivePowerL1(activePowerByThree);
		this._setActivePowerL2(activePowerByThree);
		this._setActivePowerL3(activePowerByThree);
	}

	private void calculateEnergy() {
		this.calculateConsumptionEnergy.update(this.getActivePower().get());
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public String debugLog() {
		return this.getActivePower().asString();
	}
}
