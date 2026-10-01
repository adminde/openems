package io.openems.edge.controller.ess.timeofusetariff;

import static io.openems.common.test.TestUtils.createDummyClock;
import static io.openems.edge.controller.ess.timeofusetariff.ControlMode.CHARGE_CONSUMPTION;
import static io.openems.edge.controller.ess.timeofusetariff.ControlMode.DISCHARGE_TO_GRID;
import static io.openems.edge.controller.ess.timeofusetariff.Mode.AUTOMATIC;
import static io.openems.edge.controller.ess.timeofusetariff.StateMachine.BALANCING;
import static io.openems.edge.controller.ess.timeofusetariff.StateMachine.DELAY_CHARGE;
import static io.openems.edge.controller.ess.timeofusetariff.StateMachine.DELAY_DISCHARGE;
import static io.openems.edge.controller.ess.timeofusetariff.StateMachine.DISCHARGE_GRID;
import static org.junit.Assert.assertEquals;

import java.time.Clock;
import java.util.List;

import org.junit.Test;

import io.openems.common.jsonrpc.base.JsonrpcRequest;
import io.openems.common.jsonrpc.base.JsonrpcResponse;
import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.jsonapi.Call;
import io.openems.edge.common.sum.DummySum;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.AbstractDummyOpenemsComponent;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.DummyMeta;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.energy.api.EnergyScheduler;
import io.openems.edge.energy.api.Version;
import io.openems.edge.energy.api.test.EnergyScheduleTester;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.test.DummyManagedSymmetricEss;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.test.DummyTimedata;
import io.openems.edge.timeofusetariff.test.DummyTariffGridSellProvider;
import io.openems.edge.timeofusetariff.test.DummyTariffManager;
import io.openems.edge.timeofusetariff.test.DummyTimeOfUseTariffProvider;

public class TimeOfUseTariffControllerImplTest {

	private static class DummyEnergyScheduler extends AbstractDummyOpenemsComponent<DummyEnergyScheduler>
			implements EnergyScheduler {

		private final Version version;

		public DummyEnergyScheduler(Version version) {
			super("_energy", "_energy", //
					OpenemsComponent.ChannelId.values(), //
					Controller.ChannelId.values());
			this.version = version;
		}

		@Override
		public JsonrpcResponse handleGetScheduleRequestV1(Call<JsonrpcRequest, JsonrpcResponse> call, String id) {
			return null;
		}

		@Override
		public Version getImplementationVersion() {
			return this.version;
		}

		@Override
		protected DummyEnergyScheduler self() {
			return this;
		}

	}

	@Test
	public void test() throws Exception {
		final var clock = createDummyClock();
		create(clock, Version.V2_ENERGY_SCHEDULABLE, //
				new DummyManagedSymmetricEss("ess0") //
						.withSoc(60) //
						.withCapacity(10000), //
				new DummyTimedata("timedata0")) //
				.deactivate();
	}

	@Test
	public void testActiveModesWithTimeOfUseSellPrice() throws Exception {
		final var clock = createDummyClock();
		var sut = create(clock, Version.V2_ENERGY_SCHEDULABLE, DISCHARGE_TO_GRID, //
				new DummyMeta().withIsEssChargeFromGridAllowed(false), //
				DummyTariffGridSellProvider.fromQuarterlyGridSellPrices(clock, 100.0, 120.0), //
				new DummyManagedSymmetricEss("ess0") //
						.withSoc(60) //
						.withCapacity(10000), //
				new DummyTimedata("timedata0"));

		var esh = sut.getEnergyScheduleHandler();
		EnergyScheduleTester.from(esh);

		// A sell price brings DELAY_CHARGE along, CHARGE_GRID follows the permission.
		assertEquals(List.of(BALANCING, DELAY_DISCHARGE, DISCHARGE_GRID, DELAY_CHARGE), //
				esh.modes().streamForOptimizer().toList());
		sut.deactivate();
	}

	@Test
	public void testActiveModesWithoutSellPrice() throws Exception {
		final var clock = createDummyClock();
		var sut = create(clock, Version.V2_ENERGY_SCHEDULABLE, DISCHARGE_TO_GRID, //
				new DummyMeta().withIsEssChargeFromGridAllowed(false), //
				DummyTariffGridSellProvider.empty(clock), //
				new DummyManagedSymmetricEss("ess0") //
						.withSoc(60) //
						.withCapacity(10000), //
				new DummyTimedata("timedata0"));

		var esh = sut.getEnergyScheduleHandler();
		EnergyScheduleTester.from(esh);

		// Without a sell price the control mode keeps its stock set.
		assertEquals(List.of(BALANCING, DELAY_DISCHARGE, DISCHARGE_GRID), //
				esh.modes().streamForOptimizer().toList());
		sut.deactivate();
	}

	/**
	 * Creates a {@link TimeOfUseTariffControllerImpl} instance.
	 *
	 * @param clock    a {@link Clock}
	 * @param version  the {@link EnergyScheduler} implementation {@link Version}
	 * @param ess      the {@link SymmetricEss}
	 * @param timedata the {@link Timedata}
	 * @return the object
	 * @throws Exception on error
	 */
	public static TimeOfUseTariffControllerImpl create(Clock clock, Version version, SymmetricEss ess,
			Timedata timedata) throws Exception {
		return create(clock, version, CHARGE_CONSUMPTION, new DummyMeta(), DummyTariffGridSellProvider.empty(clock),
				ess, timedata);
	}

	/**
	 * Creates a {@link TimeOfUseTariffControllerImpl} instance.
	 *
	 * @param clock                  a {@link Clock}
	 * @param version                the {@link EnergyScheduler} implementation
	 *                               {@link Version}
	 * @param controlMode            the {@link ControlMode}
	 * @param meta                   the {@link DummyMeta}
	 * @param tariffGridSellProvider the {@link DummyTariffGridSellProvider}
	 * @param ess                    the {@link SymmetricEss}
	 * @param timedata               the {@link Timedata}
	 * @return the object
	 * @throws Exception on error
	 */
	public static TimeOfUseTariffControllerImpl create(Clock clock, Version version, ControlMode controlMode,
			DummyMeta meta, DummyTariffGridSellProvider tariffGridSellProvider, SymmetricEss ess, Timedata timedata)
			throws Exception {
		var componentManager = new DummyComponentManager(clock);
		var sum = new DummySum();
		var tariffManager = new DummyTariffManager()//
				.withTariffGridBuyProvider(DummyTimeOfUseTariffProvider.empty(clock))//
				.withTariffGridSellProvider(tariffGridSellProvider);
		var energyScheduler = new DummyEnergyScheduler(version);

		var sut = new TimeOfUseTariffControllerImpl();
		new ControllerTest(sut) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", componentManager) //
				.addReference("energyScheduler", energyScheduler) //
				.addReference("timedata", timedata) //
				.addReference("tariffManager", tariffManager) //
				.addReference("sum", sum) //
				.addReference("ess", ess) //
				.addReference("meta", meta)//
				.activate(MyConfig.create() //
						.setId("ctrl0") //
						.setEnabled(true) //
						.setEssId("ess0") //
						.setMode(AUTOMATIC) //
						.setManualMode(BALANCING) //
						.setControlMode(controlMode) //
						.setEssMaxChargePower(5000) //
						.setMaxChargePowerFromGrid(10000) //
						.build()) //
				.next(new TestCase());
		return sut;
	}
}
