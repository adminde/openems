package io.openems.edge.simulator.heatpump.reacting;

import org.junit.Test;

import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.TestUtils;
import io.openems.edge.heat.api.SymmetricHeating;
import io.openems.edge.heat.pump.api.HeatPump;
import io.openems.edge.heat.test.DummyThermalEss;
import io.openems.edge.heat.tess.api.ThermalEss;

public class SimulatorHeatPumpReactingImplTest {

	private static final String HP_ID = "heatpump0";

	@Test
	public void testTurnsOnWhenBelowMinTemperature() throws Exception {
		var tess = new DummyThermalEss("tess0");
		TestUtils.withValue(tess, ThermalEss.ChannelId.MIN_TARGET_TEMPERATURE, 400);
		TestUtils.withValue(tess, ThermalEss.ChannelId.MAX_TARGET_TEMPERATURE, 700);
		TestUtils.withValue(tess, ThermalEss.ChannelId.TEMPERATURE, 350); // below min

		var sut = new SimulatorHeatPumpReactingImpl();
		sut.setThermalStorage(tess);

		new ComponentTest(sut) //
				.activate(MyConfig.create() //
						.setId(HP_ID) //
						.setThermalPower(4000) //
						.setCop(3.5f) //
						.build()) //
				.next(new TestCase() //
						.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, 4000) //
						.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, Math.round(4000 / 3.5f)) //
						.output(HP_ID, HeatPump.ChannelId.COP, 3.5f));
	}

	@Test
	public void testTurnsOffWhenAboveMaxTemperature() throws Exception {
		var tess = new DummyThermalEss("tess0");
		TestUtils.withValue(tess, ThermalEss.ChannelId.MIN_TARGET_TEMPERATURE, 400);
		TestUtils.withValue(tess, ThermalEss.ChannelId.MAX_TARGET_TEMPERATURE, 700);
		TestUtils.withValue(tess, ThermalEss.ChannelId.TEMPERATURE, 750); // above max

		var sut = new SimulatorHeatPumpReactingImpl();
		sut.setThermalStorage(tess);

		new ComponentTest(sut) //
				.activate(MyConfig.create() //
						.setId(HP_ID) //
						.setThermalPower(4000) //
						.setCop(3.5f) //
						.build()) //
				.next(new TestCase() //
						.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, 0) //
						.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, 0));
	}
}
