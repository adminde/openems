package io.openems.edge.oros.simulator.bms;

import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;

import java.time.Duration;
import java.time.Instant;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.oros.bms.BatteryManagementSystem;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "OROS.Simulator.BMS", //
		immediate = true, //
		configurationPolicy = REQUIRE //
)
public class BatteryManagementSimulatorImpl extends AbstractOpenemsComponent
		implements BatteryManagementSimulator, BatteryManagementSystem, Battery,
		OpenemsComponent, ModbusSlave, StartStoppable {

	public static final float VOLTAGE_DERATING_ZONE = 5F;

	private Config config;

	private Instant lastTimestamp = null;

	/** Current energy in the battery [Wms], based on SoC. */
	private long energy = 0;

	public BatteryManagementSimulatorImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Battery.ChannelId.values(), //
				StartStoppable.ChannelId.values(), //
				BatteryManagementSystem.ChannelId.values(), //
				BatteryManagementSimulator.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;

		this._setCapacity(config.capacity());
		this._setRackSoc(config.initialSoc() * 10);
		this._setChargeMaxVoltage(Math.round(config.maxChargeVoltage()));
		this._setDischargeMinVoltage(Math.round(config.minDischargeVoltage()));
		this._setStartStop(StartStop.START);

		BatteryManagementSystem.calculateMaxCurrentFromPowerAndVoltage(this);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run(int setPower) {
		if (!this.isEnabled()) {
			return;
		}
		var now = Instant.now();
		var capacity = this.config.capacity() * 3600F /* [Wsec] */ * 1000 /* [Wmsec] */;

		if (this.lastTimestamp == null) {
			// Initialise energy from configured initial SoC
			this.energy = (long) capacity * this.getRackSoc().get() /* [current SoC] */ / 1000;
		}
		else {
			// Calculate duration since last value
			var duration /* [msec] */ = Duration.between(this.lastTimestamp, now).toMillis();

			// Calculate energy since last run in [Wh]
			var energy /* [Wmsec] */ = setPower /* [W] */ * duration /* [msec] */;
			energy = Math.max(0, Math.min((long) capacity, energy));

			// Adding the energy to the initial energy.
			this.energy -= energy;
		}
		this.lastTimestamp = now;

		var soc = this.energy / capacity * 100;
		var voltage = calculateRackVoltage(soc);
		var current = setPower / voltage;

		this._setRackSoc(Math.round(soc * 10));
		this._setRackCurrent(current);
		this._setRackVoltage(voltage);
		this._setOpenCircuitVoltage(calculateOpenCircuitVoltage(soc));
	}

	/**
	 * Calculates the rack voltage using a piecewise-linear model.
	 *
	 * <p>The model uses a flat plateau across the mid-range and steeper slopes at
	 * the extremes (within the configured derating zone).
	 *
	 * @param soc state of charge [%]
	 * @return rack voltage [mV]
	 */
	private int calculateRackVoltage(float soc) {
		float minV = this.config.minDischargeVoltage();
		float maxV = this.config.maxChargeVoltage();
		float midSlope = (maxV - minV) / (float) (100 - 2 * VOLTAGE_DERATING_ZONE); // V per SoC-%
		float v;
		if (soc < VOLTAGE_DERATING_ZONE) {
			v = minV + (soc - VOLTAGE_DERATING_ZONE) * midSlope * 3F;
		} else if (soc > (100 - VOLTAGE_DERATING_ZONE)) {
			v = maxV + (soc - (100 - VOLTAGE_DERATING_ZONE)) * midSlope * 3F;
		} else {
			v = minV + (soc - VOLTAGE_DERATING_ZONE) * midSlope;
		}
		return Math.round(v * 1000F);
	}

	/** Calculates Open Circuit Voltage in [V] */
	private int calculateOpenCircuitVoltage(float soc) {
		return calculateRackVoltage(soc) / 1000;
	}

	@Override
	public void setStartStop(StartStop value) throws OpenemsNamedException {
		// not supported; simulator always stays in START
	}

	@Override
	public String debugLog() {
		return BatteryManagementSystem.generateDebugLog(this);
	}

}
