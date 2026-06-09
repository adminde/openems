package io.openems.edge.oros.simulator.bms;

import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;

import java.time.Duration;
import java.time.Instant;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.oros.bms.BatteryManagementSystem;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Simulator.OROS.BMS", //
		immediate = true, //
		configurationPolicy = REQUIRE //
)
public class BatteryManagementSimulatorImpl extends AbstractOpenemsComponent
		implements BatteryManagementSimulator, BatteryManagementSystem, Battery,
		OpenemsComponent, ModbusSlave, StartStoppable {

	public static final float VOLTAGE_DERATING_ZONE = 5F;

	@Reference
	private ComponentManager componentManager;

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
	public void run(int power) {
		if (!this.isEnabled()) {
			return;
		}
		var now = Instant.now(this.componentManager.getClock());
		var capacity = (long) this.config.capacity() * 3600L /* [Wsec] */ * 1000L /* [Wmsec] */;

		if (this.lastTimestamp == null) {
			// Initialise energy from configured initial SoC
			this.energy = (long) (capacity * this.getRackSoc().get() /* [current SoC, in 0.1%] */ / 1000L);
		}
		else {
			// Calculate duration since last value
			var duration /* [msec] */ = Duration.between(this.lastTimestamp, now).toMillis();

			// Calculate energy delta since last run [Wmsec]; positive setPower = discharge -> energy decreases
			var energy /* [Wmsec] */ = (long) power /* [W] */ * duration /* [msec] */;
			this.energy -= energy;

			// Clamp internal energy to [0, capacity] so it cannot drift out of physical bounds
			this.energy = Math.max(0L, Math.min((long) capacity, this.energy));
		}
		this.lastTimestamp = now;

		float soc = this.energy / (float) capacity * 100F;
		var voltage = calculateRackVoltage(soc);
		var current = power / voltage;

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
		float voltageMin = this.config.minDischargeVoltage();
		float voltageMax = this.config.maxChargeVoltage();
		float voltageMidSlope = (voltageMax - voltageMin) / (float) (100 - 2 * VOLTAGE_DERATING_ZONE); // V per SoC-%
		float voltage;
		if (soc < VOLTAGE_DERATING_ZONE) {
			voltage = voltageMin + (soc - VOLTAGE_DERATING_ZONE) * voltageMidSlope * 3F;
		} else if (soc > (100 - VOLTAGE_DERATING_ZONE)) {
			voltage = voltageMax + (soc - (100 - VOLTAGE_DERATING_ZONE)) * voltageMidSlope * 3F;
		} else {
			voltage = voltageMin + (soc - VOLTAGE_DERATING_ZONE) * voltageMidSlope;
		}
		return Math.round(voltage * 1000F);
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
