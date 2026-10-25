package io.openems.edge.scheduler.jscalendar;

import static io.openems.common.jscalendar.JSCalendar.RecurrenceFrequency.DAILY;
import static io.openems.common.jscalendar.JSCalendar.RecurrenceFrequency.WEEKLY;
import static io.openems.common.test.TestUtils.createDummyClock;
import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static java.time.Month.DECEMBER;
import static java.time.Month.FEBRUARY;
import static java.time.Month.JANUARY;
import static java.time.Month.NOVEMBER;
import static java.time.Month.OCTOBER;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.junit.Test;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jscalendar.JSCalendar;
import io.openems.common.test.TimeLeapClock;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.controller.test.DummyController;
import io.openems.edge.scheduler.api.Scheduler;
import io.openems.edge.scheduler.jscalendar.Utils.Payload;

//CHECKSTYLE:OFF
public class SchedulerJSCalendarImplTest {
	// CHECKSTYLE:ON

	@Test
	public void test() throws Exception {
		final var clock = createDummyClock(); // Starts on a WEDNESDAY
		final var tasks = JSCalendar.Tasks.<Payload>create(clock) //
				.add(t -> t //
						.setStart(LocalTime.of(11, 00)) //
						.setDuration(Duration.ofHours(1)) //
						.addRecurrenceRule(b -> b //
								.setFrequency(DAILY)) //
						.setPayload(new Payload(new String[] { "dailyAt11" }))) //
				.add(t -> t //
						.setStart(LocalTime.of(8, 30)) //
						.setDuration(Duration.ofHours(10)) //
						.addRecurrenceRule(b -> b //
								.setFrequency(WEEKLY) //
								.addByDay(TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)) //
						.setPayload(new Payload(new String[] { "weekdaysAt8_30#1", "weekdaysAt8_30#2" }))) //
				.add(t -> t //
						.setStart(LocalTime.of(7, 30)) //
						.setDuration(Duration.ofHours(14)) //
						.addRecurrenceRule(b -> b //
								.setFrequency(WEEKLY) //
								.addByDay(SATURDAY, SUNDAY)) //
						.setPayload(new Payload(new String[] { "weekendsAt7_30" }))) //
				.build();

		final SchedulerJSCalendarImpl sut = new SchedulerJSCalendarImpl();
		new ComponentTest(sut) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addComponent(new DummyController("weekdaysAt8_30#1")) //
				.addComponent(new DummyController("weekdaysAt8_30#2")) //
				.addComponent(new DummyController("weekendsAt7_30")) //
				.addComponent(new DummyController("dailyAt11")) //
				.addComponent(new DummyController("alwaysBefore")) //
				.addComponent(new DummyController("alwaysAfter#1")) //
				.addComponent(new DummyController("alwaysAfter#2")) //
				.activate(MyConfig.create() //
						.setId("scheduler0") //
						.setAlwaysRunBeforeControllerIds("alwaysBefore") //
						.setJSCalendar(JSCalendar.Tasks.serializer(clock, Payload.serializer()) //
								.serialize(tasks) //
								.toString()) //
						.setAlwaysRunAfterControllerIds(//
								"alwaysAfter#1", "alwaysAfter#2") //
						.build()) //
				.next(new TestCase() // No active task
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 00:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.next(new TestCase() // No active task
						.timeleap(clock, 8 * 60 + 29, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 08:29")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.next(new TestCase() //
						.timeleap(clock, 1, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 08:30")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"weekdaysAt8_30#1", "weekdaysAt8_30#2", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.next(new TestCase() //
						.timeleap(clock, 2 * 60 + 29, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 10:59")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"weekdaysAt8_30#1", "weekdaysAt8_30#2", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.next(new TestCase() // Both tasks are active, in the order of the calendar
						.timeleap(clock, 1, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 11:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"dailyAt11", //
								"weekdaysAt8_30#1", "weekdaysAt8_30#2", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.next(new TestCase() //
						.timeleap(clock, 59, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 11:59")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"dailyAt11", //
								"weekdaysAt8_30#1", "weekdaysAt8_30#2", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.next(new TestCase() //
						.timeleap(clock, 1, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 12:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"weekdaysAt8_30#1", "weekdaysAt8_30#2", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.next(new TestCase() //
						.timeleap(clock, 6 * 60 + 29, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 18:29")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"weekdaysAt8_30#1", "weekdaysAt8_30#2", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.next(new TestCase() //
						.timeleap(clock, 1, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 18:30")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.next(new TestCase() //
						.timeleap(clock, 5 * 60 + 30, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Thu 2020-01-02 00:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.next(new TestCase() //
						.timeleap(clock, 8 * 60 + 30, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Thu 2020-01-02 08:30")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"weekdaysAt8_30#1", "weekdaysAt8_30#2", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.next(new TestCase() // A task whose whole occurrence the leap skipped is not run late
						.timeleap(clock, 4 * 60 + 30, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Thu 2020-01-02 13:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"weekdaysAt8_30#1", "weekdaysAt8_30#2", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.next(new TestCase() //
						.timeleap(clock, 19 * 60 + 30, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Fri 2020-01-03 08:30")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"weekdaysAt8_30#1", "weekdaysAt8_30#2", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.next(new TestCase() //
						.timeleap(clock, 23 * 60 - 1, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Sat 2020-01-04 07:29")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.next(new TestCase() //
						.timeleap(clock, 1, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Sat 2020-01-04 07:30")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"weekendsAt7_30", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.next(new TestCase() //
						.timeleap(clock, 3 * 60 + 30, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Sat 2020-01-04 11:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"dailyAt11", //
								"weekendsAt7_30", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.next(new TestCase() //
						.timeleap(clock, 10 * 60 + 29, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Sat 2020-01-04 21:29")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"weekendsAt7_30", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.next(new TestCase() //
						.timeleap(clock, 1, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Sat 2020-01-04 21:30")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"alwaysAfter#1", "alwaysAfter#2"))) //
				.deactivate();
	}

	@Test
	public void testOverlappingTasks() throws Exception {
		final var clock = createDummyClock(); // Starts on a WEDNESDAY
		final var tasks = JSCalendar.Tasks.<Payload>create(clock) //
				.add(t -> t //
						.setStart(LocalTime.of(8, 0)) //
						.setDuration(Duration.ofHours(4)) //
						.addRecurrenceRule(b -> b //
								.setFrequency(DAILY)) //
						.setPayload(new Payload(new String[] { "window", "hold" }))) //
				.add(t -> t // Sunday 12:00 to Friday 18:00
						.setStart(LocalTime.of(12, 0)) //
						.setDuration(Duration.ofHours(5 * 24 + 6)) //
						.addRecurrenceRule(b -> b //
								.setFrequency(WEEKLY) //
								.addByDay(SUNDAY)) //
						.setPayload(new Payload(new String[] { "hold" }))) //
				.build();

		final SchedulerJSCalendarImpl sut = new SchedulerJSCalendarImpl();
		new ComponentTest(sut) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addComponent(new DummyController("window")) //
				.addComponent(new DummyController("hold")) //
				.addComponent(new DummyController("alwaysBefore")) //
				.addComponent(new DummyController("alwaysAfter")) //
				.activate(MyConfig.create() //
						.setId("scheduler0") //
						.setAlwaysRunBeforeControllerIds("alwaysBefore") //
						.setJSCalendar(JSCalendar.Tasks.serializer(clock, Payload.serializer()) //
								.serialize(tasks) //
								.toString()) //
						.setAlwaysRunAfterControllerIds("alwaysAfter") //
						.build()) //
				.next(new TestCase() // The weekly task runs since Sunday
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 00:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"hold", //
								"alwaysAfter"))) //
				.next(new TestCase() // Both tasks, in the order of the calendar, a shared Controller once
						.timeleap(clock, 8 * 60, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 08:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"window", "hold", //
								"alwaysAfter"))) //
				.next(new TestCase() //
						.timeleap(clock, 4 * 60, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 12:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"hold", //
								"alwaysAfter"))) //
				.next(new TestCase() //
						.timeleap(clock, 2 * 24 * 60 + 5 * 60 + 59, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Fri 2020-01-03 17:59")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"hold", //
								"alwaysAfter"))) //
				.next(new TestCase() //
						.timeleap(clock, 1, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Fri 2020-01-03 18:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"alwaysAfter"))) //
				.next(new TestCase() // The daily task alone on the weekend
						.timeleap(clock, 14 * 60, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Sat 2020-01-04 08:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"window", "hold", //
								"alwaysAfter"))) //
				.next(new TestCase() //
						.timeleap(clock, 27 * 60 + 59, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Sun 2020-01-05 11:59")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"window", "hold", //
								"alwaysAfter"))) //
				.next(new TestCase() // The daily task ends as the weekly one begins
						.timeleap(clock, 1, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Sun 2020-01-05 12:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"hold", //
								"alwaysAfter"))) //
				.deactivate();
	}

	@Test
	public void testSeasonalTasks() throws Exception {
		final var clock = createDummyClock(); // Starts on a WEDNESDAY in a leap year
		final var tasks = JSCalendar.Tasks.<Payload>create(clock) //
				.add(t -> t // Every day of the season from November to February
						.setStart(LocalTime.of(8, 0)) //
						.setDuration(Duration.ofHours(4)) //
						.addRecurrenceRule(b -> b //
								.setFrequency(DAILY) //
								.addByMonth(NOVEMBER, DECEMBER, JANUARY, FEBRUARY)) //
						.setPayload(new Payload(new String[] { "winter" }))) //
				.add(t -> t // The evening before the season
						.setStart(LocalTime.of(22, 30)) //
						.setDuration(Duration.ofHours(2)) //
						.addRecurrenceRule(b -> b //
								.setFrequency(DAILY) //
								.addByMonth(OCTOBER) //
								.addByMonthDay(-1)) //
						.setPayload(new Payload(new String[] { "eve" }))) //
				.build();

		final SchedulerJSCalendarImpl sut = new SchedulerJSCalendarImpl();
		new ComponentTest(sut) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addComponent(new DummyController("winter")) //
				.addComponent(new DummyController("eve")) //
				.activate(MyConfig.create() //
						.setId("scheduler0") //
						.setAlwaysRunBeforeControllerIds() //
						.setJSCalendar(JSCalendar.Tasks.serializer(clock, Payload.serializer()) //
								.serialize(tasks) //
								.toString()) //
						.setAlwaysRunAfterControllerIds() //
						.build()) //
				.next(new TestCase() //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 00:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut))) //
				.next(new TestCase() //
						.timeleap(clock, 8, HOURS) //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 08:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, "winter"))) //
				.next(new TestCase() // The leap day is part of the season
						.timeleap(clock, 59, DAYS) //
						.onBeforeProcessImage(() -> assertTime(clock, "Sat 2020-02-29 08:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, "winter"))) //
				.next(new TestCase() //
						.timeleap(clock, 1, DAYS) //
						.onBeforeProcessImage(() -> assertTime(clock, "Sun 2020-03-01 08:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut))) //
				.next(new TestCase() //
						.timeleap(clock, 244 * 24 * 60 + 14 * 60 + 29, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Sat 2020-10-31 22:29")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut))) //
				.next(new TestCase() //
						.timeleap(clock, 1, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Sat 2020-10-31 22:30")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, "eve"))) //
				.next(new TestCase() //
						.timeleap(clock, 90, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Sun 2020-11-01 00:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, "eve"))) //
				.next(new TestCase() //
						.timeleap(clock, 30, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Sun 2020-11-01 00:30")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut))) //
				.next(new TestCase() //
						.timeleap(clock, 7 * 60 + 30, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Sun 2020-11-01 08:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, "winter"))) //
				.deactivate();
	}

	private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("E yyyy-MM-dd HH:mm")
			.withLocale(Locale.ENGLISH);

	private static void assertTime(TimeLeapClock clock, String dateTime) throws OpenemsNamedException {
		assertEquals(dateTime, clock.now().format(DTF));
	}

	private static void assertControllerIds(Scheduler scheduler, String... controllerIds) throws OpenemsNamedException {
		assertArrayEquals(controllerIds, scheduler.getControllers().stream() //
				.toArray(String[]::new));
	}
}
