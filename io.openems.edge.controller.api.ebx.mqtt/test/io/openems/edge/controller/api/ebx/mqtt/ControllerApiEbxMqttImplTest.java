package io.openems.edge.controller.api.ebx.mqtt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.Test;

import io.openems.common.test.TimeLeapClock;
import io.openems.edge.bridge.mqtt.api.MqttVersion;
import io.openems.edge.bridge.mqtt.test.DummyBridgeMqtt;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.controller.api.ebx.mqtt.EbxFrameCodec.PowerCommand;
import io.openems.edge.controller.ess.ebx.AssetStatus;
import io.openems.edge.controller.ess.ebx.test.DummyControllerEssEbx;
import io.openems.edge.oros.ess.test.DummyEnergyStorageSystem;

public class ControllerApiEbxMqttImplTest {

	private static final String CTRL_API_ID = "ctrlApiEbxMqtt0";
	private static final String TOPIC_ROOT = "ebx/vpp/test-asset";
	private static final String COMMAND_TOPIC = TOPIC_ROOT + "/command/power/v1";
	private static final String POWER_TOPIC = TOPIC_ROOT + "/telemetry/power/v1";
	private static final String AVAILABILITY_TOPIC = TOPIC_ROOT + "/telemetry/availability/v1";
	private static final String CONSTRAINTS_TOPIC = TOPIC_ROOT + "/telemetry/constraints/v1";

	private TimeLeapClock clock;
	private DummyBridgeMqtt mqtt;
	private DummyControllerEssEbx ctrl;
	private ControllerApiEbxMqttImpl sut;
	private ComponentTest test;

	private void setup(MqttVersion mqttVersion) throws Exception {
		this.clock = new TimeLeapClock(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC);
		this.mqtt = new DummyBridgeMqtt("mqtt0").withMqttVersion(mqttVersion);
		var ess = new DummyEnergyStorageSystem("ess0") //
				.withActivePower(5000);
		ess.getAvailableDischargeEnergyChannel().setNextValue(800_000);
		ess.getAvailableChargeEnergyChannel().setNextValue(200_000);
		ess.getAvailableDischargeEnergyChannel().nextProcessImage();
		ess.getAvailableChargeEnergyChannel().nextProcessImage();
		this.ctrl = new DummyControllerEssEbx("ctrlEssEbx0") //
				.withEnergyStorageSystem(ess);
		this.ctrl._setAssetStatus(AssetStatus.FULL_AVAILABILITY);
		this.ctrl._setAvailableDischargePower(10_000);
		this.ctrl._setAvailableChargePower(10_000);
		this.sut = new ControllerApiEbxMqttImpl();
		this.test = new ComponentTest(this.sut) //
				.addReference("componentManager", new DummyComponentManager(this.clock)) //
				.addReference("mqtt", this.mqtt) //
				.addReference("ctrl", this.ctrl) //
				.activate(MyConfig.create() //
						.setId(CTRL_API_ID) //
						.setMqttId("mqtt0") //
						.setCtrlEssEbxId("ctrlEssEbx0") //
						.setTopic(TOPIC_ROOT) //
						.build());
	}

	private void simulateCommand(long sourceTsMs, float powerKw) throws Exception {
		this.mqtt.simulateMessage(COMMAND_TOPIC, EbxFrameCodec.encodePowerCommand(//
				new PowerCommand(sourceTsMs, sourceTsMs + 5000, powerKw)));
	}

	@Test
	public void testSubscribesAndOverwritesRetained() throws Exception {
		this.setup(MqttVersion.V5);

		assertTrue(this.mqtt.isSubscribed(COMMAND_TOPIC));

		var availability = this.mqtt.getPublishedMessage(AVAILABILITY_TOPIC);
		assertNotNull(availability);
		assertTrue(availability.retained());
		var constraints = this.mqtt.getPublishedMessage(CONSTRAINTS_TOPIC);
		assertNotNull(constraints);
		assertTrue(constraints.retained());

		assertNull(this.mqtt.getPublishedMessage(POWER_TOPIC));
	}

	@Test
	public void testWrongMqttVersion() throws Exception {
		this.setup(MqttVersion.V3_1_1);

		assertFalse(this.mqtt.isSubscribed(COMMAND_TOPIC));
	}

	@Test
	public void testCommandDispatchAndTimestampHandling() throws Exception {
		this.setup(MqttVersion.V5);
		var base = Instant.now(this.clock).toEpochMilli();

		// The first valid command is dispatched.
		this.simulateCommand(base + 1000, 5f);
		assertEquals(1, this.ctrl.getAppliedCommands().size());
		assertEquals(5000, this.ctrl.getAppliedCommands().get(0).power());
		assertEquals(1, this.ctrl.getReportedHeartbeats().size());

		// A duplicate timestamp is discarded without a heartbeat.
		this.simulateCommand(base + 1000, 6f);
		assertEquals(1, this.ctrl.getAppliedCommands().size());
		assertEquals(1, this.ctrl.getReportedHeartbeats().size());

		// After a sender clock step backwards the first older frame is discarded but
		// moves the baseline, the following frame is accepted again.
		this.simulateCommand(base - 5000, 7f);
		assertEquals(1, this.ctrl.getAppliedCommands().size());
		this.simulateCommand(base - 4000, 8f);
		assertEquals(2, this.ctrl.getAppliedCommands().size());
		assertEquals(8000, this.ctrl.getAppliedCommands().get(1).power());

		// A frame without finite setpoint counts as heartbeat but dispatches nothing.
		this.simulateCommand(base - 3000, Float.NaN);
		assertEquals(2, this.ctrl.getAppliedCommands().size());
		assertEquals(3, this.ctrl.getReportedHeartbeats().size());

		// A zero setpoint is suppressed on the wire by proto3 default semantics and
		// still dispatches as a valid 0 kW command.
		this.simulateCommand(base - 2000, 0f);
		assertEquals(3, this.ctrl.getAppliedCommands().size());
		assertEquals(0, this.ctrl.getAppliedCommands().get(2).power());
	}

	@Test
	public void testCyclicPublicationIsRateLimited() throws Exception {
		this.setup(MqttVersion.V5);

		// Publication starts with the first cycle, no command is required. EBX
		// withholds dispatch until the reported asset status shows availability, so
		// gating publication on a received command would deadlock the start-up.
		this.test.next(new TestCase());
		var first = this.mqtt.getPublishedMessage(POWER_TOPIC);
		assertNotNull(first);
		assertFalse(first.retained());

		// A second cycle within the publish interval does not publish again.
		this.test.next(new TestCase());
		assertEquals(first, this.mqtt.getPublishedMessage(POWER_TOPIC));

		// After the interval has elapsed the next cycle publishes again.
		this.clock.leap(1, ChronoUnit.SECONDS);
		this.test.next(new TestCase());
		var second = this.mqtt.getPublishedMessage(POWER_TOPIC);
		assertFalse(first.equals(second));
	}
}
