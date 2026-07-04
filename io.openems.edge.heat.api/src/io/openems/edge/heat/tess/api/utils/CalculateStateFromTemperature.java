package io.openems.edge.heat.tess.api.utils;

import static io.openems.edge.common.channel.ChannelUtils.setValue;

import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.component.OpenemsComponent;

/**
 * Calculates thermal storage capacity [Wh] and State of Charge [%] from
 * temperature range and water volume.
 *
 * <p>
 * Capacity formula: capacity [Wh] = cp * mass * (Tmax - Tmin) / KJ_PER_WH
 *
 * <p>
 * SoC formula: soc = (T - Tmin) / (Tmax - Tmin) * 100, clamped 0..100
 *
 * <p>
 * Usage: create an instance with the target channels, volume, and temperature
 * bounds, then call {@link #update(int)} each cycle with the current
 * temperature in [deci-°C].
 */
public class CalculateStateFromTemperature {

	private static final float WATER_SPECIFIC_HEAT = 4.1813F; // kJ/(kg·°C)
	private static final float WATER_DENSITY = 1.0F; // kg/L
	private static final float KJ_PER_WH = 3.6F;

	private final OpenemsComponent component;
	private final ChannelId channelId;

	private Float mass;
	private Integer capacity;
	private Float minTemperature;
	private Float maxTemperature;

	public CalculateStateFromTemperature(OpenemsComponent component, ChannelId channelId) {
		this.component = component;
		this.channelId = channelId;
	}

	public int getCapacity() {
		return this.capacity;
	}

	/**
	 * Sets the volume in liters.
	 *
	 * @param volumeLiters the volume in liters
	 * @return this
	 */
	public CalculateStateFromTemperature setVolume(int volumeLiters) {
		this.mass = volumeLiters * WATER_DENSITY;
		this.capacity = calculateCapacity(this.maxTemperature, this.minTemperature, this.mass);
		return this;
	}

	/**
	 * Gets the minimum temperature in [°C].
	 *
	 * @return the minimum temperature
	 */
	public Float getMinTemperature() {
		return this.minTemperature;
	}

	/**
	 * Sets the minimum temperature in [°C].
	 *
	 * @param minTemperature the minimum temperature
	 * @return this
	 */
	public CalculateStateFromTemperature setMinTemperature(float minTemperature) {
		this.minTemperature = minTemperature;
		this.capacity = calculateCapacity(this.maxTemperature, minTemperature, this.mass);
		return this;
	}

	/**
	 * Gets the maximum temperature in [deci-°C].
	 *
	 * @return the maximum temperature
	 */
	public Float getMaxTemperature() {
		return this.maxTemperature;
	}

	/**
	 * Sets the maximum temperature in [°C].
	 *
	 * @param maxTemperature the maximum temperature
	 * @return this
	 */
	public CalculateStateFromTemperature setMaxTemperature(float maxTemperature) {
		this.maxTemperature = maxTemperature;
		return this;
	}

	/**
	 * Calculates Capacity and SoC from the current temperature and writes to the
	 * target channels.
	 *
	 * @param temperature the current average temperature in [deci-°C]
	 */
	public void update(int temperature) {
		var temperatureCelsius = temperature / 10.0f;
		var deltaT = Math.min(this.maxTemperature, temperatureCelsius - this.minTemperature); // °C
		var capacity = calculateCapacity(deltaT, this.mass);
		var soc = Math.max(0, Math.min(100, (int) Math.round((double) capacity / this.capacity * 100.0)));
		setValue(this.component, this.channelId, soc);
	}

	private static Integer calculateCapacity(Float maxTemperature, Float minTemperature, Float mass) {
		if (maxTemperature == null || minTemperature == null || mass == null) {
			return null;
		}
		var deltaTemperature = Math.max(0, (maxTemperature - minTemperature)); // °C;
		return calculateCapacity(deltaTemperature, mass);
	}

	private static Integer calculateCapacity(float deltaTemperature, Float mass) {
		if (mass == null) {
			return null;
		}
		return (int) Math.round(WATER_SPECIFIC_HEAT * mass * deltaTemperature / KJ_PER_WH);
	}
}
