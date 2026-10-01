package io.openems.edge.heat.tess.api.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.Optional;

import org.junit.Test;

import io.openems.edge.heat.test.DummyManagedHeating;
import io.openems.edge.heat.test.DummyManagedThermalEss;

public class TargetPowerDistributorTest {

	@Test
	public void testResolveEffectiveTargetEmpty() {
		var tess = new DummyManagedThermalEss("tess0");
		assertNull(TargetPowerDistributor.resolveEffectiveTarget(tess));
	}

	@Test
	public void testResolveEffectiveTargetEquals() throws Exception {
		var tess = new DummyManagedThermalEss("tess0");
		tess.setTargetThermalPowerEqualsWithoutFilter(2500);
		assertEquals(Integer.valueOf(2500), TargetPowerDistributor.resolveEffectiveTarget(tess));
		// Pending writes are consumed
		assertNull(TargetPowerDistributor.resolveEffectiveTarget(tess));
	}

	@Test
	public void testResolveEffectiveTargetGreaterOrEquals() throws Exception {
		var tess = new DummyManagedThermalEss("tess0");
		tess.setTargetThermalPowerGreaterOrEquals(800);
		assertEquals(Integer.valueOf(800), TargetPowerDistributor.resolveEffectiveTarget(tess));
	}

	@Test
	public void testResolveEffectiveTargetMerge() throws Exception {
		var tess = new DummyManagedThermalEss("tess0");
		// Equals and GreaterOrEquals merge to the maximum
		tess.setTargetThermalPowerEqualsWithoutFilter(2500);
		tess.setTargetThermalPowerGreaterOrEquals(800);
		assertEquals(Integer.valueOf(2500), TargetPowerDistributor.resolveEffectiveTarget(tess));

		// LessOrEquals caps the result
		tess.setTargetThermalPowerEqualsWithoutFilter(2500);
		tess.setTargetThermalPowerLessOrEquals(1000);
		assertEquals(Integer.valueOf(1000), TargetPowerDistributor.resolveEffectiveTarget(tess));

		// LessOrEquals alone is no charge request
		tess.setTargetThermalPowerLessOrEquals(1000);
		assertNull(TargetPowerDistributor.resolveEffectiveTarget(tess));
	}

	@Test
	public void testDistributePriorityOrder() throws Exception {
		// Thermal budget: hp0 = 2000 W el x 3.0 = 6000 W th; heater0 = 3000 W th
		var hp = new DummyManagedHeating("hp0") //
				.withMaxTargetActivePower(2000) //
				.withThermalEfficiency(300F);
		var heater = new DummyManagedHeating("heater0") //
				.withMaxTargetActivePower(3000);

		var tess = new DummyManagedThermalEss("tess0");
		var remaining = TargetPowerDistributor.distribute(7000, List.of(//
				TargetPowerDistributor.ofHeating(hp, tess), //
				TargetPowerDistributor.ofHeating(heater, tess)));

		// hp0 takes 6000 W th = 2000 W el; heater0 takes the remaining 1000 W th
		assertEquals(Optional.of(2000), hp.getTargetActivePowerChannel().getNextWriteValue());
		assertEquals(Optional.of(1000), heater.getTargetActivePowerChannel().getNextWriteValue());
		assertEquals(0, remaining);
	}

	@Test
	public void testDistributeReturnsRemainder() throws Exception {
		var hp = new DummyManagedHeating("hp0") //
				.withMaxTargetActivePower(2000) //
				.withThermalEfficiency(300F);

		var remaining = TargetPowerDistributor.distribute(10000,
				List.of(TargetPowerDistributor.ofHeating(hp, new DummyManagedThermalEss("tess0"))));

		assertEquals(Optional.of(2000), hp.getTargetActivePowerChannel().getNextWriteValue());
		assertEquals(4000, remaining);
	}

	@Test
	public void testDistributeOnOffMinimum() throws Exception {
		// On/off device: minimum equals nominal power
		var heater = new DummyManagedHeating("heater0") //
				.withMaxTargetActivePower(3000) //
				.withMinTargetActivePower(3000);

		TargetPowerDistributor.distribute(1000,
				List.of(TargetPowerDistributor.ofHeating(heater, new DummyManagedThermalEss("tess0"))));

		// Allocation below the minimum results in an explicit zero
		assertEquals(Optional.of(0), heater.getTargetActivePowerChannel().getNextWriteValue());
	}

	@Test
	public void testDistributeSkipsForeignPendingWrite() throws Exception {
		var hp = new DummyManagedHeating("hp0") //
				.withMaxTargetActivePower(2000) //
				.withThermalEfficiency(300F);
		var heater = new DummyManagedHeating("heater0") //
				.withMaxTargetActivePower(3000);

		// A foreign storage already commanded hp0 in this cycle
		hp.getTargetActivePowerChannel().setNextWriteValue(500);

		var tess = new DummyManagedThermalEss("tess0");
		TargetPowerDistributor.distribute(3000, List.of(//
				TargetPowerDistributor.ofHeating(hp, tess), //
				TargetPowerDistributor.ofHeating(heater, tess)));

		// First write wins: hp0 keeps the foreign target, heater0 serves the request
		assertEquals(Optional.of(500), hp.getTargetActivePowerChannel().getNextWriteValue());
		assertEquals(Optional.of(3000), heater.getTargetActivePowerChannel().getNextWriteValue());
	}

	@Test
	public void testEfficiencyFallback() {
		var heating = new DummyManagedHeating("hp0");
		assertEquals(1F, TargetPowerDistributor.efficiencyOf(heating), 0.001F);
	}

	@Test
	public void testAggregates() {
		var hp = new DummyManagedHeating("hp0") //
				.withMaxTargetActivePower(2000) //
				.withMinTargetActivePower(500) //
				.withThermalEfficiency(300F);
		var heater = new DummyManagedHeating("heater0") //
				.withMaxTargetActivePower(3000) //
				.withMinTargetActivePower(3000);

		assertEquals(Integer.valueOf(9000), TargetPowerDistributor.sumMaxTargetThermalPower(List.of(hp, heater)));
		assertEquals(Integer.valueOf(1500), TargetPowerDistributor.minTargetThermalPower(List.of(hp, heater)));

		var unknown = new DummyManagedHeating("hp1");
		assertNull(TargetPowerDistributor.sumMaxTargetThermalPower(List.of(unknown)));
		assertNull(TargetPowerDistributor.minTargetThermalPower(List.of(unknown)));
		assertFalse(TargetPowerDistributor.ofHeating(unknown, new DummyManagedThermalEss("tess0")).hasPendingWrite());
	}
}
