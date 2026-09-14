package io.openems.edge.sungrow.pvinverter;

import java.util.Objects;
import java.util.Optional;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.function.ThrowingConsumer;

/**
 * Applies a requested active power limit to the power limitation registers of
 * the inverter.
 *
 * <p>
 * The limit is written as a percentage of the rated active power with a
 * resolution of 0.1 %. Registers are only written when the value reported by
 * the inverter differs from the requested value.
 */
public class SetPvLimitHandler implements ThrowingConsumer<Optional<Integer>, OpenemsNamedException> {

	/**
	 * Lower bound of the power limitation in 0.1 %. Zero is never written because
	 * it can put the inverter into standby.
	 */
	private static final int MIN_LIMIT = 10;
	private static final int MAX_LIMIT = 1000;

	private final PvInverterSungrow parent;

	public SetPvLimitHandler(PvInverterSungrow parent) {
		this.parent = parent;
	}

	/**
	 * Handles a PV-Inverter power limitation request.
	 *
	 * @param activePowerLimit an Optional power limit in [W]; an empty value
	 *                         disables the power limitation
	 * @throws OpenemsNamedException on error
	 */
	@Override
	public void accept(Optional<Integer> activePowerLimit) throws OpenemsNamedException {
		if (activePowerLimit.isEmpty()) {
			this.applySwitch(PowerLimitationSwitch.DISABLE);
			return;
		}

		var maxActivePower = this.parent.getMaxActivePower().getOrError();
		if (maxActivePower <= 0) {
			throw new OpenemsException("Rated active power of the inverter is not available");
		}
		var setting = Math.round(activePowerLimit.get() * (float) MAX_LIMIT / maxActivePower);
		this.applySetting(Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, setting)));
		this.applySwitch(PowerLimitationSwitch.ENABLE);
	}

	private void applySwitch(PowerLimitationSwitch value) throws OpenemsNamedException {
		if (this.parent.getPowerLimitationSwitch() != value) {
			this.parent.setPowerLimitationSwitch(value);
		}
	}

	private void applySetting(int value) throws OpenemsNamedException {
		if (!Objects.equals(this.parent.getPowerLimitationSetting().get(), value)) {
			this.parent.setPowerLimitationSetting(value);
		}
	}
}
