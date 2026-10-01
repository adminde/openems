package io.openems.edge.heat.test;

import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.test.TestUtils;
import io.openems.edge.heat.tess.api.ManagedThermalEss;
import io.openems.edge.heat.tess.api.ThermalEss;

public class DummyManagedThermalEss extends AbstractOpenemsComponent implements ManagedThermalEss {

	private String[] heatingIds = new String[0];

	public DummyManagedThermalEss(String id) {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ThermalEss.ChannelId.values(), //
				ManagedThermalEss.ChannelId.values() //
		);
		super.activate(null, id, "", true);
	}

	/**
	 * Set the Component-IDs returned by {@link #getHeatingIds()}.
	 *
	 * @param heatingIds the Heating Component-IDs
	 * @return myself
	 */
	public DummyManagedThermalEss withHeatingIds(String... heatingIds) {
		this.heatingIds = heatingIds;
		return this;
	}

	@Override
	public String[] getHeatingIds() {
		return this.heatingIds;
	}

	/**
	 * Set {@link ThermalEss.ChannelId#SOC}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedThermalEss withSoc(int value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.SOC, value);
		return this;
	}

	/**
	 * Set {@link ThermalEss.ChannelId#CAPACITY}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedThermalEss withCapacity(int value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.CAPACITY, value);
		return this;
	}

	/**
	 * Set {@link ThermalEss.ChannelId#TEMPERATURE}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedThermalEss withTemperature(int value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.TEMPERATURE, value);
		return this;
	}

	/**
	 * Set {@link ThermalEss.ChannelId#MAX_TEMPERATURE}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedThermalEss withMaxTemperature(int value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.MAX_TEMPERATURE, value);
		return this;
	}

	/**
	 * Set {@link ThermalEss.ChannelId#MIN_TEMPERATURE}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedThermalEss withMinTemperature(int value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.MIN_TEMPERATURE, value);
		return this;
	}

	/**
	 * Set {@link ThermalEss.ChannelId#TARGET_TEMPERATURE}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedThermalEss withTargetTemperature(int value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.TARGET_TEMPERATURE, value);
		return this;
	}

	/**
	 * Set {@link ThermalEss.ChannelId#THERMAL_POWER}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedThermalEss withThermalPower(int value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.THERMAL_POWER, value);
		return this;
	}

	/**
	 * Set {@link ThermalEss.ChannelId#THERMAL_CHARGE_POWER}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedThermalEss withThermalChargePower(int value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.THERMAL_CHARGE_POWER, value);
		return this;
	}

	/**
	 * Set {@link ThermalEss.ChannelId#THERMAL_DISCHARGE_POWER}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedThermalEss withThermalDischargePower(int value) {
		TestUtils.withValue(this, ThermalEss.ChannelId.THERMAL_DISCHARGE_POWER, value);
		return this;
	}

	/**
	 * Set {@link ManagedThermalEss.ChannelId#MAX_TARGET_THERMAL_POWER}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedThermalEss withMaxTargetThermalPower(int value) {
		TestUtils.withValue(this, ManagedThermalEss.ChannelId.MAX_TARGET_THERMAL_POWER, value);
		return this;
	}

	/**
	 * Set {@link ManagedThermalEss.ChannelId#MIN_TARGET_THERMAL_POWER}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedThermalEss withMinTargetThermalPower(int value) {
		TestUtils.withValue(this, ManagedThermalEss.ChannelId.MIN_TARGET_THERMAL_POWER, value);
		return this;
	}

}
