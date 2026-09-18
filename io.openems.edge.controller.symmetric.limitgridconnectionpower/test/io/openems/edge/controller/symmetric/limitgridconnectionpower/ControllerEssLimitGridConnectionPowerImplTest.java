package io.openems.edge.controller.symmetric.limitgridconnectionpower;

import static io.openems.edge.common.sum.Sum.ChannelId.GRID_ACTIVE_POWER;
import static io.openems.edge.controller.symmetric.limitgridconnectionpower.ControllerEssLimitGridConnectionPower.ChannelId.ACTIVE_CHARGE_POWER_LIMIT;
import static io.openems.edge.controller.symmetric.limitgridconnectionpower.ControllerEssLimitGridConnectionPower.ChannelId.ACTIVE_DISCHARGE_POWER_LIMIT;
import static io.openems.edge.controller.symmetric.limitgridconnectionpower.ControllerEssLimitGridConnectionPower.ChannelId.GRID_EXPORT_LIMIT_EXCEEDED;
import static io.openems.edge.controller.symmetric.limitgridconnectionpower.ControllerEssLimitGridConnectionPower.ChannelId.GRID_IMPORT_LIMIT_EXCEEDED;
import static io.openems.edge.controller.symmetric.limitgridconnectionpower.ControllerEssLimitGridConnectionPower.ChannelId.STATIC_LIMIT_FALLBACK;
import static io.openems.edge.controller.symmetric.limitgridconnectionpower.ControllerEssLimitGridConnectionPowerImpl.calculateLimits;
import static io.openems.edge.ess.api.ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_GREATER_OR_EQUALS;
import static io.openems.edge.ess.api.ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_LESS_OR_EQUALS;
import static io.openems.edge.ess.api.SymmetricEss.ChannelId.ACTIVE_POWER;
import static io.openems.edge.ess.api.SymmetricEss.ChannelId.GRID_MODE;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.openems.common.test.TimeLeapClock;
import io.openems.edge.common.filter.PT1Filter;
import io.openems.edge.common.sum.DummySum;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.DummyMeta;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.ess.test.DummyManagedSymmetricEss;
import io.openems.edge.ess.test.DummyPower;

public class ControllerEssLimitGridConnectionPowerImplTest {

	/** Hard limit for a 32 A fuse. */
	private static final int HARD_LIMIT = 22170;
	/** Hard limit minus 5 % safety buffer. */
	private static final int LIMIT = 21062;
	/** Time constant of the PT1 filter in [ms], as Ess.Power applies it by default. */
	private static final int FILTER_TIME_CONSTANT = PT1Filter.DEFAULT_TIME_CONSTANT;

	private static DummyMeta dummyMeta(boolean isChargeFromGridAllowed, boolean isDischargeToGridAllowed) {
		return new DummyMeta() //
				.withGridBuyHardLimit(HARD_LIMIT) //
				.withGridBuyHardLimitWithBuffer(LIMIT) //
				.withGridSellHardLimit(HARD_LIMIT) //
				.withGridSellHardLimitWithBuffer(LIMIT) //
				.withIsEssChargeFromGridAllowed(isChargeFromGridAllowed) //
				.withIsEssDischargeToGridAllowed(isDischargeToGridAllowed);
	}

	private static DummyManagedSymmetricEss dummyEss(TimeLeapClock clock, int filterTimeConstant) {
		return new DummyManagedSymmetricEss("ess0") //
				.setPower(new DummyPower().withPt1Filter(clock, filterTimeConstant)) //
				.withGridMode(GridMode.ON_GRID);
	}

	/**
	 * Prepares a test with a disabled filter.
	 *
	 * @param meta the {@link DummyMeta}
	 * @return the {@link ControllerTest}
	 * @throws Exception on error
	 */
	private static ControllerTest prepareTest(DummyMeta meta) throws Exception {
		return prepareTest(new ControllerEssLimitGridConnectionPowerImpl(), dummyEss(new TimeLeapClock(), 0), meta);
	}

	private static ControllerTest prepareTest(ControllerEssLimitGridConnectionPowerImpl sut,
			DummyManagedSymmetricEss ess, DummyMeta meta) throws Exception {
		return new ControllerTest(sut) //
				.addReference("meta", meta) //
				.addReference("sum", new DummySum()) //
				.addReference("ess", ess) //
				.activate(MyConfig.create() //
						.setId("ctrl0") //
						.setEssId("ess0") //
						.build());
	}

	@Test
	public void testPhysicalLimits() throws Exception {
		prepareTest(dummyMeta(true, true)) //
				// Import below limit
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, 5000) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, 5000 + LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, 5000 - LIMIT) //
						.output(ACTIVE_DISCHARGE_POWER_LIMIT, 5000 + LIMIT) //
						.output(ACTIVE_CHARGE_POWER_LIMIT, 5000 - LIMIT) //
						.output(STATIC_LIMIT_FALLBACK, false) //
						.output(GRID_IMPORT_LIMIT_EXCEEDED, false) //
						.output(GRID_EXPORT_LIMIT_EXCEEDED, false)) //
				// ESS charge power is removed from the grid power
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, 5000) //
						.input("ess0", ACTIVE_POWER, -3000) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, 2000 + LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, 2000 - LIMIT)) //
				// ESS discharge power is removed from the grid power
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, 5000) //
						.input("ess0", ACTIVE_POWER, 3000) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, 8000 + LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, 8000 - LIMIT)) //
				// Export above limit forces the ESS to charge the excess
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, -25000) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, -25000 + LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, -25000 - LIMIT) //
						.output(GRID_IMPORT_LIMIT_EXCEEDED, false) //
						.output(GRID_EXPORT_LIMIT_EXCEEDED, true)) //
				// Import above limit forces the ESS to discharge the excess
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, 25000) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, 25000 + LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, 25000 - LIMIT) //
						.output(GRID_IMPORT_LIMIT_EXCEEDED, true) //
						.output(GRID_EXPORT_LIMIT_EXCEEDED, false)) //
				// Exceeded warnings compare against the hard limit without buffer
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, HARD_LIMIT) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output(GRID_IMPORT_LIMIT_EXCEEDED, false)) //
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, HARD_LIMIT + 1) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output(GRID_IMPORT_LIMIT_EXCEEDED, true)) //
				.deactivate();
	}

	@Test
	public void testDischargeToGridNotAllowed() throws Exception {
		prepareTest(dummyMeta(true, false)) //
				// Import: ESS may cover the consumption but not more
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, 5000) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, 5000) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, 5000 - LIMIT)) //
				// Export: ESS must not discharge, but is not forced to charge
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, -5000) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, 0) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, -5000 - LIMIT)) //
				// Export above the physical limit still forces the ESS to charge
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, -25000) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, -25000 + LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, -25000 - LIMIT)) //
				.deactivate();
	}

	@Test
	public void testChargeFromGridNotAllowed() throws Exception {
		prepareTest(dummyMeta(false, true)) //
				// Import: ESS must not charge
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, 5000) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, 5000 + LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, 0)) //
				// Export: ESS may charge the surplus but not more
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, -5000) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, -5000 + LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, -5000)) //
				// Import above the physical limit still forces the ESS to discharge
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, 25000) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, 25000 + LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, 25000 - LIMIT)) //
				.deactivate();
	}

	@Test
	public void testStaticFallback() throws Exception {
		prepareTest(dummyMeta(true, true)) //
				// Grid power undefined
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, null) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, -LIMIT) //
						.output(STATIC_LIMIT_FALLBACK, true) //
						.output(GRID_IMPORT_LIMIT_EXCEEDED, false) //
						.output(GRID_EXPORT_LIMIT_EXCEEDED, false)) //
				// ESS power undefined
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, 5000) //
						.input("ess0", ACTIVE_POWER, null) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, -LIMIT) //
						.output(STATIC_LIMIT_FALLBACK, true)) //
				// Both defined again
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, 5000) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, 5000 + LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, 5000 - LIMIT) //
						.output(STATIC_LIMIT_FALLBACK, false)) //
				.deactivate();
	}

	@Test
	public void testStaticFallbackWithoutPermissions() throws Exception {
		prepareTest(dummyMeta(false, false)) //
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, null) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, 0) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, 0) //
						.output(STATIC_LIMIT_FALLBACK, true)) //
				.deactivate();
	}

	@Test
	public void testOffGrid() throws Exception {
		prepareTest(dummyMeta(true, true)) //
				.next(new TestCase() //
						.input("ess0", GRID_MODE, GridMode.OFF_GRID) //
						.input(GRID_ACTIVE_POWER, 5000) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, null) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, null) //
						.output(ACTIVE_DISCHARGE_POWER_LIMIT, null) //
						.output(ACTIVE_CHARGE_POWER_LIMIT, null) //
						.output(STATIC_LIMIT_FALLBACK, false)) //
				.deactivate();
	}

	@Test
	public void testUpperLimitIsNeverBelowLowerLimit() {
		final int[] loads = { -50000, -25000, -5000, -1, 0, 1, 5000, 25000, 50000 };
		final int[] limits = { 0, 150, LIMIT };
		for (var load : loads) {
			for (var importLimit : limits) {
				for (var exportLimit : limits) {
					for (var chargeAllowed : new boolean[] { false, true }) {
						for (var dischargeAllowed : new boolean[] { false, true }) {
							var result = calculateLimits(load, 0, importLimit, exportLimit, chargeAllowed,
									dischargeAllowed);
							assertTrue(result.upper() >= result.lower(), //
									"load=" + load + " import=" + importLimit + " export=" + exportLimit
											+ " charge=" + chargeAllowed + " discharge=" + dischargeAllowed
											+ " -> " + result);
						}
					}
				}
			}
		}
	}

	@Test
	public void testFilter() throws Exception {
		final var clock = new TimeLeapClock();
		prepareTest(new ControllerEssLimitGridConnectionPowerImpl(), dummyEss(clock, FILTER_TIME_CONSTANT),
				dummyMeta(true, true)) //
				// The first limits are applied unfiltered
				.next(new TestCase() //
						.input(GRID_ACTIVE_POWER, 5000) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, 5000 + LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, 5000 - LIMIT) //
						.output(ACTIVE_DISCHARGE_POWER_LIMIT, 5000 + LIMIT) //
						.output(ACTIVE_CHARGE_POWER_LIMIT, 5000 - LIMIT)) //
				// A load step is followed with the time constant, while the channels show
				// the unfiltered limits
				.next(new TestCase() //
						.timeleap(clock, 1, SECONDS) //
						.input(GRID_ACTIVE_POWER, 25000) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, 16111 + LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, 16111 - LIMIT) //
						.output(ACTIVE_DISCHARGE_POWER_LIMIT, 25000 + LIMIT) //
						.output(ACTIVE_CHARGE_POWER_LIMIT, 25000 - LIMIT) //
						.output(GRID_IMPORT_LIMIT_EXCEEDED, true)) //
				.next(new TestCase() //
						.timeleap(clock, 1, SECONDS) //
						.input(GRID_ACTIVE_POWER, 25000) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, 21049 + LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, 21049 - LIMIT)) //
				.next(new TestCase() //
						.timeleap(clock, 1, SECONDS) //
						.input(GRID_ACTIVE_POWER, 25000) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, 23244 + LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, 23244 - LIMIT)) //
				// Static limits are applied unfiltered and reset the filters
				.next(new TestCase() //
						.timeleap(clock, 1, SECONDS) //
						.input(GRID_ACTIVE_POWER, null) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, -LIMIT) //
						.output(STATIC_LIMIT_FALLBACK, true)) //
				.next(new TestCase() //
						.timeleap(clock, 1, SECONDS) //
						.input(GRID_ACTIVE_POWER, 25000) //
						.input("ess0", ACTIVE_POWER, 0) //
						.output("ess0", SET_ACTIVE_POWER_LESS_OR_EQUALS, 25000 + LIMIT) //
						.output("ess0", SET_ACTIVE_POWER_GREATER_OR_EQUALS, 25000 - LIMIT) //
						.output(STATIC_LIMIT_FALLBACK, false)) //
				.deactivate();
	}

	/**
	 * Simulates the closed loop with a grid measurement that reflects a change of
	 * the ESS power one cycle later than the ESS measurement. The ESS follows the
	 * applied lower limit, as the Power solver applies the feasible value closest
	 * to zero.
	 *
	 * @param filterTimeConstant the time constant of the PT1 filter in [ms]
	 * @return the applied lower limits of all simulated cycles
	 * @throws Exception on error
	 */
	private static List<Integer> simulateLaggingGridMeasurement(int filterTimeConstant) throws Exception {
		final var load = 30000;
		final var clock = new TimeLeapClock();
		final var ess = dummyEss(clock, filterTimeConstant);
		final var test = prepareTest(new ControllerEssLimitGridConnectionPowerImpl(), ess, dummyMeta(true, true));
		final var lowerLimits = new ArrayList<Integer>();
		var essPower = 0;
		var previousEssPower = 0;
		for (var cycle = 0; cycle < 40; cycle++) {
			test.next(new TestCase() //
					.timeleap(clock, 1, SECONDS) //
					.input(GRID_ACTIVE_POWER, load - previousEssPower) //
					.input("ess0", ACTIVE_POWER, essPower));
			var lowerLimit = ess.getSetActivePowerGreaterOrEqualsChannel().getNextWriteValue().get();
			lowerLimits.add(lowerLimit);
			previousEssPower = essPower;
			essPower = Math.max(0, lowerLimit);
		}
		test.deactivate();
		return lowerLimits;
	}

	@Test
	public void testLaggingGridMeasurementOscillatesWithoutFilter() throws Exception {
		var tail = simulateLaggingGridMeasurement(0).subList(30, 40);
		var max = tail.stream().mapToInt(Integer::intValue).max().getAsInt();
		var min = tail.stream().mapToInt(Integer::intValue).min().getAsInt();
		assertTrue(max - min >= 30000 - LIMIT, "Expected a sustained oscillation, but got " + tail);
	}

	@Test
	public void testLaggingGridMeasurementConvergesWithFilter() throws Exception {
		var tail = simulateLaggingGridMeasurement(FILTER_TIME_CONSTANT).subList(30, 40);
		for (var lowerLimit : tail) {
			assertTrue(Math.abs(lowerLimit - (30000 - LIMIT)) <= 50, "Expected convergence, but got " + tail);
		}
	}
}
