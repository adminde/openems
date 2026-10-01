package io.openems.edge.simulator.heat.tank;

import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.time.Duration;
import java.time.Instant;

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

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.ChannelAddress;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.heat.api.SymmetricHeating;
import io.openems.edge.heat.pump.api.HeatPump;
import io.openems.edge.heat.tess.api.ManagedThermalEss;
import io.openems.edge.heat.tess.api.utils.HeatPumpCirculation;
import io.openems.edge.heat.tess.core.Connection;
import io.openems.edge.heat.tess.core.StratifiedTank;
import io.openems.edge.simulator.datasource.api.SimulatorDatasource;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Simulator.StorageTank", //
		immediate = true, //
		configurationPolicy = REQUIRE)
@EventTopics({ //
		TOPIC_CYCLE_AFTER_PROCESS_IMAGE })
public class SimulatorStorageTankImpl extends AbstractOpenemsComponent
		implements SimulatorStorageTank, OpenemsComponent, EventHandler {

	private static final double BOTTOM = 0.0;
	private static final double TOP = 1.0;

	private Config config;
	private StratifiedTank tank;
	private Instant lastTimestamp = null;

	@Reference
	private ComponentManager componentManager;

	@Reference
	private ConfigurationAdmin cm;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = OPTIONAL)
	private volatile SimulatorDatasource datasource = null;

	public SimulatorStorageTankImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				SimulatorStorageTank.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		this.tank = new StratifiedTank(config.volume(), config.nodes(), config.initialTemperature());
		this.updateChannels();

		OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "datasource", config.datasource_id());
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public String getThermalEssId() {
		var config = this.config;
		return config != null ? config.thermalEssId() : null;
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case TOPIC_CYCLE_AFTER_PROCESS_IMAGE //
			-> this.simulate();
		}
	}

	/**
	 * Applies the heat flows since the last cycle to the tank and publishes its
	 * temperatures.
	 */
	private void simulate() {
		var now = Instant.now(this.componentManager.getClock());
		if (this.lastTimestamp != null) {
			var seconds = Duration.between(this.lastTimestamp, now).toMillis() / 1000.0;
			this.charge(seconds);
			this.draw(seconds);
		}
		this.lastTimestamp = now;
		this.updateChannels();
	}

	/**
	 * Charges the tank with the output of the Heating devices delivering to the
	 * Thermal ESS. The water of a heat pump enters at the supply and leaves at the
	 * return connection; a heat exchanger coil and any other Heating device heat
	 * the water between both connections.
	 *
	 * @param seconds the duration in [s]
	 */
	private void charge(double seconds) {
		ManagedThermalEss storage;
		try {
			if (!(this.componentManager.getComponent(this.config.thermalEssId()) instanceof ManagedThermalEss tess)) {
				return;
			}
			storage = tess;
		} catch (OpenemsNamedException e) {
			return;
		}
		var supplyHeight = this.config.supplyHeight() / 100.0;
		var returnHeight = this.config.returnHeight() / 100.0;
		for (var heatingId : storage.getHeatingIds()) {
			SymmetricHeating heating;
			try {
				if (!(this.componentManager.getComponent(heatingId) instanceof SymmetricHeating component)) {
					continue;
				}
				heating = component;
			} catch (OpenemsNamedException e) {
				continue;
			}
			if (!HeatPumpCirculation.deliversTo(heating, storage)) {
				continue;
			}
			var power = heating.getThermalPower().orElse(0);
			var supplyTemperature = heating instanceof HeatPump heatPump //
					? heatPump.getSupplyTemperature().get() //
					: null;
			if (this.config.connection() == Connection.DIRECT && supplyTemperature != null) {
				this.tank.flow(power, supplyTemperature / 10.0, supplyHeight, returnHeight, seconds);
			} else {
				this.tank.heat(power, returnHeight, supplyHeight, seconds);
			}
		}
	}

	/**
	 * Draws the thermal consumption of the datasource: hot water leaves at the top,
	 * the consumer return enters at the bottom.
	 *
	 * @param seconds the duration in [s]
	 */
	private void draw(double seconds) {
		var datasource = this.datasource;
		if (datasource == null) {
			return;
		}
		Integer consumption = datasource.getValue(OpenemsType.INTEGER, new ChannelAddress(this.id(), "ThermalPower"));
		if (consumption != null) {
			this.tank.flow(consumption, this.config.consumerReturnTemperature(), BOTTOM, TOP, seconds);
		}
	}

	private void updateChannels() {
		this._setSensorTemperature(toDeciDegree(this.tank.getTemperatureAt(this.config.sensorHeight() / 100.0)));
		this._setReturnTemperature(toDeciDegree(this.tank.getTemperatureAt(this.config.returnHeight() / 100.0)));
		this._setTopTemperature(toDeciDegree(this.tank.getTemperatureAt(TOP)));
		this._setMeanTemperature(toDeciDegree(this.tank.getMeanTemperature()));
	}

	private static int toDeciDegree(double celsius) {
		return (int) Math.round(celsius * 10);
	}

	@Override
	public String debugLog() {
		return "T:" + this.getSensorTemperature().asString() //
				+ "|Tmean:" + this.getMeanTemperature().asString();
	}
}
