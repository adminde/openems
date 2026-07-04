package io.openems.edge.heat.test;

import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.test.TestUtils;
import io.openems.edge.heat.api.ManagedSymmetricHeating;
import io.openems.edge.heat.api.SymmetricHeating;
import io.openems.edge.heat.pump.api.HeatPump;
import io.openems.edge.heat.pump.api.ManagedHeatPump;

public class DummyManagedHeatPump extends AbstractOpenemsComponent
		implements ManagedHeatPump, ManagedSymmetricHeating, HeatPump, SymmetricHeating, StartStoppable {

	public DummyManagedHeatPump(String id) {
		super(//
				OpenemsComponent.ChannelId.values(), //
				SymmetricHeating.ChannelId.values(), //
				ManagedSymmetricHeating.ChannelId.values(), //
				StartStoppable.ChannelId.values(), //
				HeatPump.ChannelId.values() //
		);
		super.activate(null, id, "", true);
	}

	@Override
	public void setStartStop(StartStop value) {
		this._setStartStop(value);
	}

	/**
	 * Set {@link HeatPump.ChannelId#COP}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedHeatPump withCop(float value) {
		TestUtils.withValue(this, HeatPump.ChannelId.COP, value);
		return this;
	}

	/**
	 * Set {@link ManagedSymmetricHeating.ChannelId#MAX_TARGET_ACTIVE_POWER}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedHeatPump withMaxActivePower(int value) {
		TestUtils.withValue(this, ManagedSymmetricHeating.ChannelId.MAX_TARGET_ACTIVE_POWER, value);
		return this;
	}

	/**
	 * Set {@link ManagedSymmetricHeating.ChannelId#MIN_TARGET_ACTIVE_POWER}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedHeatPump withMinActivePower(int value) {
		TestUtils.withValue(this, ManagedSymmetricHeating.ChannelId.MIN_TARGET_ACTIVE_POWER, value);
		return this;
	}

	/**
	 * Set {@link SymmetricHeating.ChannelId#ACTIVE_POWER}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedHeatPump withActivePower(int value) {
		TestUtils.withValue(this, SymmetricHeating.ChannelId.ACTIVE_POWER, value);
		return this;
	}

	/**
	 * Set {@link SymmetricHeating.ChannelId#THERMAL_POWER}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedHeatPump withThermalPower(int value) {
		TestUtils.withValue(this, SymmetricHeating.ChannelId.THERMAL_POWER, value);
		return this;
	}

	/**
	 * Set {@link SymmetricHeating.ChannelId#THERMAL_ENERGY}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedHeatPump withThermalEnergy(long value) {
		TestUtils.withValue(this, SymmetricHeating.ChannelId.THERMAL_ENERGY, value);
		return this;
	}

	/**
	 * Set {@link SymmetricHeating.ChannelId#ACTIVE_CONSUMPTION_ENERGY}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyManagedHeatPump withActiveConsumptionEnergy(long value) {
		TestUtils.withValue(this, SymmetricHeating.ChannelId.ACTIVE_CONSUMPTION_ENERGY, value);
		return this;
	}

}
