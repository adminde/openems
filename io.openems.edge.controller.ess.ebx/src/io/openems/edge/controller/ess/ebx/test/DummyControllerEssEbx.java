package io.openems.edge.controller.ess.ebx.test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.test.AbstractDummyOpenemsComponent;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.controller.ess.ebx.ControllerEssEbx;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;
import io.openems.edge.oros.ess.test.DummyEnergyStorageSystem;

/**
 * Dummy implementation of {@link ControllerEssEbx} for unit tests.
 *
 * <p>
 * This class is placed in the main source folder (not test folder) so it can
 * be used by other bundles for their unit tests.
 */
public class DummyControllerEssEbx extends AbstractDummyOpenemsComponent<DummyControllerEssEbx>
		implements ControllerEssEbx {

	/**
	 * A command received via {@link ControllerEssEbx#applyCommand(int, Instant)}.
	 *
	 * @param power      the target power in [W]
	 * @param validUntil the expiry of the command
	 */
	public record AppliedCommand(int power, Instant validUntil) {
	}

	private final List<AppliedCommand> appliedCommands = new ArrayList<>();
	private final List<Instant> reportedHeartbeats = new ArrayList<>();

	private EnergyStorageSystem ess = new DummyEnergyStorageSystem("ess0");
	private ElectricityMeter meter = null;

	public DummyControllerEssEbx(String id) {
		super(id, //
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				ControllerEssEbx.ChannelId.values() //
		);
	}

	@Override
	protected DummyControllerEssEbx self() {
		return this;
	}

	@Override
	public void run() {
	}

	@Override
	public EnergyStorageSystem getEnergyStorageSystem() {
		return this.ess;
	}

	/**
	 * Sets the Energy Storage System returned by
	 * {@link #getEnergyStorageSystem()}.
	 *
	 * @param ess the {@link EnergyStorageSystem}
	 * @return this
	 */
	public DummyControllerEssEbx withEnergyStorageSystem(EnergyStorageSystem ess) {
		this.ess = ess;
		return this;
	}

	@Override
	public ElectricityMeter getMeter() {
		return this.meter;
	}

	/**
	 * Sets the meter returned by {@link #getMeter()}.
	 *
	 * @param meter the {@link ElectricityMeter}
	 * @return this
	 */
	public DummyControllerEssEbx withMeter(ElectricityMeter meter) {
		this.meter = meter;
		return this;
	}

	@Override
	public void applyCommand(int power, Instant validUntil) {
		this.appliedCommands.add(new AppliedCommand(power, validUntil));
	}

	@Override
	public void reportHeartbeat(Instant sourceTimestamp) {
		this.reportedHeartbeats.add(sourceTimestamp);
	}

	/**
	 * Gets the commands received so far.
	 *
	 * @return the list of {@link AppliedCommand}s
	 */
	public List<AppliedCommand> getAppliedCommands() {
		return this.appliedCommands;
	}

	/**
	 * Gets the heartbeat events received so far.
	 *
	 * @return the list of source timestamps, entries may be null
	 */
	public List<Instant> getReportedHeartbeats() {
		return this.reportedHeartbeats;
	}
}
