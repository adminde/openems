package io.openems.edge.controller.ess.ebx;

import static io.openems.edge.controller.ess.ebx.ControllerEssEbx.ChannelId.SETPOINT_CLAMPED;
import static io.openems.edge.controller.ess.ebx.ControllerEssEbx.ChannelId.SETPOINT_EXPIRED;
import static io.openems.edge.controller.ess.ebx.ControllerEssEbx.ChannelId.STATE_MACHINE;
import static io.openems.edge.ess.api.ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_EQUALS;
import static io.openems.edge.oros.ess.api.EnergyStorageSystem.ChannelId.MAX_ACTIVE_POWER;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import io.openems.common.test.TimeLeapClock;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.DummyCycle;
import io.openems.edge.controller.ess.ebx.statemachine.StateMachine.State;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.ess.test.DummyPower;
import io.openems.edge.oros.ess.test.DummyEnergyStorageSystem;

public class ControllerEssEbxImplTest {

	private static final String CTRL_ID = "ctrl0";
	private static final String ESS_ID = "ess0";

	private TimeLeapClock clock;
	private AtomicLong monotonic;
	private ControllerEssEbxImpl sut;
	private ControllerTest test;

	private void setup(MyConfig config) throws Exception {
		this.clock = new TimeLeapClock(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC);
		this.monotonic = new AtomicLong(0);
		this.sut = new ControllerEssEbxImpl();
		this.sut.setMonotonicTime(this.monotonic::get);
		this.test = new ControllerTest(this.sut) //
				.addReference("componentManager", new DummyComponentManager(this.clock)) //
				.addReference("cycle", new DummyCycle(1000)) //
				.addReference("ess", new DummyEnergyStorageSystem(ESS_ID) //
						.setPower(new DummyPower(10_000))) //
				.activate(config);
	}

	private void reportHealthy(int power) {
		this.sut.reportHeartbeat(Instant.now(this.clock));
		this.sut.applyCommand(power, Instant.now(this.clock).plusSeconds(5));
	}

	@Test
	public void testDispatchAndFailSafe() throws Exception {
		this.setup(MyConfig.create() //
				.setId(CTRL_ID) //
				.setEssId(ESS_ID) //
				.setRampRate(20.0) //
				.setHeartbeatTimeout(10) //
				.build());

		// Boot: no heartbeat, no command, the ESS stays released.
		this.test.next(new TestCase() //
				.input(ESS_ID, MAX_ACTIVE_POWER, 10_000) //
				.output(STATE_MACHINE, State.UNDEFINED) //
				.output(ESS_ID, SET_ACTIVE_POWER_EQUALS, null));

		// The first valid command switches to DISPATCHING directly.
		this.reportHealthy(5000);
		this.test.next(new TestCase() //
				.output(STATE_MACHINE, State.DISPATCHING) //
				.output(ESS_ID, SET_ACTIVE_POWER_EQUALS, 5000));

		// A target outside the envelope is clamped and flagged.
		this.reportHealthy(50_000);
		this.test.next(new TestCase() //
				.output(STATE_MACHINE, State.DISPATCHING) //
				.output(SETPOINT_CLAMPED, true) //
				.output(ESS_ID, SET_ACTIVE_POWER_EQUALS, 10_000));

		// Heartbeat loss ramps down at 20 %/s of 10 kW, so 2 kW per cycle.
		this.monotonic.addAndGet(11_000_000_000L);
		this.clock.leap(11, ChronoUnit.SECONDS);
		this.test.next(new TestCase() //
				.output(STATE_MACHINE, State.FAIL_SAFE) //
				.output(ESS_ID, SET_ACTIVE_POWER_EQUALS, 8000));
		this.test.next(new TestCase().output(ESS_ID, SET_ACTIVE_POWER_EQUALS, 6000));
		this.test.next(new TestCase().output(ESS_ID, SET_ACTIVE_POWER_EQUALS, 4000));
		this.test.next(new TestCase().output(ESS_ID, SET_ACTIVE_POWER_EQUALS, 2000));
		this.test.next(new TestCase().output(ESS_ID, SET_ACTIVE_POWER_EQUALS, 0));

		// At zero the ESS is released.
		this.test.next(new TestCase() //
				.output(STATE_MACHINE, State.IDLE_SAFE) //
				.output(ESS_ID, SET_ACTIVE_POWER_EQUALS, null));

		// Recovery returns to DISPATCHING without ramping.
		this.reportHealthy(-3000);
		this.test.next(new TestCase() //
				.output(STATE_MACHINE, State.DISPATCHING) //
				.output(ESS_ID, SET_ACTIVE_POWER_EQUALS, -3000));
	}

	@Test
	public void testCommandExpiryTriggersFailSafe() throws Exception {
		this.setup(MyConfig.create() //
				.setId(CTRL_ID) //
				.setEssId(ESS_ID) //
				.setRampRate(20.0) //
				.setHeartbeatTimeout(10) //
				.build());

		this.test.next(new TestCase() //
				.input(ESS_ID, MAX_ACTIVE_POWER, 10_000));

		this.reportHealthy(4000);
		this.test.next(new TestCase() //
				.output(STATE_MACHINE, State.DISPATCHING) //
				.output(ESS_ID, SET_ACTIVE_POWER_EQUALS, 4000));

		// The command expires while the heartbeat stays fresh.
		this.clock.leap(6, ChronoUnit.SECONDS);
		this.sut.reportHeartbeat(Instant.now(this.clock));
		this.test.next(new TestCase() //
				.output(STATE_MACHINE, State.FAIL_SAFE) //
				.output(SETPOINT_EXPIRED, true) //
				.output(ESS_ID, SET_ACTIVE_POWER_EQUALS, 2000));

		// A fresh command recovers directly from FAIL_SAFE.
		this.reportHealthy(4000);
		this.test.next(new TestCase() //
				.output(STATE_MACHINE, State.DISPATCHING) //
				.output(SETPOINT_EXPIRED, false) //
				.output(ESS_ID, SET_ACTIVE_POWER_EQUALS, 4000));
	}

	@Test
	public void testAlwaysApplyRamp() throws Exception {
		this.setup(MyConfig.create() //
				.setId(CTRL_ID) //
				.setEssId(ESS_ID) //
				.setRampRate(20.0) //
				.setHeartbeatTimeout(10) //
				.setAlwaysApplyRamp(true) //
				.build());

		this.test.next(new TestCase() //
				.input(ESS_ID, MAX_ACTIVE_POWER, 10_000));

		// The first target is approached from zero in 2 kW steps.
		this.reportHealthy(5000);
		this.test.next(new TestCase() //
				.output(STATE_MACHINE, State.DISPATCHING) //
				.output(ESS_ID, SET_ACTIVE_POWER_EQUALS, 2000));
		this.reportHealthy(5000);
		this.test.next(new TestCase() //
				.output(ESS_ID, SET_ACTIVE_POWER_EQUALS, 4000));
		this.reportHealthy(5000);
		this.test.next(new TestCase() //
				.output(ESS_ID, SET_ACTIVE_POWER_EQUALS, 5000));
	}
}
