package io.openems.edge.simulator.heat.tess.reacting;

import static io.openems.common.test.TestUtils.createDummyClock;
import static org.junit.Assert.assertEquals;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.Test;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.common.types.ChannelAddress;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.heat.test.DummyHeatPump;
import io.openems.edge.heat.test.DummyManagedHeating;
import io.openems.edge.heat.test.DummyThermalEss;
import io.openems.edge.heat.pump.api.ManagedHeatPump;
import io.openems.edge.heat.tess.api.ThermalEss;
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

	@Test
	public void testDistributesTargetToHeatings() throws Exception {
		final var clock = createDummyClock();
		var sut = new SimulatorThermalEssReactingImpl();
		// Thermal budget: hp0 = 2000 W el x 3.0 = 6000 W th; heater0 = 3000 W th
		var hp = new DummyManagedHeating("hp0") //
				.withMaxTargetActivePower(2000) //
				.withThermalEfficiency(300F);
		var heater = new DummyManagedHeating("heater0") //
				.withMaxTargetActivePower(3000);
		// Bind order is reversed to the configured priority order
		sut.addHeating(heater);
		sut.addHeating(hp);

		var test = new ComponentTest(sut) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.activate(MyConfig.create() //
						.setId(TESS_ID) //
						.setVolume(1000) //
						.setInitialSoc(50) //
						.setMinTemperature(20) //
						.setMaxTemperature(80) //
						.setHeatingIds("hp0", "heater0") //
						.build());

		sut.setTargetThermalPowerEqualsWithoutFilter(7000);
		test.next(new TestCase());

		// Configured priority order wins over bind order; consume the writes as the
		// devices would
		assertEquals(Optional.of(2000), hp.getTargetActivePowerChannel().getNextWriteValueAndReset());
		assertEquals(Optional.of(1000), heater.getTargetActivePowerChannel().getNextWriteValueAndReset());

		// Without a pending write the Heating devices stay autonomous
		test.next(new TestCase());
		assertEquals(Optional.empty(), hp.getTargetActivePowerChannel().getNextWriteValue());
		assertEquals(Optional.empty(), heater.getTargetActivePowerChannel().getNextWriteValue());

		// Aggregated thermal intake limits are published
		assertEquals(Integer.valueOf(9000), sut.getMaxTargetThermalPowerChannel().getNextValue().get());
	}

	@Test
	public void testBooksOnlyOwnHeatOfSharedHeatPump() throws Exception {
		final var clock = createDummyClock();
		var sut = new SimulatorThermalEssReactingImpl();
		// A heat pump shared with another layer, currently serving that layer
		var hp = new SharedDummyHeatPump("hp0");
		hp.withThermalPower(2000);
		hp.setThermalStorage(new DummyThermalEss("tess9"));
		sut.addHeating(hp);

		var test = new ComponentTest(sut) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.activate(MyConfig.create() //
						.setId(TESS_ID) //
						.setVolume(1000) //
						.setInitialSoc(50) //
						.setMinTemperature(20) //
						.setMaxTemperature(80) //
						.build()) //
				.next(new TestCase() //
						.output(SOC, 50)) //
				// The heat flows into the other layer: this layer must not book it
				.next(new TestCase() //
						.timeleap(clock, 24, ChronoUnit.HOURS) //
						.output(SOC, 50));

		// The valve switches to this layer: the heat is booked here
		hp.setThermalStorage(sut);
		test.next(new TestCase() //
				.timeleap(clock, 24, ChronoUnit.HOURS) //
				.output(SOC, 100));
	}

	/**
	 * Minimal shared heat pump: a {@link DummyHeatPump} with a settable active
	 * storage, as the heat pump simulators expose it via
	 * {@link ManagedHeatPump#getThermalStorage()}.
	 */
	private static class SharedDummyHeatPump extends DummyHeatPump implements ManagedHeatPump {

		private ThermalEss thermalStorage = null;

		public SharedDummyHeatPump(String id) {
			super(id);
		}

		public void setThermalStorage(ThermalEss storage) {
			this.thermalStorage = storage;
		}

		@Override
		public ThermalEss getThermalStorage() {
			return this.thermalStorage;
		}
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
