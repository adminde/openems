package io.openems.edge.simulator.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;

import io.openems.common.types.ChannelAddress;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.simulator.app.ExecuteSimulationRequest.Profile;

public class ExecuteSimulationRequestTest {

	private static Profile profile(Integer... values) throws Exception {
		var j = new JsonArray();
		for (var v : values) {
			j.add(v);
		}
		return Profile.from(j);
	}

	private static ExecuteSimulationRequest request(Map<String, Profile> profiles) throws Exception {
		var clock = ExecuteSimulationRequest.Clock.from(JsonUtils.buildJsonObject() //
				.addProperty("start", "2025-01-01T00:00:00Z") //
				.addProperty("end", "2025-01-01T01:00:00Z") //
				.addProperty("timeleapPerCycle", 900_000) //
				.addProperty("executeCycleTwice", true) //
				.build());
		return new ExecuteSimulationRequest(List.of(), clock, profiles, List.of());
	}

	@Test
	void nextValuesStartAtTheCurrentValue() throws Exception {
		var profile = profile(1, 2, 3, 4);

		assertEquals(List.of(1, 2, 3, 4), profile.getNextValues(10));
		assertEquals(List.of(1, 2), profile.getNextValues(2)); // count limits it

		profile.selectNextValue();
		profile.selectNextValue();
		assertEquals(Integer.valueOf(3), profile.getCurrentValue());
		assertEquals(List.of(3, 4), profile.getNextValues(10));

		profile.selectNextValue();
		profile.selectNextValue(); // wraps around
		assertEquals(List.of(1, 2, 3, 4), profile.getNextValues(10));
	}

	@Test
	void profileIsFoundByFullAddressThenByChannelId() throws Exception {
		var byAddress = profile(10);
		var byChannel = profile(20);
		var request = request(Map.of("meter1/ActivePower", byAddress, "ActivePower", byChannel));

		assertEquals(byAddress, request.getProfile(new ChannelAddress("meter1", "ActivePower")));
		assertEquals(byChannel, request.getProfile(new ChannelAddress("meter2", "ActivePower")));
		assertNull(request.getProfile(new ChannelAddress("_sum", "ProductionActivePower")));
	}
}
