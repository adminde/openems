package io.openems.edge.heat.tess.layer;

import org.osgi.service.event.EventHandler;

import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.heat.tess.api.ManagedThermalEss;
import io.openems.edge.heat.tess.api.ThermalEss;

public interface ThermalEssLayer extends ManagedThermalEss, ThermalEss, OpenemsComponent, EventHandler {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Supply Temperature of the last settled heat pump circulation through this
		 * layer. Held while no heat pump circulates.
		 *
		 * <ul>
		 * <li>Interface: ThermalEssLayer
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
		 * </ul>
		 */
		SUPPLY_TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Return Temperature of the last settled heat pump circulation through this
		 * layer. Held while no heat pump circulates.
		 *
		 * <ul>
		 * <li>Interface: ThermalEssLayer
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
		 * </ul>
		 */
		RETURN_TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
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
}
