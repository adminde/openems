package io.openems.edge.heat.tess.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import io.openems.edge.heat.test.DummyThermalEss;

public class CalculateTessSocTest {

	@Test
	public void testEmpty() {
		assertNull(new CalculateTessSoc(SocAveragingMethod.GEOMETRIC).calculate());
		assertNull(new CalculateTessSoc(SocAveragingMethod.ARITHMETIC).calculate());
	}

	@Test
	public void testSingleTess() {
		for (var method : SocAveragingMethod.values()) {
			var calc = new CalculateTessSoc(method);
			var tess = new DummyThermalEss("tess0") //
					.withSoc(75) //
					.withCapacity(10000);
			calc.add(tess);
			assertEquals(Integer.valueOf(75), calc.calculate());
		}
	}

	@Test
	public void testSocZeroDominatesGeometric() {
		var calc = new CalculateTessSoc(SocAveragingMethod.GEOMETRIC);
		calc.add(new DummyThermalEss("tess0").withSoc(100).withCapacity(10000));
		calc.add(new DummyThermalEss("tess1").withSoc(0).withCapacity(5000));
		assertEquals(Integer.valueOf(0), calc.calculate());
	}

	@Test
	public void testSocZeroArithmetic() {
		var calc = new CalculateTessSoc(SocAveragingMethod.ARITHMETIC);
		calc.add(new DummyThermalEss("tess0").withSoc(100).withCapacity(10000));
		calc.add(new DummyThermalEss("tess1").withSoc(0).withCapacity(5000));
		// Weighted arithmetic mean: (10000*100 + 5000*0) / 15000 ≈ 66.7 → 67
		assertEquals(Integer.valueOf(67), calc.calculate());
	}

	@Test
	public void testWeightedGeometricMean() {
		var calc = new CalculateTessSoc(SocAveragingMethod.GEOMETRIC);
		calc.add(new DummyThermalEss("tess0").withSoc(100).withCapacity(10000));
		calc.add(new DummyThermalEss("tess1").withSoc(50).withCapacity(10000));
		// Geometric mean of (100, 50) with equal weights = sqrt(100*50) ≈ 70.7 → 71
		assertEquals(Integer.valueOf(71), calc.calculate());
	}

	@Test
	public void testWeightedArithmeticMean() {
		var calc = new CalculateTessSoc(SocAveragingMethod.ARITHMETIC);
		calc.add(new DummyThermalEss("tess0").withSoc(100).withCapacity(10000));
		calc.add(new DummyThermalEss("tess1").withSoc(50).withCapacity(10000));
		// Arithmetic mean of (100, 50) with equal weights = 75
		assertEquals(Integer.valueOf(75), calc.calculate());
	}

	@Test
	public void testCapacityWeighting() {
		var geometric = new CalculateTessSoc(SocAveragingMethod.GEOMETRIC);
		geometric.add(new DummyThermalEss("tess0").withSoc(100).withCapacity(9000));
		geometric.add(new DummyThermalEss("tess1").withSoc(50).withCapacity(1000));
		// Weighted geometric mean: exp(0.9*ln(100) + 0.1*ln(50)) ≈ 93.3 → 93
		assertEquals(Integer.valueOf(93), geometric.calculate());

		var arithmetic = new CalculateTessSoc(SocAveragingMethod.ARITHMETIC);
		arithmetic.add(new DummyThermalEss("tess0").withSoc(100).withCapacity(9000));
		arithmetic.add(new DummyThermalEss("tess1").withSoc(50).withCapacity(1000));
		// Weighted arithmetic mean: 0.9*100 + 0.1*50 = 95
		assertEquals(Integer.valueOf(95), arithmetic.calculate());
	}

	@Test
	public void testFallbackWithoutCapacity() {
		var geometric = new CalculateTessSoc(SocAveragingMethod.GEOMETRIC);
		geometric.add(new DummyThermalEss("tess0").withSoc(100));
		geometric.add(new DummyThermalEss("tess1").withSoc(50));
		// Falls back to unweighted geometric mean = sqrt(100*50) ≈ 70.7 → 71
		assertEquals(Integer.valueOf(71), geometric.calculate());

		var arithmetic = new CalculateTessSoc(SocAveragingMethod.ARITHMETIC);
		arithmetic.add(new DummyThermalEss("tess0").withSoc(100));
		arithmetic.add(new DummyThermalEss("tess1").withSoc(50));
		// Falls back to unweighted arithmetic mean = 75
		assertEquals(Integer.valueOf(75), arithmetic.calculate());
	}

	@Test
	public void testNullSocSkipped() {
		var calc = new CalculateTessSoc(SocAveragingMethod.GEOMETRIC);
		calc.add(new DummyThermalEss("tess0").withCapacity(10000));
		assertNull(calc.calculate());
	}
}
