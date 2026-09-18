package io.openems.edge.controller.highloadtimeslot;

import static io.openems.edge.controller.highloadtimeslot.WeekdayFilter.EVERDAY;
import static io.openems.edge.ess.api.ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_GREATER_OR_EQUALS;
import static io.openems.edge.ess.api.ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_LESS_OR_EQUALS;
import static io.openems.edge.ess.api.ManagedSymmetricEss.ChannelId.SET_REACTIVE_POWER_EQUALS;
import static java.time.temporal.ChronoUnit.MINUTES;

import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.Test;

import io.openems.common.test.TimeLeapClock;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.ess.test.DummyManagedSymmetricEss;

public class ControllerHighLoadTimeslotImplTest {

	@Test
	public void test() throws Exception {
		new ControllerTest(new ControllerHighLoadTimeslotImpl()) //
				.addReference("componentManager", new DummyComponentManager()) //
				.activate(MyConfig.create() //
						.setId("ctrl0") //
						.setEss("ess0") //
						.setHysteresisSoc(90) //
						.setChargePower(-10000) //
						.setDischargePower(20000) //
						.setStartDate("01.01.2019") //
						.setEndDate("01.01.2020") //
						.setStartTime("08:00") //
						.setEndTime("13:00") //
						.setWeekdayFilter(EVERDAY) //
						.build()) //
				.deactivate();
	}

	@Test
	public void testLimits() throws Exception {
		final var clock = new TimeLeapClock(Instant.parse("2019-06-03T07:00:00.00Z"), ZoneOffset.UTC);
		new ControllerTest(new ControllerHighLoadTimeslotImpl()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addComponent(new DummyManagedSymmetricEss("ess0")) //
				.activate(MyConfig.create() //
						.setId("ctrl0") //
						.setEss("ess0") //
						.setHysteresisSoc(90) //
						.setChargePower(-10000) //
						.setDischargePower(20000) //
						.setStartDate("01.01.2019") //
						.setEndDate("01.01.2020") //
						.setStartTime("08:00") //
						.setEndTime("13:00") //
						.setWeekdayFilter(EVERDAY) //
						.build()) //
				// 07:00 outside the timeslot: charge with at least the charge power
				.next(new TestCase() //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, -10000) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, null) //
						.output("ess0", SET_REACTIVE_POWER_EQUALS, 0)) //
				// 07:31 shortly before the timeslot: force charge
				.next(new TestCase() //
						.timeleap(clock, 31, MINUTES) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, -10000) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, null)) //
				// 08:01 within the timeslot: discharge with at least the discharge power
				.next(new TestCase() //
						.timeleap(clock, 30, MINUTES) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, null) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, 20000)) //
				// 13:01 after the timeslot: charge again
				.next(new TestCase() //
						.timeleap(clock, 300, MINUTES) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, -10000) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, null)) //
				.deactivate();
	}
}
