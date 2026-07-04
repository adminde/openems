package io.openems.edge.heat.test;

import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.test.TestUtils;
import io.openems.edge.heat.api.SymmetricHeating;
import io.openems.edge.heat.pump.api.HeatPump;

public class DummyHeatPump extends AbstractOpenemsComponent
		implements HeatPump, SymmetricHeating, StartStoppable {

	public DummyHeatPump(String id) {
		super(//
				OpenemsComponent.ChannelId.values(), //
				SymmetricHeating.ChannelId.values(), //
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
	public DummyHeatPump withCop(float value) {
		TestUtils.withValue(this, HeatPump.ChannelId.COP, value);
		return this;
	}

	/**
	 * Set {@link SymmetricHeating.ChannelId#ACTIVE_POWER}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyHeatPump withActivePower(int value) {
		TestUtils.withValue(this, SymmetricHeating.ChannelId.ACTIVE_POWER, value);
		return this;
	}

	/**
	 * Set {@link SymmetricHeating.ChannelId#THERMAL_POWER}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyHeatPump withThermalPower(int value) {
		TestUtils.withValue(this, SymmetricHeating.ChannelId.THERMAL_POWER, value);
		return this;
	}

	/**
	 * Set {@link HeatPump.ChannelId#MAX_TEMPERATURE}.
	 *
	 * @param value the value
	 * @return myself
	 */
	public DummyHeatPump withMaxTemperature(int value) {
		TestUtils.withValue(this, HeatPump.ChannelId.MAX_TEMPERATURE, value);
		return this;
	}

}
