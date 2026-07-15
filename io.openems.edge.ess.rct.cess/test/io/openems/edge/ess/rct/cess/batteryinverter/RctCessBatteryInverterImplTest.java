package io.openems.edge.ess.rct.cess.batteryinverter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import io.openems.common.channel.AccessMode;
import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.battery.api.BatteryErrorAcknowledge;
import io.openems.edge.batteryinverter.api.HybridManagedSymmetricBatteryInverter;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStopConfig;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.AbstractDummyOpenemsComponent;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.ess.dccharger.api.EssDcCharger;
import io.openems.edge.ess.rct.cess.battery.RctCessBattery;
import io.openems.edge.ess.rct.cess.charger.RctCessDcChargerImpl;
import io.openems.edge.oros.bms.api.BatteryManagementSystem;
import io.openems.edge.oros.pcs.api.PowerConversionSystem;

public class RctCessBatteryInverterImplTest {

	private static class DummyBms extends AbstractDummyOpenemsComponent<DummyBms> implements RctCessBattery {

		public DummyBms(String id) {
			super(id, //
					OpenemsComponent.ChannelId.values(), //
					ModbusComponent.ChannelId.values(), //
					StartStoppable.ChannelId.values(), //
					Battery.ChannelId.values(), //
					BatteryErrorAcknowledge.ChannelId.values(), //
					BatteryManagementSystem.ChannelId.values(), //
					RctCessBattery.ChannelId.values());
		}

		@Override
		protected DummyBms self() {
			return this;
		}

		@Override
		public void setStartStop(StartStop value) {
		}

		@Override
		public StartStop getStartStopTarget() {
			return StartStop.UNDEFINED;
		}

		@Override
		public void retryModbusCommunication() {
		}

		@Override
		public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
			return new ModbusSlaveTable(//
					OpenemsComponent.getModbusSlaveNatureTable(accessMode));
		}
	}

	private static RctCessDcChargerImpl activateCharger(String id) throws Exception {
		var charger = new RctCessDcChargerImpl();
		new ComponentTest(charger) //
				.activate(io.openems.edge.ess.rct.cess.charger.MyConfig.create() //
						.setId(id) //
						.build());
		return charger;
	}

	private static ComponentTest prepareTest(RctCessBatteryInverterImpl sut, DummyBms bms,
			RctCessDcChargerImpl... chargers) throws Exception {
		var test = new ComponentTest(sut) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager()) //
				.addReference("setModbus", new DummyModbusBridge("modbus0")) //
				.addReference("bms", bms);
		var chargerIds = new String[chargers.length];
		for (var i = 0; i < chargers.length; i++) {
			test.addReference("addCharger", chargers[i]);
			chargerIds[i] = chargers[i].id();
		}
		return test.activate(MyConfig.create() //
				.setId("batteryInverter0") //
				.setModbusId("modbus0") //
				.setBmsId("bms0") //
				.setChargerIds(chargerIds) //
				.setStartStop(StartStopConfig.AUTO) //
				.build());
	}

	@Test
	public void testPvSplitAndDcDischargePower() throws Exception {
		var bms = new DummyBms("bms0");
		var charger = activateCharger("charger0");

		prepareTest(new RctCessBatteryInverterImpl(), bms, charger) //
				.next(new TestCase() //
						.input(PowerConversionSystem.ChannelId.DC_POWER, 50_000) //
						.input("bms0", BatteryManagementSystem.ChannelId.RACK_POWER, 30_000) //
						// Flush the listener-calculated ACTUAL_POWER into 'value', like the real
						// Cycle process-image does for all components.
						.onAfterProcessImage(() -> charger.getActualPowerChannel().nextProcessImage()) //
						.output("charger0", EssDcCharger.ChannelId.ACTUAL_POWER, 20_000) //
						.output(RctCessBatteryInverter.ChannelId.DC_PV_POWER, 20_000) //
						.output(HybridManagedSymmetricBatteryInverter.ChannelId.DC_DISCHARGE_POWER, 30_000)) //
				.next(new TestCase("charging with PV") //
						.input(PowerConversionSystem.ChannelId.DC_POWER, -10_000) //
						.input("bms0", BatteryManagementSystem.ChannelId.RACK_POWER, -25_000) //
						.onAfterProcessImage(() -> charger.getActualPowerChannel().nextProcessImage()) //
						.output("charger0", EssDcCharger.ChannelId.ACTUAL_POWER, 15_000) //
						.output(RctCessBatteryInverter.ChannelId.DC_PV_POWER, 15_000) //
						.output(HybridManagedSymmetricBatteryInverter.ChannelId.DC_DISCHARGE_POWER, -25_000));
	}

	@Test
	public void testWithoutCharger() throws Exception {
		var bms = new DummyBms("bms0");
		var sut = new RctCessBatteryInverterImpl();

		prepareTest(sut, bms) //
				.next(new TestCase() //
						.input(PowerConversionSystem.ChannelId.DC_POWER, 40_000) //
						.input("bms0", BatteryManagementSystem.ChannelId.RACK_POWER, 40_000) //
						.output(RctCessBatteryInverter.ChannelId.DC_PV_POWER, null) //
						.output(HybridManagedSymmetricBatteryInverter.ChannelId.DC_DISCHARGE_POWER, 40_000));

		assertNull(sut.getSurplusPower());
	}

	@Test
	public void testSurplusPower() throws Exception {
		var bms = new DummyBms("bms0");
		var charger = activateCharger("charger0");
		var sut = new RctCessBatteryInverterImpl();
		var test = prepareTest(sut, bms, charger);

		test.next(new TestCase("battery not full") //
				.input(PowerConversionSystem.ChannelId.DC_POWER, 20_000) //
				.input("bms0", BatteryManagementSystem.ChannelId.RACK_POWER, 0) //
				.input("bms0", Battery.ChannelId.SOC, 50) //
				.onAfterProcessImage(() -> charger.getActualPowerChannel().nextProcessImage()));
		assertEquals(Integer.valueOf(0), sut.getSurplusPower());

		test.next(new TestCase("battery full") //
				.input("bms0", Battery.ChannelId.SOC, 100) //
				.onAfterProcessImage(() -> charger.getActualPowerChannel().nextProcessImage()));
		assertEquals(Integer.valueOf(20_000), sut.getSurplusPower());
	}

}
