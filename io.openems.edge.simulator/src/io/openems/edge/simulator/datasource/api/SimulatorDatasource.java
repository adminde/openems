package io.openems.edge.simulator.datasource.api;

import java.util.List;
import java.util.Set;

import io.openems.common.types.ChannelAddress;
import io.openems.common.types.OpenemsType;

public interface SimulatorDatasource {

	/**
	 * Gets the available keys.
	 *
	 * @return the Channel-Id
	 */
	public Set<String> getKeys();

	/**
	 * Returns the delta between two values in seconds.
	 *
	 * @return the delta in seconds
	 */
	public int getTimeDelta();

	/**
	 * Gets all values for the given key (channelId) in the given type.
	 *
	 * @param <T>            the type
	 * @param type           the expected type
	 * @param channelAddress the Channel-Address
	 * @return the values, possibly empty
	 */
	public <T> List<T> getValues(OpenemsType type, ChannelAddress channelAddress);

	/**
	 * Gets the next values for the given key (channelId), starting with the one
	 * that is currently played, at most {@code count} of them. A consumer that
	 * knows how far ahead it needs to look asks for exactly that much, so a
	 * Datasource never has to guess a horizon.
	 *
	 * <p>
	 * The default falls back to the first {@code count} of
	 * {@link #getValues(OpenemsType, ChannelAddress)}, which is all a Datasource
	 * that does not track a position can offer.
	 *
	 * @param <T>            the type
	 * @param type           the expected type
	 * @param channelAddress the Channel-Address
	 * @param count          the maximum number of values
	 * @return the values, possibly empty
	 */
	public default <T> List<T> getNextValues(OpenemsType type, ChannelAddress channelAddress, int count) {
		var values = this.<T>getValues(type, channelAddress);
		return values.size() <= count ? values : values.subList(0, count);
	}

	/**
	 * Gets the value for the given key (channelId) in the given type.
	 *
	 * @param <T>            the type
	 * @param type           the expected type
	 * @param channelAddress the Channel-Address
	 * @return the value, possibly null
	 */
	public <T> T getValue(OpenemsType type, ChannelAddress channelAddress);

}
