package io.openems.edge.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

public class DataContainerTest {

	private static DataContainer container() {
		var data = new DataContainer();
		data.setKeys(new String[] { "meter1/ActivePower", "meter2/ActivePower" });
		data.addRecord(new Float[] { 10f, 1f });
		data.addRecord(new Float[] { 20f, 2f });
		data.addRecord(new Float[] { 30f, 3f });
		return data;
	}

	@Test
	void nextValuesStartAtTheCurrentRecord() {
		var data = container();

		// nothing played yet: the whole column lies ahead
		assertEquals(List.of(10f, 20f, 30f), data.getNextValues("meter1/ActivePower", 10));

		// a cycle reads the current value and then advances, which is what
		// AbstractDatasource does on every TOPIC_CYCLE_AFTER_WRITE
		data.getValue("meter1/ActivePower");
		assertEquals(List.of(10f, 20f, 30f), data.getNextValues("meter1/ActivePower", 10));

		data.nextRecord();
		assertEquals(List.of(20f, 30f), data.getNextValues("meter1/ActivePower", 10));
		assertEquals(List.of(2f, 3f), data.getNextValues("meter2/ActivePower", 10));

		// getValues stays the whole column, it is the "all records" accessor
		assertEquals(List.of(10f, 20f, 30f), data.getValues("meter1/ActivePower"));

		data.nextRecord();
		assertEquals(List.of(30f), data.getNextValues("meter1/ActivePower", 10));

		data.nextRecord(); // past the end, rewinds to the first record
		assertEquals(List.of(10f, 20f, 30f), data.getNextValues("meter1/ActivePower", 10));
	}

	@Test
	void countLimitsHowFarAhead() {
		var data = container();

		assertEquals(List.of(10f), data.getNextValues("meter1/ActivePower", 1));
		assertEquals(List.of(10f, 20f), data.getNextValues("meter1/ActivePower", 2));
		// asking beyond the end gives what there is
		assertEquals(List.of(10f, 20f, 30f), data.getNextValues("meter1/ActivePower", 99));
	}

	@Test
	void unknownKeyHasNothingAhead() {
		assertEquals(List.of(), container().getNextValues("nope/ActivePower", 10));
	}
}
