package io.openems.edge.ess.rct.cess;

import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.oros.bms.api.BatteryManagementSystem;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;
import io.openems.edge.oros.ess.core.protection.StateOfChargeLimiter;

public class StateOfChargeClipper extends StateOfChargeLimiter {
	public static final int SOC_MARGIN = 3;

	public StateOfChargeClipper(EnergyStorageSystem parent, BatteryManagementSystem battery) {
		super(parent, battery);
	}

	@Override
	protected int calcualteStateOfCharge(ClockProvider clockProvider, int soc) {
		soc = super.calcualteStateOfCharge(clockProvider, soc);
		if (soc <= SOC_MARGIN) {
			return 0;
		}
		if (soc >= (100 - SOC_MARGIN)) {
			return 100;
		}
		var socClipped = (soc - SOC_MARGIN) * 100.0 / (100.0 - 2.0 * SOC_MARGIN);
		return (int) Math.max(0, Math.min(100, Math.round(socClipped)));
	}
}
