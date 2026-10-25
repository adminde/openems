package io.openems.edge.simulator.timeofusetariff;

import static io.openems.common.utils.DateUtils.roundDownToQuarter;
import static io.openems.edge.common.channel.ChannelUtils.setValue;
import static io.openems.edge.timeofusetariff.api.TouManualHelper.EMPTY_TOU_MANUAL_HELPER;
import static io.openems.edge.timeofusetariff.api.utils.TimeOfUseTariffUtils.generateDebugLog;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.ChannelAddress;
import io.openems.common.utils.DateUtils;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.common.referencetarget.GenerateTargetsFromReferences;
import io.openems.edge.common.meta.Meta;
import io.openems.edge.simulator.datasource.api.SimulatorDatasource;
import io.openems.edge.timeofusetariff.api.TariffGridSell;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;
import io.openems.edge.timeofusetariff.api.TimeOfUseTariff;
import io.openems.edge.timeofusetariff.api.TouManualHelper;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Simulator.TimeOfUseTariff", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@GenerateTargetsFromReferences("datasource")
public class SimulatorTimeOfUseTariffImpl extends AbstractOpenemsComponent
		implements SimulatorTimeOfUseTariff, OpenemsComponent, TimeOfUseTariff, TariffGridSell {

	/** Expected quarter-hourly column in the data source [Currency/MWh]. */
	public static final String PRICE_COLUMN = "Price";

	/**
	 * Served price window: one day plus 12 hours lookahead, mirroring the 36 hours
	 * of {@link TouManualHelper#getPrices()}.
	 */
	public static final int PRICE_WINDOW_QUARTERS = 144;

	private static final Duration QUARTER = Duration.ofMinutes(15);

	private final Logger log = LoggerFactory.getLogger(SimulatorTimeOfUseTariffImpl.class);

	@Reference
	private Meta meta;

	@Reference
	private ComponentManager componentManager;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, //
			cardinality = ReferenceCardinality.MANDATORY, //
			target = "(&(id=${config.datasource_id})(enabled=true))")
	private SimulatorDatasource datasource;

	/**
	 * Ancillary costs, resolved on first use rather than at activation: the
	 * ComponentManager still holds the real system clock there, and
	 * TouManualHelper prices against whichever clock it is handed.
	 */
	private String ancillaryCosts = "";
	private TouManualHelper helper = null;

	/** The Clock {@link #helper} was built against; see ancillaryHelper. */
	private Clock helperClock = null;
	private PriceCalculator priceCalculator;

	/** Simulated time of the first CSV row. */
	private Instant csvStartTime = null;
	private List<Double> marketPrices = List.of();

	private Instant lastAccessQuarter = null;
	private TimeOfUsePrices cachedPrices = TimeOfUsePrices.EMPTY_PRICES;

	/** Null while no sell expression is configured: selling then earns nothing. */
	private PriceCalculator sellPriceCalculator = null;
	private Instant lastSellAccessQuarter = null;
	private TimeOfUsePrices cachedSellPrices = TimeOfUsePrices.EMPTY_PRICES;

	public SimulatorTimeOfUseTariffImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				SimulatorTimeOfUseTariff.ChannelId.values() //
		);
	}

	@Activate
	private synchronized void activate(ComponentContext context, Config config) throws OpenemsNamedException {
		super.activate(context, config.id(), config.alias(), config.enabled());

		if (!config.enabled()) {
			return;
		}

		// Anchor for CSV row 0, resolved before anything else so a bad value fails
		// activation instead of producing prices for the wrong dates.
		final var csvStartTime = parseStartTime(config);

		this.applySchedule(config);

		try {
			final var priceCalculator = new PriceCalculator(config.calculateExpression());
			priceCalculator.calculate(1., 1.);
			this.priceCalculator = priceCalculator;
		} catch (Exception e) {
			this.logWarn(this.log,
					"Calculate expression [" + config.calculateExpression() + "] failed. Falling back to [x + y].");
			this.priceCalculator = new PriceCalculator("");
		}

		this.sellPriceCalculator = null;
		if (!config.sellCalculateExpression().isBlank()) {
			try {
				final var sellCalculator = new PriceCalculator(config.sellCalculateExpression());
				sellCalculator.calculate(1., 1.);
				this.sellPriceCalculator = sellCalculator;
			} catch (Exception e) {
				this.logWarn(this.log, "Sell expression [" + config.sellCalculateExpression()
						+ "] failed. Falling back to the plain market price [x].");
				this.sellPriceCalculator = new PriceCalculator("x");
			}
		}

		this.csvStartTime = csvStartTime;
		this.marketPrices = this.datasource.getValues(OpenemsType.DOUBLE,
				new ChannelAddress(this.id(), PRICE_COLUMN));
		if (this.marketPrices.isEmpty()) {
			this.logWarn(this.log, "Data source [" + config.datasource_id() + "] has no column [" + PRICE_COLUMN
					+ "]. Available keys: " + this.datasource.getKeys());
		}
		setValue(this, SimulatorTimeOfUseTariff.ChannelId.UNABLE_TO_UPDATE_PRICES, this.marketPrices.isEmpty());
	}

	/**
	 * Resolves the simulated time of the first CSV row.
	 *
	 * <p>
	 * Mandatory on purpose: at activation the ComponentManager clock is still the
	 * real system clock, because Simulator.App installs the TimeLeapClock only after
	 * all components have activated. Defaulting to 'now' would therefore anchor a
	 * simulated year to today's date and serve prices for the wrong quarters without
	 * anything looking wrong - so a missing or unparseable value fails activation.
	 *
	 * @param config the {@link Config}
	 * @return the anchor for CSV row 0
	 * @throws OpenemsNamedException on a blank or unparseable value
	 */
	private static Instant parseStartTime(Config config) throws OpenemsNamedException {
		final var startTime = config.startTime();
		if (startTime == null || startTime.isBlank()) {
			throw new OpenemsException("Configuration [startTime] is mandatory: it is the simulated time of the "
					+ "first CSV row (ISO-8601, e.g. '2025-01-01T00:00:00+01:00'). Without it row 0 cannot be "
					+ "anchored to the simulation's clock.");
		}
		return roundDownToQuarter(DateUtils.parseZonedDateTimeOrError(startTime)).toInstant();
	}

	private void applySchedule(Config config) {
		this.ancillaryCosts = config.ancillaryCosts();
		this.helper = null;
		this.helperClock = null;
	}

	/**
	 * The helper prices against the Clock it was constructed with. Simulator.App
	 * swaps the ComponentManager's real clock for a TimeLeapClock only after all
	 * components have activated, so a helper built before that keeps serving a
	 * 36-hour window around the REAL date for the whole run: every simulated
	 * quarter outside it then silently prices its fee at 0. Rebuild whenever the
	 * clock instance changes.
	 *
	 * @param clock the {@link Clock} the helper prices against
	 * @return the {@link TouManualHelper} for that clock
	 */
	private TouManualHelper ancillaryHelper(Clock clock) {
		if (this.helper != null && this.helperClock == clock) {
			return this.helper;
		}
		try {
			this.helper = new TouManualHelper(clock, Utils.parseToSchedule(clock, this.ancillaryCosts), 0.0);
			this.helperClock = clock;

		} catch (OpenemsNamedException e) {
			this.logWarn(this.log, "Unable to parse Schedule: " + e.getMessage());
			this.helper = EMPTY_TOU_MANUAL_HELPER;
			this.helperClock = clock;
		}
		return this.helper;
	}

	@Deactivate
	protected synchronized void deactivate() {
		super.deactivate();
	}

	@Override
	public synchronized TimeOfUsePrices getPrices() {
		if (this.marketPrices.isEmpty() || this.csvStartTime == null || this.priceCalculator == null) {
			return TimeOfUsePrices.EMPTY_PRICES;
		}

		final var clock = this.componentManager.getClock();
		final var now = roundDownToQuarter(ZonedDateTime.now(clock)).toInstant();

		if (now.equals(this.lastAccessQuarter)) {
			// Avoids recalculation within 15 minutes.
			return this.cachedPrices;
		}

		final var fromIndex = (int) (Duration.between(this.csvStartTime, now).dividedBy(QUARTER));
		if (fromIndex < 0 || fromIndex >= this.marketPrices.size()) {
			setValue(this, SimulatorTimeOfUseTariff.ChannelId.UNABLE_TO_UPDATE_PRICES, true);
			return TimeOfUsePrices.EMPTY_PRICES;
		}

		final var prices = Utils.processPrices(now, this.marketPrices, fromIndex, PRICE_WINDOW_QUARTERS,
				this.priceCalculator, this.ancillaryHelper(clock).getPrices());

		this.lastAccessQuarter = now;
		this.cachedPrices = prices;
		setValue(this, SimulatorTimeOfUseTariff.ChannelId.UNABLE_TO_UPDATE_PRICES, prices.isEmpty());
		return prices;
	}

	/** Sell prices carry no ancillary costs: grid fees apply to import only. */
	@Override
	public synchronized TimeOfUsePrices getGridSellPrices() {
		if (this.sellPriceCalculator == null || this.marketPrices.isEmpty() || this.csvStartTime == null) {
			return TimeOfUsePrices.EMPTY_PRICES;
		}

		final var clock = this.componentManager.getClock();
		final var now = roundDownToQuarter(ZonedDateTime.now(clock)).toInstant();

		if (now.equals(this.lastSellAccessQuarter)) {
			// Avoids recalculation within 15 minutes.
			return this.cachedSellPrices;
		}

		final var fromIndex = (int) (Duration.between(this.csvStartTime, now).dividedBy(QUARTER));
		if (fromIndex < 0 || fromIndex >= this.marketPrices.size()) {
			return TimeOfUsePrices.EMPTY_PRICES;
		}

		final var prices = Utils.processPrices(now, this.marketPrices, fromIndex, PRICE_WINDOW_QUARTERS,
				this.sellPriceCalculator, TimeOfUsePrices.EMPTY_PRICES);

		this.lastSellAccessQuarter = now;
		this.cachedSellPrices = prices;
		return prices;
	}

	@Override
	public String debugLog() {
		return generateDebugLog(this, this.meta.getCurrency());
	}
}
