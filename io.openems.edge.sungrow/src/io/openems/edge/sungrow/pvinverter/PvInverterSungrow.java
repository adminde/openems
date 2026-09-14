package io.openems.edge.sungrow.pvinverter;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Level;
import io.openems.common.channel.Unit;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.EnumReadChannel;
import io.openems.edge.common.channel.EnumWriteChannel;
import io.openems.edge.common.channel.FloatReadChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter;

public interface PvInverterSungrow extends ManagedSymmetricPvInverter, ElectricityMeter, OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/**
		 * Device Type Code.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * </ul>
		 */
		DEVICE_TYPE_CODE(Doc.of(OpenemsType.INTEGER) //
				.text("Device type code of the inverter model")), //
		/**
		 * Output Type.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: {@link OutputType}
		 * </ul>
		 */
		OUTPUT_TYPE(Doc.of(OutputType.values()) //
				.text("Output type")), //
		/**
		 * Work State.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: {@link WorkState}
		 * </ul>
		 */
		WORK_STATE(Doc.of(WorkState.values()) //
				.text("Work state")), //
		/**
		 * Daily Production Energy.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: Wh
		 * </ul>
		 */
		DAILY_PRODUCTION_ENERGY(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT_HOURS) //
				.text("Energy produced today")), //
		/**
		 * Monthly Production Energy.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: Wh
		 * </ul>
		 */
		MONTHLY_PRODUCTION_ENERGY(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT_HOURS) //
				.text("Energy produced this month")), //
		/**
		 * Total Running Time.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: h
		 * </ul>
		 */
		TOTAL_RUNNING_TIME(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.HOUR) //
				.text("Total running time")), //
		/**
		 * Internal Temperature.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: 0.1 degree Celsius
		 * </ul>
		 */
		INTERNAL_TEMPERATURE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEZIDEGREE_CELSIUS) //
				.text("Internal temperature")), //
		/**
		 * Apparent Power.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: VA
		 * </ul>
		 */
		APPARENT_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.VOLT_AMPERE) //
				.text("Total apparent power")), //
		/**
		 * Power Factor.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Float
		 * <li>Range: -1 to 1, positive values are leading, negative values are lagging
		 * </ul>
		 */
		POWER_FACTOR(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.NONE) //
				.text("Power factor")), //
		/**
		 * DC Power.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		DC_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.text("Total DC power")), //
		/**
		 * MPPT 1 Voltage.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		MPPT_1_VOLTAGE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("MPPT 1 voltage")), //
		/**
		 * MPPT 1 Current.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mA
		 * </ul>
		 */
		MPPT_1_CURRENT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.text("MPPT 1 current")), //
		/**
		 * MPPT 2 Voltage.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		MPPT_2_VOLTAGE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("MPPT 2 voltage")), //
		/**
		 * MPPT 2 Current.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mA
		 * </ul>
		 */
		MPPT_2_CURRENT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.text("MPPT 2 current")), //
		/**
		 * MPPT 3 Voltage.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		MPPT_3_VOLTAGE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("MPPT 3 voltage")), //
		/**
		 * MPPT 3 Current.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mA
		 * </ul>
		 */
		MPPT_3_CURRENT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.text("MPPT 3 current")), //
		/**
		 * MPPT 4 Voltage.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		MPPT_4_VOLTAGE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("MPPT 4 voltage")), //
		/**
		 * MPPT 4 Current.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mA
		 * </ul>
		 */
		MPPT_4_CURRENT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.text("MPPT 4 current")), //
		/**
		 * MPPT 5 Voltage.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		MPPT_5_VOLTAGE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("MPPT 5 voltage")), //
		/**
		 * MPPT 5 Current.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mA
		 * </ul>
		 */
		MPPT_5_CURRENT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.text("MPPT 5 current")), //
		/**
		 * MPPT 6 Voltage.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		MPPT_6_VOLTAGE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("MPPT 6 voltage")), //
		/**
		 * MPPT 6 Current.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mA
		 * </ul>
		 */
		MPPT_6_CURRENT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.text("MPPT 6 current")), //
		/**
		 * MPPT 7 Voltage.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		MPPT_7_VOLTAGE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("MPPT 7 voltage")), //
		/**
		 * MPPT 7 Current.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mA
		 * </ul>
		 */
		MPPT_7_CURRENT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.text("MPPT 7 current")), //
		/**
		 * MPPT 8 Voltage.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		MPPT_8_VOLTAGE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("MPPT 8 voltage")), //
		/**
		 * MPPT 8 Current.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mA
		 * </ul>
		 */
		MPPT_8_CURRENT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.text("MPPT 8 current")), //
		/**
		 * MPPT 9 Voltage.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		MPPT_9_VOLTAGE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("MPPT 9 voltage")), //
		/**
		 * MPPT 9 Current.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mA
		 * </ul>
		 */
		MPPT_9_CURRENT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.text("MPPT 9 current")), //
		/**
		 * MPPT 10 Voltage.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		MPPT_10_VOLTAGE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("MPPT 10 voltage")), //
		/**
		 * MPPT 10 Current.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mA
		 * </ul>
		 */
		MPPT_10_CURRENT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.text("MPPT 10 current")), //
		/**
		 * MPPT 11 Voltage.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		MPPT_11_VOLTAGE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("MPPT 11 voltage")), //
		/**
		 * MPPT 11 Current.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mA
		 * </ul>
		 */
		MPPT_11_CURRENT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.text("MPPT 11 current")), //
		/**
		 * MPPT 12 Voltage.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		MPPT_12_VOLTAGE(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("MPPT 12 voltage")), //
		/**
		 * MPPT 12 Current.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mA
		 * </ul>
		 */
		MPPT_12_CURRENT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.text("MPPT 12 current")), //
		/**
		 * Voltage L1-L2. Only available with three-phase three-wire output.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		VOLTAGE_L1_L2(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("Voltage L1-L2")), //
		/**
		 * Voltage L2-L3. Only available with three-phase three-wire output.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		VOLTAGE_L2_L3(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("Voltage L2-L3")), //
		/**
		 * Voltage L3-L1. Only available with three-phase three-wire output.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: mV
		 * </ul>
		 */
		VOLTAGE_L3_L1(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIVOLT) //
				.text("Voltage L3-L1")), //
		/**
		 * Power Limitation Switch.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: {@link PowerLimitationSwitch}
		 * </ul>
		 */
		POWER_LIMITATION_SWITCH(Doc.of(PowerLimitationSwitch.values()) //
				.accessMode(AccessMode.READ_WRITE) //
				.text("Power limitation switch")), //
		/**
		 * Power Limitation Setting as share of the rated active power.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: Integer
		 * <li>Unit: 0.1 %
		 * <li>Range: 0 to 1000
		 * </ul>
		 */
		POWER_LIMITATION_SETTING(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.THOUSANDTH) //
				.accessMode(AccessMode.READ_WRITE) //
				.text("Power limitation setting")), //
		/**
		 * PV Limit Failed.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: State
		 * </ul>
		 */
		PV_LIMIT_FAILED(Doc.of(Level.FAULT) //
				.text("PV-Limit failed")), //
		/**
		 * Read-Only Mode PV Limit Failed.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: State
		 * </ul>
		 */
		READ_ONLY_MODE_PV_LIMIT_FAILED(Doc.of(Level.WARNING) //
				.text("Read-Only mode is active: PV-Limit failed")), //
		/**
		 * Wrong Phase Wiring Configured.
		 *
		 * <ul>
		 * <li>Interface: PvInverterSungrow
		 * <li>Type: State
		 * </ul>
		 */
		WRONG_PHASE_WIRING_CONFIGURED(Doc.of(Level.WARNING) //
				.text("Configured Phase Wiring does not match the Output Type reported by the inverter"));

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
	 * Derives the phase voltages from the line voltages of an inverter with
	 * three-phase three-wire output. The phase voltage is the line voltage divided
	 * by the square root of three.
	 *
	 * @param inverter the {@link PvInverterSungrow}
	 */
	public static void calculatePhaseVoltagesFromLineVoltages(PvInverterSungrow inverter) {
		inverter.getVoltageL1L2Channel().onSetNextValue(value -> {
			inverter._setVoltageL1(calculatePhaseVoltage(value.get()));
		});
		inverter.getVoltageL2L3Channel().onSetNextValue(value -> {
			inverter._setVoltageL2(calculatePhaseVoltage(value.get()));
		});
		inverter.getVoltageL3L1Channel().onSetNextValue(value -> {
			inverter._setVoltageL3(calculatePhaseVoltage(value.get()));
		});
	}

	private static Integer calculatePhaseVoltage(Integer lineVoltage) {
		if (lineVoltage == null) {
			return null;
		}
		return (int) Math.round(lineVoltage / Math.sqrt(3));
	}

	/**
	 * Gets the Channel for {@link ChannelId#DEVICE_TYPE_CODE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getDeviceTypeCodeChannel() {
		return this.channel(ChannelId.DEVICE_TYPE_CODE);
	}

	/**
	 * Gets the Device Type Code. See {@link ChannelId#DEVICE_TYPE_CODE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getDeviceTypeCode() {
		return this.getDeviceTypeCodeChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#DEVICE_TYPE_CODE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDeviceTypeCode(Integer value) {
		this.getDeviceTypeCodeChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#DEVICE_TYPE_CODE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDeviceTypeCode(int value) {
		this.getDeviceTypeCodeChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#OUTPUT_TYPE}.
	 *
	 * @return the Channel
	 */
	public default EnumReadChannel getOutputTypeChannel() {
		return this.channel(ChannelId.OUTPUT_TYPE);
	}

	/**
	 * Gets the Output Type. See {@link ChannelId#OUTPUT_TYPE}.
	 *
	 * @return the {@link OutputType}
	 */
	public default OutputType getOutputType() {
		return this.getOutputTypeChannel().value().asEnum();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#OUTPUT_TYPE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setOutputType(OutputType value) {
		this.getOutputTypeChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#WORK_STATE}.
	 *
	 * @return the Channel
	 */
	public default EnumReadChannel getWorkStateChannel() {
		return this.channel(ChannelId.WORK_STATE);
	}

	/**
	 * Gets the Work State. See {@link ChannelId#WORK_STATE}.
	 *
	 * @return the {@link WorkState}
	 */
	public default WorkState getWorkState() {
		return this.getWorkStateChannel().value().asEnum();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#WORK_STATE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setWorkState(WorkState value) {
		this.getWorkStateChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#DAILY_PRODUCTION_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getDailyProductionEnergyChannel() {
		return this.channel(ChannelId.DAILY_PRODUCTION_ENERGY);
	}

	/**
	 * Gets the Daily Production Energy in [Wh]. See
	 * {@link ChannelId#DAILY_PRODUCTION_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getDailyProductionEnergy() {
		return this.getDailyProductionEnergyChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#DAILY_PRODUCTION_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDailyProductionEnergy(Integer value) {
		this.getDailyProductionEnergyChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#DAILY_PRODUCTION_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDailyProductionEnergy(int value) {
		this.getDailyProductionEnergyChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MONTHLY_PRODUCTION_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMonthlyProductionEnergyChannel() {
		return this.channel(ChannelId.MONTHLY_PRODUCTION_ENERGY);
	}

	/**
	 * Gets the Monthly Production Energy in [Wh]. See {@link ChannelId#MONTHLY_PRODUCTION_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMonthlyProductionEnergy() {
		return this.getMonthlyProductionEnergyChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MONTHLY_PRODUCTION_ENERGY}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMonthlyProductionEnergy(Integer value) {
		this.getMonthlyProductionEnergyChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MONTHLY_PRODUCTION_ENERGY}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMonthlyProductionEnergy(int value) {
		this.getMonthlyProductionEnergyChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#TOTAL_RUNNING_TIME}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getTotalRunningTimeChannel() {
		return this.channel(ChannelId.TOTAL_RUNNING_TIME);
	}

	/**
	 * Gets the Total Running Time in [h]. See
	 * {@link ChannelId#TOTAL_RUNNING_TIME}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getTotalRunningTime() {
		return this.getTotalRunningTimeChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#TOTAL_RUNNING_TIME} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTotalRunningTime(Integer value) {
		this.getTotalRunningTimeChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#TOTAL_RUNNING_TIME} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTotalRunningTime(int value) {
		this.getTotalRunningTimeChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#INTERNAL_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getInternalTemperatureChannel() {
		return this.channel(ChannelId.INTERNAL_TEMPERATURE);
	}

	/**
	 * Gets the Internal Temperature in [0.1 degree Celsius]. See
	 * {@link ChannelId#INTERNAL_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getInternalTemperature() {
		return this.getInternalTemperatureChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#INTERNAL_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setInternalTemperature(Integer value) {
		this.getInternalTemperatureChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#INTERNAL_TEMPERATURE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setInternalTemperature(int value) {
		this.getInternalTemperatureChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#APPARENT_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getApparentPowerChannel() {
		return this.channel(ChannelId.APPARENT_POWER);
	}

	/**
	 * Gets the Apparent Power in [VA]. See {@link ChannelId#APPARENT_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getApparentPower() {
		return this.getApparentPowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#APPARENT_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setApparentPower(Integer value) {
		this.getApparentPowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#APPARENT_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setApparentPower(int value) {
		this.getApparentPowerChannel().setNextValue(value);
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
	 * Gets the Power Factor. See {@link ChannelId#POWER_FACTOR}.
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
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactor(float value) {
		this.getPowerFactorChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#DC_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getDcPowerChannel() {
		return this.channel(ChannelId.DC_POWER);
	}

	/**
	 * Gets the DC Power in [W]. See {@link ChannelId#DC_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getDcPower() {
		return this.getDcPowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#DC_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDcPower(Integer value) {
		this.getDcPowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#DC_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDcPower(int value) {
		this.getDcPowerChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_1_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt1VoltageChannel() {
		return this.channel(ChannelId.MPPT_1_VOLTAGE);
	}

	/**
	 * Gets the MPPT 1 Voltage in [mV]. See {@link ChannelId#MPPT_1_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt1Voltage() {
		return this.getMppt1VoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_1_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt1Voltage(Integer value) {
		this.getMppt1VoltageChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_1_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt1Voltage(int value) {
		this.getMppt1VoltageChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_1_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt1CurrentChannel() {
		return this.channel(ChannelId.MPPT_1_CURRENT);
	}

	/**
	 * Gets the MPPT 1 Current in [mA]. See {@link ChannelId#MPPT_1_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt1Current() {
		return this.getMppt1CurrentChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_1_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt1Current(Integer value) {
		this.getMppt1CurrentChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_1_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt1Current(int value) {
		this.getMppt1CurrentChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_2_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt2VoltageChannel() {
		return this.channel(ChannelId.MPPT_2_VOLTAGE);
	}

	/**
	 * Gets the MPPT 2 Voltage in [mV]. See {@link ChannelId#MPPT_2_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt2Voltage() {
		return this.getMppt2VoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_2_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt2Voltage(Integer value) {
		this.getMppt2VoltageChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_2_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt2Voltage(int value) {
		this.getMppt2VoltageChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_2_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt2CurrentChannel() {
		return this.channel(ChannelId.MPPT_2_CURRENT);
	}

	/**
	 * Gets the MPPT 2 Current in [mA]. See {@link ChannelId#MPPT_2_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt2Current() {
		return this.getMppt2CurrentChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_2_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt2Current(Integer value) {
		this.getMppt2CurrentChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_2_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt2Current(int value) {
		this.getMppt2CurrentChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_3_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt3VoltageChannel() {
		return this.channel(ChannelId.MPPT_3_VOLTAGE);
	}

	/**
	 * Gets the MPPT 3 Voltage in [mV]. See {@link ChannelId#MPPT_3_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt3Voltage() {
		return this.getMppt3VoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_3_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt3Voltage(Integer value) {
		this.getMppt3VoltageChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_3_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt3Voltage(int value) {
		this.getMppt3VoltageChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_3_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt3CurrentChannel() {
		return this.channel(ChannelId.MPPT_3_CURRENT);
	}

	/**
	 * Gets the MPPT 3 Current in [mA]. See {@link ChannelId#MPPT_3_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt3Current() {
		return this.getMppt3CurrentChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_3_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt3Current(Integer value) {
		this.getMppt3CurrentChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_3_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt3Current(int value) {
		this.getMppt3CurrentChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_4_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt4VoltageChannel() {
		return this.channel(ChannelId.MPPT_4_VOLTAGE);
	}

	/**
	 * Gets the MPPT 4 Voltage in [mV]. See {@link ChannelId#MPPT_4_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt4Voltage() {
		return this.getMppt4VoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_4_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt4Voltage(Integer value) {
		this.getMppt4VoltageChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_4_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt4Voltage(int value) {
		this.getMppt4VoltageChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_4_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt4CurrentChannel() {
		return this.channel(ChannelId.MPPT_4_CURRENT);
	}

	/**
	 * Gets the MPPT 4 Current in [mA]. See {@link ChannelId#MPPT_4_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt4Current() {
		return this.getMppt4CurrentChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_4_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt4Current(Integer value) {
		this.getMppt4CurrentChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_4_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt4Current(int value) {
		this.getMppt4CurrentChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_5_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt5VoltageChannel() {
		return this.channel(ChannelId.MPPT_5_VOLTAGE);
	}

	/**
	 * Gets the MPPT 5 Voltage in [mV]. See {@link ChannelId#MPPT_5_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt5Voltage() {
		return this.getMppt5VoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_5_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt5Voltage(Integer value) {
		this.getMppt5VoltageChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_5_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt5Voltage(int value) {
		this.getMppt5VoltageChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_5_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt5CurrentChannel() {
		return this.channel(ChannelId.MPPT_5_CURRENT);
	}

	/**
	 * Gets the MPPT 5 Current in [mA]. See {@link ChannelId#MPPT_5_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt5Current() {
		return this.getMppt5CurrentChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_5_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt5Current(Integer value) {
		this.getMppt5CurrentChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_5_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt5Current(int value) {
		this.getMppt5CurrentChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_6_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt6VoltageChannel() {
		return this.channel(ChannelId.MPPT_6_VOLTAGE);
	}

	/**
	 * Gets the MPPT 6 Voltage in [mV]. See {@link ChannelId#MPPT_6_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt6Voltage() {
		return this.getMppt6VoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_6_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt6Voltage(Integer value) {
		this.getMppt6VoltageChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_6_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt6Voltage(int value) {
		this.getMppt6VoltageChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_6_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt6CurrentChannel() {
		return this.channel(ChannelId.MPPT_6_CURRENT);
	}

	/**
	 * Gets the MPPT 6 Current in [mA]. See {@link ChannelId#MPPT_6_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt6Current() {
		return this.getMppt6CurrentChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_6_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt6Current(Integer value) {
		this.getMppt6CurrentChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_6_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt6Current(int value) {
		this.getMppt6CurrentChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_7_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt7VoltageChannel() {
		return this.channel(ChannelId.MPPT_7_VOLTAGE);
	}

	/**
	 * Gets the MPPT 7 Voltage in [mV]. See {@link ChannelId#MPPT_7_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt7Voltage() {
		return this.getMppt7VoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_7_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt7Voltage(Integer value) {
		this.getMppt7VoltageChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_7_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt7Voltage(int value) {
		this.getMppt7VoltageChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_7_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt7CurrentChannel() {
		return this.channel(ChannelId.MPPT_7_CURRENT);
	}

	/**
	 * Gets the MPPT 7 Current in [mA]. See {@link ChannelId#MPPT_7_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt7Current() {
		return this.getMppt7CurrentChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_7_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt7Current(Integer value) {
		this.getMppt7CurrentChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_7_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt7Current(int value) {
		this.getMppt7CurrentChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_8_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt8VoltageChannel() {
		return this.channel(ChannelId.MPPT_8_VOLTAGE);
	}

	/**
	 * Gets the MPPT 8 Voltage in [mV]. See {@link ChannelId#MPPT_8_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt8Voltage() {
		return this.getMppt8VoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_8_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt8Voltage(Integer value) {
		this.getMppt8VoltageChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_8_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt8Voltage(int value) {
		this.getMppt8VoltageChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_8_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt8CurrentChannel() {
		return this.channel(ChannelId.MPPT_8_CURRENT);
	}

	/**
	 * Gets the MPPT 8 Current in [mA]. See {@link ChannelId#MPPT_8_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt8Current() {
		return this.getMppt8CurrentChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_8_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt8Current(Integer value) {
		this.getMppt8CurrentChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_8_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt8Current(int value) {
		this.getMppt8CurrentChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_9_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt9VoltageChannel() {
		return this.channel(ChannelId.MPPT_9_VOLTAGE);
	}

	/**
	 * Gets the MPPT 9 Voltage in [mV]. See {@link ChannelId#MPPT_9_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt9Voltage() {
		return this.getMppt9VoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_9_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt9Voltage(Integer value) {
		this.getMppt9VoltageChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_9_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt9Voltage(int value) {
		this.getMppt9VoltageChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_9_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt9CurrentChannel() {
		return this.channel(ChannelId.MPPT_9_CURRENT);
	}

	/**
	 * Gets the MPPT 9 Current in [mA]. See {@link ChannelId#MPPT_9_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt9Current() {
		return this.getMppt9CurrentChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_9_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt9Current(Integer value) {
		this.getMppt9CurrentChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_9_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt9Current(int value) {
		this.getMppt9CurrentChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_10_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt10VoltageChannel() {
		return this.channel(ChannelId.MPPT_10_VOLTAGE);
	}

	/**
	 * Gets the MPPT 10 Voltage in [mV]. See {@link ChannelId#MPPT_10_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt10Voltage() {
		return this.getMppt10VoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_10_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt10Voltage(Integer value) {
		this.getMppt10VoltageChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_10_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt10Voltage(int value) {
		this.getMppt10VoltageChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_10_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt10CurrentChannel() {
		return this.channel(ChannelId.MPPT_10_CURRENT);
	}

	/**
	 * Gets the MPPT 10 Current in [mA]. See {@link ChannelId#MPPT_10_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt10Current() {
		return this.getMppt10CurrentChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_10_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt10Current(Integer value) {
		this.getMppt10CurrentChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_10_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt10Current(int value) {
		this.getMppt10CurrentChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_11_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt11VoltageChannel() {
		return this.channel(ChannelId.MPPT_11_VOLTAGE);
	}

	/**
	 * Gets the MPPT 11 Voltage in [mV]. See {@link ChannelId#MPPT_11_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt11Voltage() {
		return this.getMppt11VoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_11_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt11Voltage(Integer value) {
		this.getMppt11VoltageChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_11_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt11Voltage(int value) {
		this.getMppt11VoltageChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_11_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt11CurrentChannel() {
		return this.channel(ChannelId.MPPT_11_CURRENT);
	}

	/**
	 * Gets the MPPT 11 Current in [mA]. See {@link ChannelId#MPPT_11_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt11Current() {
		return this.getMppt11CurrentChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_11_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt11Current(Integer value) {
		this.getMppt11CurrentChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_11_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt11Current(int value) {
		this.getMppt11CurrentChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_12_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt12VoltageChannel() {
		return this.channel(ChannelId.MPPT_12_VOLTAGE);
	}

	/**
	 * Gets the MPPT 12 Voltage in [mV]. See {@link ChannelId#MPPT_12_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt12Voltage() {
		return this.getMppt12VoltageChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_12_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt12Voltage(Integer value) {
		this.getMppt12VoltageChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_12_VOLTAGE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt12Voltage(int value) {
		this.getMppt12VoltageChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MPPT_12_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMppt12CurrentChannel() {
		return this.channel(ChannelId.MPPT_12_CURRENT);
	}

	/**
	 * Gets the MPPT 12 Current in [mA]. See {@link ChannelId#MPPT_12_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMppt12Current() {
		return this.getMppt12CurrentChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_12_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt12Current(Integer value) {
		this.getMppt12CurrentChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MPPT_12_CURRENT}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMppt12Current(int value) {
		this.getMppt12CurrentChannel().setNextValue(value);
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
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L1_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL1L2(int value) {
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
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L2_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL2L3(int value) {
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
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L3_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL3L1(int value) {
		this.getVoltageL3L1Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#POWER_LIMITATION_SWITCH}.
	 *
	 * @return the Channel
	 */
	public default EnumWriteChannel getPowerLimitationSwitchChannel() {
		return this.channel(ChannelId.POWER_LIMITATION_SWITCH);
	}

	/**
	 * Gets the Power Limitation Switch. See
	 * {@link ChannelId#POWER_LIMITATION_SWITCH}.
	 *
	 * @return the {@link PowerLimitationSwitch}
	 */
	public default PowerLimitationSwitch getPowerLimitationSwitch() {
		return this.getPowerLimitationSwitchChannel().value().asEnum();
	}

	/**
	 * Sets the Power Limitation Switch. See
	 * {@link ChannelId#POWER_LIMITATION_SWITCH}.
	 *
	 * @param value the {@link PowerLimitationSwitch}
	 * @throws OpenemsNamedException on error
	 */
	public default void setPowerLimitationSwitch(PowerLimitationSwitch value) throws OpenemsNamedException {
		this.getPowerLimitationSwitchChannel().setNextWriteValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#POWER_LIMITATION_SETTING}.
	 *
	 * @return the Channel
	 */
	public default IntegerWriteChannel getPowerLimitationSettingChannel() {
		return this.channel(ChannelId.POWER_LIMITATION_SETTING);
	}

	/**
	 * Gets the Power Limitation Setting in [0.1 %]. See
	 * {@link ChannelId#POWER_LIMITATION_SETTING}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getPowerLimitationSetting() {
		return this.getPowerLimitationSettingChannel().value();
	}

	/**
	 * Sets the Power Limitation Setting in [0.1 %]. See
	 * {@link ChannelId#POWER_LIMITATION_SETTING}.
	 *
	 * @param value the Integer value
	 * @throws OpenemsNamedException on error
	 */
	public default void setPowerLimitationSetting(Integer value) throws OpenemsNamedException {
		this.getPowerLimitationSettingChannel().setNextWriteValue(value);
	}

	/**
	 * Sets the Power Limitation Setting in [0.1 %]. See
	 * {@link ChannelId#POWER_LIMITATION_SETTING}.
	 *
	 * @param value the int value
	 * @throws OpenemsNamedException on error
	 */
	public default void setPowerLimitationSetting(int value) throws OpenemsNamedException {
		this.getPowerLimitationSettingChannel().setNextWriteValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#PV_LIMIT_FAILED}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getPvLimitFailedChannel() {
		return this.channel(ChannelId.PV_LIMIT_FAILED);
	}

	/**
	 * Gets the PV Limit Failed State. See {@link ChannelId#PV_LIMIT_FAILED}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getPvLimitFailed() {
		return this.getPvLimitFailedChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#PV_LIMIT_FAILED}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPvLimitFailed(boolean value) {
		this.getPvLimitFailedChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#READ_ONLY_MODE_PV_LIMIT_FAILED}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getReadOnlyModePvLimitFailedChannel() {
		return this.channel(ChannelId.READ_ONLY_MODE_PV_LIMIT_FAILED);
	}

	/**
	 * Gets the Read-Only Mode PV Limit Failed State. See
	 * {@link ChannelId#READ_ONLY_MODE_PV_LIMIT_FAILED}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getReadOnlyModePvLimitFailed() {
		return this.getReadOnlyModePvLimitFailedChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#READ_ONLY_MODE_PV_LIMIT_FAILED} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReadOnlyModePvLimitFailed(boolean value) {
		this.getReadOnlyModePvLimitFailedChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#WRONG_PHASE_WIRING_CONFIGURED}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getWrongPhaseWiringConfiguredChannel() {
		return this.channel(ChannelId.WRONG_PHASE_WIRING_CONFIGURED);
	}

	/**
	 * Gets the Wrong Phase Wiring Configured State. See
	 * {@link ChannelId#WRONG_PHASE_WIRING_CONFIGURED}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getWrongPhaseWiringConfigured() {
		return this.getWrongPhaseWiringConfiguredChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#WRONG_PHASE_WIRING_CONFIGURED} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setWrongPhaseWiringConfigured(boolean value) {
		this.getWrongPhaseWiringConfiguredChannel().setNextValue(value);
	}
}
