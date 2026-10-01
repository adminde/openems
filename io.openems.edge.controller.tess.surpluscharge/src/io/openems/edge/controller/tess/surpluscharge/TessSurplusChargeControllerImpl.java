package io.openems.edge.controller.tess.surpluscharge;

import static java.lang.Math.max;
import static java.lang.Math.round;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.heat.tess.api.ManagedThermalEss;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Tess.SurplusCharge", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class TessSurplusChargeControllerImpl extends AbstractOpenemsComponent
		implements TessSurplusChargeController, Controller, OpenemsComponent {

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private Sum sum;

	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY)
	private ManagedThermalEss tess;

	private Config config = null;

	public TessSurplusChargeControllerImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				TessSurplusChargeController.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.applyConfig(config);
	}

	@Modified
	private void modified(ComponentContext context, Config config) {
		super.modified(context, config.id(), config.alias(), config.enabled());
		this.applyConfig(config);
	}

	private synchronized void applyConfig(Config config) {
		this.config = config;

		// update filter for 'tess'
		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "tess", config.tess_id())) {
			return;
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run() throws OpenemsNamedException {
		final var efficiency = this.config.efficiency() > 0 //
				? this.config.efficiency() //
				: 1.;

		// Excess power calculation follows the EVSE-Cluster convention: grid-buy and
		// battery discharge reduce the surplus; the own current charge power counts
		// as available so a running Heating does not starve its own surplus.
		final var buyFromGrid = this.sum.getGridActivePower().orElse(0);
		final var essDischarge = this.sum.getEssDischargePower().orElse(0);
		final var ownCharge = (int) round(this.tess.getThermalChargePower().orElse(0) / efficiency);
		final var surplusPower = max(0, ownCharge - buyFromGrid - essDischarge);

		this._setSurplusPower(surplusPower);

		if (surplusPower > 0) {
			this.tess.setTargetThermalPowerGreaterOrEquals((int) round(surplusPower * efficiency));
		}
	}

	@Override
	public String debugLog() {
		return "Surplus:" + this.getSurplusPower().asString();
	}
}
