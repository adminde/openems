package io.openems.edge.heat.test;

import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.test.TestUtils;
import io.openems.edge.heat.api.ManagedSymmetricHeating;
import io.openems.edge.heat.api.SymmetricHeating;

public class DummyManagedHeating extends AbstractOpenemsComponent
		implements ManagedSymmetricHeating, SymmetricHeating {

	public DummyManagedHeating(String id) {
		super(//
				OpenemsComponent.ChannelId.values(), //
				SymmetricHeating.ChannelId.values(), //
				ManagedSymmetricHeating.ChannelId.values() //
		);
		super.activate(null, id, "", true);
	}

	/**
	 * Set {@link ManagedSymmetricHeating.ChannelId#MAX_TARGET_ACTIVE_POWER}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedHeating withMaxTargetActivePower(int value) {
		TestUtils.withValue(this, ManagedSymmetricHeating.ChannelId.MAX_TARGET_ACTIVE_POWER, value);
		return this;
	}

	/**
	 * Set {@link ManagedSymmetricHeating.ChannelId#MIN_TARGET_ACTIVE_POWER}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedHeating withMinTargetActivePower(int value) {
		TestUtils.withValue(this, ManagedSymmetricHeating.ChannelId.MIN_TARGET_ACTIVE_POWER, value);
		return this;
	}

	/**
	 * Set {@link SymmetricHeating.ChannelId#THERMAL_EFFICIENCY}.
	 *
	 * @param value the value in percent (e.g. 300 for a COP of 3.0)
	 * @return myself
	 */
	public DummyManagedHeating withThermalEfficiency(float value) {
		TestUtils.withValue(this, SymmetricHeating.ChannelId.THERMAL_EFFICIENCY, value);
		return this;
	}

	/**
	 * Set {@link SymmetricHeating.ChannelId#THERMAL_POWER}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedHeating withThermalPower(int value) {
		TestUtils.withValue(this, SymmetricHeating.ChannelId.THERMAL_POWER, value);
		return this;
	}

}
