package io.openems.edge.heat.tess.cluster;

import static io.openems.edge.heat.tess.api.ThermalEss.ChannelId.CAPACITY;
import static io.openems.edge.heat.tess.api.ThermalEss.ChannelId.MAX_TEMPERATURE;
import static io.openems.edge.heat.tess.api.ThermalEss.ChannelId.MIN_TEMPERATURE;
import static io.openems.edge.heat.tess.api.ThermalEss.ChannelId.TARGET_TEMPERATURE;
import static io.openems.edge.heat.tess.api.ThermalEss.ChannelId.SOC;
import static io.openems.edge.heat.tess.api.ThermalEss.ChannelId.TEMPERATURE;
import static io.openems.edge.heat.tess.api.ThermalEss.ChannelId.THERMAL_CHARGE_ENERGY;
import static io.openems.edge.heat.tess.api.ThermalEss.ChannelId.THERMAL_DISCHARGE_ENERGY;
import static io.openems.edge.heat.tess.api.ThermalEss.ChannelId.THERMAL_POWER;

import static io.openems.edge.heat.tess.api.ManagedThermalEss.ChannelId.MAX_TARGET_THERMAL_POWER;

import org.junit.Test;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.heat.test.DummyManagedHeating;
import io.openems.edge.heat.test.DummyManagedThermalEss;
import io.openems.edge.heat.test.DummyThermalEss;
import io.openems.edge.heat.tess.api.SocAveragingMethod;

public class ThermalEssClusterImplTest {

	@Test
	public void testCluster() throws Exception {
		new ComponentTest(new ThermalEssClusterImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("addTess", new DummyThermalEss("tess1")) //
				.addReference("addTess", new DummyThermalEss("tess2")) //
				.activate(MyConfig.create() //
						.setId("tess0") //
						.setTessIds("tess1", "tess2") //
						.build())
				.next(new TestCase() //
						.input("tess1", CAPACITY, 10000) //
						.input("tess2", CAPACITY, 5000) //
						.output(CAPACITY, 15000) //
						.input("tess1", THERMAL_POWER, 2000) //
						.input("tess2", THERMAL_POWER, 1000) //
						.output(THERMAL_POWER, 3000) //
						.input("tess1", THERMAL_CHARGE_ENERGY, 100) //
						.input("tess2", THERMAL_CHARGE_ENERGY, 200) //
						.output(THERMAL_CHARGE_ENERGY, 300L) //
						.input("tess1", THERMAL_DISCHARGE_ENERGY, 10) //
						.input("tess2", THERMAL_DISCHARGE_ENERGY, 20) //
						.output(THERMAL_DISCHARGE_ENERGY, 30L) //
						// Tightest common operating band
						.input("tess1", MAX_TEMPERATURE, 900) //
						.input("tess2", MAX_TEMPERATURE, 800) //
						.output(MAX_TEMPERATURE, 800) //
						.input("tess1", TARGET_TEMPERATURE, 700) //
						.input("tess2", TARGET_TEMPERATURE, 650) //
						.output(TARGET_TEMPERATURE, 650) //
						.input("tess1", MIN_TEMPERATURE, 450) //
						.input("tess2", MIN_TEMPERATURE, 500) //
						.output(MIN_TEMPERATURE, 500) //
				);
	}

	@Test
	public void testArithmeticSoc() throws Exception {
		new ComponentTest(new ThermalEssClusterImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("addTess", new DummyThermalEss("tess1").withCapacity(10000)) //
				.addReference("addTess", new DummyThermalEss("tess2").withCapacity(5000)) //
				.activate(MyConfig.create() //
						.setId("tess0") //
						.setTessIds("tess1", "tess2") //
						.setSocAveragingMethod(SocAveragingMethod.ARITHMETIC) //
						.build())
				.next(new TestCase() //
						.input("tess1", SOC, 100) //
						.input("tess2", SOC, 0) //
						// (10000*100 + 5000*0) / 15000 ≈ 67
						.output(SOC, 67) //
				);
	}

	@Test
	public void testGeometricSoc() throws Exception {
		new ComponentTest(new ThermalEssClusterImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("addTess", new DummyThermalEss("tess1").withCapacity(10000)) //
				.addReference("addTess", new DummyThermalEss("tess2").withCapacity(5000)) //
				.activate(MyConfig.create() //
						.setId("tess0") //
						.setTessIds("tess1", "tess2") //
						.setSocAveragingMethod(SocAveragingMethod.GEOMETRIC) //
						.build())
				.next(new TestCase() //
						.input("tess1", SOC, 100) //
						.input("tess2", SOC, 0) //
						// An empty storage forces the cluster SoC to zero
						.output(SOC, 0) //
				);
	}

	@Test
	public void testDeduplicatesSharedHeating() throws Exception {
		// One heat pump (2000 W el x 3.0 = 6000 W th) serves both layers of a
		// stratified tank; each layer reports the full conditional potential.
		var componentManager = new DummyComponentManager();
		componentManager.addComponent(new DummyManagedHeating("hp0") //
				.withMaxTargetActivePower(2000) //
				.withThermalEfficiency(300F));

		new ComponentTest(new ThermalEssClusterImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", componentManager) //
				.addReference("addTess", new DummyManagedThermalEss("tess1").withHeatingIds("hp0")) //
				.addReference("addTess", new DummyManagedThermalEss("tess2").withHeatingIds("hp0")) //
				.activate(MyConfig.create() //
						.setId("tess0") //
						.setTessIds("tess1", "tess2") //
						.build())
				.next(new TestCase() //
						.input("tess1", MAX_TARGET_THERMAL_POWER, 6000) //
						.input("tess2", MAX_TARGET_THERMAL_POWER, 6000) //
						// The shared heat pump is counted once, not twice
						.output(MAX_TARGET_THERMAL_POWER, 6000)) //
				.next(new TestCase() //
						// A full layer does not unlock the heat pump; the chargeable
						// layer still does
						.input("tess1", MAX_TARGET_THERMAL_POWER, 0) //
						.output(MAX_TARGET_THERMAL_POWER, 6000)) //
				.next(new TestCase() //
						// All layers full: no thermal intake remains
						.input("tess2", MAX_TARGET_THERMAL_POWER, 0) //
						.output(MAX_TARGET_THERMAL_POWER, 0));
	}

	@Test
	public void testSumsOpaqueMembersNextToSharedHeatings() throws Exception {
		var componentManager = new DummyComponentManager();
		componentManager.addComponent(new DummyManagedHeating("hp0") //
				.withMaxTargetActivePower(2000) //
				.withThermalEfficiency(300F));

		new ComponentTest(new ThermalEssClusterImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", componentManager) //
				.addReference("addTess", new DummyManagedThermalEss("tess1").withHeatingIds("hp0")) //
				.addReference("addTess", new DummyManagedThermalEss("tess2").withHeatingIds("hp0")) //
				// A member without Heating-IDs is opaque and contributes its own value
				.addReference("addTess", new DummyManagedThermalEss("tess3")) //
				.activate(MyConfig.create() //
						.setId("tess0") //
						.setTessIds("tess1", "tess2", "tess3") //
						.build())
				.next(new TestCase() //
						.input("tess1", MAX_TARGET_THERMAL_POWER, 6000) //
						.input("tess2", MAX_TARGET_THERMAL_POWER, 6000) //
						.input("tess3", MAX_TARGET_THERMAL_POWER, 3000) //
						.output(MAX_TARGET_THERMAL_POWER, 9000));
	}

	@Test
	public void testWeightedTemperature() throws Exception {
		new ComponentTest(new ThermalEssClusterImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("addTess", new DummyThermalEss("tess1").withCapacity(9000)) //
				.addReference("addTess", new DummyThermalEss("tess2").withCapacity(1000)) //
				.activate(MyConfig.create() //
						.setId("tess0") //
						.setTessIds("tess1", "tess2") //
						.build())
				.next(new TestCase() //
						.input("tess1", TEMPERATURE, 600) //
						.input("tess2", TEMPERATURE, 400) //
						// Capacity-weighted: 0.9*600 + 0.1*400 = 580
						.output(TEMPERATURE, 580) //
				);
	}
}
