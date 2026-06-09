package io.openems.edge.common.modbusslave;

import static io.openems.common.test.DummyOptionsEnum.UNDEFINED;
import static io.openems.common.test.DummyOptionsEnum.VALUE_1;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;

public class ModbusRecordInt16Test {

	@Test
	public void testUndefined() {
		assertEquals(//
				ModbusRecordInt16.UNDEFINED_VALUE, //
				(int) ByteBuffer.wrap(ModbusRecordInt16.UNDEFINED_BYTE_ARRAY).getShort(0));
		assertArrayEquals(//
				ModbusRecordInt16.UNDEFINED_BYTE_ARRAY, //
				ModbusRecordInt16.toByteArray(ModbusRecordInt16.UNDEFINED_VALUE));
		assertEquals(Short.MIN_VALUE, ModbusRecordInt16.UNDEFINED_VALUE);
		assertEquals(ModbusRecordInt16.UNDEFINED_BYTE_ARRAY.length, ModbusRecordInt16.BYTE_LENGTH);
	}

	@Test
	public void testValue() {
		{
			// Positive value
			var sut = new ModbusRecordInt16(0, "foo", 12345);
			assertEquals("ModbusRecordInt16 [value=12345/0x3039, type=int16]", sut.toString());
			assertEquals("\"12345\"", sut.getValueDescription());
		}
		{
			// Negative value
			var sut = new ModbusRecordInt16(0, "foo", -12345);
			assertEquals("\"-12345\"", sut.getValueDescription());
		}
	}

	@Test
	public void testNegativeToByteArray() {
		assertEquals("[-1, -100]", Arrays.toString(ModbusRecordInt16.toByteArray(-100)));
		assertEquals("[-128, 0]", Arrays.toString(ModbusRecordInt16.toByteArray(Short.MIN_VALUE)));
		assertEquals("[127, -1]", Arrays.toString(ModbusRecordInt16.toByteArray(Short.MAX_VALUE)));
	}

	@Test
	public void testNull() {
		var sut = new ModbusRecordInt16(0, "bar", null);
		assertEquals("ModbusRecordInt16 [value=UNDEFINED, type=int16]", sut.toString());
		assertEquals("", sut.getValueDescription());
	}

	@Test
	public void testOptionsEnum() {
		assertEquals("[-128, 0]", Arrays.toString(ModbusRecordInt16.toByteArray(UNDEFINED)));
		assertEquals("[0, 1]", Arrays.toString(ModbusRecordInt16.toByteArray(VALUE_1)));
	}

	@Test
	public void testReserved() {
		var sut = new ModbusRecordInt16Reserved(0);
		assertEquals("ModbusRecordInt16Reserved [type=int16]", sut.toString());
		assertEquals("", sut.getValueDescription());
	}

}
