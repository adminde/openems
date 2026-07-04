package io.openems.edge.heat.tess.cluster;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import io.openems.common.utils.IntUtils;
import io.openems.edge.common.channel.AbstractChannelListenerManager;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.heat.tess.api.CalculateTessSoc;
import io.openems.edge.heat.tess.api.SocAveragingMethod;
import io.openems.edge.heat.tess.api.ThermalEss;

public class ChannelManager extends AbstractChannelListenerManager {

	private final ThermalEssClusterImpl parent;

	public ChannelManager(ThermalEssClusterImpl parent) {
		this.parent = parent;
	}

	/**
	 * Called on Component activate().
	 *
	 * @param tesss              the List of {@link ThermalEss}
	 * @param socAveragingMethod the configured {@link SocAveragingMethod}
	 */
	protected void activate(List<ThermalEss> tesss, SocAveragingMethod socAveragingMethod) {
		this.calculateSoc(tesss, socAveragingMethod);
		this.calculateTemperature(tesss);
		this.calculate(INTEGER_SUM, tesss, ThermalEss.ChannelId.CAPACITY);
		this.calculate(INTEGER_SUM, tesss, ThermalEss.ChannelId.THERMAL_POWER);
		this.calculate(LONG_SUM, tesss, ThermalEss.ChannelId.THERMAL_CHARGE_ENERGY);
		this.calculate(LONG_SUM, tesss, ThermalEss.ChannelId.THERMAL_DISCHARGE_ENERGY);
		// Tightest common operating band: the cluster never reports limits beyond
		// those of its most restrictive member.
		this.calculate(INTEGER_MIN, tesss, ThermalEss.ChannelId.MAX_TEMPERATURE);
		this.calculate(INTEGER_MIN, tesss, ThermalEss.ChannelId.MAX_TARGET_TEMPERATURE);
		this.calculate(INTEGER_MAX, tesss, ThermalEss.ChannelId.MIN_TARGET_TEMPERATURE);
	}

	/**
	 * Calculate the average State-of-Charge of {@link ThermalEss}s using the
	 * configured {@link SocAveragingMethod}.
	 *
	 * @param tesss              the List of {@link ThermalEss}
	 * @param socAveragingMethod the configured {@link SocAveragingMethod}
	 */
	private void calculateSoc(List<ThermalEss> tesss, SocAveragingMethod socAveragingMethod) {
		final BiConsumer<Value<Integer>, Value<Integer>> callback = (oldValue, newValue) -> {
			var calculate = new CalculateTessSoc(socAveragingMethod);
			for (ThermalEss tess : tesss) {
				calculate.add(tess);
			}
			this.parent._setSoc(calculate.calculate());
		};
		for (ThermalEss tess : tesss) {
			this.addOnChangeListener(tess, ThermalEss.ChannelId.SOC, callback);
			this.addOnChangeListener(tess, ThermalEss.ChannelId.CAPACITY, callback);
		}
	}

	/**
	 * Calculate the capacity-weighted average Temperature of {@link ThermalEss}s.
	 * Falls back to the unweighted average if any capacity is unavailable.
	 *
	 * @param tesss the List of {@link ThermalEss}
	 */
	private void calculateTemperature(List<ThermalEss> tesss) {
		final BiConsumer<Value<Integer>, Value<Integer>> callback = (oldValue, newValue) -> {
			var weightedSum = 0.;
			var totalCapacity = 0.;
			var unweightedSum = 0.;
			var count = 0;
			var weighted = true;
			for (ThermalEss tess : tesss) {
				var temperature = tess.getTemperatureChannel().getNextValue().get();
				if (temperature == null) {
					continue;
				}
				count++;
				unweightedSum += temperature;
				var capacity = tess.getCapacityChannel().getNextValue().get();
				if (capacity == null || capacity <= 0) {
					weighted = false;
					continue;
				}
				weightedSum += capacity * (double) temperature;
				totalCapacity += capacity;
			}
			final Integer result;
			if (count == 0) {
				result = null;
			} else if (weighted && totalCapacity > 0) {
				result = (int) Math.round(weightedSum / totalCapacity);
			} else {
				result = (int) Math.round(unweightedSum / count);
			}
			this.parent._setTemperature(result);
		};
		for (ThermalEss tess : tesss) {
			this.addOnChangeListener(tess, ThermalEss.ChannelId.TEMPERATURE, callback);
			this.addOnChangeListener(tess, ThermalEss.ChannelId.CAPACITY, callback);
		}
	}

	private static final BiFunction<Integer, Integer, Integer> INTEGER_MIN = IntUtils::minInteger;
	private static final BiFunction<Integer, Integer, Integer> INTEGER_MAX = IntUtils::maxInteger;
	private static final BiFunction<Integer, Integer, Integer> INTEGER_SUM = IntUtils::sumInteger;
	private static final BiFunction<Long, Long, Long> LONG_SUM = TypeUtils::sum;

	/**
	 * Aggregate Channels of {@link ThermalEss}s.
	 *
	 * @param <T>        the Channel Type
	 * @param aggregator the aggregator function
	 * @param tesss      the List of {@link ThermalEss}
	 * @param channelId  the ThermalEss.ChannelId
	 */
	private <T> void calculate(BiFunction<T, T, T> aggregator, List<ThermalEss> tesss,
			ThermalEss.ChannelId channelId) {
		final BiConsumer<Value<T>, Value<T>> callback = (oldValue, newValue) -> {
			T result = null;
			for (ThermalEss tess : tesss) {
				Channel<T> channel = tess.channel(channelId);
				result = aggregator.apply(result, channel.getNextValue().get());
			}
			Channel<T> channel = this.parent.channel(channelId);
			channel.setNextValue(result);
		};
		for (ThermalEss tess : tesss) {
			this.addOnChangeListener(tess, channelId, callback);
		}
	}
}
