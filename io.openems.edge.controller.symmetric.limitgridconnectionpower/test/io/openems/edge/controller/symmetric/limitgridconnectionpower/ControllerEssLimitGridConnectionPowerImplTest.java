package io.openems.edge.controller.symmetric.limitgridconnectionpower;

import static io.openems.edge.common.sum.Sum.ChannelId.GRID_ACTIVE_POWER;
import static io.openems.edge.controller.symmetric.limitgridconnectionpower.ControllerEssLimitGridConnectionPower.ChannelId.ACTIVE_POWER_LOWER_LIMIT;
import static io.openems.edge.controller.symmetric.limitgridconnectionpower.ControllerEssLimitGridConnectionPower.ChannelId.ACTIVE_POWER_UPPER_LIMIT;
import static io.openems.edge.controller.symmetric.limitgridconnectionpower.ControllerEssLimitGridConnectionPower.ChannelId.GRID_EXPORT_LIMIT_EXCEEDED;
import static io.openems.edge.controller.symmetric.limitgridconnectionpower.ControllerEssLimitGridConnectionPower.ChannelId.GRID_IMPORT_LIMIT_EXCEEDED;
import static io.openems.edge.controller.symmetric.limitgridconnectionpower.ControllerEssLimitGridConnectionPower.ChannelId.STATIC_LIMIT_FALLBACK;
import static io.openems.edge.controller.symmetric.limitgridconnectionpower.ControllerEssLimitGridConnectionPowerImpl.calculateLimits;
import static io.openems.edge.ess.api.ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_GREATER_OR_EQUALS;
import static io.openems.edge.ess.api.ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_LESS_OR_EQUALS;
import static io.openems.edge.ess.api.SymmetricEss.ChannelId.ACTIVE_POWER;
import static io.openems.edge.ess.api.SymmetricEss.ChannelId.GRID_MODE;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.openems.edge.common.sum.DummySum;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.DummyMeta;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.ess.test.DummyManagedSymmetricEss;

public class ControllerEssLimitGridConnectionPowerImplTest {

	/** Hard limit for a 32 A fuse. */
	private static final int HARD_LIMIT = 22170;
	/** Hard limit minus 5 % safety buffer. */
	private static final int LIMIT = 21062;

	private static DummyMeta dummyMeta(boolean isChargeFromGridAllowed, boolean isDischargeToGridAllowed) {
		return new DummyMeta() //
				.withGridBuyHardLimit(HARD_LIMIT) //
				.withGridBuyHardLimitWithBuffer(LIMIT) //
				.withGridSellHardLimit(HARD_LIMIT) //
				.withGridSellHardLimitWithBuffer(LIMIT) //
				.withIsEssChargeFromGridAllowed(isChargeFromGridAllowed) //
				.withIsEssDischargeToGridAllowed(isDischargeToGridAllowed);
	}

	private static ControllerTest prepareTest(DummyMeta meta) throws Exception {
		return new ControllerTest(new ControllerEssLimitGridConnectionPowerImpl()) //
				.addReference("meta", meta) //
				.addReference("sum", new DummySum()) //
				.addReference("ess", new DummyManagedSymmetricEss("ess0") //
						.withGridMode(GridMode.ON_GRID)) //
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
						.output(ACTIVE_POWER_UPPER_LIMIT, 5000 + LIMIT) //
						.output(ACTIVE_POWER_LOWER_LIMIT, 5000 - LIMIT) //
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
						.output(ACTIVE_POWER_UPPER_LIMIT, null) //
						.output(ACTIVE_POWER_LOWER_LIMIT, null) //
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
}
