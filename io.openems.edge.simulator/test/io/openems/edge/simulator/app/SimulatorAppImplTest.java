package io.openems.edge.simulator.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.common.types.ChannelAddress;
import io.openems.common.types.MeterType;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.meter.test.DummyElectricityMeter;

public class SimulatorAppImplTest {

	@Test
	void test() throws Exception {
		new ComponentTest(new SimulatorAppImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) // #
				.addReference("componentManager", new DummyComponentManager()) //
				.activate(MyConfig.create() //
						.setId(SimulatorAppImpl.SINGLETON_SERVICE_PID) //
						.build()) //
				.deactivate();
	}

	private static ExecuteSimulationRequest.Profile profile(Integer... values) throws Exception {
		var j = new JsonArray();
		for (var v : values) {
			j.add(v);
		}
		return ExecuteSimulationRequest.Profile.from(j);
	}

	private static ExecuteSimulationRequest request(Map<String, ExecuteSimulationRequest.Profile> profiles)
			throws Exception {
		var clock = ExecuteSimulationRequest.Clock.from(JsonUtils.buildJsonObject() //
				.addProperty("start", "2025-01-01T00:00:00Z") //
				.addProperty("end", "2025-01-01T01:00:00Z") //
				.addProperty("timeleapPerCycle", 900_000) //
				.addProperty("executeCycleTwice", true) //
				.build());
		return new ExecuteSimulationRequest(List.of(), clock, profiles, List.of());
	}

	@Test
	void sumChannelsAreAddedUpFromTheMeterProfiles() throws Exception {
		var request = request(Map.of(//
				"meter1/ActivePower", profile(10, 20), //
				"meter2/ActivePower", profile(1, 2), //
				"meter3/ActivePower", profile(100, 100, 100)));
		List<ElectricityMeter> meters = List.of(//
				new DummyElectricityMeter("meter0").withMeterType(MeterType.GRID), //
				new DummyElectricityMeter("meter1").withMeterType(MeterType.CONSUMPTION_METERED), //
				new DummyElectricityMeter("meter2").withMeterType(MeterType.PRODUCTION), //
				new DummyElectricityMeter("meter3").withMeterType(MeterType.PRODUCTION));

		// production adds up both production meters, cut to the shorter profile
		assertEquals(List.of(101, 102), SimulatorAppImpl.sumProfiles(request,
				new ChannelAddress("_sum", "ProductionActivePower"), meters, p -> p.getNextValues(10)));
		// consumption comes from the consumption meter alone
		assertEquals(List.of(10, 20), SimulatorAppImpl.sumProfiles(request,
				new ChannelAddress("_sum", "ConsumptionActivePower"), meters, p -> p.getNextValues(10)));
		assertEquals(List.of(10, 20), SimulatorAppImpl.sumProfiles(request,
				new ChannelAddress("_sum", "UnmanagedConsumptionActivePower"), meters, p -> p.getNextValues(10)));
		// a channel with a profile of its own plays that profile
		assertEquals(List.of(1, 2), SimulatorAppImpl.sumProfiles(request,
				new ChannelAddress("meter2", "ActivePower"), meters, p -> p.getNextValues(10)));
		// other sum channels and unknown channels have nothing to play
		assertEquals(List.of(), SimulatorAppImpl.sumProfiles(request,
				new ChannelAddress("_sum", "GridActivePower"), meters, p -> p.getNextValues(10)));
		assertEquals(List.of(), SimulatorAppImpl.sumProfiles(request,
				new ChannelAddress("ess0", "Soc"), meters, p -> p.getNextValues(10)));
	}

	@Test
	void theCountBoundsWhatAnAnnualProfileCosts() throws Exception {
		// an annual profile must not be walked per lookup: the caller says how
		// far ahead it looks and gets exactly that much
		var request = request(Map.of(//
				"meter1/ActivePower", profile(new Integer[35040]), //
				"meter2/ActivePower", profile(new Integer[35040])));
		List<ElectricityMeter> meters = List.of(//
				new DummyElectricityMeter("meter1").withMeterType(MeterType.PRODUCTION), //
				new DummyElectricityMeter("meter2").withMeterType(MeterType.PRODUCTION));

		var values = SimulatorAppImpl.sumProfiles(request,
				new ChannelAddress("_sum", "ProductionActivePower"), meters, p -> p.getNextValues(192));

		assertEquals(192, values.size());
		assertNull(values.get(0)); // a slot that is null in every source stays null
	}
}
