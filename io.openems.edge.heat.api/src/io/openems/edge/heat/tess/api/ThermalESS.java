package io.openems.edge.heat.tess.api;

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
public interface ThermalESS extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * State of Charge.
		 *
		 * <ul>
		 * <li>Interface: ThermalEss
		 * <li>Type: Integer
		 * <li>Unit: %
		 * <li>Range: 0..100
		 * </ul>
		 */
		SOC(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.PERCENT) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("State of Charge of the energy storage system")), //
		/**
		 * Capacity.
		 *
		 * <ul>
		 * <li>Interface: ThermalEss
		 * <li>Type: Integer
		 * <li>Unit: Wh
		 * </ul>
		 *
		 * @since 2019.5.0
		 */
		CAPACITY(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT_HOURS) //
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Current Temperature reference of the Thermal Energy Storage System.
		 *
		 * <ul>
		 * <li>Interface: ThermalEss
		 * <li>Type: Integer
		 * <li>Unit: °C
		 * </ul>
		 */
		TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.persistencePriority(PersistencePriority.HIGH) //
				.accessMode(AccessMode.READ_ONLY)), //
		/**
		 * Holds the currently maximum allowed Temperature. This value is commonly
		 * defined by the heat demand.
		 *
		 * <ul>
		 * <li>Interface: ThermalEss
		 * <li>Type: Integer
		 * <li>Unit: °C
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		MAX_ALLOWED_TEMERPATURE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Holds the currently minimum allowed Temperature. This value is commonly
		 * defined by the heat demand.
		 *
		 * <ul>
		 * <li>Interface: ThermalEss
		 * <li>Type: Integer
		 * <li>Unit: °C
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		MIN_ALLOWED_TEMERPATURE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.persistencePriority(PersistencePriority.HIGH)),
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

}
