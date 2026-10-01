package io.openems.edge.heat.pump.api;

import org.osgi.annotation.versioning.ProviderType;

import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.FloatDoc;
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
		 * <li>Implementation Note: mirrors its value to
		 * {@link SymmetricHeating.ChannelId#THERMAL_EFFICIENCY} in percent (× 100).
		 * </ul>
		 */
		COP(new FloatDoc() //
				.unit(Unit.NONE) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Coefficient of Performance (e.g. 3.5)") //
				.<HeatPump>onChannelSetNextValue((self, value) -> self._setThermalEfficiency(//
						value.isDefined() ? Math.round(value.get() * 100) : null))), //

		/**
		 * Supply (outlet) Temperature.
		 *
		 * <ul>
		 * <li>Interface: HeatPump
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
		 * </ul>
		 */
		SUPPLY_TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
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
		RETURN_TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
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

		/**
		 * Maximum Thermal Power the Heat Pump can deliver.
		 *
		 * <ul>
		 * <li>Interface: HeatPump
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		MAX_THERMAL_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Minimum dispatchable Thermal Power of the Heat Pump. Equals
		 * {@link #MAX_THERMAL_POWER} for an on/off device, or the lowest modulation
		 * step for a modulating device.
		 *
		 * <ul>
		 * <li>Interface: HeatPump
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		MIN_THERMAL_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
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
	 * Gets the Channel for {@link ChannelId#SUPPLY_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getSupplyTemperatureChannel() {
		return this.channel(ChannelId.SUPPLY_TEMPERATURE);
	}

	/**
	 * Gets the Supply Temperature in [deci-°C]. See
	 * {@link ChannelId#SUPPLY_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getSupplyTemperature() {
		return this.getSupplyTemperatureChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#SUPPLY_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setSupplyTemperature(Integer value) {
		this.getSupplyTemperatureChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#SUPPLY_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setSupplyTemperature(int value) {
		this.getSupplyTemperatureChannel().setNextValue(value);
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

	/**
	 * Gets the Channel for {@link ChannelId#MAX_THERMAL_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMaxThermalPowerChannel() {
		return this.channel(ChannelId.MAX_THERMAL_POWER);
	}

	/**
	 * Gets the maximum deliverable Thermal Power in [W]. See
	 * {@link ChannelId#MAX_THERMAL_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMaxThermalPower() {
		return this.getMaxThermalPowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MAX_THERMAL_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxThermalPower(Integer value) {
		this.getMaxThermalPowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MAX_THERMAL_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxThermalPower(int value) {
		this.getMaxThermalPowerChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MIN_THERMAL_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMinThermalPowerChannel() {
		return this.channel(ChannelId.MIN_THERMAL_POWER);
	}

	/**
	 * Gets the minimum dispatchable Thermal Power in [W]. See
	 * {@link ChannelId#MIN_THERMAL_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMinThermalPower() {
		return this.getMinThermalPowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MIN_THERMAL_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMinThermalPower(Integer value) {
		this.getMinThermalPowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MIN_THERMAL_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMinThermalPower(int value) {
		this.getMinThermalPowerChannel().setNextValue(value);
	}

}
