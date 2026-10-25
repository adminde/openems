package io.openems.edge.heat.tess.layer;

import static io.openems.common.test.TestUtils.createDummyClock;

import java.time.temporal.ChronoUnit;

import org.junit.Test;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.heat.api.ManagedSymmetricHeating;
import io.openems.edge.heat.api.SymmetricHeating;
import io.openems.edge.heat.pump.api.HeatPump;
import io.openems.edge.heat.test.DummyManagedHeatPump;
import io.openems.edge.heat.tess.core.Connection;
import io.openems.edge.thermometer.api.Thermometer;
import io.openems.edge.thermometer.test.DummyThermometer;

public class ThermalEssLayerImplTest {

	private static final String TESS_ID = "tess0";
	private static final String SENSOR_ID = "thermometer0";
	private static final String HEAT_PUMP_ID = "hp0";

	private static final ChannelAddress TEMPERATURE = new ChannelAddress(TESS_ID, "Temperature");
	private static final ChannelAddress SOC = new ChannelAddress(TESS_ID, "Soc");
	private static final ChannelAddress CAPACITY = new ChannelAddress(TESS_ID, "Capacity");
	private static final ChannelAddress SUPPLY_TEMPERATURE = new ChannelAddress(TESS_ID, "SupplyTemperature");
	private static final ChannelAddress RETURN_TEMPERATURE = new ChannelAddress(TESS_ID, "ReturnTemperature");
	private static final ChannelAddress THERMAL_POWER = new ChannelAddress(TESS_ID, "ThermalPower");
	private static final ChannelAddress THERMAL_CHARGE_POWER = new ChannelAddress(TESS_ID, "ThermalChargePower");
	private static final ChannelAddress THERMAL_DISCHARGE_POWER = new ChannelAddress(TESS_ID,
			"ThermalDischargePower");
	private static final ChannelAddress MAX_TARGET_THERMAL_POWER = new ChannelAddress(TESS_ID,
			"MaxTargetThermalPower");

	private static TestCase heatPump(StartStop startStop, int supplyTemperature, int returnTemperature) {
		return new TestCase() //
				.input(HEAT_PUMP_ID, StartStoppable.ChannelId.START_STOP, startStop) //
				.input(HEAT_PUMP_ID, HeatPump.ChannelId.SUPPLY_TEMPERATURE, supplyTemperature) //
				.input(HEAT_PUMP_ID, HeatPump.ChannelId.RETURN_TEMPERATURE, returnTemperature);
	}

	@Test
	public void testStateFromSensorOnly() throws Exception {
		new ComponentTest(new ThermalEssLayerImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(createDummyClock())) //
				.addComponent(new DummyThermometer(SENSOR_ID)) //
				.activate(MyConfig.create() //
						.setId(TESS_ID) //
						.setVolume(300) //
						.setMinTemperature(20) //
						.setMaxTemperature(80) //
						.build()) //
				.next(new TestCase() //
						.input(SENSOR_ID, Thermometer.ChannelId.TEMPERATURE, 500) //
						.output(TEMPERATURE, 500) //
						.output(SOC, 50) //
						.output(CAPACITY, 20907)) //
				// Without a sensor reading the state is unknown
				.next(new TestCase() //
						.input(SENSOR_ID, Thermometer.ChannelId.TEMPERATURE, null) //
						.output(TEMPERATURE, null) //
						.output(SOC, null));
	}

	@Test
	public void testProfileFromSettledCirculation() throws Exception {
		final var clock = createDummyClock();
		var sut = new ThermalEssLayerImpl();
		var hp = new DummyManagedHeatPump(HEAT_PUMP_ID);
		sut.addHeating(hp);

		new ComponentTest(sut) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addComponent(new DummyThermometer(SENSOR_ID)) //
				.addComponent(hp) //
				.activate(MyConfig.create() //
						.setId(TESS_ID) //
						.setSensorHeight(50) //
						.setSupplyHeight(90) //
						.setReturnHeight(10) //
						.setHeatingIds(HEAT_PUMP_ID) //
						.build()) //
				// The return line still carries water that stood in the pipes
				.next(heatPump(StartStop.START, 600, 400) //
						.input(SENSOR_ID, Thermometer.ChannelId.TEMPERATURE, 450) //
						.output(TEMPERATURE, 450) //
						.output(SUPPLY_TEMPERATURE, null)) //
				// Settled: 40 °C at 10 %, 45 °C at 50 %, 60 °C at 90 %
				.next(new TestCase() //
						.timeleap(clock, 2, ChronoUnit.MINUTES) //
						.output(TEMPERATURE, 480) //
						.output(SUPPLY_TEMPERATURE, 600) //
						.output(RETURN_TEMPERATURE, 400)) //
				// Without circulation the pipe temperatures no longer describe the layer
				.next(heatPump(StartStop.STOP, 300, 200) //
						.output(TEMPERATURE, 480) //
						.output(RETURN_TEMPERATURE, 400));
	}

	@Test
	public void testSensorPrevailsAtSupplyHeight() throws Exception {
		final var clock = createDummyClock();
		var sut = new ThermalEssLayerImpl();
		var hp = new DummyManagedHeatPump(HEAT_PUMP_ID);
		sut.addHeating(hp);

		new ComponentTest(sut) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addComponent(new DummyThermometer(SENSOR_ID)) //
				.addComponent(hp) //
				.activate(MyConfig.create() //
						.setId(TESS_ID) //
						.setSensorHeight(90) //
						.setSupplyHeight(90) //
						.setReturnHeight(10) //
						.setHeatingIds(HEAT_PUMP_ID) //
						.build()) //
				.next(heatPump(StartStop.START, 600, 400) //
						.input(SENSOR_ID, Thermometer.ChannelId.TEMPERATURE, 550)) //
				// 40 °C at 10 %, 55 °C at 90 %
				.next(new TestCase() //
						.timeleap(clock, 2, ChronoUnit.MINUTES) //
						.output(TEMPERATURE, 475));
	}

	@Test
	public void testHeatExchangerIgnoresCoilTemperatures() throws Exception {
		final var clock = createDummyClock();
		var sut = new ThermalEssLayerImpl();
		var hp = new DummyManagedHeatPump(HEAT_PUMP_ID);
		sut.addHeating(hp);

		new ComponentTest(sut) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addComponent(new DummyThermometer(SENSOR_ID)) //
				.addComponent(hp) //
				.activate(MyConfig.create() //
						.setId(TESS_ID) //
						.setConnection(Connection.HEAT_EXCHANGER) //
						.setHeatingIds(HEAT_PUMP_ID) //
						.build()) //
				.next(heatPump(StartStop.START, 600, 400) //
						.input(SENSOR_ID, Thermometer.ChannelId.TEMPERATURE, 450)) //
				.next(new TestCase() //
						.timeleap(clock, 2, ChronoUnit.MINUTES) //
						.output(SUPPLY_TEMPERATURE, 600) //
						.output(TEMPERATURE, 450));
	}

	@Test
	public void testThermalPowerFromTemperatureChange() throws Exception {
		final var clock = createDummyClock();

		new ComponentTest(new ThermalEssLayerImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addComponent(new DummyThermometer(SENSOR_ID)) //
				.activate(MyConfig.create() //
						.setId(TESS_ID) //
						.setVolume(300) //
						.build()) //
				.next(new TestCase() //
						.input(SENSOR_ID, Thermometer.ChannelId.TEMPERATURE, 450) //
						.output(THERMAL_POWER, null)) //
				// 300 kg * 4181.3 J/(kg K) * 1 K / 300 s
				.next(new TestCase() //
						.timeleap(clock, 5, ChronoUnit.MINUTES) //
						.input(SENSOR_ID, Thermometer.ChannelId.TEMPERATURE, 460) //
						.output(THERMAL_POWER, 4181) //
						.output(THERMAL_CHARGE_POWER, 4181) //
						.output(THERMAL_DISCHARGE_POWER, 0));
	}

	@Test
	public void testSettlingCorrectsEstimateWithoutThermalPower() throws Exception {
		final var clock = createDummyClock();
		var sut = new ThermalEssLayerImpl();
		var hp = new DummyManagedHeatPump(HEAT_PUMP_ID);
		sut.addHeating(hp);

		new ComponentTest(sut) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addComponent(new DummyThermometer(SENSOR_ID)) //
				.addComponent(hp) //
				.activate(MyConfig.create() //
						.setId(TESS_ID) //
						.setHeatingIds(HEAT_PUMP_ID) //
						.build()) //
				.next(heatPump(StartStop.START, 600, 400) //
						.input(HEAT_PUMP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, 5000) //
						.input(SENSOR_ID, Thermometer.ChannelId.TEMPERATURE, 450)) //
				.next(new TestCase() //
						.timeleap(clock, 2, ChronoUnit.MINUTES) //
						.output(TEMPERATURE, 480)) //
				// The heat pump output is balanced by the heat demand of the same amount
				.next(new TestCase() //
						.timeleap(clock, 3, ChronoUnit.MINUTES) //
						.output(TEMPERATURE, 480) //
						.output(THERMAL_POWER, 0) //
						.output(THERMAL_CHARGE_POWER, 5000) //
						.output(THERMAL_DISCHARGE_POWER, 5000));
	}

	@Test
	public void testNoChargingAboveMaxTemperatureAtHottestPoint() throws Exception {
		final var clock = createDummyClock();
		var sut = new ThermalEssLayerImpl();
		var hp = new DummyManagedHeatPump(HEAT_PUMP_ID);
		sut.addHeating(hp);

		new ComponentTest(sut) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addComponent(new DummyThermometer(SENSOR_ID)) //
				.addComponent(hp) //
				.activate(MyConfig.create() //
						.setId(TESS_ID) //
						.setMaxTemperature(55) //
						.setHeatingIds(HEAT_PUMP_ID) //
						.build()) //
				.next(heatPump(StartStop.START, 600, 400) //
						.input(HEAT_PUMP_ID, ManagedSymmetricHeating.ChannelId.MAX_TARGET_ACTIVE_POWER, 2000) //
						.input(SENSOR_ID, Thermometer.ChannelId.TEMPERATURE, 450) //
						.output(MAX_TARGET_THERMAL_POWER, 2000)) //
				// The mean of 48 °C is below, but the top of the layer reaches 60 °C
				.next(new TestCase() //
						.timeleap(clock, 2, ChronoUnit.MINUTES) //
						.output(TEMPERATURE, 480) //
						.output(MAX_TARGET_THERMAL_POWER, 0));
	}
}
