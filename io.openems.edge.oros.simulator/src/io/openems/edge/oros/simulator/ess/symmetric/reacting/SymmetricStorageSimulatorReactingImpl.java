package io.openems.edge.oros.simulator.ess.symmetric.reacting;

import static io.openems.edge.common.type.Phase.SingleOrAllPhase.ALL;
import static io.openems.edge.ess.power.api.Pwr.ACTIVE;
import static io.openems.edge.ess.power.api.Pwr.REACTIVE;
import static io.openems.edge.ess.power.api.Relationship.EQUALS;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.io.IOException;
import java.util.ArrayList;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.referencetarget.GenerateTargetsFromReferences;
import io.openems.edge.batteryinverter.api.HybridManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.power.api.Constraint;
import io.openems.edge.ess.power.api.Power;
import io.openems.edge.oros.common.SymmetricComponent;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;
import io.openems.edge.oros.ess.core.ChannelManager;
import io.openems.edge.oros.ess.core.protection.PowerLimiter;
import io.openems.edge.oros.simulator.bms.BatteryManagementSimulator;
import io.openems.edge.oros.simulator.pcs.PowerConversionSimulator;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "Simulator.ESS.Symmetric.OROS",
		immediate = true,
		configurationPolicy = REQUIRE)
@EventTopics({
		EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE
})
@GenerateTargetsFromReferences({ "pcs", "bms" })
public class SymmetricStorageSimulatorReactingImpl extends AbstractOpenemsComponent implements SymmetricStorageSimulatorReacting,
		EnergyStorageSystem, ManagedSymmetricEss, SymmetricEss, SymmetricComponent,
		OpenemsComponent, ModbusSlave, TimedataProvider, StartStoppable, EventHandler {

	private final ChannelManager channelManager = new ChannelManager(this);

	private int standbyPower = STANDBY_POWER;

	@Reference
	private Power power;

	@Reference
	private ComponentManager componentManager;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = OPTIONAL)
	private volatile Timedata timedata = null;

	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY, //
			target = "(&(id=${config.pcs_id})(enabled=true))")
	private volatile PowerConversionSimulator pcs;

	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY, //
			target = "(&(id=${config.bms_id})(enabled=true))")
	private volatile BatteryManagementSimulator bms;

	public SymmetricStorageSimulatorReactingImpl() {
		super(
				OpenemsComponent.ChannelId.values(),
				StartStoppable.ChannelId.values(),
				SymmetricComponent.ChannelId.values(),
				SymmetricEss.ChannelId.values(),
				ManagedSymmetricEss.ChannelId.values(),
				EnergyStorageSystem.ChannelId.values(),
				SymmetricStorageSimulatorReacting.ChannelId.values()
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws IOException, OpenemsException {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.standbyPower = config.standbyPower();

		var powerLimiter = new PowerLimiter(this, this.pcs, this.bms);
		this.channelManager.setPowerLimiter(powerLimiter);
		this.channelManager.activate(this.componentManager, this.pcs, this.bms);
		this._setStartStop(StartStop.START);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		this.channelManager.deactivate();
		super.deactivate();
	}

	@Override
	public void applyPower(int activePower, int reactivePower) throws OpenemsNamedException {
		this.pcs.run(this.bms, activePower, reactivePower);
		this._setActivePower(activePower);
		this._setReactivePower(reactivePower);
	}

	@Override
	public int getPowerPrecision() {
		return this.pcs.getPowerPrecision();
	}

	@Override
	public Value<Integer> getDcDischargePower() {
		return this.getDcDischargePowerChannel().value();
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE:
			this.calculateDcPower();
			this.calculateAvailableEnergy();
			this.calculateAuxiliaryPower();
			break;
		}
	}

	private void calculateDcPower() {
		IntegerReadChannel dcPowerChannel;
		if (this.pcs instanceof HybridManagedSymmetricBatteryInverter hybrid) {
			dcPowerChannel = hybrid.getDcDischargePowerChannel();
		} else {
			dcPowerChannel = this.pcs.getDcPowerChannel();
		}
		this._setDcDischargePower(dcPowerChannel.getNextValue().get());
	}

	/**
	 * Sums the power the System draws beside its AC terminals: the control
	 * infrastructure around the clock plus the Thermal Management System of the
	 * Battery, which only runs while the Battery carries a current.
	 */
	private void calculateAuxiliaryPower() {
		var thermalManagement = this.bms.getThermalManagementPowerChannel().getNextValue();
		this._setAuxiliaryPower(this.standbyPower + thermalManagement.orElse(0));
	}

	/**
	 * Derives the energy that is still available in either direction from the State
	 * of Charge and the Capacity of the Battery. Both are reported as a positive
	 * amount, bounded by the empty and the full Battery.
	 */
	private void calculateAvailableEnergy() {
		var capacity = this.bms.getCapacityChannel().getNextValue();
		var stateOfCharge = this.bms.getRackSocChannel().getNextValue();
		if (!capacity.isDefined() || !stateOfCharge.isDefined()) {
			this._setAvailableDischargeEnergy(null);
			this._setAvailableChargeEnergy(null);
			return;
		}
		// RACK_SOC is in [0.1 %] (per-mille) -> divide by 1000 to get a fraction.
		var dischargeEnergy = Math.round(capacity.get() * (stateOfCharge.get() / 1000F));
		this._setAvailableDischargeEnergy(dischargeEnergy);
		this._setAvailableChargeEnergy(capacity.get() - dischargeEnergy);
	}

	@Override
	public Power getPower() {
		return this.power;
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	/**
	 * Retrieves StaticConstraints from {@link SymmetricBatteryInverter}.
	 *
	 * {@inheritDoc}
	 */
	@Override
	public Constraint[] getStaticConstraints() throws OpenemsNamedException {
		var constraints = new ArrayList<Constraint>();

		// If the generic system is not in State "STARTED" block ACTIVE and REACTIVE Power!
		if (!this.isStarted()) {
			constraints.add(this.createPowerConstraint("Active Power Constraint ESS not Started",
					ALL, ACTIVE, EQUALS, 0));
			constraints.add(this.createPowerConstraint("Reactive Power Constraint ESS not Started",
					ALL, REACTIVE, EQUALS, 0));
		} else if (this.isReadOnly()) {
			constraints.add(this.createPowerConstraint("Active Power Constraint ESS Read-Only Mode",
					ALL, ACTIVE, EQUALS, 0));
			constraints.add(this.createPowerConstraint("Reactive Power Constraint ESS Read-Only Mode",
					ALL, REACTIVE, EQUALS, 0));
		} else {
			// Get PCS constraints
			var pcsConstraints = this.pcs.getStaticConstraints();
			for (var constraint : pcsConstraints) {
				constraints.add(this.getPower().createSimpleConstraint(constraint.description(),
						this, constraint.phase(), constraint.pwr(), constraint.relationship(), constraint.value()));
			}
		}
		return constraints.toArray(new Constraint[constraints.size()]);
	}

	@Override
	public void setStartStop(StartStop value) {
		// not supported; simulator always stays in START
	}

	@Override
	public String debugLog() {
		return EnergyStorageSystem.generateDebugLog(this);
	}

}
