package io.openems.edge.heat.api;

import org.osgi.annotation.versioning.ProviderType;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.LongReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;

/**
 * Represents a symmetric Heating device.
 *
 * <p>
 * Provides the thermal Channels (temperature, thermal power and energy) plus the
 * summary electrical Channels of the device. The electrical Channels use the
 * same Channel-IDs as {@code ElectricityMeter} (e.g. "ActivePower") so that
 * external references stay compatible; positive {@code ACTIVE_POWER} means
 * electrical consumption.
 */
@ProviderType
public interface SymmetricHeating extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Current Temperature reference of the Heating component. Typically the flow
		 * temperature.
		 *
		 * <ul>
		 * <li>Interface: SymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
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
		 * <li>Interface: SymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: &gt; 0
		 * </ul>
		 */
		THERMAL_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Thermal Power generation of the Heating.")), //

		/**
		 * Cumulated Thermal Energy.
		 *
		 * <ul>
		 * <li>Interface: SymmetricHeating
		 * <li>Type: Long
		 * <li>Unit: Wh
		 * </ul>
		 */
		THERMAL_ENERGY(Doc.of(OpenemsType.LONG) //
				.unit(Unit.CUMULATED_WATT_HOURS) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Cumulated thermal energy of the Heating.")), //

		/**
		 * Active Power. Positive values for electrical consumption.
		 *
		 * <ul>
		 * <li>Interface: SymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		ACTIVE_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Voltage.
		 *
		 * <ul>
		 * <li>Interface: SymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * <li>Range: only positive values
		 * </ul>
		 */
		VOLTAGE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Current.
		 *
		 * <ul>
		 * <li>Interface: SymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: mA
		 * </ul>
		 */
		CURRENT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Active Production Energy. Cumulated energy relating to positive
		 * {@link #ACTIVE_POWER}.
		 *
		 * <ul>
		 * <li>Interface: SymmetricHeating
		 * <li>Type: Long
		 * <li>Unit: Wh
		 * </ul>
		 */
		ACTIVE_PRODUCTION_ENERGY(Doc.of(OpenemsType.LONG) //
				.unit(Unit.CUMULATED_WATT_HOURS) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Active Consumption Energy. Cumulated energy relating to the electrical
		 * consumption of the Heating.
		 *
		 * <ul>
		 * <li>Interface: SymmetricHeating
		 * <li>Type: Long
		 * <li>Unit: Wh
		 * </ul>
		 */
		ACTIVE_CONSUMPTION_ENERGY(Doc.of(OpenemsType.LONG) //
				.unit(Unit.CUMULATED_WATT_HOURS) //
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
	 * Gets the Channel for {@link ChannelId#TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getTemperatureChannel() {
		return this.channel(ChannelId.TEMPERATURE);
	}

	/**
	 * Gets the Temperature in [deci-°C]. See {@link ChannelId#TEMPERATURE}.
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
	 * Gets the Thermal Power in [W]. See {@link ChannelId#THERMAL_POWER}.
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

	/**
	 * Gets the Channel for {@link ChannelId#THERMAL_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getThermalEnergyChannel() {
		return this.channel(ChannelId.THERMAL_ENERGY);
	}

	/**
	 * Gets the Cumulated Thermal Energy in [Wh]. See {@link ChannelId#THERMAL_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getThermalEnergy() {
		return this.getThermalEnergyChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#THERMAL_ENERGY}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setThermalEnergy(Long value) {
		this.getThermalEnergyChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#THERMAL_ENERGY}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setThermalEnergy(long value) {
		this.getThermalEnergyChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getActivePowerChannel() {
		return this.channel(ChannelId.ACTIVE_POWER);
	}

	/**
	 * Gets the Active Power in [W]. Positive for electrical consumption. See
	 * {@link ChannelId#ACTIVE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getActivePower() {
		return this.getActivePowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePower(Integer value) {
		this.getActivePowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePower(int value) {
		this.getActivePowerChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageChannel() {
		return this.channel(ChannelId.VOLTAGE);
	}

	/**
	 * Gets the Voltage in [mV]. See {@link ChannelId#VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltage() {
		return this.getVoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltage(Integer value) {
		this.getVoltageChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltage(int value) {
		this.getVoltageChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getCurrentChannel() {
		return this.channel(ChannelId.CURRENT);
	}

	/**
	 * Gets the Current in [mA]. See {@link ChannelId#CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getCurrent() {
		return this.getCurrentChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#CURRENT} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setCurrent(Integer value) {
		this.getCurrentChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#CURRENT} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setCurrent(int value) {
		this.getCurrentChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_PRODUCTION_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getActiveProductionEnergyChannel() {
		return this.channel(ChannelId.ACTIVE_PRODUCTION_ENERGY);
	}

	/**
	 * Gets the Active Production Energy in [Wh]. See
	 * {@link ChannelId#ACTIVE_PRODUCTION_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getActiveProductionEnergy() {
		return this.getActiveProductionEnergyChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#ACTIVE_PRODUCTION_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActiveProductionEnergy(Long value) {
		this.getActiveProductionEnergyChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#ACTIVE_PRODUCTION_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActiveProductionEnergy(long value) {
		this.getActiveProductionEnergyChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_CONSUMPTION_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getActiveConsumptionEnergyChannel() {
		return this.channel(ChannelId.ACTIVE_CONSUMPTION_ENERGY);
	}

	/**
	 * Gets the Active Consumption Energy in [Wh]. See
	 * {@link ChannelId#ACTIVE_CONSUMPTION_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getActiveConsumptionEnergy() {
		return this.getActiveConsumptionEnergyChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#ACTIVE_CONSUMPTION_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActiveConsumptionEnergy(Long value) {
		this.getActiveConsumptionEnergyChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#ACTIVE_CONSUMPTION_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActiveConsumptionEnergy(long value) {
		this.getActiveConsumptionEnergyChannel().setNextValue(value);
	}

}
