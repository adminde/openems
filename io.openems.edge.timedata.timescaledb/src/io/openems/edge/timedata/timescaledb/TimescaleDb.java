package io.openems.edge.timedata.timescaledb;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.SortedMap;

import io.openems.common.timedata.CommonTimedataService;
import org.osgi.service.event.EventHandler;

import com.google.gson.JsonElement;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.timedata.Resolution;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.timedata.api.Timedata;

public interface TimescaleDb extends Timedata, CommonTimedataService, OpenemsComponent, EventHandler {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		;
		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	/**
	 * Queries historic data.
	 *
	 * @param fromDate   the From-Date
	 * @param toDate     the To-Date
	 * @param channels   the Channels
	 * @param resolution the {@link Resolution}
	 * @return the query result; possibly null
	 */
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException;

	/**
	 * Queries historic data.
	 *
	 * @param edgeId     the Edge-ID; or null query all
	 * @param fromDate   the From-Date
	 * @param toDate     the To-Date
	 * @param channels   the Channels
	 * @param resolution the {@link Resolution}
	 * @return the query result; possibly null
	 */
	@Override
	public default SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException {
		return this.queryHistoricData(fromDate, toDate, channels, resolution);
	}

	/**
	 * Queries historic energy.
	 *
	 * @param fromDate the From-Date
	 * @param toDate   the To-Date
	 * @param channels the Channels
	 * @return the query result; possibly null
	 */
	public SortedMap<ChannelAddress, JsonElement> queryHistoricEnergy(
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels)
			throws OpenemsNamedException;

	/**
	 * Queries historic energy.
	 *
	 * @param edgeId   the Edge-ID; or null query all
	 * @param fromDate the From-Date
	 * @param toDate   the To-Date
	 * @param channels the Channels
	 * @return the query result; possibly null
	 */
	@Override
	public default SortedMap<ChannelAddress, JsonElement> queryHistoricEnergy(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels)
			throws OpenemsNamedException {
		return this.queryHistoricEnergy(fromDate, toDate, channels);
	}

	/**
	 * Queries historic energy per period.
	 *
	 * <p>
	 * This is for use-cases where you want to get the energy for each period (with
	 * {@link Resolution}) per Channel, e.g. to visualize energy in a histogram
	 * chart. For each period the energy is calculated by subtracting first value of
	 * the period from the last value of the period.
	 *
	 * @param edgeId     the Edge-ID; or null query all
	 * @param fromDate   the From-Date
	 * @param toDate     the To-Date
	 * @param channels   the Channels
	 * @param resolution the {@link Resolution}
	 * @return the query result; possibly null
	 */
	@Override
	public default SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyPerPeriod(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException {
		return queryHistoricEnergyPerPeriod(fromDate, toDate, channels, resolution);
	}

	/**
	 * Queries historic energy per period.
	 *
	 * <p>
	 * This is for use-cases where you want to get the energy for each period (with
	 * {@link Resolution}) per Channel, e.g. to visualize energy in a histogram
	 * chart. For each period the energy is calculated by subtracting first value of
	 * the period from the last value of the period.
	 *
	 * @param fromDate   the From-Date
	 * @param toDate     the To-Date
	 * @param channels   the Channels
	 * @param resolution the {@link Resolution}
	 * @return the query result; possibly null
	 */
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyPerPeriod(
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException;
}
