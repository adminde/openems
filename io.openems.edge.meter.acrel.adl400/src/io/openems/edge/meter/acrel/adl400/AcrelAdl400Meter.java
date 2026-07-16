package io.openems.edge.meter.acrel.adl400;

import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
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
}
