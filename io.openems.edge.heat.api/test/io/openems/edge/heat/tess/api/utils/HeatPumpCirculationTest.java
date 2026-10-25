package io.openems.edge.heat.tess.api.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.Test;

import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.test.TestUtils;
import io.openems.edge.heat.pump.api.HeatPump;
import io.openems.edge.heat.test.DummyManagedHeatPump;
import io.openems.edge.heat.test.DummyManagedHeating;
import io.openems.edge.heat.test.DummyThermalEss;
import io.openems.edge.heat.tess.api.ThermalEss;

public class HeatPumpCirculationTest {

	private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

	/**
	 * A heat pump serving a settable storage.
	 */
	private static class ServingHeatPump extends DummyManagedHeatPump {

		private ThermalEss servedStorage = null;

		ServingHeatPump(String id) {
			super(id);
		}

		@Override
		public ThermalEss getThermalStorage() {
			return this.servedStorage;
		}
	}

	private static ServingHeatPump heatPump(ThermalEss servedStorage, StartStop startStop) {
		var hp = new ServingHeatPump("hp0");
		hp.servedStorage = servedStorage;
		TestUtils.withValue(hp, StartStoppable.ChannelId.START_STOP, startStop);
		TestUtils.withValue(hp, HeatPump.ChannelId.SUPPLY_TEMPERATURE, 500);
		TestUtils.withValue(hp, HeatPump.ChannelId.RETURN_TEMPERATURE, 300);
		return hp;
	}

	@Test
	public void testDeliversTo() {
		var buffer = new DummyThermalEss("tess0");
		var dhw = new DummyThermalEss("tess1");

		assertTrue(HeatPumpCirculation.deliversTo(heatPump(buffer, StartStop.START), buffer));
		assertFalse(HeatPumpCirculation.deliversTo(heatPump(dhw, StartStop.START), buffer));
		assertTrue(HeatPumpCirculation.deliversTo(new DummyManagedHeating("heater0"), buffer));
		// A heat pump not distinguishing its storages serves every bound storage
		assertTrue(HeatPumpCirculation.deliversTo(heatPump(null, StartStop.START), buffer));
	}

	@Test
	public void testTemperaturesAfterSettlingTime() {
		var buffer = new DummyThermalEss("tess0");
		var hp = heatPump(buffer, StartStop.START);
		var sut = new HeatPumpCirculation(Duration.ofMinutes(2));

		sut.update(buffer, List.of(hp), START);
		assertNull(sut.getSupplyTemperature());
		assertFalse(sut.isSettled());

		sut.update(buffer, List.of(hp), START.plusSeconds(60));
		assertNull(sut.getReturnTemperature());

		sut.update(buffer, List.of(hp), START.plusSeconds(120));
		assertTrue(sut.isSettled());
		assertEquals(Integer.valueOf(500), sut.getSupplyTemperature());
		assertEquals(Integer.valueOf(300), sut.getReturnTemperature());
	}

	@Test
	public void testHoldsTemperaturesWithoutCirculation() {
		var buffer = new DummyThermalEss("tess0");
		var hp = heatPump(buffer, StartStop.START);
		var sut = new HeatPumpCirculation(Duration.ZERO);
		sut.update(buffer, List.of(hp), START);

		// The heat pump stops; the pipe temperatures no longer describe the storage
		TestUtils.withValue(hp, StartStoppable.ChannelId.START_STOP, StartStop.STOP);
		TestUtils.withValue(hp, HeatPump.ChannelId.RETURN_TEMPERATURE, 200);
		sut.update(buffer, List.of(hp), START.plusSeconds(60));

		assertFalse(sut.isSettled());
		assertEquals(Integer.valueOf(300), sut.getReturnTemperature());
	}

	@Test
	public void testIgnoresHeatPumpServingAnotherStorage() {
		var buffer = new DummyThermalEss("tess0");
		var dhw = new DummyThermalEss("tess1");
		var sut = new HeatPumpCirculation(Duration.ZERO);

		sut.update(buffer, List.of(heatPump(dhw, StartStop.START)), START);

		assertNull(sut.getSupplyTemperature());
	}
}
