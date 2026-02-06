package io.openems.edge.heat.api;

import org.osgi.annotation.versioning.ProviderType;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;

@ProviderType
public interface Heating extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Current Temperature reference of the Heating component.
		 * Typically the flow temperature. See {@link ChannelId#THERMAL_POWER}.
		 *
		 * <ul>
		 * <li>Interface: Heating
		 * <li>Type: Integer
		 * <li>Unit: °C
		 * </ul>
		 */
		TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.persistencePriority(PersistencePriority.HIGH) //
				.accessMode(AccessMode.READ_ONLY)), //

		/**
		 * Thermal Power.
		 *
		 * <ul>
		 * <li>Interface: Heating
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: > 0
		 * </ul>
		 */
		THERMAL_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Thermal Power generation of the Heating.")), //

		/**
		 * Thermal Power.
		 *
		 * <ul>
		 * <li>Interface: Heating
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: > 0
		 * </ul>
		 */
		THERMAL_ENERGY(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Thermal Power generation of the Heating.")), //
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
	 * Gets the Channel for {@link ChannelId#TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getTemperatureChannel() {
		return this.channel(ChannelId.TEMPERATURE);
	}

	/**
	 * Gets the Temperature in [°C]. See 
	 * {@link ChannelId#TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getTemperature() {
		return this.getTemperatureChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#TEMPERATURE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTemperature(Integer value) {
		this.getTemperatureChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#TEMPERATURE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTemperature(int value) {
		this.getTemperatureChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#THERMAL_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getThermalPowerChannel() {
		return this.channel(ChannelId.THERMAL_POWER);
	}

	/**
	 * Gets the Thermal Power in [W]. See 
	 * {@link ChannelId#THERMAL_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getThermalPower() {
		return this.getThermalPowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#THERMAL_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setThermalPower(Integer value) {
		this.getThermalPowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#THERMAL_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setThermalPower(int value) {
		this.getThermalPowerChannel().setNextValue(value);
	}

}
