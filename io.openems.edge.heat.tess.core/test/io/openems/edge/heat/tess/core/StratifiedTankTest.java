package io.openems.edge.heat.tess.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StratifiedTankTest {

	private static final double ENERGY_DELTA = 1e-6;
	private static final double TOP = 1.0;
	private static final double BOTTOM = 0.0;

	@Test
	public void testDirectChargingHeatsTopFirst() {
		var sut = new StratifiedTank(300, 10, 30);
		final var energy = sut.getEnergy(0);

		// Supply enters at the top, return leaves at the bottom
		sut.flow(6000, 50, 0.95, 0.05, 600);

		assertTrue(sut.getTemperatureAt(TOP) > 45);
		assertEquals(30, sut.getTemperatureAt(BOTTOM), 1e-6);
		assertEquals(energy + 6000 * 600 / 3600.0, sut.getEnergy(0), ENERGY_DELTA);
	}

	@Test
	public void testDrawCoolsBottomFirst() {
		var sut = new StratifiedTank(300, 10, 50);
		final var energy = sut.getEnergy(0);

		// Consumers draw from the top; their return enters at the bottom
		sut.flow(3000, 25, BOTTOM, TOP, 600);

		assertEquals(50, sut.getTemperatureAt(TOP), 1e-6);
		assertTrue(sut.getTemperatureAt(BOTTOM) < 40);
		assertEquals(energy - 3000 * 600 / 3600.0, sut.getEnergy(0), ENERGY_DELTA);
	}

	@Test
	public void testHeatExchangerMixesUpwards() {
		var sut = new StratifiedTank(300, 10, 30);
		final var energy = sut.getEnergy(0);

		// A coil in the lower part heats the water, which rises by buoyancy
		sut.heat(3000, 0.05, 0.3, 1200);

		assertTrue(sut.getTemperatureAt(TOP) > 30);
		assertTrue(sut.getTemperatureAt(TOP) >= sut.getTemperatureAt(BOTTOM) - 1e-6);
		assertEquals(energy + 3000 * 1200 / 3600.0, sut.getEnergy(0), ENERGY_DELTA);
	}

	@Test
	public void testHeatingElementKeepsStratification() {
		var sut = new StratifiedTank(300, 10, 30);

		// A heating element in the upper part heats only the water above it
		sut.heat(2000, 0.8, 0.9, 600);

		assertTrue(sut.getTemperatureAt(TOP) > 30);
		assertEquals(30, sut.getTemperatureAt(BOTTOM), 1e-6);
	}

	@Test
	public void testLongTimeStepIsStable() {
		var sut = new StratifiedTank(300, 10, 30);

		sut.flow(6000, 55, 0.95, 0.05, 24 * 3600);

		for (var height = 0.05; height < 1; height += 0.1) {
			var temperature = sut.getTemperatureAt(height);
			assertTrue(temperature >= 30 - 1e-6 && temperature <= 55 + 1e-6);
		}
		assertTrue(sut.getMeanTemperature() > 54);
	}

	@Test
	public void testNoFlowWithoutTemperatureDifference() {
		var sut = new StratifiedTank(300, 10, 50);
		final var energy = sut.getEnergy(0);

		sut.flow(4000, 50, 0.95, 0.05, 600);

		assertEquals(energy, sut.getEnergy(0), ENERGY_DELTA);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRejectsInvalidVolume() {
		new StratifiedTank(0, 10, 30);
	}
}
