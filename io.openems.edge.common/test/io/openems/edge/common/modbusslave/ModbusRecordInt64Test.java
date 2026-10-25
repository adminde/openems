package io.openems.edge.common.modbusslave;

import static io.openems.common.test.DummyOptionsEnum.UNDEFINED;
import static io.openems.common.test.DummyOptionsEnum.VALUE_1;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

public class ModbusRecordInt64Test {

	@Test
	public void testUndefined() {
		assertEquals(//
				ModbusRecordInt64.UNDEFINED_VALUE, //
				ByteBuffer.wrap(ModbusRecordInt64.UNDEFINED_BYTE_ARRAY).getLong());
		assertArrayEquals(//
				ModbusRecordInt64.UNDEFINED_BYTE_ARRAY, //
				ModbusRecordInt64.toByteArray(ModbusRecordInt64.UNDEFINED_VALUE));
		assertEquals(Long.MAX_VALUE, ModbusRecordInt64.UNDEFINED_VALUE);
		assertEquals(ModbusRecordInt64.UNDEFINED_BYTE_ARRAY.length, ModbusRecordInt64.BYTE_LENGTH);
	}

	@Test
	public void testValue() {
		{
			// Positive value
			var sut = new ModbusRecordInt64(0, "foo", 123456789L);
			assertEquals("ModbusRecordInt64 [value=123456789/0x75bcd15, type=int64]", sut.toString());
			assertEquals("\"123456789\"", sut.getValueDescription());
		}
		{
			// Negative value
			var sut = new ModbusRecordInt64(0, "foo", -123456789L);
			assertEquals("\"-123456789\"", sut.getValueDescription());
		}
	}

	@Test
	public void testNegativeToByteArray() {
		assertEquals("[-1, -1, -1, -1, -1, -1, -1, -100]", Arrays.toString(ModbusRecordInt64.toByteArray(-100L)));
		assertEquals("[-128, 0, 0, 0, 0, 0, 0, 0]", Arrays.toString(ModbusRecordInt64.toByteArray(Long.MIN_VALUE)));
		assertEquals("[127, -1, -1, -1, -1, -1, -1, -1]", Arrays.toString(ModbusRecordInt64.toByteArray(Long.MAX_VALUE)));
	}

	@Test
	public void testNull() {
		var sut = new ModbusRecordInt64(0, "bar", null);
		assertEquals("ModbusRecordInt64 [value=UNDEFINED, type=int64]", sut.toString());
		assertEquals("", sut.getValueDescription());
	}

	@Test
	public void testOptionsEnum() {
		assertArrayEquals(ModbusRecordInt64.UNDEFINED_BYTE_ARRAY, ModbusRecordInt64.toByteArray(UNDEFINED));
		assertEquals("[0, 0, 0, 0, 0, 0, 0, 1]", Arrays.toString(ModbusRecordInt64.toByteArray(VALUE_1)));
	}

	@Test
	public void testReserved() {
		var sut = new ModbusRecordInt64Reserved(0);
		assertEquals("ModbusRecordInt64Reserved [type=int64]", sut.toString());
		assertEquals("", sut.getValueDescription());
	}

}
