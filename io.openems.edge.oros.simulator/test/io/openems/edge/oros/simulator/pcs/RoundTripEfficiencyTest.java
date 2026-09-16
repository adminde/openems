package io.openems.edge.oros.simulator.pcs;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.Test;

import io.openems.common.test.TimeLeapClock;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.oros.simulator.bms.BatteryManagementSimulatorImpl;

/**
 * Pins the AC round-trip efficiency the two loss models add up to.
 *
 * <p>At equal AC power in both directions the efficiency is the ratio of the
 * time one State of Charge band takes to discharge over the time the same band
 * takes to charge, because the AC energy is the power times that time.
 *
 * <p>The efficiency measured here covers the conversion and the Battery only. It
 * rises towards lower power, as both losses grow with the power, while the
 * auxiliary consumption of a real system does not. Figures quoted for a site are
 * measured at the point of connection and additionally carry the transformer and
 * the auxiliaries, so they are lower and fall towards lower utilisation.
 */
public class RoundTripEfficiencyTest {

	private static final int CAPACITY = 200_000;
	private static final int INTERNAL_RESISTANCE = 120;
	private static final float EFFICIENCY = 98F;

	private static final int POWER = 100_000;
	private static final int LOWER_SOC = 100;
	private static final int UPPER_SOC = 900;

	private final TimeLeapClock clock = new TimeLeapClock(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
	private final BatteryManagementSimulatorImpl bms = new BatteryManagementSimulatorImpl();
	private final PowerConversionSimulatorImpl pcs = new PowerConversionSimulatorImpl();

	private void activate() throws Exception {
		new ComponentTest(this.bms) //
				.addReference("componentManager", new DummyComponentManager(this.clock)) //
				.activate(io.openems.edge.oros.simulator.bms.MyConfig.create() //
						.setId("bms0") //
						.setCapacity(CAPACITY) //
						.setInitialSoc(50) //
						.setMaxChargeVoltage(960F) //
						.setMinDischargeVoltage(665F) //
						.setInternalResistance(INTERNAL_RESISTANCE) //
						.build()) //
				.next(new TestCase());
		new ComponentTest(this.pcs) //
				.activate(MyConfig.create() //
						.setId("pcs0") //
						.setMaxActivePower(POWER) //
						.setEfficiency(EFFICIENCY) //
						.build()) //
				.next(new TestCase());
	}

	/**
	 * Runs at constant AC power until the State of Charge passes the target.
	 *
	 * @param acPower  the AC power in [W], positive for discharge
	 * @param targetSoc the State of Charge to reach in [0.1 %]
	 * @return the elapsed time in seconds
	 * @throws Exception on error
	 */
	private int rampTo(int acPower, int targetSoc) throws Exception {
		var seconds = 0;
		while (acPower < 0 //
				? this.bms.getRackSocChannel().getNextValue().get() < targetSoc //
				: this.bms.getRackSocChannel().getNextValue().get() > targetSoc) {
			this.pcs.run(this.bms, acPower, 0);
			this.clock.leap(1, ChronoUnit.SECONDS);
			seconds++;
		}
		return seconds;
	}

	@Test
	public void testRoundTripEfficiencyOfConversionAndBattery() throws Exception {
		this.activate();

		this.rampTo(-POWER, UPPER_SOC);
		var discharge = this.rampTo(POWER, LOWER_SOC);
		var charge = this.rampTo(-POWER, UPPER_SOC);
		var efficiency = 100F * discharge / charge;

		assertTrue(efficiency > 92F && efficiency < 93F,
				"the conversion and the internal resistance have to add up to roughly 92.5 percent "
						+ "at full power, measured " + efficiency);
	}
}
