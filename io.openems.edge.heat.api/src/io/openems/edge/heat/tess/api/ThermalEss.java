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
		 * Maximum Temperature the storage tank is physically allowed to reach. Hard
		 * hardware safety limit and absolute clamp: explicit charge targets (e.g. from
		 * a Time-of-Use or PV-surplus controller) may heat up to this value.
		 * Corresponds to 100&nbsp;% {@link ChannelId#SOC}.
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
		 * Target Temperature of autonomous operation. Soft value: heating switches off
		 * at or above this value when no explicit charge target is set. Explicit
		 * targets may exceed it up to {@link ChannelId#MAX_TEMPERATURE}.
		 *
		 * <ul>
		 * <li>Interface: ThermalEss
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		TARGET_TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Minimum Temperature of the storage. Hard lower limit (comfort/frost
		 * protection): heating switches on at or below this value. Corresponds to
		 * 0&nbsp;% {@link ChannelId#SOC}.
		 *
		 * <ul>
		 * <li>Interface: ThermalEss
		 * <li>Type: Integer
		 * <li>Unit: deci-°C
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		MIN_TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
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
		 * Thermal Charge Power. The heat input component of
		 * {@link ChannelId#THERMAL_POWER}.
		 *
		 * <ul>
		 * <li>Interface: ThermalEss
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		THERMAL_CHARGE_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Thermal Discharge Power. The uncontrollable heat extraction component of
		 * {@link ChannelId#THERMAL_POWER}, including standing losses. Serves as the
		 * basis for heat-demand predictions.
		 *
		 * <ul>
		 * <li>Interface: ThermalEss
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		THERMAL_DISCHARGE_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH)),

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
	 * Gets the Channel for {@link ChannelId#TARGET_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getTargetTemperatureChannel() {
		return this.channel(ChannelId.TARGET_TEMPERATURE);
	}

	/**
	 * Gets the target Temperature of autonomous operation in [deci-°C]. See
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
	 * Gets the Channel for {@link ChannelId#MIN_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMinTemperatureChannel() {
		return this.channel(ChannelId.MIN_TEMPERATURE);
	}

	/**
	 * Gets the minimum Temperature in [deci-°C]. See
	 * {@link ChannelId#MIN_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMinTemperature() {
		return this.getMinTemperatureChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MIN_TEMPERATURE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMinTemperature(Integer value) {
		this.getMinTemperatureChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MIN_TEMPERATURE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMinTemperature(int value) {
		this.getMinTemperatureChannel().setNextValue(value);
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
	 * Gets the Channel for {@link ChannelId#THERMAL_CHARGE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getThermalChargePowerChannel() {
		return this.channel(ChannelId.THERMAL_CHARGE_POWER);
	}

	/**
	 * Gets the Thermal Charge Power in [W]. See
	 * {@link ChannelId#THERMAL_CHARGE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getThermalChargePower() {
		return this.getThermalChargePowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#THERMAL_CHARGE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setThermalChargePower(Integer value) {
		this.getThermalChargePowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#THERMAL_CHARGE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setThermalChargePower(int value) {
		this.getThermalChargePowerChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#THERMAL_DISCHARGE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getThermalDischargePowerChannel() {
		return this.channel(ChannelId.THERMAL_DISCHARGE_POWER);
	}

	/**
	 * Gets the Thermal Discharge Power in [W]. See
	 * {@link ChannelId#THERMAL_DISCHARGE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getThermalDischargePower() {
		return this.getThermalDischargePowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#THERMAL_DISCHARGE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setThermalDischargePower(Integer value) {
		this.getThermalDischargePowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#THERMAL_DISCHARGE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setThermalDischargePower(int value) {
		this.getThermalDischargePowerChannel().setNextValue(value);
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
