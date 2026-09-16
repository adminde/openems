package io.openems.edge.oros.simulator.ess.symmetric.reacting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.Test;

import io.openems.common.test.TimeLeapClock;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.ess.test.DummyPower;
import io.openems.edge.oros.simulator.bms.BatteryManagementSimulatorImpl;
import io.openems.edge.oros.simulator.pcs.PowerConversionSimulatorImpl;

public class SymmetricStorageSimulatorReactingImplTest {

	private static final String ESS_ID = "ess0";
	private static final String PCS_ID = "pcs0";
	private static final String BMS_ID = "bms0";

	private static final int CAPACITY = 200_000;
	private static final int INITIAL_SOC = 50;
	private static final int MAX_ACTIVE_POWER = 100_000;
	private static final float EFFICIENCY = 98F;
	private static final int INTERNAL_RESISTANCE = 120;
	private static final float COEFFICIENT_OF_PERFORMANCE = 2.5F;
	private static final int THERMAL_MANAGEMENT_BASE_POWER = 5;

	private final TimeLeapClock clock = new TimeLeapClock(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
	private final DummyComponentManager componentManager = new DummyComponentManager(this.clock);

	private final BatteryManagementSimulatorImpl bms = new BatteryManagementSimulatorImpl();
	private final PowerConversionSimulatorImpl pcs = new PowerConversionSimulatorImpl();
	private final SymmetricStorageSimulatorReactingImpl ess = new SymmetricStorageSimulatorReactingImpl();

	/**
	 * Activates Battery, Inverter and Storage System and runs one Cycle on each of
	 * them, so every Channel written on activate has reached the process image.
	 *
	 * @return the {@link ComponentTest} of the Energy Storage System
	 * @throws Exception on error
	 */
	private ComponentTest activate() throws Exception {
		new ComponentTest(this.bms) //
				.addReference("componentManager", this.componentManager) //
				.activate(io.openems.edge.oros.simulator.bms.MyConfig.create() //
						.setId(BMS_ID) //
						.setCapacity(CAPACITY) //
						.setInitialSoc(INITIAL_SOC) //
						.setMaxChargeVoltage(960F) //
						.setMinDischargeVoltage(665F) //
						.setInternalResistance(INTERNAL_RESISTANCE) //
						.setThermalManagementCoefficientOfPerformance(COEFFICIENT_OF_PERFORMANCE) //
						.setThermalManagementBasePower(THERMAL_MANAGEMENT_BASE_POWER) //
						.build()) //
				.next(new TestCase());
		new ComponentTest(this.pcs) //
				.activate(io.openems.edge.oros.simulator.pcs.MyConfig.create() //
						.setId(PCS_ID) //
						.setMaxActivePower(MAX_ACTIVE_POWER) //
						.setEfficiency(EFFICIENCY) //
						.build()) //
				.next(new TestCase());
		return new ComponentTest(this.ess) //
				.addReference("power", new DummyPower()) //
				.addReference("componentManager", this.componentManager) //
				.addReference("pcs", this.pcs) //
				.addReference("bms", this.bms) //
				.activate(MyConfig.create() //
						.setId(ESS_ID) //
						.setPcsId(PCS_ID) //
						.setBmsId(BMS_ID) //
						.build());
	}

	@Test
	public void testDcDischargePowerIsZeroWhileIdle() throws Exception {
		var test = this.activate();

		// The Power solver applies a set point every Cycle, a zero one while idle.
		this.ess.applyPower(0, 0);
		test.next(new TestCase());

		assertEquals(0, this.ess.getDcDischargePower().get().intValue());
	}

	@Test
	public void testDcDischargePowerFollowsTheConversionLosses() throws Exception {
		var test = this.activate();

		this.ess.applyPower(50_000, 0);
		test.next(new TestCase());
		// Discharging draws more from the Battery than reaches the grid.
		assertEquals(51_020, this.ess.getDcDischargePower().get().intValue());

		this.ess.applyPower(-50_000, 0);
		test.next(new TestCase());
		// Charging stores less in the Battery than is taken from the grid.
		assertEquals(-49_000, this.ess.getDcDischargePower().get().intValue());

		this.ess.applyPower(0, 0);
		test.next(new TestCase());
		assertEquals(0, this.ess.getDcDischargePower().get().intValue());
	}

	@Test
	public void testAvailableEnergyFollowsTheStateOfCharge() throws Exception {
		var test = this.activate();

		test.next(new TestCase());
		assertEquals(100_000, this.ess.getAvailableDischargeEnergy().get().intValue());
		assertEquals(100_000, this.ess.getAvailableChargeEnergy().get().intValue());

		// Discharging 20 kW AC for an hour draws a tenth of the Capacity plus the
		// losses of the conversion and of the internal resistance.
		this.ess.applyPower(20_000, 0);
		test.next(new TestCase().timeleap(this.clock, 1, ChronoUnit.HOURS));
		this.ess.applyPower(20_000, 0);
		test.next(new TestCase());

		assertEquals(79_600, this.ess.getAvailableDischargeEnergy().get().intValue());
		assertEquals(120_400, this.ess.getAvailableChargeEnergy().get().intValue());
	}

	@Test
	public void testAvailableEnergiesAddUpToTheCapacity() throws Exception {
		var test = this.activate();

		this.ess.applyPower(40_000, 0);
		test.next(new TestCase().timeleap(this.clock, 30, ChronoUnit.MINUTES));
		this.ess.applyPower(40_000, 0);
		test.next(new TestCase());

		assertEquals(CAPACITY, this.ess.getAvailableDischargeEnergy().get()
				+ this.ess.getAvailableChargeEnergy().get());
	}
}
