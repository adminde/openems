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

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.power.api.Constraint;
import io.openems.edge.ess.power.api.Power;
import io.openems.edge.oros.common.SymmetricComponent;
import io.openems.edge.oros.ess.core.StorageChannelManager;
import io.openems.edge.oros.ess.api.EnergyStorageProtection;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;
import io.openems.edge.oros.simulator.bms.BatteryManagementSimulator;
import io.openems.edge.oros.simulator.pcs.PowerConversionSimulator;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "Simulator.ESS.Symmetric.OROS",
		immediate = true,
		configurationPolicy = REQUIRE)
public class SymmetricStorageSimulatorReactingImpl extends AbstractOpenemsComponent
		implements SymmetricStorageSimulatorReacting, EnergyStorageSystem, ManagedSymmetricEss, SymmetricEss,
		OpenemsComponent, ModbusSlave, TimedataProvider, StartStoppable {

	private final StorageChannelManager channelManager = new StorageChannelManager(this);

	private Config config;

	@Reference
	private Power power;

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private ComponentManager componentManager;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = OPTIONAL)
	private volatile Timedata timedata = null;

	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY)
	private volatile PowerConversionSimulator pcs;

	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY)
	private volatile BatteryManagementSimulator bms;

	public SymmetricStorageSimulatorReactingImpl() {
		super(
				OpenemsComponent.ChannelId.values(),
				SymmetricEss.ChannelId.values(),
				ManagedSymmetricEss.ChannelId.values(),
				StartStoppable.ChannelId.values(),
				EnergyStorageSystem.ChannelId.values(),
				EnergyStorageProtection.ChannelId.values(),
				SymmetricComponent.ChannelId.values(),
				SymmetricStorageSimulatorReacting.ChannelId.values()
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws IOException, OpenemsException {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;

		// update filter for 'PowerConversionSystem'
		if (OpenemsComponent.updateReferenceFilter(cm, this.servicePid(), "pcs", config.pcs_id())) {
			return;
		}

		// update filter for 'BatteryManagementSystem'
		if (OpenemsComponent.updateReferenceFilter(cm, this.servicePid(), "bms", config.bms_id())) {
			return;
		}

		this.channelManager.activate(this.componentManager, this.bms, this.pcs,
				() -> this.config.maxPowerIncreasePercentage());

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
		}
		else if (this.isReadOnly()) {
			constraints.add(this.createPowerConstraint("Active Power Constraint ESS Read-Only Mode",
					ALL, ACTIVE, EQUALS, 0));
			constraints.add(this.createPowerConstraint("Reactive Power Constraint ESS Read-Only Mode",
					ALL, REACTIVE, EQUALS, 0));
		}
		else {
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
