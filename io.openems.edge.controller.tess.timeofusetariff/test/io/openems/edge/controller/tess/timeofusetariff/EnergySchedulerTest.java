package io.openems.edge.controller.tess.timeofusetariff;

import static io.openems.edge.controller.tess.timeofusetariff.EnergyScheduler.buildEnergyScheduleHandler;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import io.openems.edge.controller.test.DummyController;
import io.openems.edge.energy.api.test.EnergyScheduleTester;
import io.openems.edge.heat.test.DummyManagedThermalEss;
import io.openems.edge.predictor.api.prediction.Prediction;

public class EnergySchedulerTest {

	private static final String CTRL_ID = "ctrl0";

	private static DummyManagedThermalEss tess(int soc) {
		return new DummyManagedThermalEss("tess0") //
				.withCapacity(10000) //
				.withSoc(soc) //
				.withMinTemperature(200) //
				.withTargetTemperature(600) //
				.withMaxTemperature(800) //
				.withMaxTargetThermalPower(12000);
	}

	@Test
	public void testNull() {
		var esh = buildEnergyScheduleHandler(new DummyController(CTRL_ID), //
				() -> null, () -> tess(50), () -> Prediction.EMPTY_PREDICTION);
		var t = EnergyScheduleTester.from(esh);
		var t0 = t.simulatePeriod(0 /* NONE */);
		assertEquals(0, t0.ef().getManagedConsumption(CTRL_ID));
	}

	@Test
	public void testChargeGrid() {
		var esh = buildEnergyScheduleHandler(new DummyController(CTRL_ID), //
				() -> new EnergyScheduler.EshConfig(3.0, 1000), //
				() -> tess(50), //
				() -> Prediction.EMPTY_PREDICTION);
		var t = EnergyScheduleTester.from(esh);

		// Initial Population: CHARGE_GRID on the cheapest period of each price valley
		var ip = t.perEsh.get(0).initialPopulation();
		assertEquals(2, ip.size());
		var periodCount = t.goc.periods().size();
		var actual = ip.stream() //
				.map(p -> Arrays.toString(p.modeIndexes())) //
				.collect(Collectors.toSet());
		assertEquals(Set.of(//
				toModeIndexesString(periodCount, 15), //
				toModeIndexesString(periodCount, 3)), actual);

		// NONE: only the predicted discharge applies; no managed consumption.
		// Fallback demand 1000 W = 250 Wh/quarter; energy 5000 -> 4750
		assertEquals(0, t.simulatePeriod(0 /* NONE */).ef().getManagedConsumption(CTRL_ID));

		// CHARGE_GRID: max intake 12000 W th = 3000 Wh/quarter; grid energy =
		// 3000 / 3.0 = 1000 Wh. Energy 4500 -> 7500
		assertEquals(1000, t.simulatePeriod(1 /* CHARGE_GRID */).ef().getManagedConsumption(CTRL_ID));

		// CHARGE_GRID: remaining intake to hardware max is 2750 Wh th -> 917 Wh grid
		assertEquals(917, t.simulatePeriod(1 /* CHARGE_GRID */).ef().getManagedConsumption(CTRL_ID));

		// CHARGE_GRID: storage nearly full; only the discharged 250 Wh are refilled
		assertEquals(83, t.simulatePeriod(1 /* CHARGE_GRID */).ef().getManagedConsumption(CTRL_ID));
	}

	@Test
	public void testAutonomousReheating() {
		var esh = buildEnergyScheduleHandler(new DummyController(CTRL_ID), //
				() -> new EnergyScheduler.EshConfig(1.0, 2000), //
				() -> tess(0), //
				() -> Prediction.EMPTY_PREDICTION);
		var t = EnergyScheduleTester.from(esh);

		// Storage is at the hard minimum: the Heating reheats autonomously toward
		// the target energy (60 % of 10000 Wh = 6667 Wh) even in NONE periods —
		// at the price of the respective period.
		assertEquals(3000, t.simulatePeriod(0 /* NONE */).ef().getManagedConsumption(CTRL_ID));
		assertEquals(3000, t.simulatePeriod(0 /* NONE */).ef().getManagedConsumption(CTRL_ID));
		assertEquals(1667, t.simulatePeriod(0 /* NONE */).ef().getManagedConsumption(CTRL_ID));

		// Target energy reached: the two-point latch switches off; the storage coasts
		assertEquals(0, t.simulatePeriod(0 /* NONE */).ef().getManagedConsumption(CTRL_ID));
	}

	@Test
	public void testFullStorage() {
		var esh = buildEnergyScheduleHandler(new DummyController(CTRL_ID), //
				() -> new EnergyScheduler.EshConfig(3.0, 0), //
				() -> tess(100), //
				() -> Prediction.EMPTY_PREDICTION);
		var t = EnergyScheduleTester.from(esh);

		// A CHARGE_GRID period on a full storage takes no energy
		assertEquals(0, t.simulatePeriod(1 /* CHARGE_GRID */).ef().getManagedConsumption(CTRL_ID));
	}

	private static String toModeIndexesString(int periodCount, int chargeGridIndex) {
		var modeIndexes = new int[periodCount];
		modeIndexes[chargeGridIndex] = 1 /* CHARGE_GRID */;
		return Arrays.toString(modeIndexes);
	}
}
