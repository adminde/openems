package io.openems.edge.oros.ess.core.protection;

import java.util.function.Consumer;

import io.openems.edge.battery.api.Battery;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.oros.bms.api.BatteryManagementSystem;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;

/**
 * Helper class to limit the SoC to {@code 0 %} when the battery is below a threshold
 * and discharge is blocked, to {@code 100 %} when above a threshold and charge is blocked.
 */
public class StateOfChargeLimiter implements Consumer<ClockProvider> {
	private static final int SOC_MARGIN = 3;

	protected final EnergyStorageSystem parent;
	protected final BatteryManagementSystem battery;

	public StateOfChargeLimiter(EnergyStorageSystem parent, BatteryManagementSystem battery) {
		this.parent = parent;
		this.battery = battery;
	}

	@Override
	public void accept(ClockProvider clockProvider) {
		Channel<Integer> socChannel = this.battery.channel(Battery.ChannelId.SOC);
		var socValue = socChannel.getNextValue();

		this.parent._setSoc(socValue.isDefined() ? this.calcualteStateOfCharge(clockProvider, socValue.get()) : null);
	}

	protected int calcualteStateOfCharge(ClockProvider clockProvider, int soc) {
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
		return soc;
	}

}
