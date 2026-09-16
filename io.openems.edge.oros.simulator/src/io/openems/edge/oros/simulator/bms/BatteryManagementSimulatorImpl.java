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
import io.openems.edge.oros.bms.api.BatteryManagementSystem;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Simulator.BMS.OROS", //
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
		this._setInnerResistance(config.internalResistance());
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
		var resistance = this.getInnerResistanceChannel().getNextValue().orElse(0) / 1000F;;

		if (this.lastTimestamp == null) {
			// Initialise energy from configured initial SoC
			this.energy = (long) (capacity * this.getRackSoc().get() /* [current SoC, in 0.1%] */ / 1000L);
		} else {
			// Calculate duration since last value
			var duration /* [msec] */ = Duration.between(this.lastTimestamp, now).toMillis();

			// The chemistry carries the power at the terminals plus the loss in the
			// internal resistance, which together are the Open Circuit Voltage times the
			// current. Both follow the state at the start of the elapsed interval.
			var startVoltage = this.calculateOpenCircuitVoltage(this.calculateStateOfCharge(capacity));
			var startCurrent = calculateCurrent(power, startVoltage, resistance);

			// Calculate energy delta since last run [Wmsec]; positive setPower = discharge -> energy decreases
			var energy /* [Wmsec] */ = (long) (startVoltage * startCurrent) /* [W] */ * duration /* [msec] */;
			this.energy -= energy;

			// Clamp internal energy to [0, capacity] so it cannot drift out of physical bounds
			this.energy = Math.clamp(this.energy, 0L, (long) capacity);
		}
		this.lastTimestamp = now;

		float soc = this.calculateStateOfCharge(capacity);
		var openCircuitVoltage = this.calculateOpenCircuitVoltage(soc);
		var current = calculateCurrent(power, openCircuitVoltage, resistance);
		var voltage = openCircuitVoltage - current * resistance;

		this._setRackSoc(Math.round(soc * 10));
		this._setRackCurrent(Math.round(current * 1000F));
		this._setRackVoltage(Math.round(voltage * 1000F));
		this._setOpenCircuitVoltage(Math.round(openCircuitVoltage));
		this._setThermalManagementPower(
				Math.round(this.calculateThermalManagementPower(power, current * current * resistance)));
	}

	/**
	 * Calculates the electrical power the Thermal Management System draws to carry
	 * away a loss.
	 *
	 * <p>Pump and controls run while the Rack carries a current, the heat pump adds
	 * the work it takes to move the loss out, which is the loss over its Coefficient
	 * of Performance. A Battery at rest produces no loss and keeps its Thermal
	 * Management System switched off. The model follows the loss without delay, so
	 * it reproduces the energy over a Cycle rather than the duty cycling of the heat
	 * pump.
	 *
	 * @param power the power at the terminals in [W]
	 * @param loss  the loss to carry away in [W]
	 * @return the electrical power in [W]
	 */
	private float calculateThermalManagementPower(int power, float loss) {
		if (power == 0) {
			return 0F;
		}
		var coefficientOfPerformance = this.config.thermalEfficiency();
		if (coefficientOfPerformance <= 0F) {
			return 0;
		}
		return this.config.thermalManagementPower() + loss / coefficientOfPerformance;
	}

	/**
	 * Calculates the State of Charge from the energy currently in the Battery.
	 *
	 * @param capacity the Capacity in [Wmsec]
	 * @return the State of Charge in [%]
	 */
	private float calculateStateOfCharge(long capacity) {
		return this.energy / (float) capacity * 100F;
	}

	/**
	 * Calculates the Open Circuit Voltage using a piecewise-linear model.
	 *
	 * <p>The model uses a flat plateau across the mid-range and steeper slopes at
	 * the extremes (within the configured derating zone).
	 *
	 * @param soc state of charge [%]
	 * @return the Open Circuit Voltage [V]
	 */
	private float calculateOpenCircuitVoltage(float soc) {
		float voltageMin = this.config.minDischargeVoltage();
		float voltageMax = this.config.maxChargeVoltage();
		float voltageMidSlope = (voltageMax - voltageMin) / (float) (100 - 2 * VOLTAGE_DERATING_ZONE); // V per SoC-%
		if (soc < VOLTAGE_DERATING_ZONE) {
			return voltageMin + (soc - VOLTAGE_DERATING_ZONE) * voltageMidSlope * 3F;
		}
		if (soc > (100 - VOLTAGE_DERATING_ZONE)) {
			return voltageMax + (soc - (100 - VOLTAGE_DERATING_ZONE)) * voltageMidSlope * 3F;
		}
		return voltageMin + (soc - VOLTAGE_DERATING_ZONE) * voltageMidSlope;
	}

	/**
	 * Solves the power at the terminals for the current through the internal
	 * resistance.
	 *
	 * <p>The terminal voltage drops by the voltage across the internal resistance,
	 * which makes the terminal power a quadratic function of the current:
	 * {@code power = (openCircuitVoltage - current * resistance) * current}. Of its
	 * two roots the smaller current is the physical one. A resistance passes no more
	 * than {@code openCircuitVoltage^2 / (4 * resistance)}, beyond which the current
	 * stays at the matching maximum power point.
	 *
	 * @param power              the power at the terminals in [W], positive for discharge
	 * @param openCircuitVoltage the Open Circuit Voltage in [V]
	 * @param resistance         the internal resistance in [Ohm]
	 * @return the current in [A], positive for discharge
	 */
	private static float calculateCurrent(float power, float openCircuitVoltage, float resistance) {
		if (resistance <= 0F) {
			return power / openCircuitVoltage;
		}
		var discriminant = openCircuitVoltage * openCircuitVoltage - 4F * resistance * power;
		if (discriminant <= 0F) {
			return openCircuitVoltage / (2F * resistance);
		}
		return (openCircuitVoltage - (float) Math.sqrt(discriminant)) / (2F * resistance);
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
