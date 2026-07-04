package io.openems.edge.heat.test;

import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.test.TestUtils;
import io.openems.edge.heat.tess.api.ThermalEss;

public class DummyThermalEss extends AbstractOpenemsComponent implements ThermalEss {

	public DummyThermalEss(String id) {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ThermalEss.ChannelId.values() //
		);
		super.activate(null, id, "", true);
	}

	/**
	 * Set {@link ThermalEss.ChannelId#SOC}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyThermalEss withSoc(int value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.SOC, value);
		return this;
	}

	/**
	 * Set {@link ThermalEss.ChannelId#CAPACITY}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyThermalEss withCapacity(int value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.CAPACITY, value);
		return this;
	}

	/**
	 * Set {@link ThermalEss.ChannelId#TEMPERATURE}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyThermalEss withTemperature(int value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.TEMPERATURE, value);
		return this;
	}

	/**
	 * Set {@link ThermalEss.ChannelId#MAX_TEMPERATURE}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyThermalEss withMaxTemperature(int value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.MAX_TEMPERATURE, value);
		return this;
	}

	/**
	 * Set {@link ThermalEss.ChannelId#MIN_TARGET_TEMPERATURE}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyThermalEss withMinTargetTemperature(int value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.MIN_TARGET_TEMPERATURE, value);
		return this;
	}

	/**
	 * Set {@link ThermalEss.ChannelId#MAX_TARGET_TEMPERATURE}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyThermalEss withMaxTargetTemperature(int value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.MAX_TARGET_TEMPERATURE, value);
		return this;
	}

	/**
	 * Set {@link ThermalEss.ChannelId#THERMAL_POWER}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyThermalEss withThermalPower(int value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.THERMAL_POWER, value);
		return this;
	}

	/**
	 * Set {@link ThermalEss.ChannelId#THERMAL_CHARGE_ENERGY}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyThermalEss withThermalChargeEnergy(long value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.THERMAL_CHARGE_ENERGY, value);
		return this;
	}

	/**
	 * Set {@link ThermalEss.ChannelId#THERMAL_DISCHARGE_ENERGY}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyThermalEss withThermalDischargeEnergy(long value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.THERMAL_DISCHARGE_ENERGY, value);
		return this;
	}

}
