package io.openems.edge.controller.ess.ebx.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statemachine.AbstractContext;
import io.openems.edge.controller.ess.ebx.ControllerEssEbx;
import io.openems.edge.controller.ess.ebx.ReferenceMode;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;

/**
 * Carries the inputs of one cycle into the state machine and the applied
 * result back out. All values are captured once per cycle, the context is
 * never reused.
 */
public class Context extends AbstractContext<ControllerEssEbx> {

	protected final EnergyStorageSystem ess;
	protected final ElectricityMeter meter;
	protected final ReferenceMode referenceMode;
	protected final Integer commandPower;
	protected final boolean heartbeatFresh;
	protected final boolean commandValid;
	protected final Integer rampStep;
	protected final boolean alwaysApplyRamp;
	protected final Integer lastAppliedPower;

	private Integer appliedPower = null;
	private boolean clamped = false;

	public Context(ControllerEssEbx parent, EnergyStorageSystem ess, ElectricityMeter meter,
			ReferenceMode referenceMode, Integer commandPower, boolean heartbeatFresh, boolean commandValid,
			Integer rampStep, boolean alwaysApplyRamp, Integer lastAppliedPower) {
		super(parent);
		this.ess = ess;
		this.meter = meter;
		this.referenceMode = referenceMode;
		this.commandPower = commandPower;
		this.heartbeatFresh = heartbeatFresh;
		this.commandValid = commandValid;
		this.rampStep = rampStep;
		this.alwaysApplyRamp = alwaysApplyRamp;
		this.lastAppliedPower = lastAppliedPower;
	}

	/**
	 * Whether the interface is healthy, meaning the heartbeat is fresh and the
	 * held command has not expired.
	 *
	 * @return true if a dispatch may be executed
	 */
	protected boolean isHealthy() {
		return this.heartbeatFresh && this.commandValid;
	}

	/**
	 * Applies the held command with reference adjustment, clamping and optional
	 * ramping.
	 *
	 * @throws OpenemsNamedException on write error
	 */
	public void applyDispatch() throws OpenemsNamedException {
		var target = this.adjustToReference(this.commandPower);
		var clamped = this.clamp(target);
		this.clamped = clamped != target;
		var value = this.alwaysApplyRamp //
				? this.rampTowards(clamped) //
				: clamped;
		this.applyPower(value);
	}

	/**
	 * Applies one rate-limited step towards zero power.
	 *
	 * @throws OpenemsNamedException on write error
	 */
	public void applyRampDown() throws OpenemsNamedException {
		this.applyPower(this.rampTowards(0));
	}

	/**
	 * Gets the power written to the ESS in this cycle.
	 *
	 * @return the applied power in [W], null while nothing was written
	 */
	public Integer getAppliedPower() {
		return this.appliedPower;
	}

	/**
	 * Whether the target of this cycle was outside the achievable range.
	 *
	 * @return true if the applied power was clamped
	 */
	public boolean wasClamped() {
		return this.clamped;
	}

	/**
	 * Adjusts the target to the configured control reference point.
	 *
	 * <p>
	 * In {@link ReferenceMode#METER} the setpoint refers to the Point of
	 * Connection, so the difference between the ESS power and the power exported
	 * at the meter is compensated. The meter follows the grid convention with
	 * positive import, while the target follows the EBX convention with positive
	 * export.
	 *
	 * @param target the target power at the reference point in [W]
	 * @return the target power to request from the ESS in [W]
	 */
	protected int adjustToReference(int target) {
		if (this.referenceMode != ReferenceMode.METER || this.meter == null) {
			return target;
		}
		var meterPower = this.meter.getActivePower().get();
		var essPower = this.ess.getActivePower().get();
		if (meterPower == null || essPower == null) {
			return target;
		}
		var pocExport = -meterPower;
		return target + (essPower - pocExport);
	}

	/**
	 * Moves from the last applied power towards the target by at most the ramp
	 * step of one cycle.
	 *
	 * <p>
	 * Without a resolvable maximum active power there is no ramp step and the
	 * target is returned directly, because holding the current power for lack of a
	 * step size would be worse than a step change.
	 *
	 * @param target the target power in [W]
	 * @return the ramped power for this cycle in [W]
	 */
	protected int rampTowards(int target) {
		var step = this.rampStep;
		if (step == null) {
			return target;
		}
		var from = this.lastAppliedPower != null //
				? this.lastAppliedPower //
				: this.ess.getActivePower().orElse(0);
		if (target > from) {
			return Math.min(from + step, target);
		}
		if (target < from) {
			return Math.max(from - step, target);
		}
		return target;
	}

	private void applyPower(int target) throws OpenemsNamedException {
		var value = this.clamp(target);
		this.ess.setActivePowerEqualsWithoutFilter(value);
		this.appliedPower = value;
	}

	private int clamp(int value) {
		// The bounds are read with next-value semantics, the controller publishes
		// them in the same cycle before the state machine runs. The channels carry
		// directional magnitudes, so the charge bound is negated.
		var parent = this.getParent();
		int min = -parent.getAvailableChargePowerChannel().getNextValue().orElse(0);
		int max = parent.getAvailableDischargePowerChannel().getNextValue().orElse(0);
		return Math.max(min, Math.min(max, value));
	}
}
