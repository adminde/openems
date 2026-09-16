package io.openems.edge.oros.simulator.bms;

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

public class BatteryManagementSimulatorImplTest {

	private static final String BMS_ID = "bms0";

	private static final int CAPACITY = 200_000;
	private static final int INITIAL_SOC = 50;
	private static final float MAX_CHARGE_VOLTAGE = 960F;
	private static final float MIN_DISCHARGE_VOLTAGE = 665F;
	private static final int INTERNAL_RESISTANCE = 120;
	private static final float COEFFICIENT_OF_PERFORMANCE = 2.5F;
	private static final int THERMAL_MANAGEMENT_BASE_POWER = 200;

	private final TimeLeapClock clock = new TimeLeapClock(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);

	/**
	 * Activates a Battery and runs one Cycle, so the State of Charge written on
	 * activate has reached the process image.
	 *
	 * @param internalResistance the internal resistance in [mOhm]
	 * @return the activated {@link BatteryManagementSimulatorImpl}
	 * @throws Exception on error
	 */
	private BatteryManagementSimulatorImpl activate(int internalResistance) throws Exception {
		final var bms = new BatteryManagementSimulatorImpl();
		new ComponentTest(bms) //
				.addReference("componentManager", new DummyComponentManager(this.clock)) //
				.activate(MyConfig.create() //
						.setId(BMS_ID) //
						.setCapacity(CAPACITY) //
						.setInitialSoc(INITIAL_SOC) //
						.setMaxChargeVoltage(MAX_CHARGE_VOLTAGE) //
						.setMinDischargeVoltage(MIN_DISCHARGE_VOLTAGE) //
						.setInternalResistance(internalResistance) //
						.setThermalManagementCoefficientOfPerformance(COEFFICIENT_OF_PERFORMANCE) //
						.setThermalManagementBasePower(THERMAL_MANAGEMENT_BASE_POWER) //
						.build()) //
				.next(new TestCase());
		return bms;
	}

	private static float voltage(BatteryManagementSimulatorImpl bms) {
		return bms.getRackVoltageChannel().getNextValue().get() / 1000F;
	}

	private static float current(BatteryManagementSimulatorImpl bms) {
		return bms.getRackCurrentChannel().getNextValue().get() / 1000F;
	}

	private static int openCircuitVoltage(BatteryManagementSimulatorImpl bms) {
		return bms.getOpenCircuitVoltageChannel().getNextValue().get();
	}

	private static int stateOfCharge(BatteryManagementSimulatorImpl bms) {
		return bms.getRackSocChannel().getNextValue().get();
	}

	private static int resistance(BatteryManagementSimulatorImpl bms) {
		return bms.getInnerResistanceChannel().getNextValue().get();
	}

	private static int thermalManagementPower(BatteryManagementSimulatorImpl bms) {
		return bms.getThermalManagementPowerChannel().getNextValue().get();
	}

	@Test
	public void testTerminalVoltageFollowsTheDirectionOfTheCurrent() throws Exception {
		final var bms = this.activate(INTERNAL_RESISTANCE);

		bms.run(0);
		assertEquals(openCircuitVoltage(bms), Math.round(voltage(bms)));

		bms.run(50_000);
		assertTrue(voltage(bms) < openCircuitVoltage(bms),
				"discharging has to pull the terminal voltage below the Open Circuit Voltage");

		bms.run(-50_000);
		assertTrue(voltage(bms) > openCircuitVoltage(bms),
				"charging has to push the terminal voltage above the Open Circuit Voltage");
	}

	@Test
	public void testTerminalPowerIsVoltageTimesCurrent() throws Exception {
		final var bms = this.activate(INTERNAL_RESISTANCE);

		for (int power : new int[] { 100_000, 50_000, 0, -50_000, -100_000 }) {
			bms.run(power);
			assertEquals(power, voltage(bms) * current(bms), 5F,
					"the solved current has to reproduce the power at the terminals");
		}
	}

	@Test
	public void testTheLossIsTheSquareOfTheCurrentTimesTheResistance() throws Exception {
		final var bms = this.activate(INTERNAL_RESISTANCE);

		bms.run(100_000);
		var current = current(bms);
		var loss = openCircuitVoltage(bms) * current - voltage(bms) * current;

		// The Open Circuit Voltage Channel carries whole Volts, which at this current
		// leaves the derived loss a few tens of Watts of headroom.
		assertEquals(current * current * INTERNAL_RESISTANCE / 1000F, loss, 100F,
				"the difference between chemical and terminal power has to be the resistive loss");
	}

	@Test
	public void testTheInnerResistanceChannelDrivesTheModel() throws Exception {
		final var bms = this.activate(INTERNAL_RESISTANCE);

		assertEquals(INTERNAL_RESISTANCE, resistance(bms),
				"the configured resistance has to be published on the Channel");

		bms.run(100_000);
		var drop = openCircuitVoltage(bms) - voltage(bms);

		// The Channel is the input of the model, not a copy of it, so a Battery that
		// ages into a higher resistance drops more voltage at the same power.
		bms._setInnerResistance(INTERNAL_RESISTANCE * 2);
		bms.run(100_000);

		assertTrue(openCircuitVoltage(bms) - voltage(bms) > drop * 1.5F,
				"doubling the resistance on the Channel has to widen the voltage drop");
	}

	@Test
	public void testAnIdleBatteryRunsNoThermalManagement() throws Exception {
		final var bms = this.activate(INTERNAL_RESISTANCE);

		bms.run(50_000);
		assertTrue(thermalManagementPower(bms) > THERMAL_MANAGEMENT_BASE_POWER,
				"pump and heat pump have to draw power while the Rack carries a current");

		bms.run(0);
		assertEquals(0, thermalManagementPower(bms),
				"a Battery at rest produces no loss and keeps its Thermal Management System off");
	}

	@Test
	public void testThermalManagementCarriesTheLossOverTheCoefficientOfPerformance() throws Exception {
		final var bms = this.activate(INTERNAL_RESISTANCE);

		bms.run(100_000);
		var loss = current(bms) * current(bms) * INTERNAL_RESISTANCE / 1000F;

		assertEquals(THERMAL_MANAGEMENT_BASE_POWER + loss / COEFFICIENT_OF_PERFORMANCE,
				thermalManagementPower(bms), 2F,
				"the heat pump has to draw the loss over its Coefficient of Performance");
	}

	@Test
	public void testTheCoolingDemandGrowsFasterThanThePower() throws Exception {
		final var bms = this.activate(INTERNAL_RESISTANCE);

		bms.run(50_000);
		var half = thermalManagementPower(bms) - THERMAL_MANAGEMENT_BASE_POWER;
		bms.run(100_000);
		var full = thermalManagementPower(bms) - THERMAL_MANAGEMENT_BASE_POWER;

		// The loss follows the square of the current, so doubling the power more than
		// doubles what the Thermal Management System has to carry away.
		assertTrue(full > 2 * half,
				"doubling the power has to more than double the cooling demand");
	}

	@Test
	public void testALossFreeBatteryDrainsByTheTerminalPowerAlone() throws Exception {
		final var bms = this.activate(0);

		bms.run(50_000);
		this.clock.leap(1, ChronoUnit.HOURS);
		bms.run(50_000);

		// Half of 200 kWh less 50 kWh leaves a quarter of the Capacity.
		assertEquals(250, stateOfCharge(bms));
	}

	@Test
	public void testTheInternalResistanceDrainsTheBatteryFaster() throws Exception {
		final var lossFree = this.activate(0);
		final var lossy = this.activate(INTERNAL_RESISTANCE);

		lossFree.run(50_000);
		lossy.run(50_000);
		this.clock.leap(1, ChronoUnit.HOURS);
		lossFree.run(50_000);
		lossy.run(50_000);

		assertTrue(stateOfCharge(lossy) < stateOfCharge(lossFree),
				"the resistive loss has to come out of the Battery on top of the terminal power");
	}
}
