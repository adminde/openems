package io.openems.edge.heat.tess.api;

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

@ProviderType
public interface ThermalEss extends OpenemsComponent {

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
				.text("State of Charge of the thermal energy storage system")), //

		/**
		 * Capacity.
		 *
		 * <ul>
		 * <li>Interface: ThermalEss
		 * <li>Type: Integer
		 * <li>Unit: Wh
		 * </ul>
		 */
		CAPACITY(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT_HOURS) //
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Current average Temperature of the Thermal Energy Storage System.
		 *
		 * <ul>
		 * <li>Interface: ThermalEss
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
		 * </ul>
		 */
		TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.persistencePriority(PersistencePriority.HIGH) //
				.accessMode(AccessMode.READ_ONLY)), //

		/**
		 * Maximum Temperature the storage tank is physically allowed to reach. Hardware
		 * safety limit; used as an absolute clamp.
		 *
		 * <ul>
		 * <li>Interface: ThermalEss
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
		 * </ul>
		 */
		MAX_TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Maximum target Temperature of the operating band. Heating switches off at or
		 * above this value.
		 *
		 * <ul>
		 * <li>Interface: ThermalEss
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		MAX_TARGET_TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Minimum target Temperature of the operating band. Heating switches on at or
		 * below this value.
		 *
		 * <ul>
		 * <li>Interface: ThermalEss
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		MIN_TARGET_TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Net Thermal Power. Positive values for charging (heat input); negative for
		 * discharging (heat extraction).
		 *
		 * <ul>
		 * <li>Interface: ThermalEss
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		THERMAL_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Net thermal power. Positive for charging; negative for discharging.")),

		/**
		 * Cumulated Thermal Charge Energy.
		 *
		 * <ul>
		 * <li>Interface: ThermalEss
		 * <li>Type: Long
		 * <li>Unit: Wh
		 * </ul>
		 */
		THERMAL_CHARGE_ENERGY(Doc.of(OpenemsType.LONG) //
				.unit(Unit.CUMULATED_WATT_HOURS) //
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Cumulated Thermal Discharge Energy.
		 *
		 * <ul>
		 * <li>Interface: ThermalEss
		 * <li>Type: Long
		 * <li>Unit: Wh
		 * </ul>
		 */
		THERMAL_DISCHARGE_ENERGY(Doc.of(OpenemsType.LONG) //
				.unit(Unit.CUMULATED_WATT_HOURS) //
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
	 * Gets the Channel for {@link ChannelId#SOC}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getSocChannel() {
		return this.channel(ChannelId.SOC);
	}

	/**
	 * Gets the State of Charge in [%]. See {@link ChannelId#SOC}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getSoc() {
		return this.getSocChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#SOC} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setSoc(Integer value) {
		this.getSocChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#SOC} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setSoc(int value) {
		this.getSocChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#CAPACITY}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getCapacityChannel() {
		return this.channel(ChannelId.CAPACITY);
	}

	/**
	 * Gets the Capacity in [Wh]. See {@link ChannelId#CAPACITY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getCapacity() {
		return this.getCapacityChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#CAPACITY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setCapacity(Integer value) {
		this.getCapacityChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#CAPACITY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setCapacity(int value) {
		this.getCapacityChannel().setNextValue(value);
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
	 * Gets the Channel for {@link ChannelId#MAX_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMaxTemperatureChannel() {
		return this.channel(ChannelId.MAX_TEMPERATURE);
	}

	/**
	 * Gets the maximum physically allowed Temperature in [deci-°C]. See
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
	 * Gets the Channel for {@link ChannelId#MAX_TARGET_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMaxTargetTemperatureChannel() {
		return this.channel(ChannelId.MAX_TARGET_TEMPERATURE);
	}

	/**
	 * Gets the maximum allowed Temperature in [deci-°C]. See
	 * {@link ChannelId#MAX_TARGET_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMaxTargetTemperature() {
		return this.getMaxTargetTemperatureChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MAX_TARGET_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxTargetTemperature(Integer value) {
		this.getMaxTargetTemperatureChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MAX_TARGET_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxTargetTemperature(int value) {
		this.getMaxTargetTemperatureChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MIN_TARGET_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMinTargetTemperatureChannel() {
		return this.channel(ChannelId.MIN_TARGET_TEMPERATURE);
	}

	/**
	 * Gets the minimum allowed Temperature in [deci-°C]. See
	 * {@link ChannelId#MIN_TARGET_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMinTargetTemperature() {
		return this.getMinTargetTemperatureChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MIN_TARGET_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMinTargetTemperature(Integer value) {
		this.getMinTargetTemperatureChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MIN_TARGET_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMinTargetTemperature(int value) {
		this.getMinTargetTemperatureChannel().setNextValue(value);
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
	 * Gets the net Thermal Power in [W]. See {@link ChannelId#THERMAL_POWER}.
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
	 * Gets the Channel for {@link ChannelId#THERMAL_CHARGE_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getThermalChargeEnergyChannel() {
		return this.channel(ChannelId.THERMAL_CHARGE_ENERGY);
	}

	/**
	 * Gets the cumulated Thermal Charge Energy in [Wh]. See
	 * {@link ChannelId#THERMAL_CHARGE_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getThermalChargeEnergy() {
		return this.getThermalChargeEnergyChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#THERMAL_CHARGE_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setThermalChargeEnergy(Long value) {
		this.getThermalChargeEnergyChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#THERMAL_CHARGE_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setThermalChargeEnergy(long value) {
		this.getThermalChargeEnergyChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#THERMAL_DISCHARGE_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getThermalDischargeEnergyChannel() {
		return this.channel(ChannelId.THERMAL_DISCHARGE_ENERGY);
	}

	/**
	 * Gets the cumulated Thermal Discharge Energy in [Wh]. See
	 * {@link ChannelId#THERMAL_DISCHARGE_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getThermalDischargeEnergy() {
		return this.getThermalDischargeEnergyChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#THERMAL_DISCHARGE_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setThermalDischargeEnergy(Long value) {
		this.getThermalDischargeEnergyChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#THERMAL_DISCHARGE_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setThermalDischargeEnergy(long value) {
		this.getThermalDischargeEnergyChannel().setNextValue(value);
	}

}
