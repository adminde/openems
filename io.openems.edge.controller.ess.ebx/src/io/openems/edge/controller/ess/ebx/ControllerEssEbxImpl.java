package io.openems.edge.controller.ess.ebx;

import java.time.Instant;
import java.util.function.LongSupplier;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.referencetarget.GenerateTargetsFromReferences;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.cycle.Cycle;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.common.type.Phase;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.controller.ess.ebx.statemachine.Context;
import io.openems.edge.controller.ess.ebx.statemachine.StateMachine;
import io.openems.edge.controller.ess.ebx.statemachine.StateMachine.State;
import io.openems.edge.ess.power.api.Pwr;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Ess.Ebx", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@GenerateTargetsFromReferences({ "ess", "meter" })
public class ControllerEssEbxImpl extends AbstractOpenemsComponent
		implements ControllerEssEbx, Controller, OpenemsComponent {

	private final Logger log = LoggerFactory.getLogger(ControllerEssEbxImpl.class);
	private final StateMachine stateMachine = new StateMachine(State.UNDEFINED);

	@Reference
	private ComponentManager componentManager;

	@Reference
	private Cycle cycle;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY, //
			target = "(&(id=${config.ess_id})(enabled=true))")
	private EnergyStorageSystem ess;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL, //
			target = "(&(id=${config.meter_id})(enabled=true))")
	private volatile ElectricityMeter meter;

	private Config config;
	private LongSupplier monotonicTime = System::nanoTime;

	private volatile Command command = null;
	private volatile boolean heartbeatReported = false;
	private volatile long heartbeatNanos;
	private volatile Long clockOffset = null;

	private Integer lastAppliedPower = null;

	private record Command(int power, Instant validUntil) {
	}

	public ControllerEssEbxImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				ControllerEssEbx.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run() throws OpenemsNamedException {
		// The effective power envelope is captured before this controller applies its
		// own constraint, because afterwards the solver range collapses to the
		// setpoint itself.
		var power = this.ess.getPower();
		var minPower = power.getMinPower(this.ess, Phase.SingleOrAllPhase.ALL, Pwr.ACTIVE);
		var maxPower = power.getMaxPower(this.ess, Phase.SingleOrAllPhase.ALL, Pwr.ACTIVE);
		this._setAvailableChargePower(Math.max(0, -minPower));
		this._setAvailableDischargePower(Math.max(0, maxPower));

		var command = this.command;
		var now = Instant.now(this.componentManager.getClock());
		var heartbeatAge = this.getHeartbeatAge();
		var heartbeatFresh = heartbeatAge != null && heartbeatAge <= this.config.heartbeatTimeout() * 1000L;
		var commandValid = command != null && !now.isAfter(command.validUntil());

		this._setHeartbeatTime(heartbeatAge == null //
				? null //
				: (int) Math.min(Integer.MAX_VALUE, heartbeatAge));
		this._setHeartbeatStale(this.heartbeatReported && !heartbeatFresh);

		var clockOffset = this.clockOffset;
		this._setClockOffset(clockOffset);
		this._setClockDriftHigh(clockOffset != null //
				&& Math.abs(clockOffset) > this.config.heartbeatTimeout() * 1000L / 2);

		this._setSetpointValidUntil(command == null ? null : command.validUntil().toEpochMilli());
		this._setSetpointExpired(command != null && !commandValid);

		var context = new Context(this, this.ess, this.meter, this.config.referenceMode(),
				command == null ? null : command.power(), heartbeatFresh, commandValid, this.getRampStep(),
				this.config.alwaysApplyRamp(), this.lastAppliedPower);
		try {
			this.stateMachine.run(context);
		} catch (OpenemsNamedException e) {
			this.logError(this.log, "StateMachine failed: " + e.getMessage());
		}
		this._setStateMachine(this.stateMachine.getCurrentState());

		// The write happens after the state machine has settled on the state of this
		// cycle, so a transition never leaves the ESS unwritten for one cycle. In
		// IDLE_SAFE and UNDEFINED nothing is written and the ESS is deliberately released
		// to the remaining scheduler chain.
		switch (this.stateMachine.getCurrentState()) {
		case DISPATCHING -> context.applyDispatch();
		case FAIL_SAFE -> context.applyRampDown();
		case IDLE_SAFE, UNDEFINED -> {
			// Allow following controllers to apply setpoints
		}
		}
		this.lastAppliedPower = context.getAppliedPower();
		this._setSetpointActivePower(this.lastAppliedPower);
		this._setSetpointClamped(context.wasClamped());

		this.updateAssetStatus();
	}

	@Override
	public void applyCommand(int power, Instant validUntil) {
		this.command = new Command(power, validUntil);
	}

	@Override
	public EnergyStorageSystem getEnergyStorageSystem() {
		return this.ess;
	}

	@Override
	public ElectricityMeter getMeter() {
		return switch (this.config.referenceMode()) {
		case ESS -> null;
		case METER -> this.meter;
		};
	}

	@Override
	public void reportHeartbeat(Instant sourceTimestamp) {
		this.heartbeatNanos = this.monotonicTime.getAsLong();
		this.heartbeatReported = true;
		if (sourceTimestamp != null) {
			this.clockOffset = Instant.now(this.componentManager.getClock()).toEpochMilli()
					- sourceTimestamp.toEpochMilli();
		}
	}

	@Override
	public String debugLog() {
		return new StringBuilder() //
				.append(this.stateMachine.debugLog()) //
				.append("|Target:") //
				.append(this.getSetpointActivePower().asString()) //
				.toString();
	}

	/**
	 * Replaces the monotonic time source. Only intended for tests.
	 *
	 * @param monotonicTime the time source in nanoseconds
	 */
	void setMonotonicTime(LongSupplier monotonicTime) {
		this.monotonicTime = monotonicTime;
	}

	private Long getHeartbeatAge() {
		if (!this.heartbeatReported) {
			return null;
		}
		return (this.monotonicTime.getAsLong() - this.heartbeatNanos) / 1_000_000L;
	}

	/**
	 * Calculates the ramp step of one cycle in [W] from the configured rate in
	 * percent of the maximum active power per second.
	 *
	 * @return the ramp step, or null when no maximum active power is resolvable
	 */
	private Integer getRampStep() {
		var maxActivePower = this.ess.getMaxActivePower().get();
		if (maxActivePower == null) {
			maxActivePower = this.ess.getMaxApparentPower().get();
		}
		if (maxActivePower == null) {
			return null;
		}
		var step = maxActivePower * this.config.rampRate() / 100.0 * this.cycle.getCycleTime() / 1000.0;
		return Math.max(1, (int) Math.round(step));
	}

	/**
	 * Updates the asset status channel with the derivation of this cycle.
	 */
	private void updateAssetStatus() {
		// The derivation is binary between no and full availability. An undefined
		// grid mode during start-up reports no availability, as the interface
		// requires until internal health checks pass. Deriving restricted
		// availability requires a component-fault source that is not yet connected.
		this._setAssetStatus(this.ess.getGridMode() == GridMode.ON_GRID //
				? AssetStatus.FULL_AVAILABILITY //
				: AssetStatus.NO_AVAILABILITY);
	}
}
