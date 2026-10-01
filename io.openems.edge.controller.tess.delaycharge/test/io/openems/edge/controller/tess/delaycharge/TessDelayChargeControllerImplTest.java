package io.openems.edge.controller.tess.delaycharge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.Test;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.common.test.TimeLeapClock;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.heat.test.DummyManagedThermalEss;
import io.openems.edge.predictor.api.test.DummyPredictorManager;

public class TessDelayChargeControllerImplTest {

	@Test
	public void testManualMode() throws Exception {
		// 08:00 of the day; target 17:00 -> 540 minutes remaining
		final var clock = new TimeLeapClock(Instant.parse("2020-01-01T08:00:00Z"), ZoneOffset.UTC);
		var tess = new DummyManagedThermalEss("tess0") //
				.withCapacity(10000) //
				.withSoc(50);

		new ControllerTest(new TessDelayChargeControllerImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("predictorManager", new DummyPredictorManager()) //
				.addReference("tess", tess) //
				.activate(MyConfig.create() //
						.setId("ctrl0") //
						.setTessId("tess0") //
						.setMode(Mode.MANUAL) //
						.setManualTargetTime("17:00") //
						.build()) //
				// remaining 5000 Wh over 540 minutes = 555 W
				.next(new TestCase() //
						.output("ctrl0", TessDelayChargeController.ChannelId.TARGET_MINUTE, 1020) //
						.output("ctrl0", TessDelayChargeController.ChannelId.DELAY_CHARGE_LIMIT, 555));

		assertEquals(Optional.of(555), tess.getTargetThermalPowerLessOrEqualsChannel().getNextWriteValueAndReset());
	}

	@Test
	public void testNoLimitAfterTargetTime() throws Exception {
		final var clock = new TimeLeapClock(Instant.parse("2020-01-01T18:00:00Z"), ZoneOffset.UTC);
		var tess = new DummyManagedThermalEss("tess0") //
				.withCapacity(10000) //
				.withSoc(50);

		new ControllerTest(new TessDelayChargeControllerImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("predictorManager", new DummyPredictorManager()) //
				.addReference("tess", tess) //
				.activate(MyConfig.create() //
						.setId("ctrl0") //
						.setTessId("tess0") //
						.setMode(Mode.MANUAL) //
						.setManualTargetTime("17:00") //
						.build()) //
				.next(new TestCase() //
						.output("ctrl0", TessDelayChargeController.ChannelId.DELAY_CHARGE_LIMIT, null));

		assertEquals(Optional.empty(), tess.getTargetThermalPowerLessOrEqualsChannel().getNextWriteValue());
	}

	@Test
	public void testAutomaticModeWithoutPrediction() throws Exception {
		final var clock = new TimeLeapClock(Instant.parse("2020-01-01T08:00:00Z"), ZoneOffset.UTC);
		var tess = new DummyManagedThermalEss("tess0") //
				.withCapacity(10000) //
				.withSoc(50);

		new ControllerTest(new TessDelayChargeControllerImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("predictorManager", new DummyPredictorManager()) //
				.addReference("tess", tess) //
				.activate(MyConfig.create() //
						.setId("ctrl0") //
						.setTessId("tess0") //
						.setMode(Mode.AUTOMATIC) //
						.build()) //
				.next(new TestCase() //
						.output("ctrl0", TessDelayChargeController.ChannelId.TARGET_MINUTE, null) //
						.output("ctrl0", TessDelayChargeController.ChannelId.DELAY_CHARGE_LIMIT, null));

		assertEquals(Optional.empty(), tess.getTargetThermalPowerLessOrEqualsChannel().getNextWriteValue());
	}

	@Test
	public void testCalculateTargetMinute() {
		var startQuarter = ZonedDateTime.parse("2020-01-01T08:00:00Z");

		// Last quarter with production > consumption is index 3 (08:45..09:00)
		assertEquals(Integer.valueOf(540), TessDelayChargeControllerImpl.calculateTargetMinute(//
				new Integer[] { 0, 300, 500, 500, 100, 0 }, //
				new Integer[] { 200, 200, 200, 200, 200, 200 }, //
				startQuarter));

		// Production never exceeds consumption
		assertNull(TessDelayChargeControllerImpl.calculateTargetMinute(//
				new Integer[] { 0, 100, 150 }, //
				new Integer[] { 200, 200, 200 }, //
				startQuarter));

		// Surplus on the next day is ignored
		assertNull(TessDelayChargeControllerImpl.calculateTargetMinute(//
				new Integer[] { 0, 0, 500 }, //
				new Integer[] { 200, 200, 200 }, //
				ZonedDateTime.parse("2020-01-01T23:30:00Z")));

		// Null values are skipped
		assertEquals(Integer.valueOf(510), TessDelayChargeControllerImpl.calculateTargetMinute(//
				new Integer[] { 0, 500, null }, //
				new Integer[] { 200, 200, null }, //
				startQuarter));
	}
}
