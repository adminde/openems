package io.openems.edge.simulator.ess.symmetric.reacting;

import static io.openems.edge.common.channel.ChannelUtils.setValue;
import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveNatureTable;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.power.api.Power;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Simulator.EssSymmetric.Reacting", //
		immediate = true, //
		configurationPolicy = REQUIRE)
@EventTopics({ //
		TOPIC_CYCLE_AFTER_PROCESS_IMAGE })
public class SimulatorEssSymmetricReactingImpl extends AbstractOpenemsComponent
		implements SimulatorEssSymmetricReacting, ManagedSymmetricEss, SymmetricEss, OpenemsComponent, TimedataProvider,
		EventHandler, StartStoppable, ModbusSlave {

	private final CalculateEnergyFromPower calculateChargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricEss.ChannelId.ACTIVE_CHARGE_ENERGY);
	private final CalculateEnergyFromPower calculateDischargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricEss.ChannelId.ACTIVE_DISCHARGE_ENERGY);

	@Reference
	private Power power;

	@Reference
	private ComponentManager componentManager;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = OPTIONAL)
	private volatile Timedata timedata = null;

	/** Current Energy in the battery [Wms], based on SoC. */
	private long energy = 0;
	private Config config;

	public SimulatorEssSymmetricReactingImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				SymmetricEss.ChannelId.values(), //
				ManagedSymmetricEss.ChannelId.values(), //
				StartStoppable.ChannelId.values(), //
				SimulatorEssSymmetricReacting.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws IOException {
		super.activate(context, config.id(), config.alias(), config.enabled());

		this.config = config;
		this.energy = (long) ((double) config.capacity() /* [Wh] */ * 3600 /* [Wsec] */ * 1000 /* [Wmsec] */
				/ 100 * this.config.initialSoc() /* [current SoC] */);
		this._setSoc(config.initialSoc());
		this._setMaxApparentPower(config.maxApparentPower());
		setValue(this, ManagedSymmetricEss.ChannelId.ALLOWED_CHARGE_POWER,
				calculateAllowedChargePower(config.initialSoc(), config.maxChargePower()) * -1);
		setValue(this, ManagedSymmetricEss.ChannelId.ALLOWED_DISCHARGE_POWER,
				calculateAllowedDischargePower(config.initialSoc(), config.maxDischargePower()));
		setValue(this, SymmetricEss.ChannelId.GRID_MODE, config.gridMode());
		this._setCapacity(config.capacity());
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case TOPIC_CYCLE_AFTER_PROCESS_IMAGE //
			-> this.calculateEnergy();
		}
	}

	@Override
	public String debugLog() {
		return "SoC:" + this.getSoc().asString() //
				+ "|L:" + this.getActivePower().asString() //
				+ "|Allowed:" + this.getAllowedChargePower().asStringWithoutUnit() + ";"
				+ this.getAllowedDischargePower().asString();
	}

	@Override
	public Power getPower() {
		return this.power;
	}

	private Instant lastTimestamp = null;

	/** How long the power applied below will run for, taken from the previous
	 * cycle. Zero on the first call, which disables the clamp there. */
	private long lastDurationMillis = 0;

	/** The power applied on the previous call. Integrating the ActivePower
	 * CHANNEL instead, as the stock code did, books energy that never crossed
	 * the meter: on the leaping clock its value can be several periods stale. */
	private int lastAppliedActivePower = 0;

	@Override
	public void applyPower(int activePower, int reactivePower) throws OpenemsException {
		var now = Instant.now(this.componentManager.getClock());
		if (this.lastTimestamp != null && Duration.between(this.lastTimestamp, now).isZero()) {
			// A simulation that executes every cycle twice calls this a second time at
			// an unchanged simulated instant. Simulator.App collects on every process
			// image and keys the sample by time, so the sample it keeps for the instant
			// carries the first call's power and a grid meter computed from it. Only
			// that power may be integrated and reported. Honouring the repeat would
			// book a set point that no collected series contains.
			return;
		}

		/*
		 * calculate State of charge
		 */
		final int soc;
		if (this.lastTimestamp == null) {
			// initial run
			soc = this.config.initialSoc();

		} else {
			// calculate duration since last value
			var duration /* [msec] */ = Duration.between(this.lastTimestamp, now).toMillis();
			// A second cycle at the same instant has no duration of its own. The clamp
			// below must still see the length of a period, so the last real one stays.
			if (duration > 0) {
				this.lastDurationMillis = duration;
			}

			// calculate energy since last run in [Wh]
			var energy /* [Wmsec] */ = (long) this.lastAppliedActivePower /* [W] */ * duration /* [msec] */;

			// Adding the energy to the initial energy.
			this.energy -= energy;

			var calculatedSoc = this.energy //
					/ (this.config.capacity() * 3600. /* [Wsec] */ * 1000 /* [Wmsec] */) //
					* 100 /* [SoC] */;

			if (calculatedSoc > 100) {
				soc = 100;
			} else if (calculatedSoc < 0) {
				soc = 0;
			} else {
				soc = (int) Math.round(calculatedSoc);
			}

			this._setSoc(soc);
		}
		this.lastTimestamp = now;

		/*
		 * Apply Active/Reactive power to simulated channels
		 */
		if (soc == 0 && activePower > 0) {
			activePower = 0;
		}
		if (soc == 100 && activePower < 0) {
			activePower = 0;
		}
		/*
		 * Clamp to the energy actually present. The SoC checks above are a
		 * period behind, so without this a battery driven into empty delivers one
		 * more full period of energy it does not have.
		 */
		if (this.lastDurationMillis > 0) {
			final double capacityWmsec = this.config.capacity() /* [Wh] */ * 3600.0 * 1000.0;
			if (activePower > 0) {
				// discharge: at most the stored energy
				var limit = (int) (this.energy / this.lastDurationMillis);
				activePower = Math.min(activePower, Math.max(0, limit));
			} else if (activePower < 0) {
				// charge: at most the remaining headroom
				var limit = (int) ((capacityWmsec - this.energy) / this.lastDurationMillis);
				activePower = Math.max(activePower, -Math.max(0, limit));
			}
		}
		this._setActivePower(activePower);
		this.lastAppliedActivePower = activePower;
		if (soc == 0 && reactivePower > 0) {
			reactivePower = 0;
		}
		if (soc == 100 && reactivePower < 0) {
			reactivePower = 0;
		}
		this._setReactivePower(reactivePower);
		/*
		 * Set AllowedCharge / Discharge based on SoC with derating zone: - Charge: full
		 * below 95%, linear derating 95–100%, hard 0 at 100% - Discharge: full above
		 * 5%, linear derating 0–5%, hard 0 at 0%
		 */
		setValue(this, ManagedSymmetricEss.ChannelId.ALLOWED_CHARGE_POWER,
				calculateAllowedChargePower(soc, this.config.maxChargePower()) * -1);
		setValue(this, ManagedSymmetricEss.ChannelId.ALLOWED_DISCHARGE_POWER,
				calculateAllowedDischargePower(soc, this.config.maxDischargePower()));
	}

	@Override
	public int getPowerPrecision() {
		return 1;
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(//
				OpenemsComponent.getModbusSlaveNatureTable(accessMode), //
				SymmetricEss.getModbusSlaveNatureTable(accessMode), //
				ManagedSymmetricEss.getModbusSlaveNatureTable(accessMode), //
				StartStoppable.getModbusSlaveNatureTable(accessMode), //
				ModbusSlaveNatureTable.of(SimulatorEssSymmetricReactingImpl.class, accessMode, 100) //
						.build());
	}

	/**
	 * Calculate the Energy values from ActivePower.
	 */
	private void calculateEnergy() {
		// Calculate Energy
		var activePower = this.getActivePower().get();
		if (activePower == null) {
			// Not available
			this.calculateChargeEnergy.update(null);
			this.calculateDischargeEnergy.update(null);
		} else if (activePower > 0) {
			// Buy-From-Grid
			this.calculateChargeEnergy.update(0);
			this.calculateDischargeEnergy.update(activePower);
		} else {
			// Sell-To-Grid
			this.calculateChargeEnergy.update(activePower * -1);
			this.calculateDischargeEnergy.update(0);
		}
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public void setStartStop(StartStop value) {
		this._setStartStop(value);
	}

	/**
	 * Calculates allowed charge power with a linear derating from 95% to 100% SoC.
	 *
	 * @param soc            the current SoC [%]
	 * @param maxChargePower the maximum charge power [W], positive value
	 * @return allowed charge power as a positive value [W]
	 */
	private static int calculateAllowedChargePower(float soc, int maxChargePower) {
		if (soc >= 100) {
			return 0;
		}
		if (soc > 95) {
			return Math.round(maxChargePower * (100 - soc) / 5f);
		}
		return maxChargePower;
	}

	/**
	 * Calculates allowed discharge power with a linear derating from 5% to 0% SoC.
	 *
	 * @param soc               the current SoC [%]
	 * @param maxDischargePower the maximum discharge power [W], positive value
	 * @return allowed discharge power as a positive value [W]
	 */
	private static int calculateAllowedDischargePower(float soc, int maxDischargePower) {
		if (soc <= 0) {
			return 0;
		}
		if (soc < 5) {
			return Math.round(maxDischargePower * soc / 5f);
		}
		return maxDischargePower;
	}
}
