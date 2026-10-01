package io.openems.edge.heat.tess.layer;

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
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.heat.api.ManagedSymmetricHeating;
import io.openems.edge.heat.api.SymmetricHeating;
import io.openems.edge.heat.tess.api.ManagedThermalEss;
import io.openems.edge.heat.tess.api.ThermalEss;
import io.openems.edge.heat.tess.api.utils.CalculateChargeDischargePower;
import io.openems.edge.heat.tess.api.utils.CalculateStateFromTemperature;
import io.openems.edge.heat.tess.api.utils.HeatPumpCirculation;
import io.openems.edge.heat.tess.api.utils.TargetPowerDistributor;
import io.openems.edge.heat.tess.core.Connection;
import io.openems.edge.heat.tess.core.NetThermalPowerEstimator;
import io.openems.edge.heat.tess.core.TemperatureProfile;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Tess.Layer", //
		immediate = true, //
		configurationPolicy = REQUIRE)
@EventTopics({ //
		TOPIC_CYCLE_AFTER_PROCESS_IMAGE, //
		TOPIC_CYCLE_EXECUTE_WRITE })
public class ThermalEssLayerImpl extends AbstractOpenemsComponent implements ThermalEssLayer, ManagedThermalEss,
		ThermalEss, OpenemsComponent, TimedataProvider, EventHandler {

	/**
	 * Time after the start of a circulation until the return line carries storage
	 * water instead of the water that stood in the pipes.
	 */
	private static final Duration SETTLING_TIME = Duration.ofMinutes(2);

	private static final Duration THERMAL_POWER_WINDOW = Duration.ofMinutes(5);

	private final Logger log = LoggerFactory.getLogger(ThermalEssLayerImpl.class);

	private final CalculateStateFromTemperature calculateState = new CalculateStateFromTemperature(this,
			ThermalEss.ChannelId.SOC);
	private final CalculateChargeDischargePower calculateChargeDischargePower = new CalculateChargeDischargePower(
			this);
	private final HeatPumpCirculation circulation = new HeatPumpCirculation(SETTLING_TIME);

	private final List<SymmetricHeating> heatings = new CopyOnWriteArrayList<>();

	private Config config;
	private ChannelAddress sensorChannelAddress;
	private NetThermalPowerEstimator netThermalPower;
	private CalculateEnergyFromPower calculateChargeEnergy;
	private CalculateEnergyFromPower calculateDischargeEnergy;

	@Reference
	private ComponentManager componentManager;

	@Reference
	private ConfigurationAdmin cm;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = OPTIONAL)
	private volatile Timedata timedata = null;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = MULTIPLE)
	protected void addHeating(SymmetricHeating heating) {
		this.heatings.add(heating);
		if (heating instanceof ManagedSymmetricHeating managed) {
			managed.bindThermalStorage(this);
		}
	}

	protected void removeHeating(SymmetricHeating heating) {
		this.heatings.remove(heating);
		if (heating instanceof ManagedSymmetricHeating managed) {
			managed.unbindThermalStorage(this);
		}
	}

	public ThermalEssLayerImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ThermalEss.ChannelId.values(), //
				ManagedThermalEss.ChannelId.values(), //
				ThermalEssLayer.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsNamedException {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		this.sensorChannelAddress = ChannelAddress.fromString(config.sensorChannelAddress());

		var clock = this.componentManager.getClock();
		this.netThermalPower = new NetThermalPowerEstimator(config.volume(), THERMAL_POWER_WINDOW);
		this.calculateChargeEnergy = new CalculateEnergyFromPower(this, ThermalEss.ChannelId.THERMAL_CHARGE_ENERGY,
				clock);
		this.calculateDischargeEnergy = new CalculateEnergyFromPower(this,
				ThermalEss.ChannelId.THERMAL_DISCHARGE_ENERGY, clock);

		this.calculateState.setVolume(config.volume()) //
				.setMaxTemperature(config.maxTemperature()) //
				.setMinTemperature(config.minTemperature());
		this._setCapacity(this.calculateState.getCapacity());
		this._setMaxTemperature(Math.round(config.maxTemperature() * 10));
		this._setTargetTemperature(Math.round(config.targetTemperature() * 10));
		this._setMinTemperature(Math.round(config.minTemperature() * 10));

		OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "Heating", config.heating_ids());
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
			-> this.estimateState();
		case TOPIC_CYCLE_EXECUTE_WRITE //
			-> this.distributeTarget();
		}
	}

	/**
	 * Estimates the temperature profile of the layer and derives the mean
	 * temperature, the State-of-Charge and the thermal power from it.
	 */
	private void estimateState() {
		var now = Instant.now(this.componentManager.getClock());
		var heatings = List.copyOf(this.heatings);
		final var wasSettled = this.circulation.isSettled();
		this.circulation.update(this, heatings, now);
		this._setSupplyTemperature(this.circulation.getSupplyTemperature());
		this._setReturnTemperature(this.circulation.getReturnTemperature());

		Integer chargePower = heatings.isEmpty() ? null : 0;
		for (var heating : heatings) {
			if (HeatPumpCirculation.deliversTo(heating, this)) {
				chargePower += heating.getThermalPower().orElse(0);
			}
		}

		var profile = this.estimateProfile();
		Integer temperature = null;
		Integer hottestTemperature = null;
		Integer thermalPower = null;
		if (profile != null) {
			var mean = profile.getMeanTemperature();
			if (this.circulation.isSettled() && !wasSettled) {
				this.netThermalPower.rebase(mean);
			}
			thermalPower = this.netThermalPower.update(now, mean);
			temperature = (int) Math.round(mean * 10);
			hottestTemperature = (int) Math.round(profile.getMaxTemperature() * 10);
			this.calculateState.update(temperature);
		} else {
			this._setSoc(null);
		}
		this._setTemperature(temperature);
		this._setThermalPower(thermalPower);
		this.calculateChargeDischargePower.update(thermalPower, chargePower);
		this.calculateChargeEnergy.update(this.getThermalChargePowerChannel().getNextValue().get());
		this.calculateDischargeEnergy.update(this.getThermalDischargePowerChannel().getNextValue().get());

		this.updateTargetThermalPowerLimits(hottestTemperature);
	}

	/**
	 * Estimates the vertical temperature profile of the layer. The supply and
	 * return temperatures of the last settled circulation complete the profile if
	 * the heat pump water flows directly through the layer. The sensor reading is
	 * added last, so it prevails over a connection at the same height.
	 *
	 * @return the {@link TemperatureProfile} in [°C], or null if the sensor is
	 *         unavailable
	 */
	private TemperatureProfile estimateProfile() {
		var sensorTemperature = this.getSensorTemperature();
		if (sensorTemperature == null) {
			return null;
		}
		var profile = new TemperatureProfile();
		var supplyTemperature = this.circulation.getSupplyTemperature();
		var returnTemperature = this.circulation.getReturnTemperature();
		if (this.config.connection() == Connection.DIRECT && supplyTemperature != null
				&& returnTemperature != null) {
			profile.add(this.config.supplyHeight() / 100.0, supplyTemperature / 10.0) //
					.add(this.config.returnHeight() / 100.0, returnTemperature / 10.0);
		}
		return profile.add(this.config.sensorHeight() / 100.0, sensorTemperature / 10.0);
	}

	/**
	 * Gets the current reading of the configured sensor Channel.
	 *
	 * @return the temperature in [deci-°C], or null if unavailable
	 */
	private Integer getSensorTemperature() {
		try {
			Channel<?> channel = this.componentManager.getChannel(this.sensorChannelAddress);
			return TypeUtils.getAsType(OpenemsType.INTEGER, channel.value().get());
		} catch (OpenemsNamedException | IllegalArgumentException e) {
			return null;
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

	/**
	 * Publishes the aggregated thermal intake limits of the registered Heating
	 * devices. The maximum drops to zero once the hottest point of the layer has
	 * reached its hardware maximum temperature.
	 *
	 * @param hottestTemperature the temperature of the hottest point in
	 *                           [deci-°C], or null if unknown
	 */
	private void updateTargetThermalPowerLimits(Integer hottestTemperature) {
		var managedHeatings = this.getManagedHeatings();
		var maxTargetThermalPower = TargetPowerDistributor.sumMaxTargetThermalPower(managedHeatings);
		var maxTemperature = this.getMaxTemperature().get();
		if (hottestTemperature != null && maxTemperature != null && hottestTemperature >= maxTemperature) {
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
}
