package io.openems.shared.timescaledb.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

public class ResendMarkersTest {

	@Test
	public void testRebuildsEverySecondOfAnOutageFromSparseRows() {
		var markers = new ResendMarkers();
		markers.add(1_000, false);
		markers.add(1_030, true);
		markers.add(1_060, true);
		markers.add(1_120, true);
		markers.add(1_150, false);
		markers.add(1_180, false);

		assertEquals(LongStream.range(1_030, 1_150).boxed().toList(), markers.getTimestamps());
	}

	@Test
	public void testKeepsRowsWrittenEveryCycle() {
		var markers = new ResendMarkers();
		markers.add(1_000, false);
		markers.add(1_001, true);
		markers.add(1_002, true);
		markers.add(1_002, true); // Two Cycles share one second.
		markers.add(1_003, true);
		markers.add(1_004, false);

		assertEquals(List.of(1_001L, 1_002L, 1_003L), markers.getTimestamps());
	}

	@Test
	public void testDoesNotBridgeTimeWithoutRows() {
		var markers = new ResendMarkers();
		markers.add(1_000, true);
		markers.add(5_000, true); // The Edge did not run in between.

		var expected = new ArrayList<>(LongStream.range(1_000, 1_000 + ResendMarkers.HOLD_SECONDS).boxed().toList());
		expected.add(5_000L);
		assertEquals(expected, markers.getTimestamps());
	}
}
