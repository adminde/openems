package io.openems.shared.timescaledb;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import io.openems.common.timedata.Resolution;
import io.openems.common.types.ChannelAddress;

/**
 * Static helpers shared by the TimescaleDB read and write paths.
 */
public final class Utils {

	private Utils() {
	}

	/**
	 * Converts a {@link Resolution} to a PostgreSQL interval string, e.g.
	 * "15 Minutes" or "1 Months". Calendar-based units (months, years) keep their
	 * calendar semantics when passed to {@code time_bucket}.
	 *
	 * @param resolution the {@link Resolution}
	 * @return a SQL interval string
	 */
	public static String toSqlInterval(Resolution resolution) {
		var unit = resolution.getUnit();
		return switch (unit) {
		case YEARS, MONTHS, WEEKS, DAYS, HOURS, MINUTES, SECONDS //
			-> resolution.getValue() + " " + unit.toString();
		default //
			-> throw new IllegalArgumentException("Resolution " + unit + " is not supported");
		};
	}

	/**
	 * Approximate length of one {@link Resolution} bucket in seconds. Unlike
	 * {@link Resolution#toSeconds()} this is safe for estimated calendar units
	 * like MONTHS; use it only where a rough magnitude is needed (e.g. picking an
	 * aggregate tier), never for bucket boundaries.
	 *
	 * @param resolution the {@link Resolution}
	 * @return the approximate bucket length in seconds
	 */
	public static long approxSeconds(Resolution resolution) {
		return resolution.getUnit().getDuration().getSeconds() * resolution.getValue();
	}

	/**
	 * Prefills a result map with {@link JsonNull} for every timestamp/channel
	 * combination, so queries return the dense map the OpenEMS API expects even
	 * where no data exists.
	 *
	 * @param fromDate   the From-Date
	 * @param toDate     the To-Date
	 * @param channels   the Channels
	 * @param resolution the {@link Resolution}
	 * @return a prefilled result map
	 */
	public static TreeMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> prepareDataMap(ZonedDateTime fromDate,
			ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution) {
		var result = new TreeMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>>();
		var timestamp = fromDate;
		while (timestamp.isBefore(toDate)) {
			result.put(timestamp, prepareEnergyMap(channels) /* individual copy for each timestamp */);
			timestamp = timestamp.plus(resolution.getValue(), resolution.getUnit());
		}
		return result;
	}

	/**
	 * Prefills a single result map with {@link JsonNull} for every channel.
	 *
	 * @param channels the Channels
	 * @return a prefilled result map
	 */
	public static SortedMap<ChannelAddress, JsonElement> prepareEnergyMap(Set<ChannelAddress> channels) {
		var result = new TreeMap<ChannelAddress, JsonElement>();
		for (var channel : channels) {
			result.put(channel, JsonNull.INSTANCE);
		}
		return result;
	}
}
