package io.openems.edge.common.meta;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Unit;
import io.openems.edge.common.modbusslave.ModbusRecordUint64;
import io.openems.edge.common.test.DummyMeta;

public class MetaTest {

	@Test
	public void testModbusSlaveTableSystemTimeUtc() {
		var meta = new DummyMeta();
		var record = Arrays.stream(meta.getModbusSlaveTable(AccessMode.READ_ONLY) //
				.getNatureTables()[0].getModbusRecords()) //
				.filter(r -> r.getOffset() == 99) //
				.findFirst() //
				.orElseThrow();
		assertEquals("System Time UTC", record.getName());
		assertEquals(Unit.SECONDS, record.getUnit());

		var channel = meta.channel(Meta.ChannelId.SYSTEM_TIME_UTC);
		channel.setNextValue(1_700_000_000L);
		channel.nextProcessImage();
		assertArrayEquals(ModbusRecordUint64.toByteArray(1_700_000_000L), record.getValue(meta));
	}

}
