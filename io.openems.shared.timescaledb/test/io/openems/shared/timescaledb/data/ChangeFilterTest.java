package io.openems.shared.timescaledb.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

public class ChangeFilterTest {

	private static long at(String time) {
		return Instant.parse(time).toEpochMilli();
	}

	@Test
	public void testWritesOnlyChangedValues() {
		var filter = new ChangeFilter();
		var channel = new Object();

		filter.startCycle(at("2026-09-30T10:00:00Z"));
		assertTrue(filter.shouldWrite(channel, 5L, false));
		filter.startCycle(at("2026-09-30T10:00:01Z"));
		assertFalse(filter.shouldWrite(channel, 5L, false));
		filter.startCycle(at("2026-09-30T10:00:02Z"));
		assertTrue(filter.shouldWrite(channel, 7L, false));
		filter.startCycle(at("2026-09-30T10:00:03Z"));
		assertTrue(filter.shouldWrite(channel, 5L, false));
	}

	@Test
	public void testWritesUnchangedValuesOncePerMinute() {
		var filter = new ChangeFilter();
		var channel = new Object();

		filter.startCycle(at("2026-09-30T10:00:58Z"));
		assertTrue(filter.shouldWrite(channel, 5L, false));
		filter.startCycle(at("2026-09-30T10:00:59Z"));
		assertFalse(filter.shouldWrite(channel, 5L, false));
		filter.startCycle(at("2026-09-30T10:01:00Z"));
		assertTrue(filter.shouldWrite(channel, 5L, false));
		filter.startCycle(at("2026-09-30T10:01:01Z"));
		assertFalse(filter.shouldWrite(channel, 5L, false));
	}

	@Test
	public void testWritesAggregateChannelsEveryCycle() {
		var filter = new ChangeFilter();
		var channel = new Object();

		filter.startCycle(at("2026-09-30T10:00:00Z"));
		assertTrue(filter.shouldWrite(channel, 5L, true));
		filter.startCycle(at("2026-09-30T10:00:01Z"));
		assertTrue(filter.shouldWrite(channel, 5L, true));
	}

	@Test
	public void testWritesValueAgainAfterItWasUndefined() {
		var filter = new ChangeFilter();
		var channel = new Object();

		filter.startCycle(at("2026-09-30T10:00:00Z"));
		assertTrue(filter.shouldWrite(channel, 5L, false));
		filter.startCycle(at("2026-09-30T10:00:01Z"));
		filter.forget(channel);
		filter.startCycle(at("2026-09-30T10:00:02Z"));
		assertTrue(filter.shouldWrite(channel, 5L, false));
	}
}
