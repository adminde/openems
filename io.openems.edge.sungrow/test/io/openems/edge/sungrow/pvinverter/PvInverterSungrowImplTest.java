package io.openems.edge.sungrow.pvinverter;

import org.junit.jupiter.api.Test;

import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter;

public class PvInverterSungrowImplTest {

	private static final int OFFSET = 1;

	/**
	 * Encodes a 32-bit value as two Modbus registers with the low word first.
	 *
	 * @param value the value
	 * @return the two registers
	 */
	private static int[] u32(long value) {
		return new int[] { (int) (value & 0xFFFF), (int) (value >>> 16 & 0xFFFF) };
	}

	/**
	 * Simulates a Sungrow SG125CX-P2 with twelve MPPT inputs.
	 *
	 * @return the {@link DummyModbusBridge}
	 */
	private static DummyModbusBridge modbusBridge() {
		return new DummyModbusBridge("modbus0") //
				.withInputRegisters(5000 - OFFSET, //
						new int[] { 0x2C2D }, // Device type code SG125CX-P2
						new int[] { 1250 }, // Nominal active power 125.0 kW
						new int[] { 1 }, // Output type 3P4L
						new int[] { 1234 }, // Daily power yields 123.4 kWh
						u32(56789), // Total power yields 56789 kWh
						u32(4321), // Total running time 4321 h
						new int[] { 355 }, // Internal temperature 35.5 degree Celsius
						u32(60300), // Total apparent power 60300 VA
						new int[] { 6000, 100 }, // MPPT 1 600.0 V, 10.0 A
						new int[] { 6100, 110 }, // MPPT 2 610.0 V, 11.0 A
						new int[] { 6200, 120 }, // MPPT 3 620.0 V, 12.0 A
						u32(61000), // Total DC power 61000 W
						new int[] { 2300, 2310, 2320 }, // Phase voltages 230.0 V, 231.0 V, 232.0 V
						new int[] { 870, 880, 890 }, // Phase currents 87.0 A, 88.0 A, 89.0 A
						new int[6], // Reserved
						u32(60000), // Total active power 60000 W
						u32(-1500), // Total reactive power -1500 var
						new int[] { 950 }, // Power factor 0.950
						new int[] { 4999 }, // Grid frequency 49.99 Hz
						new int[] { 0 }, // Reserved
						new int[] { 0x8100 }) // Work state Derating run
				.withInputRegisters(5049 - OFFSET, 750) // Nominal reactive power 75.0 kvar
				.withInputRegisters(5115 - OFFSET, //
						new int[] { 6300, 130 }, // MPPT 4 630.0 V, 13.0 A
						new int[] { 6400, 140 }, // MPPT 5 640.0 V, 14.0 A
						new int[] { 6500, 150 }, // MPPT 6 650.0 V, 15.0 A
						new int[] { 6600, 160 }, // MPPT 7 660.0 V, 16.0 A
						new int[] { 6700, 170 }, // MPPT 8 670.0 V, 17.0 A
						new int[3], // Reserved
						u32(98765), // Monthly power yields 9876.5 kWh
						new int[] { 6800, 180 }, // MPPT 9 680.0 V, 18.0 A
						new int[] { 6900, 190 }, // MPPT 10 690.0 V, 19.0 A
						new int[] { 7000, 200 }, // MPPT 11 700.0 V, 20.0 A
						new int[] { 0xFFFF, 0xFFFF }) // MPPT 12 not available
				.withRegisters(5007 - OFFSET, 0xAA, 400); // Power limitation enabled at 40.0 %
	}

	@Test
	public void testReadOnly() throws Exception {
		new ComponentTest(new PvInverterSungrowImpl()) //
				.addReference("setModbus", modbusBridge()) //
				.activate(MyConfig.create() //
						.setId("pvInverter0") //
						.setReadOnly(true) //
						.setModbusId("modbus0") //
						.setModbusUnitId(1) //
						.setPhaseWiring(PhaseWiring.THREE_PHASE_FOUR_WIRE) //
						.setMaxActivePower(0) //
						.build()) //
				// One low priority task is executed per cycle
				.next(new TestCase()) //
				.next(new TestCase()) //
				.next(new TestCase() //
						.output(PvInverterSungrow.ChannelId.DEVICE_TYPE_CODE, 0x2C2D) //
						.output(ManagedSymmetricPvInverter.ChannelId.MAX_ACTIVE_POWER, 125000) //
						.output(ManagedSymmetricPvInverter.ChannelId.MAX_APPARENT_POWER, 125000) //
						.output(ManagedSymmetricPvInverter.ChannelId.MAX_REACTIVE_POWER, 75000) //
						.output(PvInverterSungrow.ChannelId.OUTPUT_TYPE, OutputType.THREE_PHASE_FOUR_LINE) //
						.output(PvInverterSungrow.ChannelId.WRONG_PHASE_WIRING_CONFIGURED, false) //
						.output(PvInverterSungrow.ChannelId.DAILY_PRODUCTION_ENERGY, 123400) //
						.output(PvInverterSungrow.ChannelId.MONTHLY_PRODUCTION_ENERGY, 9876500) //
						.output(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, 56789000L) //
						.output(PvInverterSungrow.ChannelId.TOTAL_RUNNING_TIME, 4321) //
						.output(PvInverterSungrow.ChannelId.INTERNAL_TEMPERATURE, 355) //
						.output(PvInverterSungrow.ChannelId.APPARENT_POWER, 60300) //
						.output(PvInverterSungrow.ChannelId.MPPT_1_VOLTAGE, 600000) //
						.output(PvInverterSungrow.ChannelId.MPPT_1_CURRENT, 10000) //
						.output(PvInverterSungrow.ChannelId.MPPT_3_VOLTAGE, 620000) //
						.output(PvInverterSungrow.ChannelId.MPPT_3_CURRENT, 12000) //
						.output(PvInverterSungrow.ChannelId.MPPT_4_VOLTAGE, 630000) //
						.output(PvInverterSungrow.ChannelId.MPPT_4_CURRENT, 13000) //
						.output(PvInverterSungrow.ChannelId.MPPT_8_VOLTAGE, 670000) //
						.output(PvInverterSungrow.ChannelId.MPPT_9_VOLTAGE, 680000) //
						.output(PvInverterSungrow.ChannelId.MPPT_11_CURRENT, 20000) //
						.output(PvInverterSungrow.ChannelId.MPPT_12_VOLTAGE, null) //
						.output(PvInverterSungrow.ChannelId.MPPT_12_CURRENT, null) //
						.output(PvInverterSungrow.ChannelId.DC_POWER, 61000) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L1, 230000) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L2, 231000) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L3, 232000) //
						.output(ElectricityMeter.ChannelId.VOLTAGE, 231000) //
						.output(ElectricityMeter.ChannelId.CURRENT_L1, 87000) //
						.output(ElectricityMeter.ChannelId.CURRENT_L2, 88000) //
						.output(ElectricityMeter.ChannelId.CURRENT_L3, 89000) //
						.output(ElectricityMeter.ChannelId.CURRENT, 264000) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 60000) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, 20000) //
						.output(ElectricityMeter.ChannelId.REACTIVE_POWER, -1500) //
						.output(PvInverterSungrow.ChannelId.POWER_FACTOR, 0.95F) //
						.output(ElectricityMeter.ChannelId.FREQUENCY, 499900) //
						.output(PvInverterSungrow.ChannelId.WORK_STATE, WorkState.DERATING_RUN)) //
				.next(new TestCase("Power limit is not applied in read-only mode") //
						.input(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT, 62500) //
						.output(PvInverterSungrow.ChannelId.READ_ONLY_MODE_PV_LIMIT_FAILED, true) //
						.output(PvInverterSungrow.ChannelId.POWER_LIMITATION_SETTING, null)) //
				.next(new TestCase() //
						.output(PvInverterSungrow.ChannelId.READ_ONLY_MODE_PV_LIMIT_FAILED, false)) //
				.deactivate();
	}

	@Test
	public void testThreePhaseThreeWire() throws Exception {
		new ComponentTest(new PvInverterSungrowImpl()) //
				.addReference("setModbus", modbusBridge()) //
				.activate(MyConfig.create() //
						.setId("pvInverter0") //
						.setReadOnly(true) //
						.setModbusId("modbus0") //
						.setModbusUnitId(1) //
						.setPhaseWiring(PhaseWiring.THREE_PHASE_THREE_WIRE) //
						.setMaxActivePower(0) //
						.build()) //
				.next(new TestCase() //
						.output(PvInverterSungrow.ChannelId.VOLTAGE_L1_L2, 230000) //
						.output(PvInverterSungrow.ChannelId.VOLTAGE_L2_L3, 231000) //
						.output(PvInverterSungrow.ChannelId.VOLTAGE_L3_L1, 232000) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L1, 132791) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L2, 133368) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L3, 133945) //
						.output(ElectricityMeter.ChannelId.VOLTAGE, 133368) //
						// The inverter reports a 3P4L output type
						.output(PvInverterSungrow.ChannelId.WRONG_PHASE_WIRING_CONFIGURED, true)) //
				.deactivate();
	}

	@Test
	public void testActivePowerLimit() throws Exception {
		new ComponentTest(new PvInverterSungrowImpl()) //
				.addReference("setModbus", modbusBridge()) //
				.activate(MyConfig.create() //
						.setId("pvInverter0") //
						.setReadOnly(false) //
						.setModbusId("modbus0") //
						.setModbusUnitId(1) //
						.setPhaseWiring(PhaseWiring.THREE_PHASE_FOUR_WIRE) //
						.setMaxActivePower(0) //
						.build()) //
				.next(new TestCase("No limit requested: power limitation is disabled") //
						.output(PvInverterSungrow.ChannelId.POWER_LIMITATION_SWITCH, PowerLimitationSwitch.DISABLE) //
						.output(PvInverterSungrow.ChannelId.POWER_LIMITATION_SETTING, null)) //
				.next(new TestCase("Inverter still reports enabled limitation: disable is written again") //
						.output(PvInverterSungrow.ChannelId.POWER_LIMITATION_SWITCH, PowerLimitationSwitch.DISABLE)) //
				.next(new TestCase() //
						.output(PvInverterSungrow.ChannelId.POWER_LIMITATION_SWITCH, PowerLimitationSwitch.DISABLE)) //
				.next(new TestCase("Limit of 50 % is written, switch is already enabled") //
						.input(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT, 62500) //
						.output(PvInverterSungrow.ChannelId.POWER_LIMITATION_SETTING, 500) //
						.output(PvInverterSungrow.ChannelId.POWER_LIMITATION_SWITCH, PowerLimitationSwitch.UNDEFINED) //
						.output(PvInverterSungrow.ChannelId.PV_LIMIT_FAILED, false)) //
				.next(new TestCase("Limit below 1 % is raised to 1 %") //
						.input(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT, 0) //
						.output(PvInverterSungrow.ChannelId.POWER_LIMITATION_SETTING, 10)) //
				.next(new TestCase("Limit above rated power is capped to 100 %") //
						.input(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT, 200000) //
						.output(PvInverterSungrow.ChannelId.POWER_LIMITATION_SETTING, 1000)) //
				.next(new TestCase("Limit equal to the reported setting is not written again") //
						.input(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT, 50000) //
						.output(PvInverterSungrow.ChannelId.POWER_LIMITATION_SETTING, null)) //
				.next(new TestCase("No limit disables the power limitation") //
						.output(PvInverterSungrow.ChannelId.POWER_LIMITATION_SWITCH, PowerLimitationSwitch.DISABLE) //
						.output(PvInverterSungrow.ChannelId.POWER_LIMITATION_SETTING, null) //
						.output(PvInverterSungrow.ChannelId.PV_LIMIT_FAILED, false)) //
				.deactivate();
	}

	@Test
	public void testConfiguredMaxActivePower() throws Exception {
		new ComponentTest(new PvInverterSungrowImpl()) //
				.addReference("setModbus", modbusBridge()) //
				.activate(MyConfig.create() //
						.setId("pvInverter0") //
						.setReadOnly(false) //
						.setModbusId("modbus0") //
						.setModbusUnitId(1) //
						.setPhaseWiring(PhaseWiring.THREE_PHASE_FOUR_WIRE) //
						.setMaxActivePower(100000) //
						.build()) //
				.next(new TestCase() //
						.output(ManagedSymmetricPvInverter.ChannelId.MAX_ACTIVE_POWER, 100000) //
						.output(ManagedSymmetricPvInverter.ChannelId.MAX_APPARENT_POWER, 100000) //
						.output(PvInverterSungrow.ChannelId.POWER_LIMITATION_SWITCH, PowerLimitationSwitch.DISABLE)) //
				.next(new TestCase("Limit is calculated from the configured rated power") //
						.input(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT, 50000) //
						.output(PvInverterSungrow.ChannelId.POWER_LIMITATION_SETTING, 500)) //
				.deactivate();
	}
}
