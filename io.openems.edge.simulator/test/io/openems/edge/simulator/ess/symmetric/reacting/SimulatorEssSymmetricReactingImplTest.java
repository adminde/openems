package io.openems.edge.simulator.ess.symmetric.reacting;

import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import io.openems.common.test.TestUtils;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.ess.test.DummyPower;
import io.openems.edge.ess.test.ManagedSymmetricEssTest;

/**
 * The simulated ESS runs on a clock that leaps per cycle. A set point applies
 * from the instant it is given until the next instant, and a second call at an
 * unchanged instant is ignored, because Simulator.App keeps one sample per
 * instant and that sample carries the first call's power.
 */
public class SimulatorEssSymmetricReactingImplTest {

	private static final ChannelAddress ESS_SOC = new ChannelAddress("ess0", "Soc");
	private static final ChannelAddress ESS_ACTIVE_POWER = new ChannelAddress("ess0", "ActivePower");
	private static final ChannelAddress ESS_SET_ACTIVE_POWER_EQUALS = new ChannelAddress("ess0",
			"SetActivePowerEquals");

	private static ManagedSymmetricEssTest ess(java.time.Clock clock, int initialSoc) throws Exception {
		return new ManagedSymmetricEssTest(new SimulatorEssSymmetricReactingImpl()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("power", new DummyPower()) //
				.activate(MyConfig.create() //
						.setId("ess0") //
						.setCapacity(10_000) //
						.setMaxApparentPower(10_000) //
						.setMaxChargePower(10_000) //
						.setMaxDischargePower(10_000) //
						.setInitialSoc(initialSoc) //
						.setGridMode(GridMode.ON_GRID) //
						.build());
	}

	@Test
	void integratesTheAppliedPowerOverTheElapsedTime() throws Exception {
		final var clock = TestUtils.createDummyClock();
		ess(clock, 50) //
				.next(new TestCase() //
						.output(ESS_SOC, 50)) //
				.next(new TestCase() //
						.timeleap(clock, 15, ChronoUnit.MINUTES) //
						.input(ESS_SET_ACTIVE_POWER_EQUALS, -10_000) //
						.output(ESS_SOC, 50)) //
				.next(new TestCase() //
						.timeleap(clock, 15, ChronoUnit.MINUTES) //
						.input(ESS_SET_ACTIVE_POWER_EQUALS, -10_000) //
						.output(ESS_SOC, 75)) //
				.next(new TestCase() //
						.timeleap(clock, 15, ChronoUnit.MINUTES) //
						.input(ESS_SET_ACTIVE_POWER_EQUALS, 10_000) //
						.output(ESS_SOC, 100)) //
				.next(new TestCase() //
						.timeleap(clock, 30, ChronoUnit.MINUTES) //
						.input(ESS_SET_ACTIVE_POWER_EQUALS, 10_000) //
						.output(ESS_SOC, 50)) //
				.deactivate();
	}

	@Test
	void ignoresASecondCallAtTheSameInstant() throws Exception {
		final var clock = TestUtils.createDummyClock();
		ess(clock, 50) //
				.next(new TestCase() //
						.timeleap(clock, 15, ChronoUnit.MINUTES) //
						.input(ESS_SET_ACTIVE_POWER_EQUALS, -10_000) //
						.output(ESS_SOC, 50) //
						.output(ESS_ACTIVE_POWER, -10_000)) //
				.next(new TestCase() //
						.input(ESS_SET_ACTIVE_POWER_EQUALS, 10_000) //
						.output(ESS_SOC, 50) //
						.output(ESS_ACTIVE_POWER, -10_000)) //
				.next(new TestCase() //
						.timeleap(clock, 15, ChronoUnit.MINUTES) //
						.input(ESS_SET_ACTIVE_POWER_EQUALS, 0) //
						.output(ESS_SOC, 75)) //
				.deactivate();
	}

	/**
	 * With 1 kWh of headroom left, a 10 kW charge request is cut to 4 kW, the pack
	 * then reaches exactly 100 %, and the following discharge starts from 10 kWh
	 * rather than from a phantom 11,5 kWh.
	 */
	@Test
	void clampsTheAppliedPowerToTheEnergyPresent() throws Exception {
		final var clock = TestUtils.createDummyClock();
		ess(clock, 80) //
				.next(new TestCase() //
						.timeleap(clock, 15, ChronoUnit.MINUTES) //
						.input(ESS_SET_ACTIVE_POWER_EQUALS, -4_000) //
						.output(ESS_SOC, 80) //
						.output(ESS_ACTIVE_POWER, -4_000)) //
				.next(new TestCase() //
						.timeleap(clock, 15, ChronoUnit.MINUTES) //
						.input(ESS_SET_ACTIVE_POWER_EQUALS, -10_000) //
						.output(ESS_SOC, 90) //
						.output(ESS_ACTIVE_POWER, -4_000)) //
				.next(new TestCase() //
						.timeleap(clock, 15, ChronoUnit.MINUTES) //
						.input(ESS_SET_ACTIVE_POWER_EQUALS, 10_000) //
						.output(ESS_SOC, 100)) //
				.next(new TestCase() //
						.timeleap(clock, 15, ChronoUnit.MINUTES) //
						.input(ESS_SET_ACTIVE_POWER_EQUALS, 10_000) //
						.output(ESS_SOC, 75)) //
				.deactivate();
	}
}
