package io.openems.edge.oros.ess.core.protection;

import static io.openems.edge.common.test.TestUtils.withValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import io.openems.common.test.TimeLeapClock;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.battery.protection.BatteryProtection;
import io.openems.edge.battery.protection.BatteryVoltageProtection;
import io.openems.edge.battery.test.AbstractDummyBattery;
import io.openems.edge.batteryinverter.api.ManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.test.AbstractDummyOpenemsComponent;
import io.openems.edge.oros.bms.api.BatteryManagementSystem;
import io.openems.edge.oros.common.SymmetricComponent;
import io.openems.edge.oros.ess.api.DummyEnergyStorageSystem;
import io.openems.edge.oros.pcs.api.PowerConversionSystem;

public class PowerLimiterTest {

	private static final int CHARGE_MAX_POWER = 10_000; // [W]
	private static final int DISCHARGE_MAX_POWER = 20_000; // [W]

	/**
	 * Verifies the max-increase ramp of Allowed-Charge-Power and Allowed-Discharge-Power:
	 *
	 * <ul>
	 * <li>the increase per second is limited to maxPowerIncreasePercentage of the
	 * inverter's nominal charge/discharge power
	 * <li>each ramp continues from the last value of the <b>same</b> direction:
	 * the charge ramp from the last Allowed-Charge-Power, the discharge ramp from
	 * the last Allowed-Discharge-Power
	 * <li>the ramp is capped at the target value
	 * </ul>
	 */
	@Test
	public void testMaxIncreaseRamp() {
		final var clock = new TimeLeapClock(Instant.parse("2026-01-01T00:00:00.00Z"), ZoneOffset.UTC);
		final ClockProvider clockProvider = () -> clock;

		final var ess = new DummyEnergyStorageSystem("ess0");
		withValue(ess, StartStoppable.ChannelId.START_STOP, StartStop.START);

		final var battery = new DummyBatteryManagementSystem("bms0");
		final var inverter = new DummyPowerConversionSystem("pcs0");

		// 10 %/s of nominal power: charge ramp = 1000 W/s, discharge ramp = 2000 W/s
		final var sut = new PowerLimiter(ess, inverter, battery, () -> 10.F,
				new OverChargeCurrentLimiter(ess, inverter, battery),
				new DeepDischargeCurrentLimiter(ess, inverter, battery));

		// First run: ramp starts from zero with fixed 1 second
		battery.withVoltage(100)
				.withChargeMaxCurrent(50) // charge target: 5000 W
				.withDischargeMaxCurrent(100); // discharge target: 10000 W
		sut.accept(clockProvider);
		assertEquals(-1000, getAllowedChargePower(ess));
		assertEquals(2000, getAllowedDischargePower(ess));

		// After 1 s each ramp continues from its own last value. If the charge ramp
		// wrongly continued from the last Allowed-Discharge-Power (2000 W), it would
		// reach 3000 W instead of 2000 W.
		clock.leap(1, ChronoUnit.SECONDS);
		sut.accept(clockProvider);
		assertEquals(-2000, getAllowedChargePower(ess));
		assertEquals(4000, getAllowedDischargePower(ess));

		clock.leap(1, ChronoUnit.SECONDS);
		sut.accept(clockProvider);
		assertEquals(-3000, getAllowedChargePower(ess));
		assertEquals(6000, getAllowedDischargePower(ess));

		// After 5 more seconds both ramps are capped at their targets
		clock.leap(5, ChronoUnit.SECONDS);
		sut.accept(clockProvider);
		assertEquals(-5000, getAllowedChargePower(ess));
		assertEquals(10000, getAllowedDischargePower(ess));
	}

	private static int getAllowedChargePower(DummyEnergyStorageSystem ess) {
		return ess.getAllowedChargePowerChannel().getNextValue().get();
	}

	private static int getAllowedDischargePower(DummyEnergyStorageSystem ess) {
		return ess.getAllowedDischargePowerChannel().getNextValue().get();
	}

	private static class DummyBatteryManagementSystem extends AbstractDummyBattery<DummyBatteryManagementSystem>
			implements BatteryManagementSystem {

		public DummyBatteryManagementSystem(String id) {
			super(id, //
					OpenemsComponent.ChannelId.values(), //
					StartStoppable.ChannelId.values(), //
					Battery.ChannelId.values(), //
					BatteryProtection.ChannelId.values(), //
					BatteryVoltageProtection.ChannelId.values(), //
					BatteryManagementSystem.ChannelId.values() //
			);
		}

		@Override
		protected DummyBatteryManagementSystem self() {
			return this;
		}
	}

	private static class DummyPowerConversionSystem
			extends AbstractDummyOpenemsComponent<DummyPowerConversionSystem>
			implements PowerConversionSystem {

		public DummyPowerConversionSystem(String id) {
			super(id, //
					OpenemsComponent.ChannelId.values(), //
					StartStoppable.ChannelId.values(), //
					SymmetricBatteryInverter.ChannelId.values(), //
					ManagedSymmetricBatteryInverter.ChannelId.values(), //
					SymmetricComponent.ChannelId.values(), //
					PowerConversionSystem.ChannelId.values() //
			);
		}

		@Override
		protected DummyPowerConversionSystem self() {
			return this;
		}

		@Override
		public float getEfficiencyFactor() {
			return 100.F;
		}

		@Override
		public int getChargeMaxPower() {
			return CHARGE_MAX_POWER;
		}

		@Override
		public int getDischargeMaxPower() {
			return DISCHARGE_MAX_POWER;
		}

		@Override
		public int getPowerPrecision() {
			return 1;
		}

		@Override
		public void run(Battery battery, int setActivePower, int setReactivePower) {
		}

		@Override
		public void setStartStop(StartStop value) {
		}
	}
}
