package io.openems.edge.oros.ess.protection;

import static io.openems.common.utils.IntUtils.maxInt;
import static io.openems.common.utils.IntUtils.minInt;
import static io.openems.common.utils.IntUtils.minInteger;
import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

import io.openems.common.utils.IntUtils;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.ess.api.HybridEss;
import io.openems.edge.oros.ess.SystemChannelManager;
import io.openems.edge.oros.ess.EnergyStorageSystem;

/**
 * Helper class to handle calculation of Allowed-Charge-Power and
 * Allowed-Discharge-Power. This class is used by {@link SystemChannelManager}
 * as a callback to updates of Battery Channels.
 */
public class PowerLimiter implements Consumer<ClockProvider> {
	private static final int PROTECTION_TIMEOUT = 360; // [seconds]

	private final EnergyStorageSystem parent;
	private final OverChargeCurrentLimiter overChargeCurrentLimiter;
	private final DeepDischargeCurrentLimiter deepDischargeCurrentLimiter;

	private final float maxAllowedChargePowerIncrease;
	private final float maxAllowedDischargePowerIncrease;

	private float lastAllowedChargePower;
	private float lastAllowedDischargePower;

	private Instant lastCalculate = null;
	private Instant lastProtectionEntry = null;

	public PowerLimiter(EnergyStorageSystem parent,
				OverChargeCurrentLimiter overChargeCurrentLimiter,
				DeepDischargeCurrentLimiter deepDischargeCurrentLimiter) {
		this.parent = parent;
		this.overChargeCurrentLimiter = overChargeCurrentLimiter;
		this.deepDischargeCurrentLimiter = deepDischargeCurrentLimiter;

		var pcs = parent.getPowerConversionSystem();
		var increaseFactor = parent.getMaxPowerIncreasePercentage() / 100.F;
		this.maxAllowedChargePowerIncrease = max(parent.getPowerPrecision(),
				pcs.getMaxChargePower() * increaseFactor);
		this.maxAllowedDischargePowerIncrease = max(parent.getPowerPrecision(),
				pcs.getMaxDischargePower() * increaseFactor);
	}

	@Override
	public void accept(ClockProvider clockProvider) {
		var battery = this.parent.getBattery();
		var chargeMaxCurrent = battery.getChargeMaxCurrentChannel().getNextValue().get();
		var dischargeMaxCurrent = battery.getDischargeMaxCurrentChannel().getNextValue().get();
		chargeMaxCurrent = IntUtils.minInteger(chargeMaxCurrent, this.overChargeCurrentLimiter.getMaxCurrent());
		dischargeMaxCurrent = IntUtils.minInteger(dischargeMaxCurrent, this.deepDischargeCurrentLimiter.getMaxCurrent());

		final var voltage = battery.getVoltageChannel().getNextValue().get();
		if (voltage == null || chargeMaxCurrent == null || dischargeMaxCurrent == null) {
			return;
		}
		final var current = battery.getCurrentChannel().value();
		this.checkProtectionExtremes(clockProvider, chargeMaxCurrent, dischargeMaxCurrent, current);

		this.calculateAllowedChargeDischargePower(clockProvider, this.parent.isStarted(),
				chargeMaxCurrent, dischargeMaxCurrent, voltage);

		var allowedChargePower = Math.round(this.lastAllowedChargePower) * -1;  // invert charge power
		var allowedDischargePower = Math.round(this.lastAllowedDischargePower);

		if (this.parent instanceof HybridEss ess) {
			var pvProduction = Math.max(
					TypeUtils.orElse(
							TypeUtils.subtract(ess.getActivePower().get(), ess.getDcDischargePower().get()),
							0),
					0);
			allowedDischargePower += pvProduction;
		}
		this.parent.getAllowedChargePowerChannel().setNextValue(allowedChargePower);
		this.parent._setAllowedDischargePower(allowedDischargePower);
	}

	/**
	 * Applies the max increase ramp.
	 *
	 * @param maxIncrease the maximum increase per second in [W]
	 * @param lastValue   the result value in [W] of previous run
	 * @param newValue    the current value in [W]
	 * @param lastInstant the timestamp of the previous run
	 * @param thisInstant the current timestamp
	 * @return the ramped value in [W]
	 */
	private static float calculateMaxIncrease(float maxIncrease, float lastValue, float newValue,
				Instant lastInstant, Instant thisInstant) {
		final float seconds;
		if (lastValue < 0 || lastInstant == null) {
			// Was in Force-Mode before
			lastValue = 0;
			seconds = 1.F;
		} else {
			seconds = Duration.between(lastInstant, thisInstant).toMillis() / 1000.F;
		}
		return min(newValue, lastValue + newValue * maxIncrease * seconds);
	}

	/**
	 * Calculates Allowed-Charge-Power and Allowed-Discharge-Power from the given parameters.
	 * Result is stored in 'lastAllowedChargePower' and 'lastAllowedDischargePower' variables
	 * as positive values.
	 *
	 * @param clockProvider       the {@link ClockProvider}
	 * @param isStarted           is the ESS started?
	 * @param chargeMaxCurrent    the processed {@link Battery.ChannelId#CHARGE_MAX_CURRENT}
	 * @param dischargeMaxCurrent the processed {@link Battery.ChannelId#DISCHARGE_MAX_CURRENT}
	 * @param voltage             the {@link Battery.ChannelId#VOLTAGE}
	 */
	private void calculateAllowedChargeDischargePower(ClockProvider clockProvider, boolean isStarted,
				int chargeMaxCurrent, int dischargeMaxCurrent, int voltage) {
		final var now = Instant.now(clockProvider.getClock());
		float charge;
		float discharge;

		/*
		 * Calculate initial AllowedChargePower and AllowedDischargePower
		 */
		if (!isStarted) {
			// Block ACTIVE and REACTIVE Power if
			// - GenericEss is not in State "STARTED"
			// - any of CHARGE_MAX_CURRENT, DISHARGE_MAX_CURRENT or VOLTAGE are missing
			charge = 0;
			discharge = 0;

		} else {
			// Calculate AllowedChargePower and AllowedDischargePower from battery current
			// limits and voltage.
			// Efficiency factor is not considered in chargeMaxCurrent (DC Power > AC Power)
			charge = chargeMaxCurrent * voltage;
			discharge = round(dischargeMaxCurrent * voltage * this.parent.getPowerConversionSystem().getEfficiencyFactor() / 100.F);
		}

		/*
		 * Handle Force Charge and Discharge
		 */
		if (charge < 0 && discharge < 0) {
			// Both Force Charge and Discharge are active -> cannot do anything
			charge = 0;
			discharge = 0;

		} else if (discharge < 0) {
			// Force Charge is active
			// Make sure AllowedChargePower is greater-or-equals absolute
			// AllowedDischargePower
			charge = max(charge, abs(discharge));

		} else if (charge < 0) {
			// Force Discharge is active
			// Make sure AllowedDischargePower is greater-or-equals absolute
			// AllowedChargePower
			discharge = max(abs(charge), discharge);
		}

		/*
		 * In Non-Force Mode: apply the max increase ramp.
		 */
		if (charge > 0) {
			charge = calculateMaxIncrease(this.maxAllowedChargePowerIncrease,
					this.lastAllowedChargePower, charge,
					this.lastCalculate, now);
		}
		if (discharge > 0) {
			discharge = calculateMaxIncrease(this.maxAllowedDischargePowerIncrease,
					this.lastAllowedDischargePower, discharge,
					this.lastCalculate, now);
		}
		this.lastCalculate = now;
		this.lastAllowedChargePower = charge;
		this.lastAllowedDischargePower = discharge;
	}

	public void checkProtectionExtremes(ClockProvider clockProvider,
				Integer chargeMaxCurrent, Integer dischargeMaxCurrent, Value<Integer> current) {
		if (dischargeMaxCurrent == null || chargeMaxCurrent == null || !current.isDefined()) {
			return;
		}
		if (dischargeMaxCurrent >= 0 || chargeMaxCurrent >= 0) {
			this.lastProtectionEntry = null;
			this.parent._setDeepDischargeProtection(false);
			this.parent._setOverChargeProtection(false);
			return;
		}

		if (this.lastProtectionEntry == null) {
			this.lastProtectionEntry = Instant.now(clockProvider.getClock());
		}
		if (dischargeMaxCurrent < 0
				&& current.get() >= 0
				&& this.hasProtectionExtremeTimeout(Instant.now(clockProvider.getClock()))) {
			this.parent._setDeepDischargeProtection(true);
		}
		if (chargeMaxCurrent < 0
				&& current.get() <= 0
				&& this.hasProtectionExtremeTimeout(Instant.now(clockProvider.getClock()))) {
			this.parent._setOverChargeProtection(true);
		}
	}

	public boolean hasProtectionExtremeTimeout(Instant instant) {
		return Duration.between(this.lastProtectionEntry, instant).getSeconds() > PROTECTION_TIMEOUT;
	}
}
