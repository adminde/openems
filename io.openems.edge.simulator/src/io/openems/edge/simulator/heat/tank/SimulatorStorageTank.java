package io.openems.edge.simulator.heat.tank;

import org.osgi.service.event.EventHandler;

import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;

public interface SimulatorStorageTank extends OpenemsComponent, EventHandler {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Temperature at the height of the immersion sleeve.
		 *
		 * <ul>
		 * <li>Interface: SimulatorStorageTank
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
		 * </ul>
		 */
		SENSOR_TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Temperature at the height of the return connection, i.e. of the water
		 * flowing back to the heat pump.
		 *
		 * <ul>
		 * <li>Interface: SimulatorStorageTank
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
		 * </ul>
		 */
		RETURN_TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Temperature at the top, the hottest point of the tank.
		 *
		 * <ul>
		 * <li>Interface: SimulatorStorageTank
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
		 * </ul>
		 */
		TOP_TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Actual mean temperature of the tank, the reference for estimates of the
		 * stored heat.
		 *
		 * <ul>
		 * <li>Interface: SimulatorStorageTank
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
		 * </ul>
		 */
		MEAN_TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.persistencePriority(PersistencePriority.HIGH)), //
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
	 * Gets the Component-ID of the Thermal ESS this tank is the physical plant of.
	 *
	 * @return the Component-ID
	 */
	public String getThermalEssId();

	/**
	 * Gets the Channel for {@link ChannelId#SENSOR_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getSensorTemperatureChannel() {
		return this.channel(ChannelId.SENSOR_TEMPERATURE);
	}

	/**
	 * Gets the Sensor Temperature in [deci-°C]. See
	 * {@link ChannelId#SENSOR_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getSensorTemperature() {
		return this.getSensorTemperatureChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#SENSOR_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setSensorTemperature(Integer value) {
		this.getSensorTemperatureChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#SENSOR_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setSensorTemperature(int value) {
		this.getSensorTemperatureChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#RETURN_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getReturnTemperatureChannel() {
		return this.channel(ChannelId.RETURN_TEMPERATURE);
	}

	/**
	 * Gets the Return Temperature in [deci-°C]. See
	 * {@link ChannelId#RETURN_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getReturnTemperature() {
		return this.getReturnTemperatureChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#RETURN_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReturnTemperature(Integer value) {
		this.getReturnTemperatureChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#RETURN_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReturnTemperature(int value) {
		this.getReturnTemperatureChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#TOP_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getTopTemperatureChannel() {
		return this.channel(ChannelId.TOP_TEMPERATURE);
	}

	/**
	 * Gets the Top Temperature in [deci-°C]. See {@link ChannelId#TOP_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getTopTemperature() {
		return this.getTopTemperatureChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#TOP_TEMPERATURE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTopTemperature(Integer value) {
		this.getTopTemperatureChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#TOP_TEMPERATURE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTopTemperature(int value) {
		this.getTopTemperatureChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MEAN_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMeanTemperatureChannel() {
		return this.channel(ChannelId.MEAN_TEMPERATURE);
	}

	/**
	 * Gets the Mean Temperature in [deci-°C]. See
	 * {@link ChannelId#MEAN_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMeanTemperature() {
		return this.getMeanTemperatureChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MEAN_TEMPERATURE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMeanTemperature(Integer value) {
		this.getMeanTemperatureChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MEAN_TEMPERATURE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMeanTemperature(int value) {
		this.getMeanTemperatureChannel().setNextValue(value);
	}
}
