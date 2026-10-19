package io.openems.edge.common.modbusslave;

import static io.openems.common.channel.AccessMode.READ_WRITE;
import static io.openems.common.channel.AccessMode.WRITE_ONLY;
import static io.openems.edge.common.modbusslave.ModbusType.INT16;
import static io.openems.edge.common.modbusslave.ModbusType.INT32;
import static io.openems.edge.common.modbusslave.ModbusType.UINT16;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.jupiter.api.Test;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.test.TestUtils;

public class ModbusRecordChannelTest {

	private class DummyComponent extends AbstractOpenemsComponent implements OpenemsComponent {

		public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
			WRITE_ONLY_CHANNEL(Doc.of(OpenemsType.INTEGER)//
					.accessMode(WRITE_ONLY)),
			READ_ONLY_CHANNEL(Doc.of(OpenemsType.INTEGER)//
					.accessMode(AccessMode.READ_ONLY)),
			READ_WRITE_CHANNEL(Doc.of(OpenemsType.INTEGER)//
					.accessMode(READ_WRITE)), //
			POWER_CHANNEL(Doc.of(OpenemsType.INTEGER)//
					.unit(Unit.WATT) //
					.accessMode(READ_WRITE)), //
			VOLTAGE_CHANNEL(Doc.of(OpenemsType.INTEGER)//
					.unit(Unit.MILLIVOLT) //
					.accessMode(READ_WRITE)), //
			SHARE_CHANNEL(Doc.of(OpenemsType.INTEGER)//
					.unit(Unit.THOUSANDTH) //
					.accessMode(READ_WRITE)); //

			private final Doc doc;

			private ChannelId(Doc doc) {
				this.doc = doc;
			}

			@Override
			public Doc doc() {
				return this.doc;
			}
		}

		public DummyComponent(String id) {
			super(//
					OpenemsComponent.ChannelId.values(), //
					ChannelId.values() //
			);
			super.activate(null, id, "", true);
		}

		protected DummyComponent withReadWriteChannel(int value) {
			TestUtils.withValue(this, ChannelId.READ_WRITE_CHANNEL, value);
			return this;
		}

		protected DummyComponent withReadOnlyChannel(int value) {
			TestUtils.withValue(this, ChannelId.READ_ONLY_CHANNEL, value);
			return this;
		}

		protected DummyComponent withPowerChannel(int value) {
			TestUtils.withValue(this, ChannelId.POWER_CHANNEL, value);
			return this;
		}

		protected DummyComponent withVoltageChannel(int value) {
			TestUtils.withValue(this, ChannelId.VOLTAGE_CHANNEL, value);
			return this;
		}

	}

	@Test
	public void testGetUnsignedValue() {
		var component = new DummyComponent("foo0") //
				.withReadOnlyChannel(100) //
				.withReadWriteChannel(200);

		assertArrayEquals(ModbusRecordUint16.UNDEFINED_BYTE_ARRAY,
				new ModbusRecordChannel(0, UINT16, DummyComponent.ChannelId.WRITE_ONLY_CHANNEL, READ_WRITE, null)
						.getValue(component));

		assertArrayEquals(ModbusRecordUint16.toByteArray(100),
				new ModbusRecordChannel(0, UINT16, DummyComponent.ChannelId.READ_ONLY_CHANNEL, READ_WRITE, null)
						.getValue(component));
	}

	@Test
	public void testGetSignedValue() {
		var component = new DummyComponent("foo0") //
				.withReadOnlyChannel(-100);

		assertArrayEquals(ModbusRecordInt16.toByteArray(-100),
				new ModbusRecordChannel(0, INT16, DummyComponent.ChannelId.READ_ONLY_CHANNEL, READ_WRITE, null)
						.getValue(component));
	}

	@Test
	public void testGetValueWithMapper() {
		var component = new DummyComponent("foo0") //
				.withReadOnlyChannel(100);

		assertArrayEquals(ModbusRecordUint16.toByteArray(200),
				new ModbusRecordChannel(0, UINT16, DummyComponent.ChannelId.READ_ONLY_CHANNEL, READ_WRITE,
						value -> value.intValue() * 2).getValue(component));
	}

	@Test
	public void testGetValueWithGain() {
		var component = new DummyComponent("foo0") //
				.withPowerChannel(-12346) //
				.withVoltageChannel(400_049);

		var power = new ModbusRecordChannel(0, "Power", INT32, DummyComponent.ChannelId.POWER_CHANNEL, READ_WRITE,
				null, null, Unit.KILOWATT, 100);
		assertArrayEquals(ModbusRecordInt32.toByteArray(-1235), power.getValue(component));
		assertEquals(Unit.KILOWATT, power.getUnit());
		assertEquals(100, power.getGain());

		var voltage = new ModbusRecordChannel(0, "Voltage", UINT16, DummyComponent.ChannelId.VOLTAGE_CHANNEL,
				READ_WRITE, null, null, Unit.VOLT, 10);
		assertArrayEquals(ModbusRecordUint16.toByteArray(4000), voltage.getValue(component));
	}

	@Test
	public void testGetValueWithMapperAndGain() {
		var component = new DummyComponent("foo0") //
				.withPowerChannel(-12346);

		var power = new ModbusRecordChannel(0, "Power", INT32, DummyComponent.ChannelId.POWER_CHANNEL, READ_WRITE,
				null, value -> -value.intValue(), Unit.KILOWATT, 100);
		assertArrayEquals(ModbusRecordInt32.toByteArray(1235), power.getValue(component));
	}

	@Test
	public void testGetValueFromSource() {
		var table = new DummyComponent("foo0");
		var source = new DummyComponent("bar0") //
				.withReadOnlyChannel(100);

		assertArrayEquals(ModbusRecordUint16.toByteArray(100),
				new ModbusRecordChannel(0, "Bar", UINT16, DummyComponent.ChannelId.READ_ONLY_CHANNEL, READ_WRITE,
						source, null, null, 1).getValue(table));
	}

	@Test
	public void testWriteValueWithGain() {
		var power = new ModbusRecordChannel(0, "Power", INT32, DummyComponent.ChannelId.POWER_CHANNEL, READ_WRITE,
				null, null, Unit.KILOWATT, 100);
		var written = new Object[1];
		power.onWriteValue(value -> written[0] = value);
		var bytes = ModbusRecordInt32.toByteArray(-1234);
		power.writeValue(0, bytes[0], bytes[1]);
		power.writeValue(1, bytes[2], bytes[3]);
		assertEquals(-12340L, written[0]);

		var voltage = new ModbusRecordChannel(0, "Voltage", UINT16, DummyComponent.ChannelId.VOLTAGE_CHANNEL,
				READ_WRITE, null, null, Unit.VOLT, 10);
		voltage.onWriteValue(value -> written[0] = value);
		bytes = ModbusRecordUint16.toByteArray(40000);
		voltage.writeValue(0, bytes[0], bytes[1]);
		assertEquals(4_000_000L, written[0]);
	}

	@Test
	public void testPerMilleAsPercent() {
		var component = new DummyComponent("foo0");
		TestUtils.withValue(component, DummyComponent.ChannelId.SHARE_CHANNEL, 456);

		var share = new ModbusRecordChannel(0, "Share", UINT16, DummyComponent.ChannelId.SHARE_CHANNEL, READ_WRITE,
				null, null, Unit.PERCENT, 10);
		assertArrayEquals(ModbusRecordUint16.toByteArray(456), share.getValue(component));
	}

	@Test
	public void testIncompatibleUnit() {
		assertThrows(IllegalArgumentException.class, () -> new ModbusRecordChannel(0, "Power", INT32,
				DummyComponent.ChannelId.POWER_CHANNEL, READ_WRITE, null, null, Unit.VOLT, 10));
		assertThrows(IllegalArgumentException.class, () -> new ModbusRecordChannel(0, "Power", INT32,
				DummyComponent.ChannelId.POWER_CHANNEL, READ_WRITE, null, null, Unit.KILOWATT, 0));
	}

	@Test
	public void testGetName() {
		var unnamed = new ModbusRecordChannel(0, UINT16, DummyComponent.ChannelId.READ_ONLY_CHANNEL, READ_WRITE,
				null);
		unnamed.setComponentId("foo0");
		assertEquals("foo0/ReadOnlyChannel", unnamed.getName());

		var named = new ModbusRecordChannel(0, "Foo Value", UINT16, DummyComponent.ChannelId.READ_ONLY_CHANNEL,
				READ_WRITE, null);
		named.setComponentId("foo0");
		assertEquals("Foo Value", named.getName());
	}

}
