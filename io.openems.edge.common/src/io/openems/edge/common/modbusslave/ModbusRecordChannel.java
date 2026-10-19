package io.openems.edge.common.modbusslave;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Unit;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.component.OpenemsComponent;

public class ModbusRecordChannel extends ModbusRecord {

	private final Logger log = LoggerFactory.getLogger(ModbusRecordChannel.class);

	private final String name;
	private final ChannelId channelId;
	private final AccessMode accessMode;
	private final UnaryOperator<Number> mapValue;
	private final OpenemsComponent source;
	private final Unit unit;
	private final int gain;
	private final double factor;

	protected Consumer<Object> onWriteValueCallback = null;

	/**
	 * this is used to buffer calls to writeValue(). Once the buffer is full, the
	 * value is actually forwarded to the channel.
	 */
	private final Byte[] writeValueBuffer;

	public ModbusRecordChannel(int offset, ModbusType type, ChannelId channelId, AccessMode modbusApiAccessMode,
			UnaryOperator<Number> mapValue) {
		this(offset, null, type, channelId, modbusApiAccessMode, mapValue);
	}

	public ModbusRecordChannel(int offset, String name, ModbusType type, ChannelId channelId,
			AccessMode modbusApiAccessMode, UnaryOperator<Number> mapValue) {
		this(offset, name, type, channelId, modbusApiAccessMode, null, mapValue, null, 1);
	}

	/**
	 * Creates a {@link ModbusRecordChannel} that transmits the Channel value in
	 * the given {@link Unit}, multiplied by the given gain.
	 *
	 * <p>
	 * The register value is the Channel value converted to the given Unit and
	 * multiplied by the gain, e.g. a Channel in [W] with Unit [kW] and gain 100
	 * transmits 12345 W as 1235. Written values are converted back accordingly.
	 * The optional mapping function is applied to read values before the
	 * conversion.
	 *
	 * <p>
	 * The Channel belongs to the given source Component, or to the Component of
	 * the Modbus table if no source is given. A source allows a table to pass
	 * through the Channel of another Component, e.g. of the Sum.
	 *
	 * @param offset              the register address offset
	 * @param name                the name of the register
	 * @param type                the {@link ModbusType}
	 * @param channelId           the {@link ChannelId}
	 * @param modbusApiAccessMode the configured {@link AccessMode}
	 * @param source              the Component of the Channel, or null for the
	 *                            Component of the Modbus table
	 * @param mapValue            an optional mapping function for read values
	 * @param unit                the {@link Unit} of the register, sharing the
	 *                            base Unit of the Channel, or null to transmit
	 *                            the Channel value as it is
	 * @param gain                the positive gain of the register
	 */
	public ModbusRecordChannel(int offset, String name, ModbusType type, ChannelId channelId,
			AccessMode modbusApiAccessMode, OpenemsComponent source, UnaryOperator<Number> mapValue, Unit unit,
			int gain) {
		super(offset, type);
		this.name = name;
		this.channelId = channelId;
		this.accessMode = evaluateActualAccessMode(channelId, modbusApiAccessMode);
		this.mapValue = mapValue;
		this.source = source;
		this.unit = unit;
		this.gain = gain;
		this.factor = unit == null ? 1 : calculateFactor(channelId, unit, gain);

		// initialize buffer
		var byteLength = switch (this.getType()) {
		case ENUM16, UINT16 -> ModbusRecordUint16.BYTE_LENGTH;
		case UINT32 -> ModbusRecordUint32.BYTE_LENGTH;
		case UINT64 -> ModbusRecordUint64.BYTE_LENGTH;
		case INT16 -> ModbusRecordInt16.BYTE_LENGTH;
		case INT32 -> ModbusRecordInt32.BYTE_LENGTH;
		case INT64 -> ModbusRecordInt64.BYTE_LENGTH;
		case FLOAT32 -> ModbusRecordFloat32.BYTE_LENGTH;
		case FLOAT64 -> ModbusRecordFloat64.BYTE_LENGTH;
		case STRING16 -> ModbusRecordString16.BYTE_LENGTH;
		};
		this.writeValueBuffer = new Byte[byteLength];
	}

	/**
	 * Evaluate the AccessMode from configured Modbus-Api-AccessMode and
	 * Channel-AccessMode.
	 *
	 * @param channelId           the {@link ChannelId}
	 * @param modbusApiAccessMode the configured {@link AccessMode}
	 * @return the actual {@link AccessMode}
	 */
	private static AccessMode evaluateActualAccessMode(ChannelId channelId, AccessMode modbusApiAccessMode) {
		var channelAccessMode = channelId.doc().getAccessMode();
		return switch (modbusApiAccessMode) {
		case READ_ONLY -> AccessMode.READ_ONLY;

		case READ_WRITE -> //
			switch (channelAccessMode) {
			case READ_ONLY -> AccessMode.READ_ONLY;
			case READ_WRITE -> AccessMode.READ_WRITE;
			case WRITE_ONLY -> AccessMode.WRITE_ONLY;
			};

		case WRITE_ONLY -> //
			switch (channelAccessMode) {
			case READ_ONLY -> AccessMode.READ_ONLY;
			case READ_WRITE, WRITE_ONLY -> AccessMode.WRITE_ONLY;
			};
		};
	}

	/**
	 * Calculates the factor between the Channel value and the register value.
	 *
	 * @param channelId the {@link ChannelId}
	 * @param unit      the {@link Unit} of the register
	 * @param gain      the gain of the register
	 * @return the factor
	 */
	private static double calculateFactor(ChannelId channelId, Unit unit, int gain) {
		if (gain <= 0) {
			throw new IllegalArgumentException(
					"Gain [" + gain + "] of Channel [" + channelId.id() + "] is not positive");
		}
		var channelUnit = channelId.doc().getUnit();
		if (getBaseUnit(channelUnit) != getBaseUnit(unit)) {
			throw new IllegalArgumentException("Unit [" + channelUnit + "] of Channel [" + channelId.id()
					+ "] cannot be converted to [" + unit + "]");
		}
		return Math.pow(10, getScaleFactor(channelUnit) - getScaleFactor(unit)) * gain;
	}

	private static Unit getBaseUnit(Unit unit) {
		if (unit.discreteUnit != null) {
			return getBaseUnit(unit.discreteUnit);
		}
		// The Unit enumeration does not relate per mille to percent.
		if (unit == Unit.THOUSANDTH) {
			return Unit.PERCENT;
		}
		return unit.baseUnit != null ? unit.baseUnit : unit;
	}

	private static int getScaleFactor(Unit unit) {
		if (unit.discreteUnit != null) {
			return getScaleFactor(unit.discreteUnit);
		}
		if (unit == Unit.THOUSANDTH) {
			return -1;
		}
		return unit.baseUnit != null ? unit.scaleFactor : 0;
	}

	public ChannelId getChannelId() {
		return this.channelId;
	}

	/**
	 * Gets the Component of the Channel, if it differs from the Component of the
	 * Modbus table.
	 *
	 * @return the source Component, or null
	 */
	public OpenemsComponent getSource() {
		return this.source;
	}

	@Override
	public String toString() {
		return "ModbusRecordChannel [" //
				+ "channelId=" + this.channelId + ", " //
				+ "offset=" + this.getOffset() + ", " //
				+ "type=" + this.getType() //
				+ "]";
	}

	@Override
	public byte[] getValue(OpenemsComponent tableComponent) {
		final var component = this.source != null ? this.source : tableComponent;
		final Object value;
		if (component != null) {
			Channel<?> channel = component.channel(this.channelId);
			if (channel != null) {
				value = switch (channel.channelDoc().getAccessMode()) {
				case WRITE_ONLY -> null; // no read allowed/possible

				case READ_ONLY, READ_WRITE -> {
					try {
						yield this.getMappedValueOrChannelValue(channel);
					} catch (IllegalArgumentException e) {
						this.log.warn("Channel [" + channel.address() + "] is not available: " + e.getMessage());
						yield null;
					}
				}
				};

			} else {
				this.log.warn("Channel [" + component.id() + "/" + this.channelId.id() + "] is not available for "
						+ this.toString());
				value = null;
			}
		} else {
			value = null;
		}

		return switch (this.getType()) {
		case ENUM16, UINT16 -> //
			switch (this.accessMode) {
			case READ_ONLY, READ_WRITE -> ModbusRecordUint16.toByteArray(value);
			case WRITE_ONLY -> ModbusRecordUint16.UNDEFINED_BYTE_ARRAY;
			};
		case UINT32 -> //
			switch (this.accessMode) {
			case READ_ONLY, READ_WRITE -> ModbusRecordUint32.toByteArray(value);
			case WRITE_ONLY -> ModbusRecordUint32.UNDEFINED_BYTE_ARRAY;
			};
		case UINT64 -> //
			switch (this.accessMode) {
			case READ_ONLY, READ_WRITE -> ModbusRecordUint64.toByteArray(value);
			case WRITE_ONLY -> ModbusRecordUint64.UNDEFINED_BYTE_ARRAY;
			};
		case INT16 -> //
			switch (this.accessMode) {
			case READ_ONLY, READ_WRITE -> ModbusRecordInt16.toByteArray(value);
			case WRITE_ONLY -> ModbusRecordInt16.UNDEFINED_BYTE_ARRAY;
			};
		case INT32 -> //
			switch (this.accessMode) {
			case READ_ONLY, READ_WRITE -> ModbusRecordInt32.toByteArray(value);
			case WRITE_ONLY -> ModbusRecordInt32.UNDEFINED_BYTE_ARRAY;
			};
		case INT64 -> //
			switch (this.accessMode) {
			case READ_ONLY, READ_WRITE -> ModbusRecordInt64.toByteArray(value);
			case WRITE_ONLY -> ModbusRecordInt64.UNDEFINED_BYTE_ARRAY;
			};
		case FLOAT32 -> //
			switch (this.accessMode) {
			case READ_ONLY, READ_WRITE -> ModbusRecordFloat32.toByteArray(value);
			case WRITE_ONLY -> ModbusRecordFloat32.UNDEFINED_BYTE_ARRAY;
			};
		case FLOAT64 -> //
			switch (this.accessMode) {
			case READ_ONLY, READ_WRITE -> ModbusRecordFloat64.toByteArray(value);
			case WRITE_ONLY -> ModbusRecordFloat64.UNDEFINED_BYTE_ARRAY;
			};
		case STRING16 -> //
			switch (this.accessMode) {
			case READ_ONLY, READ_WRITE -> ModbusRecordString16.toByteArray(value);
			case WRITE_ONLY -> ModbusRecordString16.UNDEFINED_BYTE_ARRAY;
			};
		};
	}

	/**
	 * Add a onWriteValue callback.
	 *
	 * @param onWriteValueCallback the callback
	 */
	public void onWriteValue(Consumer<Object> onWriteValueCallback) {
		this.onWriteValueCallback = onWriteValueCallback;
	}

	@Override
	public void writeValue(int index, byte byte1, byte byte2) {
		switch (this.accessMode) {
		case READ_ONLY:
			// Read-Only Access enabled. Do not write value.
			return;

		case READ_WRITE:
		case WRITE_ONLY:
			break;
		}

		this.writeValueBuffer[index * 2] = byte1;
		this.writeValueBuffer[index * 2 + 1] = byte2;
		// is the buffer full?
		for (Byte element : this.writeValueBuffer) {
			if (element == null) {
				return; // no, it is not full
			}
		}

		// yes, it is full -> Prepare ByteBuffer
		var buff = ByteBuffer.allocate(this.writeValueBuffer.length);
		for (Byte element : this.writeValueBuffer) {
			buff.put(element);
		}
		buff.rewind();

		// clear buffer
		Arrays.fill(this.writeValueBuffer, null);

		// Get Value-Object from ByteBuffer
		final Object value;
		if (this.unit != null) {
			value = this.toChannelValue(buff);
		} else {
			value = switch (this.getType()) {
			case ENUM16, UINT16, INT16 -> buff.getShort();
			case UINT32, INT32 -> buff.getInt();
			case UINT64, INT64 -> buff.getLong();
			case FLOAT32 -> buff.getFloat();
			case FLOAT64 -> buff.getDouble();
			case STRING16 -> ""; // TODO implement String conversion
			};
		}

		// Forward Value to ApiWorker
		if (this.onWriteValueCallback != null) {
			this.onWriteValueCallback.accept(value);
		} else {
			this.log.error("No onWriteValueCallback registered. What should I do with [" + value + "]?");
		}
	}

	@Override
	public String getName() {
		if (this.name == null) {
			return new ChannelAddress(this.getComponentId(), this.getChannelId().id()).toString();
		}
		return this.name;
	}

	/**
	 * Converts a written register value to the Channel value.
	 *
	 * @param buff the {@link ByteBuffer} holding the register value
	 * @return the Channel value
	 */
	private Object toChannelValue(ByteBuffer buff) {
		double registerValue = switch (this.getType()) {
		case ENUM16, UINT16 -> Short.toUnsignedInt(buff.getShort());
		case INT16 -> buff.getShort();
		case UINT32 -> Integer.toUnsignedLong(buff.getInt());
		case INT32 -> buff.getInt();
		case UINT64, INT64 -> buff.getLong();
		case FLOAT32 -> buff.getFloat();
		case FLOAT64 -> buff.getDouble();
		case STRING16 -> throw new IllegalArgumentException("A String register cannot have a gain");
		};
		var channelValue = registerValue / this.factor;
		return switch (this.channelId.doc().getType()) {
		case FLOAT, DOUBLE -> channelValue;
		case BOOLEAN, SHORT, INTEGER, LONG, STRING -> Math.round(channelValue);
		};
	}

	@Override
	public Unit getUnit() {
		return this.unit != null ? this.unit : this.channelId.doc().getUnit();
	}

	@Override
	public int getGain() {
		return this.gain;
	}

	@Override
	public String getValueDescription() {
		return this.channelId.doc().getText();
	}

	@Override
	public AccessMode getAccessMode() {
		return this.accessMode;
	}

	private Object getMappedValueOrChannelValue(final Channel<?> channel) {
		final var channelValue = channel.value().get();
		if (channelValue == null) {
			return null;
		}
		final var mappedValue = this.mapValue == null //
				? channelValue //
				: this.applyValueMapper(channelValue);
		if (this.unit == null) {
			return mappedValue;
		}
		return this.applyFactor(mappedValue);
	}

	private Object applyFactor(Object value) {
		if (!(value instanceof Number number)) {
			throw new IllegalArgumentException(
					"Gain can only be applied to Number values, but got [" + value.getClass().getName() + "]");
		}
		var registerValue = number.doubleValue() * this.factor;
		return switch (this.getType()) {
		case FLOAT32, FLOAT64 -> registerValue;
		case ENUM16, UINT16, UINT32, UINT64, INT16, INT32, INT64, STRING16 -> Math.round(registerValue);
		};
	}

	private Object applyValueMapper(Object value) {
		if (value instanceof Number number) {
			return this.mapValue.apply(number);
		}
		throw new IllegalArgumentException(
				"Mapper can only be applied to Number values, but got [" + value.getClass().getName() + "]");
	}
}
