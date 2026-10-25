package io.openems.edge.controller.tess.timeofusetariff;

import static io.openems.common.test.TestUtils.createDummyClock;

import org.junit.Test;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.heat.test.DummyManagedThermalEss;
import io.openems.edge.predictor.api.test.DummyPredictorManager;
import io.openems.edge.timedata.test.DummyTimedata;
import io.openems.edge.timeofusetariff.test.DummyTariffGridSellProvider;
import io.openems.edge.timeofusetariff.test.DummyTariffManager;
import io.openems.edge.timeofusetariff.test.DummyTimeOfUseTariffProvider;

public class TessTimeOfUseTariffControllerImplTest {

	@Test
	public void test() throws Exception {
		final var clock = createDummyClock();
		final var tariffManager = new DummyTariffManager() //
				.withTariffGridBuyProvider(DummyTimeOfUseTariffProvider.empty(clock)) //
				.withTariffGridSellProvider(DummyTariffGridSellProvider.empty(clock));

		new ControllerTest(new TessTimeOfUseTariffControllerImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("tariffManager", tariffManager) //
				.addReference("predictorManager", new DummyPredictorManager()) //
				.addReference("timedata", new DummyTimedata("timedata0")) //
				.addReference("tess", new DummyManagedThermalEss("tess0") //
						.withSoc(50) //
						.withCapacity(10000) //
						.withMinTemperature(200) //
						.withTargetTemperature(600) //
						.withMaxTemperature(800) //
						.withMaxTargetThermalPower(12000)) //
				.activate(MyConfig.create() //
						.setId("ctrl0") //
						.setTessId("tess0") //
						.setEfficiency(3.0) //
						.setThermalDemand(500) //
						.build()) //
				// Without a schedule the Controller stays in NONE and writes nothing
				.next(new TestCase() //
						.output("ctrl0", TessTimeOfUseTariffController.ChannelId.STATE_MACHINE, StateMachine.NONE)) //
				.deactivate();
	}
}
