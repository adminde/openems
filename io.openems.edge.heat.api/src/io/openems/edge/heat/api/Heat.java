package io.openems.edge.heat.api;

import org.osgi.annotation.versioning.ProviderType;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;

/**
 * Represents a Heat element, e.g. an electric heating rod with its own
 * thermostat.
 *
 * <p>
 * The thermostat reports its measured temperature via
 * {@link SymmetricHeating.ChannelId#TEMPERATURE}.
 */
@ProviderType
public interface Heat extends AsymmetricHeating, OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Current Status of the Heat element.
		 *
		 * <ul>
		 * <li>Interface: Heat
		 * <li>Type: Status
		 * </ul>
		 */
		STATUS(Doc.of(Status.values()) //
				.persistencePriority(PersistencePriority.HIGH) //
				.accessMode(AccessMode.READ_ONLY)), //

		/**
		 * Target Temperature of the thermostat of the Heat element. Not to be confused
		 * with the Target Temperature of a Thermal Energy Storage System.
		 *
		 * <ul>
		 * <li>Interface: Heat
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
		 * </ul>
		 */
		TARGET_TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.persistencePriority(PersistencePriority.HIGH) //
				.accessMode(AccessMode.READ_ONLY)), //

		/**
		 * Thermal Energy required to reach the {@link ChannelId#TARGET_TEMPERATURE}.
		 *
		 * <ul>
		 * <li>Interface: Heat
		 * <li>Type: Integer
		 * <li>Unit: Wh
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		REMAINING_HEAT_ENERGY(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT_HOURS) //
				.accessMode(AccessMode.READ_ONLY)), //
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
	 * Gets the Channel for {@link ChannelId#STATUS}.
	 *
	 * @return the Channel
	 */
	public default Channel<Status> getStatusChannel() {
		return this.channel(ChannelId.STATUS);
	}

	/**
	 * Gets the Status of the Heat element. See {@link ChannelId#STATUS}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Status getStatus() {
		return this.getStatusChannel().value().asEnum();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#STATUS} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setStatus(Status value) {
		this.getStatusChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#TARGET_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getTargetTemperatureChannel() {
		return this.channel(ChannelId.TARGET_TEMPERATURE);
	}

	/**
	 * Gets the Target Temperature of the thermostat in [deci-°C]. See
	 * {@link ChannelId#TARGET_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getTargetTemperature() {
		return this.getTargetTemperatureChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#TARGET_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTargetTemperature(Integer value) {
		this.getTargetTemperatureChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#TARGET_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTargetTemperature(int value) {
		this.getTargetTemperatureChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#REMAINING_HEAT_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getRemainingHeatEnergyChannel() {
		return this.channel(ChannelId.REMAINING_HEAT_ENERGY);
	}

	/**
	 * Gets the Thermal Energy required to reach the Target Temperature in [Wh]. See
	 * {@link ChannelId#REMAINING_HEAT_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getRemainingHeatEnergy() {
		return this.getRemainingHeatEnergyChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#REMAINING_HEAT_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setRemainingHeatEnergy(Integer value) {
		this.getRemainingHeatEnergyChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#REMAINING_HEAT_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setRemainingHeatEnergy(int value) {
		this.getRemainingHeatEnergyChannel().setNextValue(value);
	}

}
