package io.openems.edge.simulator.heat.pump.reacting;

import static io.openems.common.test.TestUtils.createDummyClock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.temporal.ChronoUnit;

import org.junit.Test;
import org.osgi.service.event.Event;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.TestUtils;
import io.openems.edge.heat.api.SymmetricHeating;
import io.openems.edge.heat.pump.api.HeatPump;
import io.openems.edge.heat.pump.core.HeatPumpPerformanceEstimator;
import io.openems.edge.heat.test.DummyThermalEss;
import io.openems.edge.heat.tess.api.ThermalEss;
import io.openems.edge.simulator.heat.tank.SimulatorStorageTank;

public class SimulatorHeatPumpReactingImplTest {

	private static final String HP_ID = "heatpump0";

	private static final int NOMINAL_ACTIVE = Math.round(8000 / 3.5f); // 2286 W el.
	private static final int MIN_ACTIVE = Math.round(2000 / 3.5f); // 571 W el.
	private static final HeatPumpPerformanceEstimator ESTIMATOR = HeatPumpPerformanceEstimator.of(3.5);

	/**
	 * The flow runs 5 K above the storage temperature; the COP sink of a storage
	 * at 30.0 °C is thus the standard rating point W35.
	 *
	 * @param temperatureDeciCelsius the storage temperature in [deci-°C]
	 * @return the flow temperature in [°C]
	 */
	private static double sink(int temperatureDeciCelsius) {
		return temperatureDeciCelsius / 10.0 + 5.0;
	}

	private static ComponentTest activateOnOff(SimulatorHeatPumpReactingImpl sut) throws Exception {
		return new ComponentTest(sut) //
				.addReference("componentManager", new DummyComponentManager(createDummyClock())) //
				.activate(MyConfig.create() //
						.setId(HP_ID) //
						.setThermalPower(4000) //
						.setCop(3.5f) //
						.setMinRuntime(0) //
						.build());
	}

	private static ComponentTest activateModulating(SimulatorHeatPumpReactingImpl sut) throws Exception {
		return new ComponentTest(sut) //
				.addReference("componentManager", new DummyComponentManager(createDummyClock())) //
				.activate(MyConfig.create() //
						.setId(HP_ID) //
						.setThermalPower(8000) //
						.setModulating(true) //
						.setMinThermalPower(2000) //
						.setCop(3.5f) //
						.setMinRuntime(0) //
						.build());
	}

	private static DummyThermalEss storage(int temperatureDeciCelsius) {
		return new DummyThermalEss("tess0") //
				.withMinTemperature(400) //
				.withTargetTemperature(800) //
				.withTemperature(temperatureDeciCelsius);
	}

	/**
	 * A TestCase asserting that the heat pump produces no power, e.g. while
	 * STARTING (diverter valve positioning) or STOPPED.
	 *
	 * @return the {@link TestCase}
	 */
	private static TestCase expectNoPower() {
		return new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, 0) //
				.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, 0);
	}

	@Test
	public void testRejectsCopBelowOne() throws Exception {
		var sut = new SimulatorHeatPumpReactingImpl();
		try {
			new ComponentTest(sut) //
					.addReference("componentManager", new DummyComponentManager(createDummyClock())) //
					.activate(MyConfig.create() //
							.setId(HP_ID) //
							.setThermalPower(4000) //
							.setCop(0.9f) //
							.build());
			fail("Expected an exception for COP < 1");
		} catch (Exception e) {
			// The test framework wraps the activate() exception in reflection layers.
			Throwable cause = e;
			while (cause.getCause() != null) {
				cause = cause.getCause();
			}
			assertTrue(cause instanceof OpenemsException);
		}
	}

	/*
	 * On/off operation (modulating = false).
	 */

	@Test
	public void testTurnsOnWhenBelowMinTemperature() throws Exception {
		var tess = new DummyThermalEss("tess0") //
				.withMinTemperature(400) //
				.withTargetTemperature(700) //
				.withTemperature(300); // below min; flow at the rating point W35

		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(tess);

		activateOnOff(sut) //
				// One STARTING cycle: the diverter valve positions, no heat yet
				.next(expectNoPower()) //
				.next(new TestCase() //
						.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, 4000) //
						.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, Math.round(4000 / 3.5f)) //
						.output(HP_ID, HeatPump.ChannelId.COP, 3.5f) //
						// COP is mirrored to Thermal Efficiency in percent
						.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_EFFICIENCY, 350) //
						// Flow runs 5 K above the storage temperature
						.output(HP_ID, HeatPump.ChannelId.SUPPLY_TEMPERATURE, 350) //
						.output(HP_ID, HeatPump.ChannelId.RETURN_TEMPERATURE, 300));
	}

	@Test
	public void testTurnsOffWhenAboveTargetTemperature() throws Exception {
		var tess = new DummyThermalEss("tess0") //
				.withMinTemperature(400) //
				.withTargetTemperature(700) //
				.withTemperature(750); // above target

		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(tess);

		activateOnOff(sut) //
				.next(expectNoPower());
	}

	@Test
	public void testConstantThermalPowerRisingElectricalPower() throws Exception {
		var copHot = (float) ESTIMATOR.estimateCop(sink(650), 1.0);
		var electricalHot = Math.round(4000 / copHot);
		// Sanity: a hotter buffer draws more electrical power for the same heat output
		assertTrue(electricalHot > Math.round(4000 / 3.5f));

		var tess = new DummyThermalEss("tess0") //
				.withMinTemperature(400) //
				.withTargetTemperature(800) //
				.withTemperature(300); // at the rating point (storage 30.0 °C, flow 35 °C)

		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(tess);

		var test = activateOnOff(sut) //
				.next(expectNoPower()); // STARTING

		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, 4000) //
				.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, Math.round(4000 / 3.5f)) //
				.output(HP_ID, HeatPump.ChannelId.COP, 3.5f));

		// Hold running while the buffer heats up: thermal output stays, electrical rises
		TestUtils.withValue(tess, ThermalEss.ChannelId.TEMPERATURE, 650);
		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, 4000) //
				.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, electricalHot) //
				.output(HP_ID, HeatPump.ChannelId.COP, copHot));
	}

	@Test
	public void testOnOffFollowsRequestCoveringItsDraw() throws Exception {
		var tess = new DummyThermalEss("tess0") //
				.withMinTemperature(400) //
				.withTargetTemperature(700) //
				.withMaxTemperature(800) //
				.withTemperature(720); // above soft target, below hardware max

		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(tess);

		var test = activateOnOff(sut);

		var cop = (float) ESTIMATOR.estimateCop(sink(720), 1.0);
		var electricalDraw = Math.round(4000 / cop);

		// A surplus covering the full electrical draw charges beyond the soft
		// target temperature, up to the hardware maximum
		sut.applyPower(tess, electricalDraw + 100);
		test.next(expectNoPower()); // STARTING
		sut.applyPower(tess, electricalDraw + 100);
		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, electricalDraw) //
				.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, 4000));

		// A surplus below the full electrical draw cannot run an on/off device
		sut.applyPower(tess, electricalDraw - 100);
		test.next(expectNoPower());

		// The hardware maximum temperature stops any request
		TestUtils.withValue(tess, ThermalEss.ChannelId.TEMPERATURE, 800);
		sut.applyPower(tess, 10000);
		test.next(expectNoPower());
	}

	@Test
	public void testStratifiedDhwPriority() throws Exception {
		var dhw = new DummyThermalEss("tess1") //
				.withMinTemperature(450) //
				.withTargetTemperature(550) //
				.withTemperature(400); // below its minimum
		var buffer = new DummyThermalEss("tess2") //
				.withMinTemperature(300) //
				.withTargetTemperature(450) //
				.withTemperature(250); // below its minimum

		var sut = new SimulatorHeatPumpReactingImpl();
		// Registration order is reversed to the expected priority order
		sut.bindThermalStorage(buffer);
		sut.bindThermalStorage(dhw);

		var test = activateOnOff(sut) //
				.next(expectNoPower()); // STARTING

		// Both layers demand heat: the DHW layer (highest MinTemperature) wins
		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, 4000));
		assertEquals(dhw, sut.getThermalStorage());

		// The DHW layer is satisfied: the valve repositions to the buffer layer
		TestUtils.withValue(dhw, ThermalEss.ChannelId.TEMPERATURE, 560);
		test.next(expectNoPower()); // STARTING again for the valve switch
		assertEquals(buffer, sut.getThermalStorage());
		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, 4000));

		// The buffer layer is satisfied as well: switch off
		TestUtils.withValue(buffer, ThermalEss.ChannelId.TEMPERATURE, 460);
		test.next(expectNoPower());
		assertNull(sut.getThermalStorage());
	}

	@Test
	public void testFrostProtectionTurnsOn() throws Exception {
		var tess = new DummyThermalEss("tess0") //
				.withMinTemperature(400) //
				.withTargetTemperature(700) //
				.withMaxTemperature(800) //
				.withTemperature(300); // below hard minimum

		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(tess);

		activateOnOff(sut) //
				.next(expectNoPower()) // STARTING
				.next(new TestCase() //
						.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, Math.round(4000 / 3.5f)) //
						.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, 4000));
	}

	/*
	 * Modulating operation (modulating = true).
	 */

	@Test
	public void testSurplusBelowMinimumStaysOff() throws Exception {
		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(storage(500));
		var test = activateModulating(sut);

		sut.getTargetActivePowerChannel().setNextWriteValue(MIN_ACTIVE - 100);
		test.next(expectNoPower());
	}

	@Test
	public void testFollowsSurplusWithinRange() throws Exception {
		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(storage(500));
		var test = activateModulating(sut);

		var cop = (float) ESTIMATOR.estimateCop(sink(500), (double) 1500 / NOMINAL_ACTIVE);
		final var thermal = Math.round(1500 * cop);

		sut.getTargetActivePowerChannel().setNextWriteValue(1500);
		test.next(expectNoPower()); // STARTING
		sut.getTargetActivePowerChannel().setNextWriteValue(1500);
		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, 1500) //
				.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, thermal) //
				.output(HP_ID, HeatPump.ChannelId.COP, cop));
	}

	@Test
	public void testClampsSurplusToNominal() throws Exception {
		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(storage(500));
		var test = activateModulating(sut);

		var cop = (float) ESTIMATOR.estimateCop(sink(500), 1.0);
		final var thermal = Math.round(NOMINAL_ACTIVE * cop);

		sut.getTargetActivePowerChannel().setNextWriteValue(50000);
		test.next(expectNoPower()); // STARTING
		sut.getTargetActivePowerChannel().setNextWriteValue(50000);
		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, NOMINAL_ACTIVE) //
				.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, thermal));
	}

	@Test
	public void testHysteresisKeepsRunningNearMinimum() throws Exception {
		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(storage(500));
		var test = activateModulating(sut);

		// Start well above the minimum
		sut.getTargetActivePowerChannel().setNextWriteValue(1000);
		test.next(expectNoPower()); // STARTING
		sut.getTargetActivePowerChannel().setNextWriteValue(1000);
		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, 1000));

		// Surplus drops just below the minimum but within the hysteresis band: keep
		// running, clamped up to the minimum modulation
		sut.getTargetActivePowerChannel().setNextWriteValue(MIN_ACTIVE - 100);
		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, MIN_ACTIVE));

		// Surplus drops clearly below the hysteresis band: switch off
		sut.getTargetActivePowerChannel().setNextWriteValue(100);
		test.next(expectNoPower());
	}

	@Test
	public void testFrostProtectionForcesFullPower() throws Exception {
		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(storage(350)); // below the hard minimum
		var test = activateModulating(sut);

		// No surplus offered; frost protection forces full power
		test.next(expectNoPower()); // STARTING
		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, NOMINAL_ACTIVE));
	}

	@Test
	public void testRequestExpiresWithCycle() throws Exception {
		var buffer = storage(500);

		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(buffer);
		var test = activateModulating(sut);

		sut.applyPower(buffer, 1500);
		test.next(expectNoPower()); // STARTING
		sut.applyPower(buffer, 1500);
		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, 1500));

		// No new request: the heat pump switches off
		test.next(expectNoPower());
	}

	@Test
	public void testAnonymousChannelRequestServesHighestPriority() throws Exception {
		var dhw = new DummyThermalEss("tess1") //
				.withMinTemperature(450) //
				.withTargetTemperature(550) //
				.withMaxTemperature(650) //
				.withTemperature(560); // satisfied
		var buffer = new DummyThermalEss("tess2") //
				.withMinTemperature(300) //
				.withTargetTemperature(450) //
				.withTemperature(400); // satisfied

		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(buffer);
		sut.bindThermalStorage(dhw);
		var test = activateModulating(sut);

		// A direct channel write is attributed to the highest-priority storage
		sut.getTargetActivePowerChannel().setNextWriteValue(2000);
		test.next(expectNoPower()); // STARTING
		sut.getTargetActivePowerChannel().setNextWriteValue(2000);
		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, 2000));
		assertEquals(dhw, sut.getThermalStorage());
	}

	@Test
	public void testForeignRequestIgnored() throws Exception {
		var buffer = storage(500);
		var foreign = new DummyThermalEss("tess9") //
				.withMinTemperature(450) //
				.withTargetTemperature(550) //
				.withTemperature(400);

		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(buffer);
		var test = activateModulating(sut);

		// Requests of storages not served by this heat pump are ignored
		sut.applyPower(foreign, 1500);
		test.next(expectNoPower());
	}

	@Test
	public void testRequestChargesBufferLayerBeyondTarget() throws Exception {
		var dhw = new DummyThermalEss("tess1") //
				.withMinTemperature(450) //
				.withTargetTemperature(550) //
				.withMaxTemperature(650) //
				.withTemperature(560); // satisfied
		var buffer = new DummyThermalEss("tess2") //
				.withMinTemperature(300) //
				.withTargetTemperature(450) //
				.withMaxTemperature(700) //
				.withTemperature(500); // above its soft target, below its hard max

		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(dhw);
		sut.bindThermalStorage(buffer);
		var test = activateModulating(sut);

		// A surplus controller targets the buffer layer explicitly: the request
		// heats beyond the soft target temperature
		sut.applyPower(buffer, 1500);
		test.next(expectNoPower()); // STARTING
		sut.applyPower(buffer, 1500);
		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, 1500));
		assertEquals(buffer, sut.getThermalStorage());

		// The hard maximum temperature of the layer stops further requests
		TestUtils.withValue(buffer, ThermalEss.ChannelId.TEMPERATURE, 700);
		sut.applyPower(buffer, 1500);
		test.next(expectNoPower());
	}

	@Test
	public void testMinimumRuntimeHoldsModulatingAtMinimum() throws Exception {
		final var clock = createDummyClock();
		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(storage(500));
		var test = new ComponentTest(sut) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.activate(MyConfig.create() //
						.setId(HP_ID) //
						.setThermalPower(8000) //
						.setModulating(true) //
						.setMinThermalPower(2000) //
						.setCop(3.5f) //
						.setMinRuntime(15) //
						.build());

		sut.getTargetActivePowerChannel().setNextWriteValue(1500);
		test.next(expectNoPower()); // STARTING
		sut.getTargetActivePowerChannel().setNextWriteValue(1500);
		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, 1500));

		// The request ended, but the compressor must finish its minimum runtime:
		// it holds at the minimum modulation
		var copHold = (float) ESTIMATOR.estimateCop(sink(500), (double) MIN_ACTIVE / NOMINAL_ACTIVE);
		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, MIN_ACTIVE) //
				.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, Math.round(MIN_ACTIVE * copHold)));

		// After the minimum runtime the compressor shuts down (STOPPING)
		test.next(new TestCase() //
				.timeleap(clock, 16, ChronoUnit.MINUTES) //
				.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, 0) //
				.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, 0));
	}

	@Test
	public void testMinimumRuntimeChargesOnOffBeyondTarget() throws Exception {
		final var clock = createDummyClock();
		var tess = new DummyThermalEss("tess0") //
				.withMinTemperature(400) //
				.withTargetTemperature(700) //
				.withMaxTemperature(800) //
				.withTemperature(300); // below min; autonomous start

		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(tess);
		var test = new ComponentTest(sut) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.activate(MyConfig.create() //
						.setId(HP_ID) //
						.setThermalPower(4000) //
						.setCop(3.5f) //
						.setMinRuntime(15) //
						.build());

		test.next(expectNoPower()); // STARTING
		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, 4000));

		// The storage reached its off point, but the compressor must finish its
		// minimum runtime: it keeps charging beyond the soft target at full power
		TestUtils.withValue(tess, ThermalEss.ChannelId.TEMPERATURE, 750);
		var copHot = (float) ESTIMATOR.estimateCop(sink(750), 1.0);
		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, 4000) //
				.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, Math.round(4000 / copHot)));

		// The hardware maximum temperature stops the compressor even within the
		// minimum runtime
		TestUtils.withValue(tess, ThermalEss.ChannelId.TEMPERATURE, 800);
		test.next(expectNoPower());
	}

	/*
	 * Operation on a simulated storage tank.
	 */

	/**
	 * A storage tank with settable temperatures.
	 */
	private static class DummyStorageTank extends AbstractOpenemsComponent implements SimulatorStorageTank {

		private final String thermalEssId;

		DummyStorageTank(String id, String thermalEssId, int sensor, int top, int returnTemperature) {
			super(//
					OpenemsComponent.ChannelId.values(), //
					SimulatorStorageTank.ChannelId.values() //
			);
			super.activate(null, id, "", true);
			this.thermalEssId = thermalEssId;
			TestUtils.withValue(this, SimulatorStorageTank.ChannelId.SENSOR_TEMPERATURE, sensor);
			TestUtils.withValue(this, SimulatorStorageTank.ChannelId.TOP_TEMPERATURE, top);
			TestUtils.withValue(this, SimulatorStorageTank.ChannelId.RETURN_TEMPERATURE, returnTemperature);
		}

		@Override
		public String getThermalEssId() {
			return this.thermalEssId;
		}

		@Override
		public void handleEvent(Event event) {
		}
	}

	@Test
	public void testSwitchesOnTankSensorAndHeatsTankReturn() throws Exception {
		// The estimated mean of the storage is fine, but the immersion sleeve the
		// two-point control switches on is below the minimum
		var tess = new DummyThermalEss("tess0") //
				.withMinTemperature(400) //
				.withTargetTemperature(700) //
				.withMaxTemperature(800) //
				.withTemperature(600);
		var tank = new DummyStorageTank("tank0", "tess0", 350, 650, 300);

		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(tess);
		sut.addStorageTank(tank);

		var test = activateOnOff(sut) //
				.next(expectNoPower()) // STARTING
				// The flow runs the spread above the water returning from the tank
				.next(new TestCase() //
						.output(HP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, 4000) //
						.output(HP_ID, HeatPump.ChannelId.SUPPLY_TEMPERATURE, 350) //
						.output(HP_ID, HeatPump.ChannelId.RETURN_TEMPERATURE, 300));

		// The top of the tank reaches the hardware maximum, although the immersion
		// sleeve is still below the off point
		TestUtils.withValue(tank, SimulatorStorageTank.ChannelId.TOP_TEMPERATURE, 800);
		test.next(expectNoPower());
	}

	@Test
	public void testMinViolationDisplacesRequest() throws Exception {
		var dhw = new DummyThermalEss("tess1") //
				.withMinTemperature(450) //
				.withTargetTemperature(550) //
				.withTemperature(400); // below its minimum
		var buffer = new DummyThermalEss("tess2") //
				.withMinTemperature(300) //
				.withTargetTemperature(450) //
				.withTemperature(400); // satisfied

		var sut = new SimulatorHeatPumpReactingImpl();
		sut.bindThermalStorage(dhw);
		sut.bindThermalStorage(buffer);
		var test = activateModulating(sut);

		// Comfort protection of the DHW layer displaces the explicit request for
		// the buffer layer and runs at nominal power
		sut.applyPower(buffer, 1500);
		test.next(expectNoPower()); // STARTING
		test.next(new TestCase() //
				.output(HP_ID, SymmetricHeating.ChannelId.ACTIVE_POWER, NOMINAL_ACTIVE));
		assertEquals(dhw, sut.getThermalStorage());
	}
}
