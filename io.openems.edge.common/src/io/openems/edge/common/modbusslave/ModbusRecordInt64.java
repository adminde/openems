package io.openems.edge.common.modbusslave;

import java.nio.ByteBuffer;

import io.openems.common.types.OpenemsType;
import io.openems.common.types.OptionsEnum;
import io.openems.edge.common.type.TypeUtils;

public class ModbusRecordInt64 extends ModbusRecordConstant {

	public static final long UNDEFINED_VALUE = Long.MIN_VALUE;
	public static final byte[] UNDEFINED_BYTE_ARRAY = toByteArray(UNDEFINED_VALUE);
	public static final int BYTE_LENGTH = 8;

	protected final Long value;

	public ModbusRecordInt64(int offset, String name, Long value) {
		super(offset, name, ModbusType.INT64, toByteArray(value));
		this.value = value;
	}

	@Override
	public String toString() {
		return this.generateToString("ModbusRecordInt64", this.value, Long::toHexString);
	}

	/**
	 * Convert to byte array.
	 *
	 * @param value the value
	 * @return the byte array
	 */
	public static byte[] toByteArray(long value) {
		return ByteBuffer.allocate(BYTE_LENGTH).putLong(value).array();
	}

	/**
	 * Convert to byte array.
	 *
	 * @param value the value
	 * @return the byte array
	 */
	public static byte[] toByteArray(Object value) {
		if (value == null || (value instanceof OptionsEnum oe && oe.isUndefined())) {
			return UNDEFINED_BYTE_ARRAY;
		}
		return toByteArray((long) TypeUtils.getAsType(OpenemsType.LONG, value));
	}

	@Override
	public String getValueDescription() {
		return this.value != null //
				? "\"" + Long.toString(this.value) + "\"" //
				: "";
	}

}
