package io.openems.edge.controller.tess.delaycharge;

import static io.openems.common.utils.DateUtils.roundDownToQuarter;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.ChannelAddress;
import io.openems.common.utils.DateUtils;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.heat.tess.api.ManagedThermalEss;
import io.openems.edge.predictor.api.manager.PredictorManager;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Tess.DelayCharge", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class TessDelayChargeControllerImpl extends AbstractOpenemsComponent
		implements TessDelayChargeController, Controller, OpenemsComponent {

	private static final LocalTime DEFAULT_TARGET_TIME = LocalTime.of(17, 0);

	private final Logger log = LoggerFactory.getLogger(TessDelayChargeControllerImpl.class);

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private ComponentManager componentManager;

	@Reference
	private PredictorManager predictorManager;

	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY)
	private ManagedThermalEss tess;

	private Config config = null;

	public TessDelayChargeControllerImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				TessDelayChargeController.ChannelId.values() //
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
		final var now = ZonedDateTime.now(this.componentManager.getClock());
		final var targetMinute = switch (this.config.mode()) {
		case AUTOMATIC -> this.calculateAutomaticTargetMinute(now);
		case MANUAL -> this.getManualTargetMinute();
		};
		this._setTargetMinute(targetMinute);
		if (targetMinute == null) {
			this._setDelayChargeLimit(null);
			return;
		}

		final var nowMinute = now.get(ChronoField.MINUTE_OF_DAY);
		if (nowMinute >= targetMinute) {
			// Target time has passed; no limit until tomorrow
			this._setDelayChargeLimit(null);
			return;
		}

		final var capacity = this.tess.getCapacity().orElse(0);
		final var soc = this.tess.getSoc().orElse(0);
		final var remainingEnergy = max(0, capacity - round(capacity * soc / 100F)); // [Wh]
		final var remainingSeconds = (targetMinute - nowMinute) * 60L;
		final var limit = (int) (remainingEnergy * 3600L / remainingSeconds); // [W]

		this._setDelayChargeLimit(limit);
		this.tess.setTargetThermalPowerLessOrEquals(limit);
	}

	/**
	 * Gets the target minute of the day from the configured manual target time.
	 * Falls back to 17:00 when the configured value cannot be parsed.
	 *
	 * @return the target minute of the day
	 */
	private Integer getManualTargetMinute() {
		var targetTime = DateUtils.parseLocalTimeOrNull(this.config.manualTargetTime());
		if (targetTime == null) {
			this.logWarn(this.log, "Unable to parse Manual Target Time [" + this.config.manualTargetTime()
					+ "]; falling back to [" + DEFAULT_TARGET_TIME + "]");
			targetTime = DEFAULT_TARGET_TIME;
		}
		return targetTime.get(ChronoField.MINUTE_OF_DAY);
	}

	/**
	 * Calculates the target minute of the day from the predicted end of PV
	 * production.
	 *
	 * @param now the current time
	 * @return the target minute of the day, or null if production is never
	 *         predicted to exceed consumption today
	 */
	private Integer calculateAutomaticTargetMinute(ZonedDateTime now) {
		final var production = this.predictorManager
				.getPrediction(new ChannelAddress("_sum", "ProductionActivePower"));
		final var consumption = this.predictorManager
				.getPrediction(new ChannelAddress("_sum", "ConsumptionActivePower"));
		return calculateTargetMinute(production.asArray(), consumption.asArray(), roundDownToQuarter(now));
	}

	/**
	 * Finds the end of the last quarter-hour of the current day in which the
	 * predicted production exceeds the predicted consumption.
	 *
	 * @param production   quarterly production prediction, starting at
	 *                     startQuarter
	 * @param consumption  quarterly consumption prediction, starting at
	 *                     startQuarter
	 * @param startQuarter the start time of the first prediction value
	 * @return the target minute of the day, or null
	 */
	protected static Integer calculateTargetMinute(Integer[] production, Integer[] consumption,
			ZonedDateTime startQuarter) {
		final var length = min(production.length, consumption.length);
		for (var i = length - 1; i >= 0; i--) {
			if (production[i] == null || consumption[i] == null || production[i] <= consumption[i]) {
				continue;
			}
			var quarterStart = startQuarter.plusMinutes(i * 15L);
			if (!quarterStart.toLocalDate().equals(startQuarter.toLocalDate())) {
				// Only consider the current day
				continue;
			}
			return quarterStart.get(ChronoField.MINUTE_OF_DAY) + 15;
		}
		return null;
	}

	@Override
	public String debugLog() {
		return "Limit:" + this.getDelayChargeLimit().asString();
	}
}
