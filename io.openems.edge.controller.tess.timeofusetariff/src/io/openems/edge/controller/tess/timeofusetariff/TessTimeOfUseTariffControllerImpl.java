package io.openems.edge.controller.tess.timeofusetariff;

import static io.openems.edge.controller.tess.timeofusetariff.EnergyScheduler.buildEnergyScheduleHandler;
import static io.openems.edge.controller.tess.timeofusetariff.StateMachine.CHARGE_GRID;
import static io.openems.edge.controller.tess.timeofusetariff.StateMachine.NONE;
import static io.openems.edge.energy.api.handler.RescheduleMode.OPTIMIZE_CURRENT_PERIOD;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
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
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.controller.tess.timeofusetariff.EnergyScheduler.OptimizationContext;
import io.openems.edge.controller.tess.timeofusetariff.EnergyScheduler.ScheduleContext;
import io.openems.edge.energy.api.EnergySchedulable;
import io.openems.edge.energy.api.handler.EshWithDifferentModes;
import io.openems.edge.heat.tess.api.ManagedThermalEss;
import io.openems.edge.heat.tess.api.ThermalEss;
import io.openems.edge.predictor.api.manager.PredictorManager;
import io.openems.edge.predictor.api.prediction.Prediction;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateActiveTime;
import io.openems.edge.timeofusetariff.api.TariffManager;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Tess.Time-Of-Use-Tariff", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class TessTimeOfUseTariffControllerImpl extends AbstractOpenemsComponent implements
		TessTimeOfUseTariffController, EnergySchedulable, Controller, OpenemsComponent, TimedataProvider {

	private final CalculateActiveTime calculateChargedTime = new CalculateActiveTime(this,
			TessTimeOfUseTariffController.ChannelId.CHARGED_TIME);

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private ComponentManager componentManager;

	@Reference
	private TariffManager tariffManager;

	@Reference
	private PredictorManager predictorManager;

	@Reference(policyOption = GREEDY, cardinality = OPTIONAL)
	private volatile Timedata timedata;

	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY)
	private ManagedThermalEss tess;

	private EshWithDifferentModes<StateMachine, OptimizationContext, ScheduleContext> energyScheduleHandler;
	private Config config = null;

	public TessTimeOfUseTariffControllerImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				TessTimeOfUseTariffController.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.energyScheduleHandler = buildEnergyScheduleHandler(this, //
				() -> this.config != null && this.config.enabled() //
						? new EnergyScheduler.EshConfig(this.config.efficiency(), this.config.thermalDemand()) //
						: null, //
				() -> this.tess, //
				() -> {
					var prediction = this.predictorManager.getPrediction(new ChannelAddress(this.config.tess_id(),
							ThermalEss.ChannelId.THERMAL_DISCHARGE_POWER.id()));
					return prediction != null ? prediction : Prediction.EMPTY_PREDICTION;
				});
		this.applyConfig(config);
	}

	@Modified
	private void modified(ComponentContext context, Config config) {
		super.modified(context, config.id(), config.alias(), config.enabled());
		this.applyConfig(config);
		this.energyScheduleHandler.triggerReschedule("TessTimeOfUseTariffControllerImpl::modified()",
				OPTIMIZE_CURRENT_PERIOD);
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
		final var period = this.energyScheduleHandler.getCurrentPeriod();
		final var state = period == null //
				? NONE //
				: period.mode();

		if (state == CHARGE_GRID) {
			var maxTargetThermalPower = this.tess.getMaxTargetThermalPower().get();
			if (maxTargetThermalPower != null) {
				this.tess.setTargetThermalPowerEqualsWithoutFilter(maxTargetThermalPower);
			}
		}
		// In NONE the Controller writes nothing: the Heating devices stay autonomous
		// and a higher-priority controller may request charging via
		// TargetThermalPowerGreaterOrEquals.

		this._setStateMachine(state);
		this.calculateChargedTime.update(state == CHARGE_GRID);
		this._setQuarterlyPrices(this.tariffManager.getGridBuyDayAheadPrices() //
				.getAt(this.componentManager.getClock().instant()));
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public EshWithDifferentModes<StateMachine, OptimizationContext, ScheduleContext> getEnergyScheduleHandler() {
		return this.energyScheduleHandler;
	}

	@Override
	public String debugLog() {
		var b = new StringBuilder() //
				.append(this.getStateMachine());
		if (this.energyScheduleHandler == null || this.energyScheduleHandler.getCurrentPeriod() == null) {
			b.append("|No Schedule available");
		}
		return b.toString();
	}
}
