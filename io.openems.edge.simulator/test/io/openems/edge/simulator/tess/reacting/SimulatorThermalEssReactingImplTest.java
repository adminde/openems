package io.openems.edge.simulator.tess.reacting;

import static io.openems.common.test.TestUtils.createDummyClock;

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
import io.openems.edge.heat.test.DummyHeatPump;
import io.openems.edge.simulator.datasource.api.SimulatorDatasource;

public class SimulatorThermalEssReactingImplTest {

	private static final String TESS_ID = "tess0";
	private static final ChannelAddress SOC = new ChannelAddress(TESS_ID, "Soc");

	@Test
	public void testInitialStateAndChargingToClamp() throws Exception {
		final var clock = createDummyClock();
		var sut = new SimulatorThermalEssReactingImpl();
		// a heating that constantly delivers thermal power into the storage
		sut.addHeating(new DummyHeatPump("hp0").withThermalPower(2000));

		new ComponentTest(sut) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.activate(MyConfig.create() //
						.setId(TESS_ID) //
						.setVolume(1000) //
						.setInitialSoc(50) //
						.setMinTemperature(20) //
						.setMaxTemperature(80) //
						.build()) //
				// first cycle only initializes the timestamp; SoC stays at the initial value
				.next(new TestCase() //
						.output(SOC, 50)) //
				// charging for a long time drives the storage to the upper clamp (100 %)
				.next(new TestCase() //
						.timeleap(clock, 24, ChronoUnit.HOURS) //
						.output(SOC, 100));
	}

	@Test
	public void testDischargingToClamp() throws Exception {
		final var clock = createDummyClock();
		var sut = new SimulatorThermalEssReactingImpl();

		new ComponentTest(sut) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("datasource", new ConstantThermalConsumption(3000)) //
				.activate(MyConfig.create() //
						.setId(TESS_ID) //
						.setVolume(1000) //
						.setInitialSoc(50) //
						.setMinTemperature(20) //
						.setMaxTemperature(80) //
						.build()) //
				.next(new TestCase() //
						.output(SOC, 50)) //
				// constant consumption with no heating drains the storage to the lower clamp (0 %)
				.next(new TestCase() //
						.timeleap(clock, 24, ChronoUnit.HOURS) //
						.output(SOC, 0));
	}

	/**
	 * Minimal {@link SimulatorDatasource} that always returns a constant
	 * 'ThermalConsumption' value.
	 */
	private static class ConstantThermalConsumption implements SimulatorDatasource {

		private final int consumption;

		public ConstantThermalConsumption(int consumption) {
			this.consumption = consumption;
		}

		@Override
		public Set<String> getKeys() {
			return Set.of("ThermalConsumption");
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
