package io.openems.edge.heat.tess.api.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import io.openems.edge.heat.test.DummyThermalEss;

public class CalculateChargeDischargePowerTest {

	private static Integer chargePowerOf(DummyThermalEss tess) {
		return tess.getThermalChargePowerChannel().getNextValue().get();
	}

	private static Integer dischargePowerOf(DummyThermalEss tess) {
		return tess.getThermalDischargePowerChannel().getNextValue().get();
	}

	@Test
	public void testSplitWithGeneratorPower() {
		var tess = new DummyThermalEss("tess0");
		var sut = new CalculateChargeDischargePower(tess);

		// Charging 4000 W while 1000 W are extracted: net = 3000 W
		sut.update(3000, 4000);
		assertEquals(Integer.valueOf(4000), chargePowerOf(tess));
		assertEquals(Integer.valueOf(1000), dischargePowerOf(tess));

		// Pure discharge
		sut.update(-1500, 0);
		assertEquals(Integer.valueOf(0), chargePowerOf(tess));
		assertEquals(Integer.valueOf(1500), dischargePowerOf(tess));
	}

	@Test
	public void testSplitFromNetOnly() {
		var tess = new DummyThermalEss("tess0");
		var sut = new CalculateChargeDischargePower(tess);

		sut.update(2000, null);
		assertEquals(Integer.valueOf(2000), chargePowerOf(tess));
		assertEquals(Integer.valueOf(0), dischargePowerOf(tess));

		sut.update(-800, null);
		assertEquals(Integer.valueOf(0), chargePowerOf(tess));
		assertEquals(Integer.valueOf(800), dischargePowerOf(tess));
	}

	@Test
	public void testUnknownValues() {
		var tess = new DummyThermalEss("tess0");
		var sut = new CalculateChargeDischargePower(tess);

		sut.update(null, 4000);
		assertEquals(Integer.valueOf(4000), chargePowerOf(tess));
		assertNull(dischargePowerOf(tess));

		sut.update(null, null);
		assertNull(chargePowerOf(tess));
		assertNull(dischargePowerOf(tess));
	}
}
