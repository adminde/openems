package io.openems.edge.heat.tess.api.utils;

import static io.openems.edge.common.channel.ChannelUtils.setValue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;

import io.openems.common.types.ChannelAddress;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.timedata.api.TimedataProvider;

/**
 * Calculates cumulated thermal discharge energy [Wh_Σ] for a thermal energy
 * storage.
 *
 * <p>
 * Discharge power is derived from the difference between the known thermal
 * input power (from heating components) and the net thermal power observed via
 * temperature change (cp × mass × dT/dt). A 5-minute moving average smooths
 * the thermal inertia before integration.
 *
 * <p>
 * Usage: make the component implement {@link TimedataProvider}, create an
 * instance, and call {@link #update(int, Integer)} each cycle.
 */
public class CalculateEnergyFromTemperature {

	private static final float WATER_SPECIFIC_HEAT = 4.1813F; // kJ/(kg·°C)
	private static final float WATER_DENSITY = 1.0F; // kg/L
	private static final float KJ_PER_WH = 3.6F;

	private static final int MOVING_AVG_WINDOW = 5 * 60 * 1000;

	private record DischargeSample(Instant timestamp, int power) {
	}

	private static enum State {
		TIMEDATA_QUERY_NOT_STARTED, TIMEDATA_QUERY_IS_RUNNING, CALCULATE_ENERGY_OPERATION
	}

	/**
	 * Keeps the current State.
	 */
	private State state = State.TIMEDATA_QUERY_NOT_STARTED;

	private final LinkedList<DischargeSample> dischargeSamples = new LinkedList<>();

	private final TimedataProvider component;

	/**
	 * Keeps the target {@link ChannelId} of the Energy channel.
	 */
	private final ChannelId channelId;

	/**
	 * BaseCumulatedEnergy keeps the energy in [Wh]. It is initialized during
	 * TIMEDATA_QUERY_* states.
	 */
	private Long baseCumulatedEnergy = null;

	/**
	 * ContinuousCumulatedEnergy keeps the exceeding energy in [Wmsec]. It is
	 * continuously updated during CALCULATE_ENERGY_OPERATION state.
	 */
	private long continuousCumulatedEnergy = 0L;

	/**
	 * Keeps the timestamp of the last data.
	 */
	private Instant lastTimestamp = null;

	/**
	 * Keeps the last temperature value.
	 */
	private Integer lastTemperature = null;

	private Float mass;

	private final Clock clock;

	public CalculateEnergyFromTemperature(TimedataProvider component, ChannelId channelId) {
		this(component, channelId,  Clock.systemDefaultZone());
	}

	public CalculateEnergyFromTemperature(TimedataProvider component, ChannelId channelId, Clock clock) {
		this.component = component;
		this.channelId = channelId;
		this.clock = clock;
	}

	/**
	 * Sets the volume in liters.
	 *
	 * @param volumeLiters the volume in liters
	 * @return this
	 */
	public CalculateEnergyFromTemperature setVolume(int volumeLiters) {
		this.mass = volumeLiters * WATER_DENSITY;
		return this;
	}

	/**
	 * Calculate the Discharge Energy and update the Channel.
	 *
	 * @param currentTemperature the current average temperature in [deci-°C]
	 * @param thermalPower       the summed thermal input power from heating
	 *                           components in [W], or null if not available
	 */
	public void update(int currentTemperature, Integer thermalPower) {
		switch (this.state) {
		case TIMEDATA_QUERY_NOT_STARTED:
			this.initializeCumulatedEnergyFromTimedata();
			break;

		case TIMEDATA_QUERY_IS_RUNNING:
			break;

		case CALCULATE_ENERGY_OPERATION:
			this.calculateEnergy(currentTemperature, thermalPower);
			break;
		}

		this.lastTimestamp = Instant.now(this.clock);
		this.lastTemperature = currentTemperature;
	}

	/**
	 * Initialize cumulated energy value from Timedata service.
	 */
	private void initializeCumulatedEnergyFromTimedata() {
		var timedata = this.component.getTimedata();
		var componentId = this.component.id();
		if (timedata == null || componentId == null) {
			this.state = State.TIMEDATA_QUERY_NOT_STARTED;

		} else {
			this.state = State.TIMEDATA_QUERY_IS_RUNNING;

			timedata.getLatestValue(new ChannelAddress(this.component.id(), this.channelId.id()))
					.thenAccept(cumulatedEnergyOpt -> {
						this.state = State.CALCULATE_ENERGY_OPERATION;

						if (cumulatedEnergyOpt.isPresent()) {
							try {
								this.baseCumulatedEnergy = TypeUtils.getAsType(OpenemsType.LONG,
										cumulatedEnergyOpt.get());
							} catch (IllegalArgumentException e) {
								this.baseCumulatedEnergy = 0L;
							}
						} else {
							this.baseCumulatedEnergy = 0L;
						}
					});
		}
	}

	/**
	 * Calculate the discharge energy from temperature change and thermal input
	 * power.
	 *
	 * @param currentTemperature the current average temperature in [deci-°C]
	 * @param thermalPower       the summed thermal input power in [W]
	 */
	private void calculateEnergy(int currentTemperature, Integer thermalPower) {
		if (this.lastTimestamp == null || this.lastTemperature == null || this.mass == null || this.baseCumulatedEnergy == null) {
			setValue(this.component, this.channelId, this.baseCumulatedEnergy);
			return;
		}
		var now = Instant.now(this.clock);
		var durationMs = Duration.between(this.lastTimestamp, now).toMillis();

		if (durationMs > 0 && thermalPower != null) {
			// Net thermal power from temperature change [W]
			var deltaTemperature = (currentTemperature - this.lastTemperature) / 10.0; // °C
			var deltaEnergy = WATER_SPECIFIC_HEAT * this.mass * deltaTemperature / KJ_PER_WH;
			var deltaPower = (int) Math.round(deltaEnergy * 3_600_000.0 / durationMs);

			// Discharge = input power - stored power (positive = heat leaving the tank)
			this.dischargeSamples.addLast(new DischargeSample(now, thermalPower - deltaPower));
			var dischargeSamplesWindow = now.minusMillis(MOVING_AVG_WINDOW);
			while (!this.dischargeSamples.isEmpty() && this.dischargeSamples.peekFirst().timestamp().isBefore(dischargeSamplesWindow)) {
				this.dischargeSamples.removeFirst();
			}

			// Calculate moving average and integrate
			var dischargePower =  Math.max(0, this.dischargeSamples.stream().mapToInt(DischargeSample::power).average().orElse(0));
			this.continuousCumulatedEnergy += (long) (dischargePower * durationMs);

			// Update base energy if 1 Wh passed
			if (this.continuousCumulatedEnergy >= 3_600_000 /* 1 Wh */) {
				this.baseCumulatedEnergy += this.continuousCumulatedEnergy / 3_600_000;
				this.continuousCumulatedEnergy %= 3_600_000;
			}
		}
		setValue(this.component, this.channelId, this.baseCumulatedEnergy);
	}

	/**
	 * Set baseEnergy manually and go to CALCULATE_ENERGY_OPERATION.
	 *
	 * @param baseCumulatedEnergy the base cumulated energy in [Wh]
	 */
	public void setBaseEnergyManually(long baseCumulatedEnergy) {
		this.baseCumulatedEnergy = baseCumulatedEnergy;
		this.state = State.CALCULATE_ENERGY_OPERATION;
	}
}
