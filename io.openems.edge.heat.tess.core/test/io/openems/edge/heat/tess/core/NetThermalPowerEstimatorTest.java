package io.openems.edge.heat.tess.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;

public class NetThermalPowerEstimatorTest {

	private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

	@Test
	public void testNoEstimateBeforeWindowIsCovered() {
		var sut = new NetThermalPowerEstimator(300, Duration.ofMinutes(5));

		assertNull(sut.update(START, 50));
		assertNull(sut.update(START.plusSeconds(299), 51));
	}

	@Test
	public void testPowerFromTemperatureChange() {
		var sut = new NetThermalPowerEstimator(300, Duration.ofMinutes(5));
		sut.update(START, 50);

		// 300 kg * 4181.3 J/(kg K) * 1 K / 300 s
		assertEquals(Integer.valueOf(4181), sut.update(START.plusSeconds(300), 51));
		assertEquals(Integer.valueOf(-4181), sut.update(START.plusSeconds(600), 50));
	}

	@Test
	public void testSlidingWindowDropsOldSamples() {
		var sut = new NetThermalPowerEstimator(300, Duration.ofMinutes(5));
		for (var second = 0; second <= 600; second += 60) {
			// Heating by 1 K in the first five minutes, constant afterwards
			sut.update(START.plusSeconds(second), 50 + Math.min(second, 300) / 300.0);
		}

		assertEquals(Integer.valueOf(0), sut.update(START.plusSeconds(660), 51));
	}

	@Test
	public void testRebaseHidesCorrectionStep() {
		var sut = new NetThermalPowerEstimator(300, Duration.ofMinutes(5));
		sut.update(START, 50);
		sut.update(START.plusSeconds(240), 50);

		// Additional temperatures correct the estimated mean by 5 K
		sut.rebase(55);

		assertEquals(Integer.valueOf(0), sut.update(START.plusSeconds(300), 55));
	}
}
