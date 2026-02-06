package io.openems.edge.ess.hyperstrong;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.ess.hyperstrong.hypercube.HyperCube;

/**
 * Helper class to handle calculation of Allowed-Charge-Power and
 * Allowed-Discharge-Power. This class is used by {@link ChannelManager} as a
 * callback to updates of Battery Channels.
 */
public class AllowedPowerHandler implements Consumer<ClockProvider> {

	private final HyperCube parent;

	private final float maxAllowedBatteryChargePowerIncrease;
	private final float maxAllowedBatteryDischargePowerIncrease;

	private float lastAllowedBatteryChargePower;
	private float lastAllowedBatteryDischargePower;

	private Instant lastCalculate = null;

	public AllowedPowerHandler(HyperCube parent) {
		var model = parent.getModel();
		this.maxAllowedBatteryChargePowerIncrease = max(parent.getPowerPrecision(),
				model.getMaxChargePower() * HyperCube.MAX_POWER_INCREASE_PERCENTAGE);
		this.maxAllowedBatteryDischargePowerIncrease = max(parent.getPowerPrecision(),
				model.getMaxDischargePower() * HyperCube.MAX_POWER_INCREASE_PERCENTAGE);
		this.parent = parent;
	}

	@Override
	public void accept(ClockProvider clockProvider) {
		this.calculateAllowedChargeDischargePower(clockProvider);

		// Battery limits
		var batteryAllowedChargePower = Math.round(this.lastAllowedBatteryChargePower);
		var batteryAllowedDischargePower = Math.round(this.lastAllowedBatteryDischargePower);

		// Apply AllowedChargePower and AllowedDischargePower
		this.parent._setAllowedChargePower(batteryAllowedChargePower * -1 /* invert charge power */);
		this.parent._setAllowedDischargePower(batteryAllowedDischargePower);
	}

	/**
	 * Calculates Allowed-Charge-Power and Allowed-Discharge Power from the given
	 * parameters. Result is stored in 'lastBatteryAllowedChargePower' and
	 * 'lastBatteryAllowedDischargePower' variables - both as positive values!
	 *
	 * @param clockProvider the {@link ClockProvider}
	 * @param battery       the {@link Battery}
	 * @param inverter      the {@link SymmetricBatteryInverter}
	 */
	protected void calculateAllowedChargeDischargePower(ClockProvider clockProvider) {
		//final var cycleTime = this.parent.getCycleTime();
		var chargeMaxPower = this.parent.getBatteryChargeMaxPowerChannel().getNextValue().get();
		var dischargeMaxPower = this.parent.getBatteryDischargeMaxPowerChannel().getNextValue().get();

		this.calculateAllowedChargeDischargePower(clockProvider, this.parent.isStarted(),
				chargeMaxPower, dischargeMaxPower);
	}

	/**
	 * Calculates Allowed-Charge-Power and Allowed-Discharge Power from the given
	 * parameters. Result is stored in 'allowedChargePower' and
	 * 'allowedDischargePower' variables - both as positive values!
	 *
	 * @param clockProvider       the {@link ClockProvider}
	 * @param isStarted           is the ESS started?
	 * @param chargeMaxCurrent    the {@link Battery.ChannelId#CHARGE_MAX_CURRENT}
	 * @param dischargeMaxCurrent the {@link Battery.ChannelId#DISCHARGE_MAX_CURRENT}
	 * @param voltage             the {@link Battery.ChannelId#VOLTAGE}
	 */
	protected void calculateAllowedChargeDischargePower(ClockProvider clockProvider, boolean isStarted,
			Integer chargeMaxPower, Integer dischargeMaxPower) {
		final var now = Instant.now(clockProvider.getClock());
		float charge;
		float discharge;

		/*
		 * Calculate initial AllowedChargePower and AllowedDischargePower
		 */
		if (!isStarted || chargeMaxPower == null || dischargeMaxPower == null) {
			// Block ACTIVE and REACTIVE Power if
			// - GenericEss is not in State "STARTED"
			// - any of RACK_CHARGE_MAX_POWER or RACK_DISHARGE_MAX_POWER are missing
			charge = 0;
			discharge = 0;

		} else {
			// Calculate AllowedChargePower and AllowedDischargePower from battery current
			// limits and voltage.
			// Efficiency factor is not considered in chargeMaxCurrent (DC Power > AC Power)
			charge = chargeMaxPower;
			discharge = round(dischargeMaxPower * HyperCube.EFFICIENCY_FACTOR);
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
			charge = applyMaxIncrease(this.maxAllowedBatteryChargePowerIncrease, 
					this.lastAllowedBatteryChargePower, charge, this.lastCalculate, now);
		}
		if (discharge > 0) {
			discharge = applyMaxIncrease(this.maxAllowedBatteryDischargePowerIncrease, 
					this.lastAllowedBatteryDischargePower, discharge, this.lastCalculate, now);
		}

		/*
		 * Apply result
		 */
		this.lastCalculate = now;
		this.lastAllowedBatteryChargePower = charge;
		this.lastAllowedBatteryDischargePower = discharge;
	}

	/**
	 * Applies the max increase ramp, built from MAX_POWER_INCREASE_PERCENTAGE.
	 *
	 * @param lastValue   the result value in [W] of previous run
	 * @param newValue    the current value [W]
	 * @param lastInstant the timestamp of the previous run
	 * @param thisInstant the current timestamp
	 * @return the new value
	 */
	private static float applyMaxIncrease(float maxIncrease, float lastValue, float newValue, 
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

}