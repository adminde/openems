package io.openems.edge.controller.tess.surpluscharge;

import static org.junit.Assert.assertEquals;

import java.util.Optional;

import org.junit.Test;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.edge.common.sum.DummySum;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.heat.test.DummyManagedThermalEss;

public class TessSurplusChargeControllerImplTest {

	@Test
	public void testSurplusIsRequestedAsGreaterOrEquals() throws Exception {
		var tess = new DummyManagedThermalEss("tess0");

		new ControllerTest(new TessSurplusChargeControllerImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("sum", new DummySum() //
						// feed-in of 3000 W; battery idle
						.withGridActivePower(-3000) //
						.withEssDischargePower(0)) //
				.addReference("tess", tess) //
				.activate(MyConfig.create() //
						.setId("ctrl0") //
						.setTessId("tess0") //
						.setEfficiency(3.0) //
						.build()) //
				.next(new TestCase() //
						.output("ctrl0", TessSurplusChargeController.ChannelId.SURPLUS_POWER, 3000));

		// 3000 W electrical surplus x 3.0 = 9000 W thermal request
		assertEquals(Optional.of(9000),
				tess.getTargetThermalPowerGreaterOrEqualsChannel().getNextWriteValueAndReset());
	}

	@Test
	public void testOwnChargeCountsAsSurplus() throws Exception {
		var tess = new DummyManagedThermalEss("tess0") //
				// the Heating is already charging with 6000 W thermal = 2000 W electrical
				.withThermalChargePower(6000);

		new ControllerTest(new TessSurplusChargeControllerImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("sum", new DummySum() //
						// balanced grid: the Heating consumes exactly the PV surplus
						.withGridActivePower(0) //
						.withEssDischargePower(0)) //
				.addReference("tess", tess) //
				.activate(MyConfig.create() //
						.setId("ctrl0") //
						.setTessId("tess0") //
						.setEfficiency(3.0) //
						.build()) //
				.next(new TestCase() //
						.output("ctrl0", TessSurplusChargeController.ChannelId.SURPLUS_POWER, 2000));

		assertEquals(Optional.of(6000),
				tess.getTargetThermalPowerGreaterOrEqualsChannel().getNextWriteValueAndReset());
	}

	@Test
	public void testNoSurplusWritesNothing() throws Exception {
		var tess = new DummyManagedThermalEss("tess0");

		new ControllerTest(new TessSurplusChargeControllerImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("sum", new DummySum() //
						// buying from grid and discharging the battery: no surplus
						.withGridActivePower(500) //
						.withEssDischargePower(1000)) //
				.addReference("tess", tess) //
				.activate(MyConfig.create() //
						.setId("ctrl0") //
						.setTessId("tess0") //
						.setEfficiency(3.0) //
						.build()) //
				.next(new TestCase() //
						.output("ctrl0", TessSurplusChargeController.ChannelId.SURPLUS_POWER, 0));

		assertEquals(Optional.empty(), tess.getTargetThermalPowerGreaterOrEqualsChannel().getNextWriteValue());
	}
}
