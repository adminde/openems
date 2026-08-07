package io.openems.edge.oros.ess.core.protection;

import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.oros.bms.api.BatteryManagementSystem;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;
import io.openems.edge.oros.ess.core.ChannelManager.StateOfChargeListener;

/**
 * Limits the SoC to {@code 0 %} when the battery is below a threshold
 * and discharge is blocked, to {@code 100 %} when above a threshold and charge is blocked.
 */
public class StateOfChargeLimiter extends StateOfChargeListener {
	private static final int SOC_MARGIN = 3;

	public StateOfChargeLimiter(EnergyStorageSystem parent, BatteryManagementSystem battery) {
		super(parent, battery);
	}

	public StateOfChargeLimiter(EnergyStorageSystem parent, BatteryManagementSystem battery,
			ChannelId stateOfChargeId) {
		super(parent, battery, stateOfChargeId);
	}

	@Override
	protected int calculateStateOfCharge(ClockProvider clockProvider, int soc) {
		var chargeMaxCurrent = this.battery.getChargeMaxCurrentChannel().getNextValue();
		var dischargeMaxCurrent = this.battery.getDischargeMaxCurrentChannel().getNextValue();

		if (dischargeMaxCurrent.isDefined()
				&& dischargeMaxCurrent.get() <= 0
				&& soc < SOC_MARGIN) {
			return 0;

		}
		if (chargeMaxCurrent.isDefined()
				&& chargeMaxCurrent.get() <= 0
				&& soc > (100 - SOC_MARGIN)) {
			return 100;

		}
		return super.calculateStateOfCharge(clockProvider, soc);
	}

}
