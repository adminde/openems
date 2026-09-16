package io.openems.edge.oros.simulator.auxiliary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.Test;

import io.openems.common.test.TimeLeapClock;
import io.openems.common.types.MeterType;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.oros.simulator.bms.BatteryManagementSimulatorImpl;

public class AuxiliarySimulatorImplTest {

	private static final String METER_ID = "meter0";
	private static final String BMS_ID = "bms0";

	private static final int CAPACITY = 261_000;
	private static final int INTERNAL_RESISTANCE = 120;
	private static final float COEFFICIENT_OF_PERFORMANCE = 2.5F;
	private static final int THERMAL_MANAGEMENT_BASE_POWER = 5;
	private static final int STANDBY_POWER = 100;

	private final TimeLeapClock clock = new TimeLeapClock(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
	private final BatteryManagementSimulatorImpl bms = new BatteryManagementSimulatorImpl();
	private final AuxiliarySimulatorImpl meter = new AuxiliarySimulatorImpl();

	/**
	 * Activates Battery and auxiliary Meter and runs one Cycle on the Battery, so
	 * the State of Charge written on activate has reached the process image.
	 *
	 * @return the {@link ComponentTest} of the Meter
	 * @throws Exception on error
	 */
	private ComponentTest activate() throws Exception {
		new ComponentTest(this.bms) //
				.addReference("componentManager", new DummyComponentManager(this.clock)) //
				.activate(io.openems.edge.oros.simulator.bms.MyConfig.create() //
						.setId(BMS_ID) //
						.setCapacity(CAPACITY) //
						.setInitialSoc(50) //
						.setMaxChargeVoltage(936F) //
						.setMinDischargeVoltage(728F) //
						.setInternalResistance(INTERNAL_RESISTANCE) //
						.setThermalManagementCoefficientOfPerformance(COEFFICIENT_OF_PERFORMANCE) //
						.setThermalManagementBasePower(THERMAL_MANAGEMENT_BASE_POWER) //
						.build()) //
				.next(new TestCase());
		return new ComponentTest(this.meter) //
				.addReference("bms", this.bms) //
				.activate(MyConfig.create() //
						.setId(METER_ID) //
						.setBmsId(BMS_ID) //
						.setStandbyPower(STANDBY_POWER) //
						.build());
	}

	@Test
	public void testItReportsConsumption() throws Exception {
		this.activate();

		assertEquals(MeterType.CONSUMPTION_METERED, this.meter.getMeterType());
	}

	@Test
	public void testAnIdleBatteryLeavesTheStandbyPowerAlone() throws Exception {
		var test = this.activate();

		this.bms.run(0);
		test.next(new TestCase());

		assertEquals(STANDBY_POWER, this.meter.getActivePower().get().intValue(),
				"without a Thermal Management System running only the controls draw power");
	}

	@Test
	public void testTheThermalManagementAddsToTheStandbyPower() throws Exception {
		var test = this.activate();

		this.bms.run(125_000);
		test.next(new TestCase());

		var thermalManagement = this.bms.getThermalManagementPowerChannel().getNextValue().get();
		assertTrue(thermalManagement > 0, "the Thermal Management System has to run under load");
		assertEquals(STANDBY_POWER + thermalManagement, this.meter.getActivePower().get().intValue(),
				"the Meter has to carry the standby and the Thermal Management System");
	}

	@Test
	public void testThePowerIsSplitOverThePhases() throws Exception {
		var test = this.activate();

		this.bms.run(125_000);
		test.next(new TestCase());

		var third = this.meter.getActivePower().get() / 3;
		assertEquals(third, this.meter.getActivePowerL1().get().intValue());
		assertEquals(third, this.meter.getActivePowerL2().get().intValue());
		assertEquals(third, this.meter.getActivePowerL3().get().intValue());
	}
}
