package io.openems.edge.simulator.timeofusetariff;

import static io.openems.common.jscalendar.JSCalendar.RecurrenceFrequency.DAILY;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.google.common.collect.ImmutableSortedMap;
import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jscalendar.JSCalendar;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.timeofusetariff.api.AncillaryCosts;
import io.openems.edge.timeofusetariff.api.AncillaryCosts.GridFee;
import io.openems.edge.timeofusetariff.api.GermanDSO;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;

public class Utils {

	private static final Duration QUARTER = Duration.ofMinutes(15);

	/**
	 * Calculates gross prices for a window of quarter-hourly market prices.
	 *
	 * @param from            first quarter of the window
	 * @param marketPrices    the full market price column [Currency/MWh]
	 * @param fromIndex       index of 'from' in marketPrices
	 * @param quarters        maximum window length in quarters
	 * @param priceCalculator the {@link PriceCalculator}
	 * @param gridFees        ancillary costs [ct/kWh]
	 * @return the gross prices, the window ending early at a data gap
	 */
	protected static TimeOfUsePrices processPrices(Instant from, List<Double> marketPrices, int fromIndex,
			int quarters, PriceCalculator priceCalculator, TimeOfUsePrices gridFees) {
		final var result = ImmutableSortedMap.<Instant, Double>naturalOrder();

		final var toIndex = Math.min(fromIndex + quarters, marketPrices.size());
		for (var i = fromIndex; i < toIndex; i++) {
			var marketPrice = marketPrices.get(i);
			if (marketPrice == null) {
				break;
			}
			var time = from.plus(QUARTER.multipliedBy(i - fromIndex));
			var gridFee = gridFees.getAtOrElse(time, 0.0);

			// Converting grid fees from ct/kWh to EUR/MWh.
			var gridFeesPerMwh = gridFee * 10;

			var priceWithFee = priceCalculator.calculate(//
					marketPrice, // x = market price from the data source
					gridFeesPerMwh); // y = Ancillary costs

			result.put(time, priceWithFee);
		}

		return TimeOfUsePrices.from(result.build());
	}

	/**
	 * Parses the ancillary cost configuration JSON into a schedule of
	 * {@link JSCalendar.Tasks}.
	 *
	 * @param clock          The {@link Clock}
	 * @param ancillaryCosts the JSON configuration object
	 * @return a {@link JSCalendar.Tasks} instance representing the schedule or an
	 *         empty list if no valid schedule is provided.
	 * @throws OpenemsNamedException on error.
	 */
	public static JSCalendar.Tasks<Double> parseToSchedule(Clock clock, String ancillaryCosts)
			throws OpenemsNamedException {
		if (ancillaryCosts == null || ancillaryCosts.isBlank()) {
			return JSCalendar.Tasks.empty();
		}

		final var dso = parseDso(ancillaryCosts);
		if (dso != null) {
			return toSchedule(clock, dso.gridFee);
		}
		return AncillaryCosts.parseForGermany(clock, ancillaryCosts);
	}

	private static GermanDSO parseDso(String ancillaryCosts) {
		try {
			final JsonObject json = JsonUtils.parseToJsonObject(ancillaryCosts);
			final var dso = JsonUtils.getAsOptionalString(json, "dso").orElse(null);
			return dso == null ? null : GermanDSO.valueOf(dso);
		} catch (OpenemsNamedException | IllegalArgumentException e) {
			return null; // no preset selected, or a custom "OTHER" schedule
		}
	}

	/**
	 * Builds the fee schedule of a {@link GermanDSO} preset, splitting a time range
	 * that wraps midnight into a same-day head and a next-day tail.
	 *
	 * <p>
	 * {@code GridFee.toSchedule} emits one daily task per time range, starting on
	 * the DateRange's first day. For an overnight range like 23:00-05:00 the first
	 * instance therefore begins at 23:00 on day one, leaving 00:00-05:00 of that
	 * day to the PREVIOUS DateRange's last instance - so 1 January is billed at the
	 * previous year's tariff, or at 0 when that year is unpublished. Splitting at
	 * midnight gives each calendar day the tariff of the range it belongs to.
	 *
	 * @param clock   the {@link Clock}
	 * @param gridFee the operator's {@link GridFee}
	 * @return the schedule
	 */
	private static JSCalendar.Tasks<Double> toSchedule(Clock clock, GridFee gridFee) {
		final var tasks = JSCalendar.Tasks.<Double>create(clock);

		for (var dateRange : gridFee.dateRanges()) {
			for (var timeRange : dateRange.timeRanges()) {
				final double payload = switch (timeRange.tariff()) {
				case LOW -> dateRange.lowTariff();
				case STANDARD -> dateRange.standardTariff();
				case HIGH -> dateRange.highTariff();
				};

				final var start = timeRange.start();
				final var end = timeRange.end();
				if (end.isAfter(start)) {
					addDaily(tasks, dateRange, start, secondsBetween(start, end), payload);
					continue;
				}
				// wraps midnight: head runs to the end of the day, tail from its start
				addDaily(tasks, dateRange, start, secondsBetween(start, LocalTime.MIDNIGHT), payload);
				addDaily(tasks, dateRange, LocalTime.MIDNIGHT,
						Duration.ofSeconds(end.toSecondOfDay()), payload);
			}
		}

		return tasks.build();
	}

	/**
	 * Seconds from {@code from} to {@code to}, treating 00:00 as the end of the
	 * day.
	 *
	 * @param from the start of the range
	 * @param to   the end of the range
	 * @return the {@link Duration} between them
	 */
	private static Duration secondsBetween(LocalTime from, LocalTime to) {
		final var toSecond = to.equals(LocalTime.MIDNIGHT) ? 86400 : to.toSecondOfDay();
		return Duration.ofSeconds(toSecond - from.toSecondOfDay());
	}

	private static void addDaily(JSCalendar.Tasks.Builder<Double> tasks, GridFee.DateRange dateRange,
			LocalTime at, Duration duration, double payload) {
		if (duration.isZero() || duration.isNegative()) {
			return;
		}
		final var startDateTime = LocalDateTime.of(dateRange.start(), at);
		tasks.add(t -> t //
				.setStart(startDateTime) //
				.setDuration(duration) //
				.addRecurrenceRule(b -> b.setFrequency(DAILY) //
						.setUntil(dateRange.end())) //
				.setPayload(payload));
	}

}
