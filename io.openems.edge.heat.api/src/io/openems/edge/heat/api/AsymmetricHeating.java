package io.openems.edge.heat.api;

import static io.openems.common.utils.IntUtils.sumInteger;

import java.util.function.Consumer;

import org.osgi.annotation.versioning.ProviderType;

import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.LongReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.type.TypeUtils;

/**
 * Represents an asymmetric (per-phase capable) Heating device.
 *
 * <p>
 * Adds the per-phase electrical Channels. The Channel-IDs match
 * {@code ElectricityMeter} (e.g. "ActivePowerL1") for external compatibility.
 */
@ProviderType
public interface AsymmetricHeating extends SymmetricHeating {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Active Power L1.
		 *
		 * <ul>
		 * <li>Interface: AsymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		ACTIVE_POWER_L1(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH)), //
		/**
		 * Active Power L2.
		 *
		 * <ul>
		 * <li>Interface: AsymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		ACTIVE_POWER_L2(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH)), //
		/**
		 * Active Power L3.
		 *
		 * <ul>
		 * <li>Interface: AsymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		ACTIVE_POWER_L3(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH)), //
		/**
		 * Voltage L1.
		 *
		 * <ul>
		 * <li>Interface: AsymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		VOLTAGE_L1(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.persistencePriority(PersistencePriority.HIGH)), //
		/**
		 * Voltage L2.
		 *
		 * <ul>
		 * <li>Interface: AsymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		VOLTAGE_L2(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.persistencePriority(PersistencePriority.HIGH)), //
		/**
		 * Voltage L3.
		 *
		 * <ul>
		 * <li>Interface: AsymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		VOLTAGE_L3(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.persistencePriority(PersistencePriority.HIGH)), //
		/**
		 * Current L1.
		 *
		 * <ul>
		 * <li>Interface: AsymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: mA
		 * </ul>
		 */
		CURRENT_L1(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.persistencePriority(PersistencePriority.HIGH)), //
		/**
		 * Current L2.
		 *
		 * <ul>
		 * <li>Interface: AsymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: mA
		 * </ul>
		 */
		CURRENT_L2(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.persistencePriority(PersistencePriority.HIGH)), //
		/**
		 * Current L3.
		 *
		 * <ul>
		 * <li>Interface: AsymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: mA
		 * </ul>
		 */
		CURRENT_L3(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.persistencePriority(PersistencePriority.HIGH)), //
		/**
		 * Active Production Energy L1.
		 *
		 * <ul>
		 * <li>Interface: AsymmetricHeating
		 * <li>Type: Long
		 * <li>Unit: Wh
		 * </ul>
		 */
		ACTIVE_PRODUCTION_ENERGY_L1(Doc.of(OpenemsType.LONG) //
				.unit(Unit.CUMULATED_WATT_HOURS) //
				.persistencePriority(PersistencePriority.HIGH)), //
		/**
		 * Active Production Energy L2.
		 *
		 * <ul>
		 * <li>Interface: AsymmetricHeating
		 * <li>Type: Long
		 * <li>Unit: Wh
		 * </ul>
		 */
		ACTIVE_PRODUCTION_ENERGY_L2(Doc.of(OpenemsType.LONG) //
				.unit(Unit.CUMULATED_WATT_HOURS) //
				.persistencePriority(PersistencePriority.HIGH)), //
		/**
		 * Active Production Energy L3.
		 *
		 * <ul>
		 * <li>Interface: AsymmetricHeating
		 * <li>Type: Long
		 * <li>Unit: Wh
		 * </ul>
		 */
		ACTIVE_PRODUCTION_ENERGY_L3(Doc.of(OpenemsType.LONG) //
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
	 * Gets the Channel for {@link ChannelId#ACTIVE_POWER_L1}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getActivePowerL1Channel() {
		return this.channel(ChannelId.ACTIVE_POWER_L1);
	}

	/**
	 * Gets the Active Power on L1 in [W]. See {@link ChannelId#ACTIVE_POWER_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getActivePowerL1() {
		return this.getActivePowerL1Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL1(Integer value) {
		this.getActivePowerL1Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL1(int value) {
		this.getActivePowerL1Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_POWER_L2}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getActivePowerL2Channel() {
		return this.channel(ChannelId.ACTIVE_POWER_L2);
	}

	/**
	 * Gets the Active Power on L2 in [W]. See {@link ChannelId#ACTIVE_POWER_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getActivePowerL2() {
		return this.getActivePowerL2Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL2(Integer value) {
		this.getActivePowerL2Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL2(int value) {
		this.getActivePowerL2Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_POWER_L3}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getActivePowerL3Channel() {
		return this.channel(ChannelId.ACTIVE_POWER_L3);
	}

	/**
	 * Gets the Active Power on L3 in [W]. See {@link ChannelId#ACTIVE_POWER_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getActivePowerL3() {
		return this.getActivePowerL3Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL3(Integer value) {
		this.getActivePowerL3Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL3(int value) {
		this.getActivePowerL3Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L1}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL1Channel() {
		return this.channel(ChannelId.VOLTAGE_L1);
	}

	/**
	 * Gets the Voltage on L1 in [mV]. See {@link ChannelId#VOLTAGE_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL1() {
		return this.getVoltageL1Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL1(Integer value) {
		this.getVoltageL1Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL1(int value) {
		this.getVoltageL1Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L2}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL2Channel() {
		return this.channel(ChannelId.VOLTAGE_L2);
	}

	/**
	 * Gets the Voltage on L2 in [mV]. See {@link ChannelId#VOLTAGE_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL2() {
		return this.getVoltageL2Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL2(Integer value) {
		this.getVoltageL2Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL2(int value) {
		this.getVoltageL2Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L3}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL3Channel() {
		return this.channel(ChannelId.VOLTAGE_L3);
	}

	/**
	 * Gets the Voltage on L3 in [mV]. See {@link ChannelId#VOLTAGE_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL3() {
		return this.getVoltageL3Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL3(Integer value) {
		this.getVoltageL3Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL3(int value) {
		this.getVoltageL3Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#CURRENT_L1}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getCurrentL1Channel() {
		return this.channel(ChannelId.CURRENT_L1);
	}

	/**
	 * Gets the Current on L1 in [mA]. See {@link ChannelId#CURRENT_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getCurrentL1() {
		return this.getCurrentL1Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#CURRENT_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setCurrentL1(Integer value) {
		this.getCurrentL1Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#CURRENT_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setCurrentL1(int value) {
		this.getCurrentL1Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#CURRENT_L2}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getCurrentL2Channel() {
		return this.channel(ChannelId.CURRENT_L2);
	}

	/**
	 * Gets the Current on L2 in [mA]. See {@link ChannelId#CURRENT_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getCurrentL2() {
		return this.getCurrentL2Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#CURRENT_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setCurrentL2(Integer value) {
		this.getCurrentL2Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#CURRENT_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setCurrentL2(int value) {
		this.getCurrentL2Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#CURRENT_L3}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getCurrentL3Channel() {
		return this.channel(ChannelId.CURRENT_L3);
	}

	/**
	 * Gets the Current on L3 in [mA]. See {@link ChannelId#CURRENT_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getCurrentL3() {
		return this.getCurrentL3Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#CURRENT_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setCurrentL3(Integer value) {
		this.getCurrentL3Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#CURRENT_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setCurrentL3(int value) {
		this.getCurrentL3Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_PRODUCTION_ENERGY_L1}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getActiveProductionEnergyL1Channel() {
		return this.channel(ChannelId.ACTIVE_PRODUCTION_ENERGY_L1);
	}

	/**
	 * Gets the Active Production Energy on L1 in [Wh]. See
	 * {@link ChannelId#ACTIVE_PRODUCTION_ENERGY_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getActiveProductionEnergyL1() {
		return this.getActiveProductionEnergyL1Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#ACTIVE_PRODUCTION_ENERGY_L1} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActiveProductionEnergyL1(Long value) {
		this.getActiveProductionEnergyL1Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#ACTIVE_PRODUCTION_ENERGY_L1} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActiveProductionEnergyL1(long value) {
		this.getActiveProductionEnergyL1Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_PRODUCTION_ENERGY_L2}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getActiveProductionEnergyL2Channel() {
		return this.channel(ChannelId.ACTIVE_PRODUCTION_ENERGY_L2);
	}

	/**
	 * Gets the Active Production Energy on L2 in [Wh]. See
	 * {@link ChannelId#ACTIVE_PRODUCTION_ENERGY_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getActiveProductionEnergyL2() {
		return this.getActiveProductionEnergyL2Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#ACTIVE_PRODUCTION_ENERGY_L2} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActiveProductionEnergyL2(Long value) {
		this.getActiveProductionEnergyL2Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#ACTIVE_PRODUCTION_ENERGY_L2} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActiveProductionEnergyL2(long value) {
		this.getActiveProductionEnergyL2Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_PRODUCTION_ENERGY_L3}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getActiveProductionEnergyL3Channel() {
		return this.channel(ChannelId.ACTIVE_PRODUCTION_ENERGY_L3);
	}

	/**
	 * Gets the Active Production Energy on L3 in [Wh]. See
	 * {@link ChannelId#ACTIVE_PRODUCTION_ENERGY_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getActiveProductionEnergyL3() {
		return this.getActiveProductionEnergyL3Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#ACTIVE_PRODUCTION_ENERGY_L3} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActiveProductionEnergyL3(Long value) {
		this.getActiveProductionEnergyL3Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#ACTIVE_PRODUCTION_ENERGY_L3} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActiveProductionEnergyL3(long value) {
		this.getActiveProductionEnergyL3Channel().setNextValue(value);
	}

	/**
	 * Initializes Channel listeners to set the {@link SymmetricHeating}
	 * {@code ACTIVE_POWER} Channel value as the sum of L1 + L2 + L3.
	 *
	 * @param heating the {@link AsymmetricHeating}
	 */
	public static void initializePowerSumChannels(AsymmetricHeating heating) {
		final Consumer<Value<Integer>> activePowerSum = ignore -> {
			heating._setActivePower(sumInteger(//
					heating.getActivePowerL1Channel().getNextValue().get(), //
					heating.getActivePowerL2Channel().getNextValue().get(), //
					heating.getActivePowerL3Channel().getNextValue().get()));
		};
		heating.getActivePowerL1Channel().onSetNextValue(activePowerSum);
		heating.getActivePowerL2Channel().onSetNextValue(activePowerSum);
		heating.getActivePowerL3Channel().onSetNextValue(activePowerSum);
	}

	/**
	 * Initializes Channel listeners to calculate the {@code CURRENT} Channel value
	 * as the sum of {@code CURRENT_L1}, {@code CURRENT_L2} and {@code CURRENT_L3}.
	 *
	 * @param heating the {@link AsymmetricHeating}
	 */
	public static void calculateSumCurrentFromPhases(AsymmetricHeating heating) {
		final Consumer<Value<Integer>> calculate = ignore -> {
			heating._setCurrent(sumInteger(//
					heating.getCurrentL1Channel().getNextValue().get(), //
					heating.getCurrentL2Channel().getNextValue().get(), //
					heating.getCurrentL3Channel().getNextValue().get()));
		};
		heating.getCurrentL1Channel().onSetNextValue(calculate);
		heating.getCurrentL2Channel().onSetNextValue(calculate);
		heating.getCurrentL3Channel().onSetNextValue(calculate);
	}

	/**
	 * Initializes Channel listeners to calculate the {@code VOLTAGE} Channel value
	 * as the average of {@code VOLTAGE_L1}, {@code VOLTAGE_L2} and
	 * {@code VOLTAGE_L3}.
	 *
	 * @param heating the {@link AsymmetricHeating}
	 */
	public static void calculateAverageVoltageFromPhases(AsymmetricHeating heating) {
		final Consumer<Value<Integer>> calculate = ignore -> {
			heating._setVoltage(TypeUtils.averageRounded(//
					heating.getVoltageL1Channel().getNextValue().get(), //
					heating.getVoltageL2Channel().getNextValue().get(), //
					heating.getVoltageL3Channel().getNextValue().get()));
		};
		heating.getVoltageL1Channel().onSetNextValue(calculate);
		heating.getVoltageL2Channel().onSetNextValue(calculate);
		heating.getVoltageL3Channel().onSetNextValue(calculate);
	}

	/**
	 * Initializes Channel listeners to calculate the {@code ACTIVE_POWER_L1},
	 * {@code ACTIVE_POWER_L2} and {@code ACTIVE_POWER_L3} Channels from
	 * {@code ACTIVE_POWER} by dividing by three.
	 *
	 * @param heating the {@link AsymmetricHeating}
	 */
	public static void calculatePhasesFromActivePower(AsymmetricHeating heating) {
		heating.getActivePowerChannel().onSetNextValue(value -> {
			var phase = TypeUtils.divide(value.get(), 3);
			heating._setActivePowerL1(phase);
			heating._setActivePowerL2(phase);
			heating._setActivePowerL3(phase);
		});
	}
}
