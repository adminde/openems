package io.openems.edge.ess.hyperstrong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class AlarmAnalysisTest {

	@Test
	public void testConvertAlarmInteger_allFourCodesAtBit0() {
		assertEquals(0, AlarmAnalysis.convertAlarmInteger(0, 0b00));
		assertEquals(1, AlarmAnalysis.convertAlarmInteger(0, 0b01));
		assertEquals(2, AlarmAnalysis.convertAlarmInteger(0, 0b10));
		assertEquals(3, AlarmAnalysis.convertAlarmInteger(0, 0b11));
	}

	@Test
	public void testConvertAlarmInteger_isolatesBitPair() {
		// register has bits 0..15 set to a known pattern: 11_10_01_00_00_00_00_00
		int register = 0b1110_0100_0000_0000;
		assertEquals(0, AlarmAnalysis.convertAlarmInteger(0, register));
		assertEquals(0, AlarmAnalysis.convertAlarmInteger(2, register));
		assertEquals(0, AlarmAnalysis.convertAlarmInteger(4, register));
		assertEquals(0, AlarmAnalysis.convertAlarmInteger(6, register));
		assertEquals(0, AlarmAnalysis.convertAlarmInteger(8, register));
		assertEquals(1, AlarmAnalysis.convertAlarmInteger(10, register));
		assertEquals(2, AlarmAnalysis.convertAlarmInteger(12, register));
		assertEquals(3, AlarmAnalysis.convertAlarmInteger(14, register));
	}

	@Test
	public void testConvertAlarmInteger_boundaryBits() {
		assertEquals(3, AlarmAnalysis.convertAlarmInteger(0, 0x0003));
		assertEquals(3, AlarmAnalysis.convertAlarmInteger(14, 0xC000));
	}

	@Test
	public void testConvertAlarmInteger_rejectsOutOfRange() {
		try {
			AlarmAnalysis.convertAlarmInteger(-1, 0);
			fail("expected IllegalArgumentException");
		} catch (IllegalArgumentException expected) {
			// ok
		}
		try {
			AlarmAnalysis.convertAlarmInteger(15, 0);
			fail("expected IllegalArgumentException");
		} catch (IllegalArgumentException expected) {
			// ok
		}
	}
}
