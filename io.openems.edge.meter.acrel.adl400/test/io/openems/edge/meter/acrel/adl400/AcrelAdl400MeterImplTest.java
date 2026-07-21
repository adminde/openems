package io.openems.edge.meter.acrel.adl400;

import static io.openems.common.types.MeterType.PRODUCTION;

import org.junit.Test;

import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.test.ComponentTest;
import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.edge.meter.api.ElectricityMeter;

public class AcrelAdl400MeterImplTest {

	private static final String METER_ID = "meter0";
	private static final String MODBUS_ID = "modbus0";

	/**
	 * Encodes a float as two 16-bit Modbus words in LSWMSW order (low word first).
	 *
	 * @param value the float value
	 * @return the two words {@code [lsw, msw]}
	 */
	private static int[] f(float value) {
		var bits = Float.floatToIntBits(value);
		return new int[] { bits & 0xFFFF, (bits >>> 16) & 0xFFFF };
	}

	/**
	 * Encodes an int (energy) as two 16-bit Modbus words in LSWMSW order.
	 *
	 * @param value the int value
	 * @return the two words {@code [lsw, msw]}
	 */
	private static int[] i32(int value) {
		return new int[] { value & 0xFFFF, (value >>> 16) & 0xFFFF };
	}

	/**
	 * Builds {@code count} zero-value dummy registers.
	 *
	 * @param count the number of registers
	 * @return the dummy registers
	 */
	private static int[] dummy(int count) {
		return new int[count];
	}

	@Test
	public void test3P4W() throws Exception {
		new ComponentTest(new AcrelAdl400MeterImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setModbus", new DummyModbusBridge(MODBUS_ID) //
						// THD: 0x05DD..0x05E2
						.withRegisters(0x05DD, new int[] { 15000, 15000, 15000, 5000, 5000, 5000 }) //
						// Energy: 0x084C..0x0875
						.withRegisters(0x084C, i32(100), dummy(8), i32(200), dummy(18), i32(300), dummy(8), i32(400)) //
						// Contiguous float block 0x0800..0x0835 (primary-side data)
						.withRegisters(0x0800, //
								f(230f), f(230f), f(230f), // phase voltages L1, L2, L3
								f(400f), f(400f), f(400f), // line-to-line voltages A-B, C-B, A-C
								f(10f), f(10f), f(10f), // currents L1, L2, L3
								dummy(2), // zero-line current
								f(0.3f), f(0.3f), f(0.4f), f(1.0f), // active power L1, L2, L3, total
								f(0.1f), f(0.1f), f(0.1f), f(0.3f), // reactive power L1, L2, L3, total
								dummy(8), // apparent power A, B, C, total
								f(0.95f), f(0.96f), f(0.97f), // power factor L1, L2, L3
								f(0.98f), // total power factor
								f(50f))) // frequency
				.activate(MyConfig.create() //
						.setId(METER_ID) //
						.setModbusId(MODBUS_ID) //
						.setType(PRODUCTION) //
						.setPhaseWiring(PhaseWiring.THREE_PHASE_FOUR_WIRE) //
						.build()) //
				.next(new TestCase()) //
				.next(new TestCase()) //
				.next(new TestCase() //
						// Voltage: V -> mV (SCALE_FACTOR_3)
						.output(ElectricityMeter.ChannelId.VOLTAGE_L1, 230000) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L2, 230000) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L3, 230000) //
						.output(ElectricityMeter.ChannelId.VOLTAGE, 230000) // average of phases
						// Line-to-line voltages: V -> mV
						.output(AcrelAdl400Meter.ChannelId.VOLTAGE_L1_L2, 400000) //
						.output(AcrelAdl400Meter.ChannelId.VOLTAGE_L2_L3, 400000) //
						.output(AcrelAdl400Meter.ChannelId.VOLTAGE_L3_L1, 400000) //
						// Frequency: Hz -> mHz
						.output(ElectricityMeter.ChannelId.FREQUENCY, 50000) //
						// Current: A -> mA
						.output(ElectricityMeter.ChannelId.CURRENT_L1, 10000) //
						.output(ElectricityMeter.ChannelId.CURRENT, 30000) // sum of phases
						// Power: kW -> W (SCALE_FACTOR_3)
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 1000) //
						.output(ElectricityMeter.ChannelId.REACTIVE_POWER, 300) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, 300) //
						.output(ElectricityMeter.ChannelId.REACTIVE_POWER_L1, 100) //
						// Harmonic distortion: 15000 -> 15.0%
						.output(AcrelAdl400Meter.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L1, 15.0f) //
						.output(AcrelAdl400Meter.ChannelId.CURRENT_HARMONIC_DISTORTION_L1, 5.0f) //
						// Power factor (unscaled float)
						.output(AcrelAdl400Meter.ChannelId.POWER_FACTOR_L1, 0.95f) //
						.output(AcrelAdl400Meter.ChannelId.POWER_FACTOR_L2, 0.96f) //
						.output(AcrelAdl400Meter.ChannelId.POWER_FACTOR_L3, 0.97f) //
						.output(AcrelAdl400Meter.ChannelId.POWER_FACTOR, 0.98f) //
						// Energy: 0.1 kWh -> Wh (SCALE_FACTOR_2); forward -> PRODUCTION
						.output(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, 10000L) //
						.output(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, 20000L) //
						.output(AcrelAdl400Meter.ChannelId.REACTIVE_LAGGING_ENERGY, 30000L) //
						.output(AcrelAdl400Meter.ChannelId.REACTIVE_LEADING_ENERGY, 40000L));
	}

	@Test
	public void test3P3W() throws Exception {
		new ComponentTest(new AcrelAdl400MeterImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setModbus", new DummyModbusBridge(MODBUS_ID) //
						// THD: 0x05DD..0x05E2
						.withRegisters(0x05DD, new int[] { 15000, 15000, 15000, 5000, 5000, 5000 }) //
						// Energy: 0x084C..0x0875
						.withRegisters(0x084C, i32(100), dummy(8), i32(200), dummy(18), i32(300), dummy(8), i32(400)) //
						// Contiguous float block 0x0800..0x0835 (primary-side data)
						.withRegisters(0x0800, //
								dummy(6), // per-phase voltages L1, L2, L3 (not used in 3P3W)
								f(400f), f(400f), f(400f), // line-to-line voltages A-B, C-B, A-C
								f(10f), f(10f), f(10f), // currents L1, L2, L3
								dummy(8), // zero-line current + per-phase active power
								f(1.0f), // total active power
								dummy(6), // per-phase reactive power
								f(0.3f), // total reactive power
								dummy(16), // apparent power + power factors
								f(50f))) // frequency
				.activate(MyConfig.create() //
						.setId(METER_ID) //
						.setModbusId(MODBUS_ID) //
						.setType(PRODUCTION) //
						.setPhaseWiring(PhaseWiring.THREE_PHASE_THREE_WIRE) //
						.build()) //
				.next(new TestCase()) //
				.next(new TestCase()) //
				.next(new TestCase() //
						// L-L Voltage: V -> mV (SCALE_FACTOR_3)
						.output(AcrelAdl400Meter.ChannelId.VOLTAGE_L1_L2, 400000) //
						.output(AcrelAdl400Meter.ChannelId.VOLTAGE_L2_L3, 400000) //
						.output(AcrelAdl400Meter.ChannelId.VOLTAGE_L3_L1, 400000) //
						// Phase voltages derived from line-to-line: 400000 / sqrt(3)
						.output(ElectricityMeter.ChannelId.VOLTAGE_L1, 230940) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L2, 230940) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L3, 230940) //
						.output(ElectricityMeter.ChannelId.VOLTAGE, 230940) // average of phases
						// Power: kW -> W (SCALE_FACTOR_3)
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 1000) //
						.output(ElectricityMeter.ChannelId.REACTIVE_POWER, 300) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, null)); // No per-phase power
	}
}
