package io.openems.edge.meter.phoenixcontact;

import org.junit.Test;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.meter.api.ElectricityMeter;

public class PhoenixContactMeterImplTest {

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
		new ComponentTest(new PhoenixContactMeterImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setModbus", new DummyModbusBridge(MODBUS_ID) //
						// Active Energy consumed/delivered: 0x9306..0x930F
						.withInputRegisters(0x9306, f(10000f), dummy(6), f(20000f)) //
						// Reactive Energy consumed/delivered: 0x9350..0x9353
						.withInputRegisters(0x9350, f(30000f), f(40000f)) //
						// Contiguous instantaneous block: 0x8000..0x8035
						.withInputRegisters(0x8000, //
								f(400f), f(400f), f(400f), // U12, U23, U31
								f(230f), f(230f), f(230f), // U1, U2, U3
								f(50f), // Frequency
								f(10f), f(10f), f(10f), // I1, I2, I3
								dummy(2), // IN
								f(1000f), f(500f), // total P, Q
								dummy(2), // total S
								f(0.95f), // total power factor
								f(300f), f(300f), f(400f), // P L1, L2, L3
								f(100f), f(200f), f(200f), // Q L1, L2, L3
								dummy(6), // S L1, L2, L3
								f(0.99f), f(0.98f), f(0.97f)) // PF L1, L2, L3
						// Phase Angles: 0x8043..0x804A
						.withInputRegisters(0x8043, f(15.0f), f(15.0f), f(15.1f), f(14.9f)) //
						// THD: 0x8806..0x8811
						.withInputRegisters(0x8806, f(1.1f), f(1.2f), f(1.3f), f(2.1f), f(2.2f), f(2.3f))) //
				.activate(MyConfig.create() //
						.setId(METER_ID) //
						.setModbusId(MODBUS_ID) //
						.setPhaseWiring(PhaseWiring.THREE_PHASE_FOUR_WIRE) //
						.build()) //
				// One LOW-priority Modbus task executes per cycle; four cycles are
				// needed until both energy tasks, phase angles and THD have been read.
				.next(new TestCase()) //
				.next(new TestCase()) //
				.next(new TestCase()) //
				.next(new TestCase() //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L1, 230000) //
						.output(ElectricityMeter.ChannelId.VOLTAGE, 230000) //
						.output(PhoenixContactMeter.ChannelId.VOLTAGE_L1_L2, 400000) //
						.output(ElectricityMeter.ChannelId.FREQUENCY, 50000) //
						.output(ElectricityMeter.ChannelId.CURRENT_L1, 10000) //
						.output(ElectricityMeter.ChannelId.CURRENT, 30000) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 1000) //
						.output(ElectricityMeter.ChannelId.REACTIVE_POWER, 500) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, 300) //
						.output(ElectricityMeter.ChannelId.REACTIVE_POWER_L1, 100) //
						.output(PhoenixContactMeter.ChannelId.POWER_FACTOR, 0.95f) //
						.output(PhoenixContactMeter.ChannelId.POWER_FACTOR_L1, 0.99f) //
						.output(PhoenixContactMeter.ChannelId.POWER_FACTOR_L2, 0.98f) //
						.output(PhoenixContactMeter.ChannelId.POWER_FACTOR_L3, 0.97f) //
						// "consumed" (import) -> ACTIVE_PRODUCTION_ENERGY
						.output(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, 10000L) //
						.output(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, 20000L) //
						.output(PhoenixContactMeter.ChannelId.REACTIVE_LAGGING_ENERGY, 30000L) //
						.output(PhoenixContactMeter.ChannelId.REACTIVE_LEADING_ENERGY, 40000L) //
						.output(PhoenixContactMeter.ChannelId.PHASE_ANGLE, 15.0f) //
						.output(PhoenixContactMeter.ChannelId.PHASE_ANGLE_L1, 15.0f) //
						.output(PhoenixContactMeter.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L1, 1.1f) //
						.output(PhoenixContactMeter.ChannelId.CURRENT_HARMONIC_DISTORTION_L1, 2.1f));
	}

	@Test
	public void test3P4WInverted() throws Exception {
		new ComponentTest(new PhoenixContactMeterImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setModbus", new DummyModbusBridge(MODBUS_ID) //
						.withInputRegisters(0x9306, f(10000f), dummy(6), f(20000f)) //
						.withInputRegisters(0x9350, f(30000f), f(40000f)) //
						.withInputRegisters(0x8000, //
								f(400f), f(400f), f(400f), //
								f(230f), f(230f), f(230f), //
								f(50f), //
								f(10f), f(10f), f(10f), //
								dummy(2), //
								f(1000f), f(500f), //
								dummy(2), //
								f(0.95f), //
								f(300f), f(300f), f(400f), //
								f(100f), f(200f), f(200f), //
								dummy(6), //
								f(0.99f), f(0.98f), f(0.97f)) //
						.withInputRegisters(0x8043, f(15.0f), f(15.0f), f(15.1f), f(14.9f)) //
						.withInputRegisters(0x8806, f(1.1f), f(1.2f), f(1.3f), f(2.1f), f(2.2f), f(2.3f))) //
				.activate(MyConfig.create() //
						.setId(METER_ID) //
						.setModbusId(MODBUS_ID) //
						.setPhaseWiring(PhaseWiring.THREE_PHASE_FOUR_WIRE) //
						.setInvert(true) //
						.build()) //
				// One LOW-priority Modbus task executes per cycle; four cycles are
				// needed until both energy tasks, phase angles and THD have been read.
				.next(new TestCase()) //
				.next(new TestCase()) //
				.next(new TestCase()) //
				.next(new TestCase() //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, -1000) //
						.output(ElectricityMeter.ChannelId.REACTIVE_POWER, -500) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, -300) //
						.output(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, 20000L) //
						.output(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, 10000L) //
						.output(PhoenixContactMeter.ChannelId.REACTIVE_LAGGING_ENERGY, 40000L) //
						.output(PhoenixContactMeter.ChannelId.REACTIVE_LEADING_ENERGY, 30000L));
	}

	@Test
	public void test3P3W() throws Exception {
		new ComponentTest(new PhoenixContactMeterImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setModbus", new DummyModbusBridge(MODBUS_ID) //
						// Active Energy consumed/delivered: 0x9306..0x930F
						.withInputRegisters(0x9306, f(10000f), dummy(6), f(20000f)) //
						// Reactive Energy consumed/delivered: 0x9350..0x9353
						.withInputRegisters(0x9350, f(30000f), f(40000f)) //
						// Contiguous instantaneous block: 0x8000..0x801D
						.withInputRegisters(0x8000, //
								f(400f), f(400f), f(400f), // U12, U23, U31
								dummy(6), // phase voltages
								f(50f), // Frequency
								f(10f), f(10f), f(10f), // I1, I2, I3
								dummy(2), // IN
								f(1000f), f(500f), // total P, Q
								dummy(2), // total S
								f(0.95f)) // total power factor
						// Phase Angle: 0x8043..0x8044
						.withInputRegisters(0x8043, f(15.0f)) //
						// THD: 0x8800..0x8811
						.withInputRegisters(0x8800, f(2.5f), f(2.6f), dummy(8), f(3.1f), dummy(2), f(3.3f))) //
				.activate(MyConfig.create() //
						.setId(METER_ID) //
						.setModbusId(MODBUS_ID) //
						.setPhaseWiring(PhaseWiring.THREE_PHASE_THREE_WIRE) //
						.build()) //
				// One LOW-priority Modbus task executes per cycle; four cycles are
				// needed until both energy tasks, phase angles and THD have been read.
				.next(new TestCase()) //
				.next(new TestCase()) //
				.next(new TestCase()) //
				.next(new TestCase() //
						.output(PhoenixContactMeter.ChannelId.VOLTAGE_L1_L2, 400000) //
						// Phase voltages derived as L-L / sqrt(3)
						.output(ElectricityMeter.ChannelId.VOLTAGE_L1, 230940) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L2, 230940) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L3, 230940) //
						.output(ElectricityMeter.ChannelId.VOLTAGE, 230940) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 1000) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, null) //
						.output(PhoenixContactMeter.ChannelId.POWER_FACTOR, 0.95f) //
						.output(PhoenixContactMeter.ChannelId.POWER_FACTOR_L1, null) //
						.output(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, 10000L) //
						.output(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, 20000L) //
						.output(PhoenixContactMeter.ChannelId.REACTIVE_LAGGING_ENERGY, 30000L) //
						.output(PhoenixContactMeter.ChannelId.REACTIVE_LEADING_ENERGY, 40000L) //
						.output(PhoenixContactMeter.ChannelId.PHASE_ANGLE, 15.0f) //
						.output(PhoenixContactMeter.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L1_L2, 2.5f) //
						.output(PhoenixContactMeter.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L2_L3, 2.6f) //
						.output(PhoenixContactMeter.ChannelId.CURRENT_HARMONIC_DISTORTION_L1, 3.1f) //
						.output(PhoenixContactMeter.ChannelId.CURRENT_HARMONIC_DISTORTION_L3, 3.3f));
	}

	@Test
	public void test1P2W() throws Exception {
		new ComponentTest(new PhoenixContactMeterImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setModbus", new DummyModbusBridge(MODBUS_ID) //
						// Active Energy consumed/delivered: 0x9306..0x930F
						.withInputRegisters(0x9306, f(10000f), dummy(6), f(20000f)) //
						// Reactive Energy consumed/delivered: 0x9350..0x9353
						.withInputRegisters(0x9350, f(30000f), f(40000f)) //
						// Contiguous instantaneous block: 0x8006..0x8031
						.withInputRegisters(0x8006, //
								f(230f), // U1
								dummy(4), // U2, U3
								f(50f), // Frequency
								f(10f), // I1
								dummy(6), // I2, I3, IN
								f(1000f), f(500f), // total P, Q
								dummy(2), // total S
								f(0.95f), // total power factor
								f(1000f), // P L1
								dummy(4), // P L2, L3
								f(500f), // Q L1
								dummy(10), // Q L2, L3; S L1, L2, L3
								f(0.95f)) // PF L1
						// Phase Angles: 0x8043..0x8046
						.withInputRegisters(0x8043, f(15.0f), f(15.0f)) //
						// THD: 0x8806..0x880D
						.withInputRegisters(0x8806, f(1.1f), dummy(4), f(2.1f))) //
				.activate(MyConfig.create() //
						.setId(METER_ID) //
						.setModbusId(MODBUS_ID) //
						.setPhaseWiring(PhaseWiring.SINGLE_PHASE_TWO_WIRE) //
						.build()) //
				// One LOW-priority Modbus task executes per cycle; four cycles are
				// needed until both energy tasks, phase angles and THD have been read.
				.next(new TestCase()) //
				.next(new TestCase()) //
				.next(new TestCase()) //
				.next(new TestCase() //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L1, 230000) //
						.output(ElectricityMeter.ChannelId.VOLTAGE, 230000) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L2, null) //
						.output(PhoenixContactMeter.ChannelId.VOLTAGE_L1_L2, null) //
						.output(ElectricityMeter.ChannelId.FREQUENCY, 50000) //
						.output(ElectricityMeter.ChannelId.CURRENT_L1, 10000) //
						.output(ElectricityMeter.ChannelId.CURRENT, 10000) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 1000) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, 1000) //
						.output(ElectricityMeter.ChannelId.REACTIVE_POWER, 500) //
						.output(ElectricityMeter.ChannelId.REACTIVE_POWER_L1, 500) //
						.output(PhoenixContactMeter.ChannelId.POWER_FACTOR, 0.95f) //
						.output(PhoenixContactMeter.ChannelId.POWER_FACTOR_L1, 0.95f) //
						.output(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, 10000L) //
						.output(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, 20000L) //
						.output(PhoenixContactMeter.ChannelId.REACTIVE_LAGGING_ENERGY, 30000L) //
						.output(PhoenixContactMeter.ChannelId.REACTIVE_LEADING_ENERGY, 40000L) //
						.output(PhoenixContactMeter.ChannelId.PHASE_ANGLE, 15.0f) //
						.output(PhoenixContactMeter.ChannelId.PHASE_ANGLE_L1, 15.0f) //
						.output(PhoenixContactMeter.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L1, 1.1f) //
						.output(PhoenixContactMeter.ChannelId.CURRENT_HARMONIC_DISTORTION_L1, 2.1f));
	}
}
