package io.openems.edge.controller.ess.timeofusetariff;

import static io.openems.edge.controller.ess.timeofusetariff.EnergyScheduler.applyDelayCharge;
import static io.openems.edge.controller.ess.timeofusetariff.EnergyScheduler.applyDischargeConsumption;
import static io.openems.edge.controller.ess.timeofusetariff.EnergyScheduler.applyLimitCharge;
import static io.openems.edge.controller.ess.timeofusetariff.EnergyScheduler.buildEnergyScheduleHandler;
import static io.openems.edge.controller.ess.timeofusetariff.StateMachine.DELAY_CHARGE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.common.collect.ImmutableList;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.controller.test.DummyController;
import io.openems.edge.energy.api.handler.Fitness;
import io.openems.edge.energy.api.simulation.EnergyFlow;
import io.openems.edge.energy.api.simulation.GlobalScheduleContext;
import io.openems.edge.energy.api.test.EnergyScheduleTester;

class EnergySchedulerTest {

	@Test
	void testNull() {
		var esh = buildEnergyScheduleHandler(new DummyController("ctrl0"), () -> null);
		var t = EnergyScheduleTester.from(esh);
		var t0 = t.simulatePeriod(0 /* BALANCING */);
		assertEquals(106 /* fallback to balancing */, t0.ef().solve().getEss());
	}

	@Test
	void testChargeConsumption() {
		var esh = buildEnergyScheduleHandler(new DummyController("ctrl0"),
				() -> new EnergyScheduler.Config(ControlMode.CHARGE_CONSUMPTION.modes, null, null));
		var t = EnergyScheduleTester.from(esh);

		// Initial Population: DELAY_DISCHARGE and CHARGE_GRID
		var ip = t.perEsh.get(0).initialPopulation();
		assertEquals(4, ip.size());
		assertEquals(//
				"[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]", //
				Arrays.toString(ip.get(0).modeIndexes()));
		assertEquals(//
				"[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]", //
				Arrays.toString(ip.get(1).modeIndexes()));
		assertEquals(//
				"[0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]", //
				Arrays.toString(ip.get(2).modeIndexes()));
		assertEquals(//
				"[0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]", //
				Arrays.toString(ip.get(3).modeIndexes()));

		assertEquals(106, t.simulatePeriod(0 /* BALANCING */).ef().solve().getEss());
		assertEquals(0, t.simulatePeriod(1 /* DELAY_DISCHARGE */).ef().solve().getEss());
		assertEquals(-1327, t.simulatePeriod(2 /* CHARGE_GRID */).ef().solve().getEss());
	}

	@Test
	void testDelayDischarge() {
		var esh = buildEnergyScheduleHandler(new DummyController("ctrl0"),
				() -> new EnergyScheduler.Config(ControlMode.DELAY_DISCHARGE.modes, null, null));
		var t = EnergyScheduleTester.from(esh);

		// Initial Population: DELAY_DISCHARGE only
		var ip = t.perEsh.get(0).initialPopulation();
		assertEquals(2, ip.size());
		assertEquals(//
				"[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]", //
				Arrays.toString(ip.get(0).modeIndexes()));
		assertEquals(//
				"[0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]", //
				Arrays.toString(ip.get(1).modeIndexes()));

		assertEquals(106, t.simulatePeriod(0 /* BALANCING */).ef().solve().getEss());
		assertEquals(0, t.simulatePeriod(1 /* DELAY_DISCHARGE */).ef().solve().getEss());
	}

	@Test
	void testDischargeToGrid() {
		var esh = buildEnergyScheduleHandler(new DummyController("ctrl0"),
				() -> new EnergyScheduler.Config(ControlMode.DISCHARGE_TO_GRID.modes, null, null));
		var t = EnergyScheduleTester.from(esh);

		// Initial Population: DELAY_DISCHARGE, CHARGE_GRID and DISCHARGE_GRID
		var ip = t.perEsh.get(0).initialPopulation();
		assertEquals(5, ip.size());
	}

	@Test
	void testDischargeToGrid_aboveTheFloor() {
		var esh = buildEnergyScheduleHandler(new DummyController("ctrl0"),
				() -> new EnergyScheduler.Config(ControlMode.DISCHARGE_TO_GRID.modes, null, null));
		var t = EnergyScheduleTester.from(esh);

		// 5 kWh of 22 kWh. Half the capacity per hour is 11 kW, 2750 Wh per quarter,
		// on top of the 106 Wh the consumption takes.
		var t0 = t.simulatePeriod(3 /* DISCHARGE_GRID */);
		assertEquals(0, t0.fitness().build().hardConstraintViolations());
		assertEquals(2856, t0.ef().solve().getEss());
	}

	@Test
	void testDischargeToGrid_atTheFloor() throws OpenemsException {
		var esh = buildEnergyScheduleHandler(new DummyController("ctrl0"),
				() -> new EnergyScheduler.Config(ControlMode.DISCHARGE_TO_GRID.modes, null, null));
		var t = EnergyScheduleTester.from(esh);

		// Leave 220 Wh, the one percent floor of the 22 kWh storage.
		var gsc = GlobalScheduleContext.from(t.goc);
		gsc.ess.calculateInitialEnergy(4780);
		var period = t.goc.periods().get(0);
		var ef = EnergyFlow.Model.from(gsc, period);
		var fitness = Fitness.builder();

		esh.simulate(period, gsc, null, ef, 3 /* DISCHARGE_GRID */, fitness, false);

		// The period is rejected and only the consumption is served.
		assertEquals(1, fitness.build().hardConstraintViolations());
		assertEquals(106, ef.solve().getEss());
	}

	@Test
	void testDelayCharge_initialPopulation() {
		var esh = buildEnergyScheduleHandler(new DummyController("ctrl0"),
				() -> new EnergyScheduler.Config(withDelayCharge(ControlMode.DISCHARGE_TO_GRID), null, null));
		var t = EnergyScheduleTester.from(esh);

		// One more seed: the surplus stays with the grid and is stored only in the
		// two price valleys. It sorts last, after the seeds that start with BALANCING.
		var ip = t.perEsh.get(0).initialPopulation();
		assertEquals(6, ip.size());
		var expected = new int[t.goc.periods().size()];
		Arrays.fill(expected, 5 /* DELAY_CHARGE */);
		expected[3] = 0 /* BALANCING */;
		expected[15] = 0 /* BALANCING */;
		assertArrayEquals(expected, ip.get(5).modeIndexes());
	}

	@Test
	void testDelayCharge_leavesSurplusToTheGrid() {
		var esh = buildEnergyScheduleHandler(new DummyController("ctrl0"),
				() -> new EnergyScheduler.Config(withDelayCharge(ControlMode.DISCHARGE_TO_GRID), null, null));
		var t = EnergyScheduleTester.from(esh);

		// The hour from 07:00 produces 1925 Wh against 268 Wh of consumption.
		assertEquals(-1657, t.simulatePeriodIndex(25, 0 /* BALANCING */).ef().solve().getEss());
		var t25 = t.simulatePeriodIndex(25, 5 /* DELAY_CHARGE */);
		assertEquals(0, t25.fitness().build().hardConstraintViolations());
		assertEquals(0, t25.ef().solve().getEss());
		assertEquals(-1657, t25.ef().solve().getGrid());

		// With a deficit DELAY_CHARGE still serves the consumption.
		assertEquals(106, t.simulatePeriodIndex(0, 5 /* DELAY_CHARGE */).ef().solve().getEss());
	}

	private static ImmutableList<StateMachine> withDelayCharge(ControlMode controlMode) {
		return ImmutableList.<StateMachine>builder() //
				.addAll(controlMode.modes) //
				.add(DELAY_CHARGE) //
				.build();
	}

	@Nested
	@ExtendWith(MockitoExtension.class)
	@DisplayName("applyDelayCharge()")
	class ApplyDelayChargeTest {

		@Mock
		private EnergyFlow.Model model;

		@Test
		void shouldDelayCharge_whenSurplus() {
			when(this.model.getSurplus()).thenReturn(1000);
			applyDelayCharge(this.model);
			verify(this.model).setEss(0);
		}

		@Test
		void shouldDischarge_whenNoSurplus() {
			when(this.model.getSurplus()).thenReturn(-1000);
			applyDelayCharge(this.model);
			verify(this.model).setEss(1000);
		}

		@Test
		void shouldDelayCharge_whenExactlyZeroSurplus() {
			when(this.model.getSurplus()).thenReturn(0);
			applyDelayCharge(this.model);
			verify(this.model).setEss(0);
		}
	}

	@Nested
	@ExtendWith(MockitoExtension.class)
	@DisplayName("applyLimitCharge()")
	class ApplyLimitChargeTest {

		@Mock
		private EnergyFlow.Model model;

		@Test
		void shouldLimitCharge_whenLimitLessThanSurplus() {
			when(this.model.getSurplus()).thenReturn(1000);
			applyLimitCharge(this.model, 500);
			verify(this.model).setEss(-500);
		}

		@Test
		void shouldChargeSurplus_whenLimitGreaterThanSurplus() {
			when(this.model.getSurplus()).thenReturn(600);
			applyLimitCharge(this.model, 1000);
			verify(this.model).setEss(-600);
		}

		@Test
		void shouldChargeSurplus_whenNoLimit() {
			when(this.model.getSurplus()).thenReturn(1000);
			applyLimitCharge(this.model, null);
			verify(this.model).setEss(-1000);
		}

		@Test
		void shouldDischarge_whenNoSurplus() {
			when(this.model.getSurplus()).thenReturn(-1000);
			applyLimitCharge(this.model, 500);
			verify(this.model).setEss(1000);
		}
	}

	@Nested
	@ExtendWith(MockitoExtension.class)
	@DisplayName("applyDischargeConsumption()")
	class ApplyDischargeConsumptionTest {

		@Mock
		private EnergyFlow.Model model;

		@Test
		void shouldDischargeConsumption() {
			when(this.model.getConsumption()).thenReturn(1000);
			applyDischargeConsumption(this.model);
			verify(this.model).setEss(1000);
		}
	}
}