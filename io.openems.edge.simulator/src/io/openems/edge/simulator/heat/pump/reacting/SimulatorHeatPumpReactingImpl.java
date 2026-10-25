package io.openems.edge.simulator.heat.pump.reacting;

import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;
import static org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.heat.api.ManagedSymmetricHeating;
import io.openems.edge.heat.api.SymmetricHeating;
import io.openems.edge.heat.pump.api.HeatPump;
import io.openems.edge.heat.pump.api.ManagedHeatPump;
import io.openems.edge.heat.pump.core.HeatPumpPerformanceEstimator;
import io.openems.edge.heat.tess.api.ThermalEss;
import io.openems.edge.simulator.heat.pump.reacting.statemachine.Context;
import io.openems.edge.simulator.heat.pump.reacting.statemachine.StateMachine;
import io.openems.edge.simulator.heat.pump.reacting.statemachine.StateMachine.State;
import io.openems.edge.simulator.heat.tank.SimulatorStorageTank;
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
public class SimulatorHeatPumpReactingImpl extends AbstractOpenemsComponent implements
		SimulatorHeatPumpReacting, ManagedHeatPump, HeatPump, ManagedSymmetricHeating, SymmetricHeating,
		StartStoppable, OpenemsComponent, TimedataProvider, EventHandler {

	private final Logger log = LoggerFactory.getLogger(SimulatorHeatPumpReactingImpl.class);

	private final StateMachine stateMachine = new StateMachine(State.STOPPED);

	private final CalculateEnergyFromPower calculateConsumptionEnergy = new CalculateEnergyFromPower(this,
			SymmetricHeating.ChannelId.ACTIVE_CONSUMPTION_ENERGY);
	private final CalculateEnergyFromPower calculateThermalEnergy = new CalculateEnergyFromPower(this,
			SymmetricHeating.ChannelId.THERMAL_ENERGY);

	/*
	 * The storages served exclusively by this heat pump, e.g. the layers of a
	 * stratified tank charged through a diverter valve. Priority is derived from
	 * the storages' MinTemperature: the storage with the highest hard lower limit
	 * is served first (e.g. domestic hot water before the space-heating buffer),
	 * with the Component-ID as deterministic tie-break.
	 */
	private final List<ThermalEss> thermalStorages = new CopyOnWriteArrayList<>();
	private final Map<ThermalEss, Integer> thermalStorageRequests = new ConcurrentHashMap<>();
	private final Map<ThermalEss, Boolean> thermalStorageDemands = new ConcurrentHashMap<>();
	private volatile ThermalEss activeThermalStorage = null;

	/*
	 * The simulated water of the storages, if any. A tank provides the
	 * temperatures a real heat pump measures: the immersion sleeve its two-point
	 * control switches on, the top for the hardware limit and the water flowing
	 * back at the return connection.
	 */
	private final List<SimulatorStorageTank> storageTanks = new CopyOnWriteArrayList<>();

	private Config config;
	private HeatPumpPerformanceEstimator copEstimator;
	private int nominalActivePower;
	private int minActivePower;
	private int minActivePowerHysteresis;
	private Duration minRuntime = Duration.ZERO;
	private int activePower = 0;
	private int thermalPower = 0;
	private double appliedSupplyTemperature = HeatPumpPerformanceEstimator.STANDARD_RATING_SINK_TEMPERATURE;

	@Reference
	private ComponentManager componentManager;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = OPTIONAL)
	private volatile Timedata timedata = null;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = MULTIPLE)
	protected void addStorageTank(SimulatorStorageTank tank) {
		this.storageTanks.add(tank);
	}

	protected void removeStorageTank(SimulatorStorageTank tank) {
		this.storageTanks.remove(tank);
	}

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
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		super.activate(context, config.id(), config.alias(), config.enabled());
		if (config.cop() < 1) {
			throw new OpenemsException("Invalid COP : " + config.cop());
		}
		this.config = config;
		this.copEstimator = HeatPumpPerformanceEstimator.of(config.cop());
		this.nominalActivePower = Math.round(config.thermalPower() / config.cop());
		this.minActivePower = Math.min(Math.round(config.minThermalPower() / config.cop()), this.nominalActivePower);
		this.minActivePowerHysteresis = config.hysteresis() > 0 ? config.hysteresis() : this.minActivePower / 2;
		this.minRuntime = Duration.ofMinutes(Math.max(0, config.minRuntime()));
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void bindThermalStorage(ThermalEss storage) {
		if (storage != null && !this.thermalStorages.contains(storage)) {
			this.thermalStorages.add(storage);
		}
	}

	@Override
	public void unbindThermalStorage(ThermalEss storage) {
		this.thermalStorages.remove(storage);
		this.thermalStorageRequests.remove(storage);
		this.thermalStorageDemands.remove(storage);
	}

	@Override
	public ThermalEss getThermalStorage() {
		return this.activeThermalStorage;
	}

	@Override
	public void applyPower(ThermalEss storage, int targetPower) throws OpenemsNamedException {
		if (!this.thermalStorages.isEmpty()) {
			// Requests are arbitrated between the registered storages in the next
			// cycle; a storage below its MinTemperature precedes any request. Requests
			// of unregistered storages are ignored.
			if (storage != null && this.thermalStorages.contains(storage)) {
				this.thermalStorageRequests.put(storage, targetPower);
			}
		} else {
			this.getTargetActivePowerChannel().setNextWriteValue(targetPower);
		}
	}

	@Override
	public void setStartStop(StartStop value) {
		// The heat pump starts and stops autonomously with its heat demand; the
		// StartStop Channel reflects the actual compressor runtime.
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
			-> {
			this.handleStateMachine();
			this.calculateEnergy();
		}
		}
	}

	/**
	 * Runs the {@link StateMachine} on the arbitrated heat demand of this cycle
	 * and publishes the resulting Channel values.
	 */
	private void handleStateMachine() {
		this.channel(SimulatorHeatPumpReacting.ChannelId.STATE_MACHINE)
				.setNextValue(this.stateMachine.getCurrentState());

		var context = this.createContext();
		try {
			this.stateMachine.run(context);
		} catch (OpenemsNamedException e) {
			this.logError(this.log, "StateMachine failed: " + e.getMessage());
		}

		this.updateChannels(context);
	}

	/**
	 * Collects the inputs of one StateMachine cycle: consumes the pending power
	 * requests, arbitrates the storage to serve and derives the flow temperature.
	 *
	 * @return the {@link Context}
	 */
	private Context createContext() {
		// A direct write to the TargetActivePower Channel (e.g. via REST) is an
		// anonymous request and is attributed to the highest-priority storage.
		final var channelRequest = this.getTargetActivePowerChannel().getNextWriteValueAndReset();

		ThermalEss storage = null;
		Integer requestedPower = null;
		var autonomousDemand = false;
		if (!this.thermalStorages.isEmpty()) {
			channelRequest.ifPresent(power -> {
				var ordered = this.orderedThermalStorages();
				if (!ordered.isEmpty()) {
					this.thermalStorageRequests.put(ordered.get(0), power);
				}
			});
			var decision = this.decideThermalStorage(this.getMaxTemperature().get());
			storage = decision.storage();
			requestedPower = decision.requestedPower();
			autonomousDemand = decision.isAutonomous();
		} else if (channelRequest.isPresent()) {
			// No storage bound: follow the request without temperature limits.
			requestedPower = channelRequest.get();
		}

		return new Context(this, this.config.modulating(), this.config.thermalPower(), this.nominalActivePower,
				this.minActivePower, this.minActivePowerHysteresis, this.minRuntime,
				Instant.now(this.componentManager.getClock()), this.getMaxTemperature().get(), this.copEstimator,
				this.config.spread(), storage, requestedPower, autonomousDemand);
	}

	/**
	 * Gets the temperature the two-point control of a storage switches on: the
	 * immersion sleeve of its simulated tank, or the storage temperature.
	 *
	 * @param storage the {@link ThermalEss}
	 * @return the temperature in [deci-°C], or null if unknown
	 */
	public Integer switchingTemperatureOf(ThermalEss storage) {
		var tank = this.storageTankOf(storage);
		return tank != null ? tank.getSensorTemperature().get() : storage.getTemperature().get();
	}

	/**
	 * Gets the temperature of the hottest point of a storage, which is limited by
	 * the hardware maximum temperatures: the top of its simulated tank, or the
	 * storage temperature.
	 *
	 * @param storage the {@link ThermalEss}
	 * @return the temperature in [deci-°C], or null if unknown
	 */
	public Integer hottestTemperatureOf(ThermalEss storage) {
		var tank = this.storageTankOf(storage);
		return tank != null ? tank.getTopTemperature().get() : storage.getTemperature().get();
	}

	/**
	 * Gets the temperature of the water flowing back from a storage while
	 * charging it: the return connection of its simulated tank, or the storage
	 * temperature.
	 *
	 * @param storage the {@link ThermalEss}
	 * @return the temperature in [deci-°C], or null if unknown
	 */
	public Integer returnTemperatureOf(ThermalEss storage) {
		var tank = this.storageTankOf(storage);
		return tank != null ? tank.getReturnTemperature().get() : storage.getTemperature().get();
	}

	private SimulatorStorageTank storageTankOf(ThermalEss storage) {
		for (var tank : this.storageTanks) {
			if (storage.id().equals(tank.getThermalEssId())) {
				return tank;
			}
		}
		return null;
	}

	/**
	 * Applies the operating point decided by a StateHandler.
	 *
	 * @param activePower       the electrical consumption in [W]
	 * @param thermalPower      the thermal output in [W]
	 * @param cop               the estimated COP
	 * @param supplyTemperature the flow temperature in [°C]
	 */
	public void applyPowerState(int activePower, int thermalPower, float cop, double supplyTemperature) {
		this.activePower = activePower;
		this.thermalPower = thermalPower;
		this.appliedSupplyTemperature = supplyTemperature;
		this._setActivePower(activePower);
		this._setThermalPower(thermalPower);
		this._setCop(cop);
	}

	/**
	 * Overrides the storage the produced heat is attributed to. Used by the
	 * StateMachine while the compressor finishes its minimum runtime after the
	 * heat demand already ended.
	 *
	 * @param storage the {@link ThermalEss} to attribute the heat to
	 */
	public void setActiveThermalStorage(ThermalEss storage) {
		this.activeThermalStorage = storage;
	}

	/**
	 * Publishes the operating limits and temperatures after the StateMachine ran.
	 * The StartStop Channel reflects the actual compressor runtime.
	 *
	 * @param context the {@link Context} of this cycle
	 */
	private void updateChannels(Context context) {
		var running = this.stateMachine.getCurrentState() == State.RUNNING;
		this._setStartStop(running ? StartStop.START : StartStop.STOP);
		if (this.config.modulating()) {
			this._setMaxTargetActivePower(this.nominalActivePower);
			this._setMinTargetActivePower(this.minActivePower);
			this._setMinThermalPower(this.config.minThermalPower());
		} else {
			// An on/off device can only take its full electrical draw.
			var electricalDraw = context.electricalDraw();
			this._setMaxTargetActivePower(electricalDraw);
			this._setMinTargetActivePower(electricalDraw);
			this._setMinThermalPower(this.config.thermalPower());
		}
		this._setMaxThermalPower(this.config.thermalPower());
		this._setSupplyTemperature(running ? toDeciDegree(this.appliedSupplyTemperature) : 0);
		this._setReturnTemperature(running ? toDeciDegree(this.appliedSupplyTemperature - this.config.spread()) : 0);
		if (context.getStorageTemperature() != null) {
			this._setTemperature(context.getStorageTemperature());
		}
	}

	private void calculateEnergy() {
		this.calculateConsumptionEnergy.update(this.activePower);
		this.calculateThermalEnergy.update(this.thermalPower);
	}

	/**
	 * The arbitration result of one cycle. Without a storage the heat pump does
	 * not heat; without a requested power the storage is served by autonomous
	 * two-point control.
	 */
	private record Decision(ThermalEss storage, Integer requestedPower) {

		private static final Decision OFF = new Decision(null, null);

		private boolean isAutonomous() {
			return this.storage != null && this.requestedPower == null;
		}
	}

	/**
	 * Arbitrates the storage to serve in this cycle and consumes the pending
	 * requests. Autonomous two-point demand — a storage at or below its
	 * MinTemperature, held until its off point is reached — always precedes
	 * explicit power requests: comfort and frost protection cannot be
	 * overridden. Explicit requests are served for the highest-priority
	 * requesting storage that can still take heat, up to the hardware limits;
	 * undelivered requests expire with the cycle.
	 *
	 * @param heatPumpMaxTemperature the hardware maximum temperature of the heat
	 *                               pump in [deci-°C], or null if unknown
	 * @return the {@link Decision}
	 */
	private Decision decideThermalStorage(Integer heatPumpMaxTemperature) {
		var ordered = this.orderedThermalStorages();

		var requests = new HashMap<ThermalEss, Integer>();
		for (var storage : ordered) {
			var request = this.thermalStorageRequests.remove(storage);
			if (request != null) {
				requests.put(storage, request);
			}
		}
		// Requests of storages unregistered in the meantime expire silently.
		this.thermalStorageRequests.clear();

		// Two-point demand per storage: on at or below MinTemperature, off at the
		// autonomous off point, hold in between. An autonomous heating cycle thus
		// runs to completion and is not interrupted by explicit requests. The
		// hardware limits of the hottest point stop any heating cycle.
		for (var storage : ordered) {
			var temperature = this.switchingTemperatureOf(storage);
			var minTemperature = storage.getMinTemperature().get();
			var offPoint = minIgnoreNull(storage.getTargetTemperature().get(), storage.getMaxTemperature().get(),
					heatPumpMaxTemperature);
			if (temperature == null || minTemperature == null || offPoint == null
					|| !this.canTakeHeat(storage, heatPumpMaxTemperature)) {
				this.thermalStorageDemands.put(storage, false);
			} else if (temperature <= minTemperature) {
				this.thermalStorageDemands.put(storage, true);
			} else if (temperature >= offPoint) {
				this.thermalStorageDemands.put(storage, false);
			} else {
				this.thermalStorageDemands.putIfAbsent(storage, false);
			}
		}

		// Comfort and frost protection precede explicit requests.
		for (var storage : ordered) {
			if (this.thermalStorageDemands.getOrDefault(storage, false)) {
				this.activeThermalStorage = storage;
				return new Decision(storage, null);
			}
		}

		// Highest-priority positive request whose storage can still take heat, up
		// to the hardware limits of both the storage and the heat pump.
		for (var storage : ordered) {
			var power = requests.get(storage);
			if (power == null || power <= 0 || !this.canTakeHeat(storage, heatPumpMaxTemperature)) {
				continue;
			}
			this.activeThermalStorage = storage;
			return new Decision(storage, power);
		}

		this.activeThermalStorage = null;
		return Decision.OFF;
	}

	/**
	 * Whether a storage can still take heat, i.e. its hottest point is below the
	 * hardware maximum temperatures of both the storage and the heat pump.
	 *
	 * @param storage                the {@link ThermalEss}
	 * @param heatPumpMaxTemperature the hardware maximum temperature of the heat
	 *                               pump in [deci-°C], or null if unknown
	 * @return true if heat can be delivered
	 */
	public boolean canTakeHeat(ThermalEss storage, Integer heatPumpMaxTemperature) {
		var temperature = this.hottestTemperatureOf(storage);
		var hardOffPoint = minIgnoreNull(storage.getMaxTemperature().get(), heatPumpMaxTemperature);
		return temperature == null || hardOffPoint == null || temperature < hardOffPoint;
	}

	/**
	 * Gets the registered storages in priority order: highest MinTemperature
	 * first, Component-ID as tie-break. Storages without a known MinTemperature
	 * come last.
	 *
	 * @return the ordered List of {@link ThermalEss}
	 */
	private List<ThermalEss> orderedThermalStorages() {
		return this.thermalStorages.stream() //
				.sorted(Comparator //
						.comparing((ThermalEss storage) -> storage.getMinTemperature().orElse(Integer.MIN_VALUE),
								Comparator.reverseOrder()) //
						.thenComparing(ThermalEss::id)) //
				.toList();
	}

	/**
	 * Converts a temperature in [°C] to [deci-°C].
	 *
	 * @param celsius the temperature in °C
	 * @return the temperature in deci-°C
	 */
	private static int toDeciDegree(double celsius) {
		return (int) Math.round(celsius * 10);
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
		return this.stateMachine.debugLog() //
				+ "|P:" + this.getActivePower().asString() //
				+ "|Pth:" + this.getThermalPower().asString();
	}
}
