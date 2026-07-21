package io.openems.edge.meter.phoenixcontact;

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

public interface PhoenixContactMeter extends ElectricityMeter, OpenemsComponent {

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
				.text("Total Power Factor")), //
		POWER_FACTOR_L1(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.NONE) //
				.text("Power Factor L1")), //
		POWER_FACTOR_L2(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.NONE) //
				.text("Power Factor L2")), //
		POWER_FACTOR_L3(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.NONE) //
				.text("Power Factor L3")), //
		PHASE_ANGLE(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.DECIMAL_DEGREE) //
				.text("Total Phase Angle U-I")), //
		PHASE_ANGLE_L1(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.DECIMAL_DEGREE) //
				.text("Phase Angle U-I L1")), //
		PHASE_ANGLE_L2(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.DECIMAL_DEGREE) //
				.text("Phase Angle U-I L2")), //
		PHASE_ANGLE_L3(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.DECIMAL_DEGREE) //
				.text("Phase Angle U-I L3")), //
		REACTIVE_LAGGING_ENERGY(Doc.of(OpenemsType.LONG) //
				.unit(Unit.VOLT_AMPERE_REACTIVE_HOURS) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Lagging Reactive Energy (integral over positive/inductive reactive power)")), //
		REACTIVE_LEADING_ENERGY(Doc.of(OpenemsType.LONG) //
				.unit(Unit.VOLT_AMPERE_REACTIVE_HOURS) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Leading Reactive Energy (integral over negative/capacitive reactive power)"));

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
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L1_L2}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL1L2Channel() {
		return this.channel(ChannelId.VOLTAGE_L1_L2);
	}

	/**
	 * Gets the Voltage L1-L2 in [mV]. See {@link ChannelId#VOLTAGE_L1_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL1L2() {
		return this.getVoltageL1L2Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L1_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL1L2(Integer value) {
		this.getVoltageL1L2Channel().setNextValue(value);
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
	 * Gets the Voltage L2-L3 in [mV]. See {@link ChannelId#VOLTAGE_L2_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL2L3() {
		return this.getVoltageL2L3Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L2_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL2L3(Integer value) {
		this.getVoltageL2L3Channel().setNextValue(value);
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
	 * Gets the Voltage L3-L1 in [mV]. See {@link ChannelId#VOLTAGE_L3_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL3L1() {
		return this.getVoltageL3L1Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L3_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL3L1(Integer value) {
		this.getVoltageL3L1Channel().setNextValue(value);
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
	 * Gets the Voltage Harmonic Distortion L1 in [%]. See
	 * {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getVoltageHarmonicDistortionL1() {
		return this.getVoltageHarmonicDistortionL1Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L1} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageHarmonicDistortionL1(Float value) {
		this.getVoltageHarmonicDistortionL1Channel().setNextValue(value);
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
	 * Gets the Voltage Harmonic Distortion L2 in [%]. See
	 * {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getVoltageHarmonicDistortionL2() {
		return this.getVoltageHarmonicDistortionL2Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L2} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageHarmonicDistortionL2(Float value) {
		this.getVoltageHarmonicDistortionL2Channel().setNextValue(value);
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
	 * Gets the Voltage Harmonic Distortion L3 in [%]. See
	 * {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getVoltageHarmonicDistortionL3() {
		return this.getVoltageHarmonicDistortionL3Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L3} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageHarmonicDistortionL3(Float value) {
		this.getVoltageHarmonicDistortionL3Channel().setNextValue(value);
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
	 * Gets the Current Harmonic Distortion L1 in [%]. See
	 * {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getCurrentHarmonicDistortionL1() {
		return this.getCurrentHarmonicDistortionL1Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L1} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setCurrentHarmonicDistortionL1(Float value) {
		this.getCurrentHarmonicDistortionL1Channel().setNextValue(value);
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
	 * Gets the Current Harmonic Distortion L2 in [%]. See
	 * {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getCurrentHarmonicDistortionL2() {
		return this.getCurrentHarmonicDistortionL2Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L2} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setCurrentHarmonicDistortionL2(Float value) {
		this.getCurrentHarmonicDistortionL2Channel().setNextValue(value);
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
	 * Gets the Current Harmonic Distortion L3 in [%]. See
	 * {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getCurrentHarmonicDistortionL3() {
		return this.getCurrentHarmonicDistortionL3Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L3} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setCurrentHarmonicDistortionL3(Float value) {
		this.getCurrentHarmonicDistortionL3Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#POWER_FACTOR}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getPowerFactorChannel() {
		return this.channel(ChannelId.POWER_FACTOR);
	}

	/**
	 * Gets the total Power Factor. See {@link ChannelId#POWER_FACTOR}.
	 *
	 * @return the Channel {@link Value}
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
	 * Gets the Channel for {@link ChannelId#POWER_FACTOR_L1}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getPowerFactorL1Channel() {
		return this.channel(ChannelId.POWER_FACTOR_L1);
	}

	/**
	 * Gets the Power Factor L1. See {@link ChannelId#POWER_FACTOR_L1}.
	 *
	 * @return the Channel {@link Value}
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
	 * Gets the Channel for {@link ChannelId#POWER_FACTOR_L2}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getPowerFactorL2Channel() {
		return this.channel(ChannelId.POWER_FACTOR_L2);
	}

	/**
	 * Gets the Power Factor L2. See {@link ChannelId#POWER_FACTOR_L2}.
	 *
	 * @return the Channel {@link Value}
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
	 * Gets the Channel for {@link ChannelId#POWER_FACTOR_L3}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getPowerFactorL3Channel() {
		return this.channel(ChannelId.POWER_FACTOR_L3);
	}

	/**
	 * Gets the Power Factor L3. See {@link ChannelId#POWER_FACTOR_L3}.
	 *
	 * @return the Channel {@link Value}
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
	 * Gets the Channel for {@link ChannelId#PHASE_ANGLE}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getPhaseAngleChannel() {
		return this.channel(ChannelId.PHASE_ANGLE);
	}

	/**
	 * Gets the total Phase Angle U-I in [°]. See {@link ChannelId#PHASE_ANGLE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getPhaseAngle() {
		return this.getPhaseAngleChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#PHASE_ANGLE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPhaseAngle(Float value) {
		this.getPhaseAngleChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#PHASE_ANGLE_L1}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getPhaseAngleL1Channel() {
		return this.channel(ChannelId.PHASE_ANGLE_L1);
	}

	/**
	 * Gets the Phase Angle U-I L1 in [°]. See {@link ChannelId#PHASE_ANGLE_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getPhaseAngleL1() {
		return this.getPhaseAngleL1Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#PHASE_ANGLE_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPhaseAngleL1(Float value) {
		this.getPhaseAngleL1Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#PHASE_ANGLE_L2}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getPhaseAngleL2Channel() {
		return this.channel(ChannelId.PHASE_ANGLE_L2);
	}

	/**
	 * Gets the Phase Angle U-I L2 in [°]. See {@link ChannelId#PHASE_ANGLE_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getPhaseAngleL2() {
		return this.getPhaseAngleL2Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#PHASE_ANGLE_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPhaseAngleL2(Float value) {
		this.getPhaseAngleL2Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#PHASE_ANGLE_L3}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getPhaseAngleL3Channel() {
		return this.channel(ChannelId.PHASE_ANGLE_L3);
	}

	/**
	 * Gets the Phase Angle U-I L3 in [°]. See {@link ChannelId#PHASE_ANGLE_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getPhaseAngleL3() {
		return this.getPhaseAngleL3Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#PHASE_ANGLE_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPhaseAngleL3(Float value) {
		this.getPhaseAngleL3Channel().setNextValue(value);
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
	 * Gets the Lagging Reactive Energy in [varh]. See
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
	 * Gets the Channel for {@link ChannelId#REACTIVE_LEADING_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getReactiveLeadingEnergyChannel() {
		return this.channel(ChannelId.REACTIVE_LEADING_ENERGY);
	}

	/**
	 * Gets the Leading Reactive Energy in [varh]. See
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

	public static void calculatePhaseVoltages(PhoenixContactMeter meter) {
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
