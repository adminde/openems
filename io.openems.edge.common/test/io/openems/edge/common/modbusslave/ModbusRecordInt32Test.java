package io.openems.edge.common.modbusslave;

import static io.openems.common.test.DummyOptionsEnum.UNDEFINED;
import static io.openems.common.test.DummyOptionsEnum.VALUE_1;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;

public class ModbusRecordInt32Test {

	@Test
	public void testUndefined() {
		assertEquals(//
				ModbusRecordInt32.UNDEFINED_VALUE, //
				ByteBuffer.wrap(ModbusRecordInt32.UNDEFINED_BYTE_ARRAY).getInt(0));
		assertArrayEquals(//
				ModbusRecordInt32.UNDEFINED_BYTE_ARRAY, //
				ModbusRecordInt32.toByteArray(ModbusRecordInt32.UNDEFINED_VALUE));
		assertEquals((long) Integer.MIN_VALUE, ModbusRecordInt32.UNDEFINED_VALUE);
		assertEquals(ModbusRecordInt32.UNDEFINED_BYTE_ARRAY.length, ModbusRecordInt32.BYTE_LENGTH);
	}

	@Test
	public void testValue() {
		{
			// Positive value
			var sut = new ModbusRecordInt32(0, "foo", 123456789L);
			assertEquals("ModbusRecordInt32 [value=123456789/0x75bcd15, type=int32]", sut.toString());
			assertEquals("\"123456789\"", sut.getValueDescription());
		}
		{
			// Negative value
			var sut = new ModbusRecordInt32(0, "foo", -123456789L);
			assertEquals("\"-123456789\"", sut.getValueDescription());
		}
	}

	@Test
	public void testNegativeToByteArray() {
		assertEquals("[-1, -1, -1, -100]", Arrays.toString(ModbusRecordInt32.toByteArray(-100L)));
		assertEquals("[-128, 0, 0, 0]", Arrays.toString(ModbusRecordInt32.toByteArray(Integer.MIN_VALUE)));
		assertEquals("[127, -1, -1, -1]", Arrays.toString(ModbusRecordInt32.toByteArray(Integer.MAX_VALUE)));
	}

	@Test
	public void testNull() {
		var sut = new ModbusRecordInt32(0, "bar", null);
		assertEquals("ModbusRecordInt32 [value=UNDEFINED, type=int32]", sut.toString());
		assertEquals("", sut.getValueDescription());
	}

	@Test
	public void testOptionsEnum() {
		assertEquals("[-128, 0, 0, 0]", Arrays.toString(ModbusRecordInt32.toByteArray(UNDEFINED)));
		assertEquals("[0, 0, 0, 1]", Arrays.toString(ModbusRecordInt32.toByteArray(VALUE_1)));
	}

	@Test
	public void testReserved() {
		var sut = new ModbusRecordInt32Reserved(0);
		assertEquals("ModbusRecordInt32Reserved [type=int32]", sut.toString());
		assertEquals("", sut.getValueDescription());
	}

}
