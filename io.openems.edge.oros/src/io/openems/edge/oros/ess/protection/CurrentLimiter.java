package io.openems.edge.oros.ess.protection;

import java.util.function.Consumer;

import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.common.filter.PT1Filter;
import io.openems.edge.oros.bms.BatteryManagementSystem;
import io.openems.edge.oros.ess.EnergyStorageSystem;
import io.openems.edge.oros.pcs.PowerConversionSystem;
import io.openems.edge.oros.ess.SystemChannelManager;

/**
 * Helper class to handle calculation of Max-Charge-Current and
 * Max-Discharge-Current. This class is used by {@link SystemChannelManager}
 * as a callback to updates of Battery Channels.
 */
public abstract class CurrentLimiter implements Consumer<ClockProvider> {
	public static final int FILTER_TIME_CONSTANT = 10_000; // [milliseconds]

	private final EnergyStorageSystem parent;
	private final PT1Filter maxCurrentLimitFilter;
	private final Channel<Integer> maxCurrentChannel;

	private Integer maxCurrent;

	protected CurrentLimiter(EnergyStorageSystem parent, Channel<Integer> channel) {
		this.parent = parent;
		this.maxCurrentChannel = channel;
		this.maxCurrentLimitFilter = new PT1Filter(FILTER_TIME_CONSTANT);
	}

	public Integer getMaxCurrent() {
		return this.maxCurrent;
	}

	@Override
	public void accept(ClockProvider clockProvider) {
		final var battery = this.parent.getBatteryManagementSystem();
		final var values = this.getLimitValues(battery, this.parent.getPowerConversionSystem());
		if (values == null) {
			return;
		}
		var maxCurrent = calculateMaxCurrent(values, this.maxCurrentLimitFilter);

		this.maxCurrentChannel.setNextValue(maxCurrent);
		this.maxCurrent = maxCurrent;
	}

	protected abstract VoltageLimitValues getLimitValues(BatteryManagementSystem battery, PowerConversionSystem inverter);

	protected abstract Integer calculateMaxCurrent(VoltageLimitValues values, PT1Filter filter);

	protected record VoltageLimitValues(
			boolean isBatteryStarted,
			int innerResistance,
			int current,
			int voltage,
			int voltageLimit,
			Integer voltageProtectionLimit, // nullable
			int pcsVoltageLimit) {

		protected static VoltageLimitValues from(
				 boolean isBatteryStarted,
				 Integer innerResistance,
				 Integer current,
				 Integer voltage,
				 Integer voltageLimit,
				 Integer voltageProtectionLimit,
				 Integer pcsVoltageLimit) {
			if (!isBatteryStarted
					|| innerResistance == null
					|| current == null
					|| voltage == null
					|| voltageLimit == null
					|| pcsVoltageLimit == null
			) {
				return null;
			}
			return new VoltageLimitValues(isBatteryStarted, innerResistance, current,
					voltage, voltageLimit, voltageProtectionLimit, pcsVoltageLimit);
		}
	}
}
