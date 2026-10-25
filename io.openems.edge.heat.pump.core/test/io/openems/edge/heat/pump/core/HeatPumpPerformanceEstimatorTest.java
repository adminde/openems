package io.openems.edge.heat.pump.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HeatPumpPerformanceEstimatorTest {

	private static final double DELTA = 1e-6;

	@Test
	public void testRatingPointReturnsReferenceCop() {
		var sut = HeatPumpPerformanceEstimator.of(3.5, 35);
		assertEquals(3.5, sut.estimateCop(35, 1.0), DELTA);
	}

	@Test
	public void testStandardRatingPointFactory() {
		var sut = HeatPumpPerformanceEstimator.of(3.5);
		assertEquals(3.5, sut.estimateCop(HeatPumpPerformanceEstimator.STANDARD_RATING_SINK_TEMPERATURE, 1.0), DELTA);
	}

	@Test
	public void testCopDropsWithRisingSinkTemperature() {
		var sut = HeatPumpPerformanceEstimator.of(3.5, 35);
		var atReference = sut.estimateCop(35, 1.0);
		var atHotBuffer = sut.estimateCop(55, 1.0);
		assertTrue("COP must drop at a hotter sink: " + atHotBuffer + " < " + atReference,
				atHotBuffer < atReference);
	}

	@Test
	public void testCopRisesTowardsPartLoadPeak() {
		var sut = HeatPumpPerformanceEstimator.of(3.5, 35);
		var fullLoad = sut.estimateCop(35, 1.0);
		var midLoad = sut.estimateCop(35, 0.5);
		assertTrue("COP must be higher in part load: " + midLoad + " > " + fullLoad, midLoad > fullLoad);
		// Peak factor at part-load ratio 0.5 is 1.08.
		assertEquals(3.5 * 1.08, midLoad, DELTA);
	}

	@Test
	public void testPartLoadFactorIsOneAtFullLoad() {
		var sut = HeatPumpPerformanceEstimator.of(4.0, 35);
		// At full load and reference sink temperature both factors are 1.0.
		assertEquals(4.0, sut.estimateCop(35, 1.0), DELTA);
	}

	@Test
	public void testCopIsClampedToAtLeastOne() {
		var sut = HeatPumpPerformanceEstimator.of(3.5, 35);
		// A very high sink temperature drives the Carnot term down; COP must not fall
		// below 1.0.
		assertTrue(sut.estimateCop(250, 1.0) >= 1.0);
	}
}
