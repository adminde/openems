package io.openems.edge.simulator.heat.tank;

import static io.openems.common.test.TestUtils.createDummyClock;
import static org.junit.Assert.assertTrue;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.common.types.ChannelAddress;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.heat.api.SymmetricHeating;
import io.openems.edge.heat.pump.api.HeatPump;
import io.openems.edge.heat.test.DummyManagedHeatPump;
import io.openems.edge.heat.test.DummyManagedThermalEss;
import io.openems.edge.simulator.datasource.api.SimulatorDatasource;

public class SimulatorStorageTankImplTest {

	private static final String TANK_ID = "tank0";
	private static final String TESS_ID = "tess0";
	private static final String HEAT_PUMP_ID = "hp0";

	private static final ChannelAddress SENSOR_TEMPERATURE = new ChannelAddress(TANK_ID, "SensorTemperature");
	private static final ChannelAddress RETURN_TEMPERATURE = new ChannelAddress(TANK_ID, "ReturnTemperature");
	private static final ChannelAddress TOP_TEMPERATURE = new ChannelAddress(TANK_ID, "TopTemperature");
	private static final ChannelAddress MEAN_TEMPERATURE = new ChannelAddress(TANK_ID, "MeanTemperature");

	@Test
	public void testHeatPumpChargesFromTheTop() throws Exception {
		final var clock = createDummyClock();
		var sut = new SimulatorStorageTankImpl();
		var hp = new DummyManagedHeatPump(HEAT_PUMP_ID);

		new ComponentTest(sut) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addComponent(new DummyManagedThermalEss(TESS_ID).withHeatingIds(HEAT_PUMP_ID)) //
				.addComponent(hp) //
				.activate(MyConfig.create() //
						.setId(TANK_ID) //
						.setThermalEssId(TESS_ID) //
						.setVolume(300) //
						.setInitialTemperature(40) //
						.build()) //
				.next(new TestCase() //
						.input(HEAT_PUMP_ID, SymmetricHeating.ChannelId.THERMAL_POWER, 4000) //
						.input(HEAT_PUMP_ID, HeatPump.ChannelId.SUPPLY_TEMPERATURE, 500) //
						.output(SENSOR_TEMPERATURE, 400) //
						.output(MEAN_TEMPERATURE, 400)) //
				// 4000 W for 10 minutes raise the mean of 300 L by 1.9 K; the hot water
				// stays at the top, the sensor and the return still see cold water
				.next(new TestCase() //
						.timeleap(clock, 10, ChronoUnit.MINUTES) //
						.output(MEAN_TEMPERATURE, 419) //
						.output(SENSOR_TEMPERATURE, 400) //
						.output(RETURN_TEMPERATURE, 400));

		assertTrue(sut.getTopTemperatureChannel().getNextValue().get() > 480);
	}

	@Test
	public void testConsumersDrawFromTheTop() throws Exception {
		final var clock = createDummyClock();

		new ComponentTest(new SimulatorStorageTankImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("datasource", new ConstantThermalConsumption(3000)) //
				.activate(MyConfig.create() //
						.setId(TANK_ID) //
						.setThermalEssId(TESS_ID) //
						.setVolume(300) //
						.setInitialTemperature(50) //
						.setConsumerReturnTemperature(30) //
						.build()) //
				.next(new TestCase()) //
				// 3000 W for 10 minutes lower the mean of 300 L by 1.4 K; the
				// consumer return stays at the bottom
				.next(new TestCase() //
						.timeleap(clock, 10, ChronoUnit.MINUTES) //
						.output(MEAN_TEMPERATURE, 486) //
						.output(TOP_TEMPERATURE, 500));
	}

	private static class ConstantThermalConsumption implements SimulatorDatasource {

		private final int consumption;

		public ConstantThermalConsumption(int consumption) {
			this.consumption = consumption;
		}

		@Override
		public Set<String> getKeys() {
			return Set.of("ThermalPower");
		}

		@Override
		public int getTimeDelta() {
			return 1;
		}

		@Override
		public <T> List<T> getValues(OpenemsType type, ChannelAddress channelAddress) {
			return List.of();
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T> T getValue(OpenemsType type, ChannelAddress channelAddress) {
			return (T) Integer.valueOf(this.consumption);
		}
	}
}
