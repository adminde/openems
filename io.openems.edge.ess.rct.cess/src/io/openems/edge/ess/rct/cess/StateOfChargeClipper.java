package io.openems.edge.ess.rct.cess;

import java.util.function.Consumer;

import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.oros.bms.api.BatteryManagementSystem;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;
import io.openems.edge.oros.ess.core.protection.PowerLimiter;
import io.openems.edge.oros.ess.core.protection.StateOfChargeLimiter;

public class StateOfChargeClipper extends StateOfChargeLimiter {
	public static final int SOC_MARGIN = 3;

	public StateOfChargeClipper(EnergyStorageSystem parent, BatteryManagementSystem battery) {
		super(parent, battery);
	}

	@Override
	protected int calculateStateOfCharge(ClockProvider clockProvider, int soc) {
		soc = super.calculateStateOfCharge(clockProvider, soc);
		if (soc <= SOC_MARGIN) {
			return 0;
		}
		if (soc >= (100 - SOC_MARGIN)) {
			return 100;
		}
		var socClipped = (soc - SOC_MARGIN) * 100.0 / (100.0 - 2.0 * SOC_MARGIN);
		return (int) Math.max(0, Math.min(100, Math.round(socClipped)));
	}

	/**
	 * Clips Allowed-Charge-Power to zero and Allowed-Discharge-Power to the DC production
	 * whenever the State-of-Charge blocks the respective direction. Force-Charge and
	 * Force-Discharge requests, which are expressed by an inverted sign, are left untouched.
	 *
	 * <p>
	 * Both directions are re-evaluated on every update of the Allowed-Power Channels and of
	 * the State-of-Charge, because the Battery Channels driving them are read independently.
	 *
	 * @param ess the {@link RctCess} to add the listeners to
	 */
	public static void clipAllowedPowerByStateOfCharge(RctCess ess) {
		var stateOfCharge = ess.getSocChannel();
		var allowedChargePower = ess.getAllowedChargePowerChannel();
		var allowedDischargePower = ess.getAllowedDischargePowerChannel();

		final Consumer<Value<Integer>> clipChargePower = value -> {
			var socValue = stateOfCharge.getNextValue();
			if (!value.isDefined() || value.get() >= 0  || !socValue.isDefined() || !isChargeBlocked(socValue.get())) {
				return;
			}
			allowedChargePower.setNextValue(0);
		};
		final Consumer<Value<Integer>> clipDischargePower = value -> {
			var socValue = stateOfCharge.getNextValue();
			if (!value.isDefined() || !socValue.isDefined() || !isDischargeBlocked(socValue.get())) {
				return;
			}
			// The DC sources feed through the inverter, so their production stays available
			// for discharge even though the Battery itself is empty.
			var pvProduction = PowerLimiter.calculatePvProduction(ess);
			if (value.get() <= pvProduction) {
				return;
			}
			allowedDischargePower.setNextValue(pvProduction);
		};
		stateOfCharge.onSetNextValue(ignored -> {
			clipChargePower.accept(allowedChargePower.getNextValue());
			clipDischargePower.accept(allowedDischargePower.getNextValue());
		});
		allowedChargePower.onSetNextValue(clipChargePower);
		allowedDischargePower.onSetNextValue(clipDischargePower);
	}

	/**
	 * Does the clipped State-of-Charge block charging? The rescaled State-of-Charge reaches
	 * {@code 100 %} while the Battery still reports a charge current, so the
	 * Allowed-Charge-Power has to be blocked here.
	 *
	 * @param soc the clipped State-of-Charge in %
	 * @return true if Allowed-Charge-Power has to be zero
	 */
	private static boolean isChargeBlocked(int soc) {
		return soc >= 100;
	}

	/**
	 * Does the clipped State-of-Charge block discharging? The rescaled State-of-Charge reaches
	 * {@code 0 %} while the Battery still reports a discharge current, so the
	 * Allowed-Discharge-Power has to be blocked here.
	 *
	 * @param soc the clipped State-of-Charge in %
	 * @return true if Allowed-Discharge-Power has to be zero
	 */
	private static boolean isDischargeBlocked(int soc) {
		return soc <= 0;
	}
}
