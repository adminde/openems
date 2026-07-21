package io.openems.edge.meter.acrel.adl400;

import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.FloatReadChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.LongReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.api.ElectricityMeter;

public interface AcrelAdl400Meter extends ElectricityMeter, OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		VOLTAGE_L1_L2(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("Voltage L1-L2")), //
		VOLTAGE_L2_L3(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("Voltage L2-L3")), //
		VOLTAGE_L3_L1(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("Voltage L3-L1")), //

		VOLTAGE_HARMONIC_DISTORTION_L1(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.PERCENT) //
				.text("Voltage Harmonic Distortion L1")), //
		VOLTAGE_HARMONIC_DISTORTION_L2(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.PERCENT) //
				.text("Voltage Harmonic Distortion L2")), //
		VOLTAGE_HARMONIC_DISTORTION_L3(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.PERCENT) //
				.text("Voltage Harmonic Distortion L3")), //

		CURRENT_HARMONIC_DISTORTION_L1(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.PERCENT) //
				.text("Current Harmonic Distortion L1")), //
		CURRENT_HARMONIC_DISTORTION_L2(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.PERCENT) //
				.text("Current Harmonic Distortion L2")), //
		CURRENT_HARMONIC_DISTORTION_L3(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.PERCENT) //
				.text("Current Harmonic Distortion L3")), //

		POWER_FACTOR(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.NONE) //
				.text("Power Factor")), //
		POWER_FACTOR_L1(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.NONE) //
				.text("Power Factor L1")), //
		POWER_FACTOR_L2(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.NONE) //
				.text("Power Factor L2")), //
		POWER_FACTOR_L3(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.NONE) //
				.text("Power Factor L3")), //

		REACTIVE_LAGGING_ENERGY(Doc.of(OpenemsType.LONG) //
				.unit(Unit.VOLT_AMPERE_REACTIVE_HOURS) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Lagging Reactive Energy (integral over positive/inductive reactive power)")), //
		REACTIVE_LEADING_ENERGY(Doc.of(OpenemsType.LONG) //
				.unit(Unit.VOLT_AMPERE_REACTIVE_HOURS) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Leading Reactive Energy (integral over negative/capacitive reactive power)"));

		private final io.openems.edge.common.channel.Doc doc;

		private ChannelId(io.openems.edge.common.channel.Doc doc) {
			this.doc = doc;
		}

		@Override
		public io.openems.edge.common.channel.Doc doc() {
			return this.doc;
		}
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L1_L2}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL1L2Channel() {
		return this.channel(ChannelId.VOLTAGE_L1_L2);
	}

	/**
	 * Gets the Voltage L1-L2 in [mV]. See
	 * {@link ChannelId#VOLTAGE_L1_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL1L2() {
		return this.getVoltageL1L2Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L2_L3}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL2L3Channel() {
		return this.channel(ChannelId.VOLTAGE_L2_L3);
	}

	/**
	 * Gets the Voltage L2-L3 in [mV]. See
	 * {@link ChannelId#VOLTAGE_L2_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL2L3() {
		return this.getVoltageL2L3Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L3_L1}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL3L1Channel() {
		return this.channel(ChannelId.VOLTAGE_L3_L1);
	}

	/**
	 * Gets the Voltage L3-L1 in [mV]. See
	 * {@link ChannelId#VOLTAGE_L3_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL3L1() {
		return this.getVoltageL3L1Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L1}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getVoltageHarmonicDistortionL1Channel() {
		return this.channel(ChannelId.VOLTAGE_HARMONIC_DISTORTION_L1);
	}

	/**
	 * Gets the Voltage Harmonic Distortion on L1 in [%]. See
	 * {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getVoltageHarmonicDistortionL1() {
		return this.getVoltageHarmonicDistortionL1Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L2}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getVoltageHarmonicDistortionL2Channel() {
		return this.channel(ChannelId.VOLTAGE_HARMONIC_DISTORTION_L2);
	}

	/**
	 * Gets the Voltage Harmonic Distortion on L2 in [%]. See
	 * {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getVoltageHarmonicDistortionL2() {
		return this.getVoltageHarmonicDistortionL2Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L3}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getVoltageHarmonicDistortionL3Channel() {
		return this.channel(ChannelId.VOLTAGE_HARMONIC_DISTORTION_L3);
	}

	/**
	 * Gets the Voltage Harmonic Distortion on L3 in [%]. See
	 * {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getVoltageHarmonicDistortionL3() {
		return this.getVoltageHarmonicDistortionL3Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L1}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getCurrentHarmonicDistortionL1Channel() {
		return this.channel(ChannelId.CURRENT_HARMONIC_DISTORTION_L1);
	}

	/**
	 * Gets the Current Harmonic Distortion on L1 in [%]. See
	 * {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getCurrentHarmonicDistortionL1() {
		return this.getCurrentHarmonicDistortionL1Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L2}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getCurrentHarmonicDistortionL2Channel() {
		return this.channel(ChannelId.CURRENT_HARMONIC_DISTORTION_L2);
	}

	/**
	 * Gets the Current Harmonic Distortion on L2 in [%]. See
	 * {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getCurrentHarmonicDistortionL2() {
		return this.getCurrentHarmonicDistortionL2Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L3}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getCurrentHarmonicDistortionL3Channel() {
		return this.channel(ChannelId.CURRENT_HARMONIC_DISTORTION_L3);
	}

	/**
	 * Gets the Current Harmonic Distortion on L3 in [%]. See
	 * {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getCurrentHarmonicDistortionL3() {
		return this.getCurrentHarmonicDistortionL3Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#POWER_FACTOR}.
	 *
	 * @return the Channel for the voltage ratio
	 */
	public default FloatReadChannel getPowerFactorChannel() {
		return this.channel(ChannelId.POWER_FACTOR);
	}

	/**
	 * Gets the Power Factor.
	 *
	 * @return the Channel {@link Value} containing the power factor
	 */
	public default Value<Float> getPowerFactor() {
		return this.getPowerFactorChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactor(Float value) {
		this.getPowerFactorChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactor(float value) { this.getPowerFactorChannel().setNextValue(value); }

	/**
	 * Gets the Channel for {@link ChannelId#POWER_FACTOR_L1}.
	 *
	 * @return the Channel for the voltage ratio
	 */
	public default FloatReadChannel getPowerFactorL1Channel() {
		return this.channel(ChannelId.POWER_FACTOR_L1);
	}

	/**
	 * Gets the Power Factor of L1.
	 *
	 * @return the Channel {@link Value} containing the power factor
	 */
	public default Value<Float> getPowerFactorL1() {
		return this.getPowerFactorL1Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactorL1(Float value) {
		this.getPowerFactorL1Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactorL1(float value) {
		this.getPowerFactorL1Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#POWER_FACTOR_L2}.
	 *
	 * @return the Channel for the voltage ratio
	 */
	public default FloatReadChannel getPowerFactorL2Channel() {
		return this.channel(ChannelId.POWER_FACTOR_L2);
	}

	/**
	 * Gets the Power Factor of L2.
	 *
	 * @return the Channel {@link Value} containing the power factor
	 */
	public default Value<Float> getPowerFactorL2() {
		return this.getPowerFactorL2Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactorL2(Float value) {
		this.getPowerFactorL2Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactorL2(float value) {
		this.getPowerFactorL2Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#POWER_FACTOR_L3}.
	 *
	 * @return the Channel for the voltage ratio
	 */
	public default FloatReadChannel getPowerFactorL3Channel() {
		return this.channel(ChannelId.POWER_FACTOR_L3);
	}

	/**
	 * Gets the Power Factor of L3.
	 *
	 * @return the Channel {@link Value} containing the power factor
	 */
	public default Value<Float> getPowerFactorL3() {
		return this.getPowerFactorL3Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactorL3(Float value) {
		this.getPowerFactorL3Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactorL3(float value) {
		this.getPowerFactorL3Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#REACTIVE_LAGGING_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getReactiveLaggingEnergyChannel() {
		return this.channel(ChannelId.REACTIVE_LAGGING_ENERGY);
	}

	/**
	 * Gets the Reactive Lagging Energy in [varh]. This relates to positive
	 * (lagging/inductive) REACTIVE_POWER. See
	 * {@link ChannelId#REACTIVE_LAGGING_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getReactiveLaggingEnergy() {
		return this.getReactiveLaggingEnergyChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#REACTIVE_LAGGING_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReactiveLaggingEnergy(Long value) {
		this.getReactiveLaggingEnergyChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#REACTIVE_LAGGING_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReactiveLaggingEnergy(long value) {
		this.getReactiveLaggingEnergyChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#REACTIVE_LEADING_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getReactiveLeadingEnergyChannel() {
		return this.channel(ChannelId.REACTIVE_LEADING_ENERGY);
	}

	/**
	 * Gets the Reactive Leading Energy in [varh]. This relates to negative
	 * (leading/capacitive) REACTIVE_POWER. See
	 * {@link ChannelId#REACTIVE_LEADING_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getReactiveLeadingEnergy() {
		return this.getReactiveLeadingEnergyChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#REACTIVE_LEADING_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReactiveLeadingEnergy(Long value) {
		this.getReactiveLeadingEnergyChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#REACTIVE_LEADING_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReactiveLeadingEnergy(long value) {
		this.getReactiveLeadingEnergyChannel().setNextValue(value);
	}

	public static void calculatePhaseVoltages(AcrelAdl400Meter meter) {
		meter.getVoltageL1L2Channel().onSetNextValue(value -> {
			meter._setVoltageL1(calculatePhaseVoltage(value.get()));
		});
		meter.getVoltageL2L3Channel().onSetNextValue(value -> {
			meter._setVoltageL2(calculatePhaseVoltage(value.get()));
		});
		meter.getVoltageL3L1Channel().onSetNextValue(value -> {
			meter._setVoltageL3(calculatePhaseVoltage(value.get()));
		});
	}

	private static Integer calculatePhaseVoltage(Integer phaseToPhaseVoltage) {
		if (phaseToPhaseVoltage == null) {
			return null;
		}
		return (int) Math.round(phaseToPhaseVoltage / Math.sqrt(3));
	}

}
