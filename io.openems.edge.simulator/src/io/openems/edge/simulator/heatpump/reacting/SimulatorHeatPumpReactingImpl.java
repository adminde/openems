package io.openems.edge.simulator.heatpump.reacting;

import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.heat.api.ManagedSymmetricHeating;
import io.openems.edge.heat.api.SymmetricHeating;
import io.openems.edge.heat.pump.api.HeatPump;
import io.openems.edge.heat.pump.api.ManagedHeatPump;
import io.openems.edge.heat.tess.api.ThermalEss;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Simulator.HeatPump.Reacting", //
		immediate = true, //
		configurationPolicy = REQUIRE)
@EventTopics({ //
		TOPIC_CYCLE_AFTER_PROCESS_IMAGE })
public class SimulatorHeatPumpReactingImpl extends AbstractOpenemsComponent
		implements SimulatorHeatPumpReacting, ManagedHeatPump, HeatPump, ManagedSymmetricHeating, SymmetricHeating,
		StartStoppable, OpenemsComponent, TimedataProvider, EventHandler {

	private final CalculateEnergyFromPower calculateConsumptionEnergy = new CalculateEnergyFromPower(this,
			SymmetricHeating.ChannelId.ACTIVE_CONSUMPTION_ENERGY);
	private final CalculateEnergyFromPower calculateThermalEnergy = new CalculateEnergyFromPower(this,
			SymmetricHeating.ChannelId.THERMAL_ENERGY);

	private Config config;
	private volatile ThermalEss thermalStorage;
	private boolean running = false;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = OPTIONAL)
	private volatile Timedata timedata = null;

	public SimulatorHeatPumpReactingImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				SymmetricHeating.ChannelId.values(), //
				ManagedSymmetricHeating.ChannelId.values(), //
				HeatPump.ChannelId.values(), //
				ManagedHeatPump.ChannelId.values(), //
				StartStoppable.ChannelId.values(), //
				SimulatorHeatPumpReacting.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		this._setStartStop(StartStop.START);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void setThermalStorage(ThermalEss thermalStorage) {
		this.thermalStorage = thermalStorage;
	}

	@Override
	public void setStartStop(StartStop value) {
		this._setStartStop(value);
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case TOPIC_CYCLE_AFTER_PROCESS_IMAGE //
			-> this.cycle();
		}
	}

	private void cycle() {
		var tess = this.thermalStorage;
		Integer temperature = null;
		Integer minTarget = null;
		Integer offPoint = null;
		if (tess != null) {
			temperature = tess.getTemperature().get();
			minTarget = tess.getMinTargetTemperature().get();
			// Effective switch-off point: operating-band maximum, clamped by the hardware
			// limits of both the storage and the heat pump.
			offPoint = minIgnoreNull(tess.getMaxTargetTemperature().get(), tess.getMaxTemperature().get(),
					this.getMaxTemperature().get());
		}

		// Two-point control: switch on at min target, off at the effective off point.
		// Hold state in between.
		if (temperature != null && minTarget != null && offPoint != null) {
			if (temperature <= minTarget) {
				this.running = true;
			} else if (temperature >= offPoint) {
				this.running = false;
			}
		} else {
			// no storage bound or values missing — stay off
			this.running = false;
		}

		final int thermalPower = this.running ? this.config.thermalPower() : 0;
		final float cop = this.config.cop();
		final int activePower = this.running && cop > 0 //
				? Math.round(thermalPower / cop) //
				: 0;

		this._setThermalPower(thermalPower);
		this._setActivePower(activePower);
		this._setCop(cop);
		this._setTemperatureSupply(this.running ? toDeciDegree(this.config.supplyTemperature()) : 0);
		this._setTemperatureReturn(this.running ? toDeciDegree(this.config.returnTemperature()) : 0);
		if (temperature != null) {
			this._setTemperature(temperature);
		}

		this.calculateConsumptionEnergy.update(activePower);
		this.calculateThermalEnergy.update(thermalPower);
	}

	/**
	 * Converts a temperature in [°C] to [deci-°C].
	 *
	 * @param celsius the temperature in °C
	 * @return the temperature in deci-°C
	 */
	private static int toDeciDegree(float celsius) {
		return Math.round(celsius * 10);
	}

	/**
	 * Returns the smallest of the given values, ignoring {@code null} entries.
	 *
	 * @param values the values
	 * @return the minimum, or {@code null} if all values are {@code null}
	 */
	private static Integer minIgnoreNull(Integer... values) {
		Integer result = null;
		for (Integer value : values) {
			if (value == null) {
				continue;
			}
			if (result == null || value < result) {
				result = value;
			}
		}
		return result;
	}

	@Override
	public String debugLog() {
		return (this.running ? "ON" : "OFF") //
				+ "|P:" + this.getActivePower().asString() //
				+ "|Pth:" + this.getThermalPower().asString();
	}
}
