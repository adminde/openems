package io.openems.edge.simulator.heat.tess.reacting;

import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE;
import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;
import static org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.ChannelAddress;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.heat.api.ManagedSymmetricHeating;
import io.openems.edge.heat.api.SymmetricHeating;
import io.openems.edge.heat.tess.api.ManagedThermalEss;
import io.openems.edge.heat.tess.api.ThermalEss;
import io.openems.edge.heat.tess.api.utils.CalculateChargeDischargePower;
import io.openems.edge.heat.tess.api.utils.CalculateEnergyFromTemperature;
import io.openems.edge.heat.tess.api.utils.CalculateStateFromTemperature;
import io.openems.edge.heat.tess.api.utils.HeatPumpCirculation;
import io.openems.edge.heat.tess.api.utils.TargetPowerDistributor;
import io.openems.edge.simulator.datasource.api.SimulatorDatasource;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Simulator.ThermalEss.Reacting", //
		immediate = true, //
		configurationPolicy = REQUIRE)
@EventTopics({ //
		TOPIC_CYCLE_AFTER_PROCESS_IMAGE, //
		TOPIC_CYCLE_EXECUTE_WRITE })
public class SimulatorThermalEssReactingImpl extends AbstractOpenemsComponent implements SimulatorThermalEssReacting,
		ManagedThermalEss, ThermalEss, OpenemsComponent, TimedataProvider, EventHandler {

	private final Logger log = LoggerFactory.getLogger(SimulatorThermalEssReactingImpl.class);

	private final CalculateEnergyFromPower calculateChargeEnergy = new CalculateEnergyFromPower(this,
			ThermalEss.ChannelId.THERMAL_CHARGE_ENERGY);
	private CalculateEnergyFromTemperature calculateDischargeEnergy = new CalculateEnergyFromTemperature(this,
			ThermalEss.ChannelId.THERMAL_DISCHARGE_ENERGY);
	private CalculateStateFromTemperature calculateState = new CalculateStateFromTemperature(this,
			ThermalEss.ChannelId.SOC);
	private final CalculateChargeDischargePower calculateChargeDischargePower = new CalculateChargeDischargePower(
			this);

	private final List<SymmetricHeating> heatings = new CopyOnWriteArrayList<>();

	private Config config;

	private long energy = 0; // [Wmsec]
	private int capacity;

	private Instant lastTimestamp = null;

	@Reference
	private ComponentManager componentManager;

	@Reference
	private ConfigurationAdmin cm;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = OPTIONAL)
	private volatile Timedata timedata = null;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = OPTIONAL)
	private volatile SimulatorDatasource datasource = null;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = MULTIPLE)
	protected void addHeating(SymmetricHeating heating) {
		this.heatings.add(heating);
		if (heating instanceof ManagedSymmetricHeating mh) {
			// A heat pump may serve multiple storages exclusively, e.g. the layers of
			// a stratified tank; generic Heating devices are bound one-to-one.
			mh.bindThermalStorage(this);
		}
	}

	protected void removeHeating(SymmetricHeating heating) {
		this.heatings.remove(heating);
		if (heating instanceof ManagedSymmetricHeating mh) {
			mh.unbindThermalStorage(this);
		}
	}

	public SimulatorThermalEssReactingImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ThermalEss.ChannelId.values(), //
				ManagedThermalEss.ChannelId.values(), //
				SimulatorThermalEssReacting.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;

		// update target filters for dynamic references
		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "Heating", config.heating_ids())) {
			return;
		}
		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "datasource", config.datasource_id())) {
			return;
		}

		this.calculateDischargeEnergy.setVolume(config.volume());
		this.calculateState.setVolume(config.volume())
				.setMaxTemperature(config.maxTemperature())
				.setMinTemperature(config.minTemperature());
		this.capacity = this.calculateState.getCapacity();
		this._setCapacity(this.capacity);

		var initialTemp = calculateTemperatureFromSoc(config.initialSoc(),
				config.minTemperature(), config.maxTemperature());
		this._setMaxTemperature((int) (config.maxTemperature() * 10));
		this._setTargetTemperature((int) (config.targetTemperature() * 10));
		this._setMinTemperature((int) (config.minTemperature() * 10));
		this._setTemperature(initialTemp);

		this.calculateState.update(initialTemp);
		this.energy = (long) ((double) this.capacity * 3_600_000L / 100 * config.initialSoc());
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public String[] getHeatingIds() {
		// Aggregators may call before activate() has bound the configuration.
		var config = this.config;
		return config != null ? config.heating_ids() : new String[0];
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case TOPIC_CYCLE_AFTER_PROCESS_IMAGE //
			-> this.calculateEnergy();
		case TOPIC_CYCLE_EXECUTE_WRITE //
			-> this.distributeTarget();
		}
	}

	/**
	 * Resolves the effective thermal target of this cycle and distributes it to
	 * the managed Heating devices in the configured priority order. Without a
	 * pending write the Heating devices remain autonomous.
	 */
	private void distributeTarget() {
		var target = TargetPowerDistributor.resolveEffectiveTarget(this);
		if (target == null) {
			return;
		}
		var receivers = this.getManagedHeatings().stream() //
				.map(heating -> TargetPowerDistributor.ofHeating(heating, this)) //
				.toList();
		try {
			TargetPowerDistributor.distribute(target, receivers);
		} catch (OpenemsNamedException e) {
			this.logWarn(this.log, "Unable to distribute thermal target: " + e.getMessage());
		}
	}

	/**
	 * Gets the managed Heating devices in the priority order of the configured
	 * Heating-IDs. Dynamic references bind in arbitrary order, so the configured
	 * order is authoritative.
	 *
	 * @return the ordered List of {@link ManagedSymmetricHeating}
	 */
	private List<ManagedSymmetricHeating> getManagedHeatings() {
		var result = new ArrayList<ManagedSymmetricHeating>();
		for (var heatingId : this.config.heating_ids()) {
			for (var heating : this.heatings) {
				if (heating.id().equals(heatingId) && heating instanceof ManagedSymmetricHeating managed) {
					result.add(managed);
					break;
				}
			}
		}
		return result;
	}

	private void calculateEnergy() {
		var now = Instant.now(this.componentManager.getClock());

		// Aggregate net thermal power: heatings produce, datasource consumes. A
		// heat pump shared between multiple storages only charges its currently
		// served storage; its heat must not be booked into the other layers.
		var chargePower = 0;
		for (SymmetricHeating heating : this.heatings) {
			if (HeatPumpCirculation.deliversTo(heating, this)) {
				chargePower += heating.getThermalPower().orElse(0);
			}
		}
		var dischargePower = 0;
		if (this.datasource != null) {
			Integer consumption = this.datasource.getValue(OpenemsType.INTEGER,
					new ChannelAddress(this.id(), "ThermalPower"));
			if (consumption != null) {
				dischargePower = consumption;
			}
		}
		var thermalPower = chargePower - dischargePower;

		if (this.lastTimestamp != null) {
			var durationMs = Duration.between(this.lastTimestamp, now).toMillis();

			// energy [Wmsec] += power [W] * duration [ms]; positive = charging
			this.energy += (long) thermalPower * durationMs;

			// clamp energy to [0, capacity]
			var maxEnergy = (long) this.capacity * 3_600_000L;
			if (this.energy > maxEnergy) {
				this.energy = maxEnergy;
			} else if (this.energy < 0) {
				this.energy = 0;
			}

			// SoC + Temperature from energy
			var soc = this.capacity > 0 //
					? (int) Math.round(this.energy * 100.0 / (this.capacity * 3_600_000.0)) //
					: 0;
			soc = Math.min(100, Math.max(0, soc));
			var temperature = calculateTemperatureFromSoc(soc, this.config.minTemperature(),
					this.config.maxTemperature());

			this.calculateState.update(temperature); // writes CAPACITY + SOC
			this._setTemperature(temperature);
			this._setThermalPower(thermalPower);
			this.calculateChargeDischargePower.update(thermalPower, chargePower);

			// Update charge energy (direct power integration)
			this.calculateChargeEnergy.update(thermalPower > 0 ? thermalPower : 0);

			// Update discharge energy (5-min moving average from temperature)
			this.calculateDischargeEnergy.update(temperature, thermalPower);

			this.updateTargetThermalPowerLimits(temperature);
		}

		this.lastTimestamp = now;
	}

	/**
	 * Publishes the aggregated thermal intake limits of the registered Heating
	 * devices. The maximum drops to zero once the storage has reached its hardware
	 * maximum temperature.
	 *
	 * @param temperature the current storage temperature in [deci-°C]
	 */
	private void updateTargetThermalPowerLimits(int temperature) {
		var managedHeatings = this.getManagedHeatings();
		var maxTargetThermalPower = TargetPowerDistributor.sumMaxTargetThermalPower(managedHeatings);
		var maxTemperature = this.getMaxTemperature().get();
		if (maxTemperature != null && temperature >= maxTemperature) {
			maxTargetThermalPower = 0;
		}
		this._setMaxTargetThermalPower(maxTargetThermalPower);
		this._setMinTargetThermalPower(TargetPowerDistributor.minTargetThermalPower(managedHeatings));
	}

	@Override
	public String debugLog() {
		return "SoC:" + this.getSoc().asString() //
				+ "|T:" + this.getTemperature().asString();
	}

	/**
	 * Linear interpolation of temperature from SoC.
	 *
	 * @param soc     the current State of Charge [%]
	 * @param minTemp the minimum temperature in [°C]
	 * @param maxTemp the maximum temperature in [°C]
	 * @return the interpolated temperature in [dezi-°C]
	 */
	private static int calculateTemperatureFromSoc(int soc, float minTemp, float maxTemp) {
		return Math.round((minTemp + (maxTemp - minTemp) * soc / 100f) * 10);
	}
}
