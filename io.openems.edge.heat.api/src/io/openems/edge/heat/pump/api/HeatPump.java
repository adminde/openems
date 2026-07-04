package io.openems.edge.heat.pump.api;

import org.osgi.annotation.versioning.ProviderType;

import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.FloatReadChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.heat.api.SymmetricHeating;

@ProviderType
public interface HeatPump extends SymmetricHeating, StartStoppable {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Coefficient of Performance (e.g. 3.5).
		 *
		 * <ul>
		 * <li>Interface: HeatPump
		 * <li>Type: Float
		 * <li>Unit: None (dimensionless)
		 * </ul>
		 */
		COP(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.NONE) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Coefficient of Performance (e.g. 3.5)")), //

		/**
		 * Supply (outlet) Temperature.
		 *
		 * <ul>
		 * <li>Interface: HeatPump
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
		 * </ul>
		 */
		TEMPERATURE_SUPPLY(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Return (inlet) Temperature.
		 *
		 * <ul>
		 * <li>Interface: HeatPump
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
		 * </ul>
		 */
		TEMPERATURE_RETURN(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Maximum Temperature the Heat Pump is able to produce. Hardware limit; used as
		 * a hard stop for control logic.
		 *
		 * <ul>
		 * <li>Interface: HeatPump
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
		 * </ul>
		 */
		MAX_TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
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
	 * Gets the Channel for {@link ChannelId#COP}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getCopChannel() {
		return this.channel(ChannelId.COP);
	}

	/**
	 * Gets the Coefficient of Performance. See {@link ChannelId#COP}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getCop() {
		return this.getCopChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#COP} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setCop(Float value) {
		this.getCopChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#COP} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setCop(float value) {
		this.getCopChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#TEMPERATURE_SUPPLY}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getTemperatureSupplyChannel() {
		return this.channel(ChannelId.TEMPERATURE_SUPPLY);
	}

	/**
	 * Gets the Supply Temperature in [deci-°C]. See
	 * {@link ChannelId#TEMPERATURE_SUPPLY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getTemperatureSupply() {
		return this.getTemperatureSupplyChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#TEMPERATURE_SUPPLY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTemperatureSupply(Integer value) {
		this.getTemperatureSupplyChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#TEMPERATURE_SUPPLY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTemperatureSupply(int value) {
		this.getTemperatureSupplyChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#TEMPERATURE_RETURN}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getTemperatureReturnChannel() {
		return this.channel(ChannelId.TEMPERATURE_RETURN);
	}

	/**
	 * Gets the Return Temperature in [deci-°C]. See
	 * {@link ChannelId#TEMPERATURE_RETURN}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getTemperatureReturn() {
		return this.getTemperatureReturnChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#TEMPERATURE_RETURN} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTemperatureReturn(Integer value) {
		this.getTemperatureReturnChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#TEMPERATURE_RETURN} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTemperatureReturn(int value) {
		this.getTemperatureReturnChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MAX_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMaxTemperatureChannel() {
		return this.channel(ChannelId.MAX_TEMPERATURE);
	}

	/**
	 * Gets the maximum producible Temperature in [deci-°C]. See
	 * {@link ChannelId#MAX_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMaxTemperature() {
		return this.getMaxTemperatureChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MAX_TEMPERATURE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxTemperature(Integer value) {
		this.getMaxTemperatureChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MAX_TEMPERATURE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxTemperature(int value) {
		this.getMaxTemperatureChannel().setNextValue(value);
	}

}
