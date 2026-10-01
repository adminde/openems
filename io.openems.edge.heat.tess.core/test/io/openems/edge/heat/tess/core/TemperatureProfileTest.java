package io.openems.edge.heat.tess.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TemperatureProfileTest {

	private static final double DELTA = 1e-9;

	@Test
	public void testEmptyProfile() {
		var sut = new TemperatureProfile();
		assertNull(sut.getMeanTemperature());
		assertNull(sut.getMaxTemperature());
	}

	@Test
	public void testSinglePointDescribesMixedTank() {
		var sut = new TemperatureProfile().add(0.9, 50);
		assertEquals(50, sut.getMeanTemperature(), DELTA);
	}

	@Test
	public void testLinearInterpolationAndConstantContinuation() {
		// 30 °C below 10 %, linear up to 50 °C at 90 %, 50 °C above
		var sut = new TemperatureProfile() //
				.add(0.1, 30) //
				.add(0.9, 50);
		assertEquals(30 * 0.1 + 0.8 * 40 + 50 * 0.1, sut.getMeanTemperature(), DELTA);
		assertEquals(50, sut.getMaxTemperature(), DELTA);
	}

	@Test
	public void testPointAtSameHeightIsReplaced() {
		var sut = new TemperatureProfile() //
				.add(0.9, 48) //
				.add(0.1, 25) //
				.add(0.9, 50);
		assertEquals(25 * 0.1 + 0.8 * 37.5 + 50 * 0.1, sut.getMeanTemperature(), DELTA);
	}

	@Test
	public void testHeightIsClamped() {
		var sut = new TemperatureProfile() //
				.add(-0.5, 20) //
				.add(1.5, 60);
		assertEquals(40, sut.getMeanTemperature(), DELTA);
	}
}
