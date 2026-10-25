package io.openems.edge.simulator.predictor;

import static io.openems.common.utils.DateUtils.roundDownToQuarter;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.time.Instant;
import java.util.List;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.referencetarget.GenerateTargetsFromReferences;
import io.openems.common.types.ChannelAddress;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.predictor.api.prediction.AbstractPredictor;
import io.openems.edge.predictor.api.prediction.Prediction;
import io.openems.edge.predictor.api.prediction.Predictor;
import io.openems.edge.simulator.datasource.api.SimulatorDatasource;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Simulator.Predictor", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@EventTopics({ //
		EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE //
})
@GenerateTargetsFromReferences("datasource")
public class SimulatorPredictorImpl extends AbstractPredictor
		implements SimulatorPredictor, Predictor, OpenemsComponent {

	@Reference
	private ComponentManager componentManager;

	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY, //
			target = "(&(id=${config.datasource_id})(enabled=true))")
	private SimulatorDatasource datasource;

	public SimulatorPredictorImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				SimulatorPredictor.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsNamedException {
		super.activate(context, config.id(), config.alias(), config.enabled(), config.logVerbosity(),
				config.channelAddresses());
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	protected ClockProvider getClockProvider() {
		return this.componentManager;
	}

	/** Quarter hours a Prediction covers: two days. */
	private static final int HORIZON_QUARTERS = 48 /* hours */ * 4 /* quarters per hour */;

	/** Seconds between two Datasource values when it does not say. */
	private static final int DEFAULT_TIME_DELTA = 300;

	@Override
	protected Prediction createNewPrediction(ChannelAddress channelAddress) {
		var timeDelta = this.datasource.getTimeDelta();
		if (timeDelta <= 0) {
			timeDelta = DEFAULT_TIME_DELTA;
		}
		// Ask the Datasource for exactly as much as the horizon needs. The
		// horizon is the Predictor's own business, so the Datasource never has
		// to guess one.
		var count = (int) Math.ceil((double) HORIZON_QUARTERS * 900 / timeDelta);
		var source = this.datasource.<Integer>getNextValues(OpenemsType.INTEGER, channelAddress, count);
		if (source.isEmpty()) {
			return Prediction.EMPTY_PREDICTION;
		}
		// Rasterized to quarter hours, starting at the CURRENT quarter hour,
		// which is where consumers like the GridOptimizedCharge controller read
		// index 0 (see its predictionStartQuarterHour).
		var values = rasterize(source, timeDelta, HORIZON_QUARTERS);
		var now = roundDownToQuarter(Instant.now(this.componentManager.getClock()));
		return Prediction.from(now, values);
	}

	/**
	 * Rasterizes values that lie timeDelta seconds apart onto quarter hours:
	 * several values per quarter hour are averaged, a value spanning several
	 * quarter hours is repeated. A source shorter than the requested range is
	 * cycled.
	 *
	 * @param source           the values, starting now
	 * @param timeDeltaSeconds the seconds between two values
	 * @param quarters         the number of quarter hours to fill
	 * @return one value per quarter hour
	 */
	protected static Integer[] rasterize(List<Integer> source, int timeDeltaSeconds, int quarters) {
		var result = new Integer[quarters];
		if (timeDeltaSeconds <= 900) {
			var perQuarter = Math.max(1, Math.round(900f / timeDeltaSeconds));
			var chunk = new Integer[perQuarter];
			var index = 0;
			for (var i = 0; i < quarters; i++) {
				for (var j = 0; j < perQuarter; j++) {
					chunk[j] = source.get(index++ % source.size());
				}
				result[i] = TypeUtils.averageInt(chunk);
			}
		} else {
			var quartersPerValue = Math.max(1, Math.round(timeDeltaSeconds / 900f));
			for (var i = 0; i < quarters; i++) {
				result[i] = source.get(i / quartersPerValue % source.size());
			}
		}
		return result;
	}
}
