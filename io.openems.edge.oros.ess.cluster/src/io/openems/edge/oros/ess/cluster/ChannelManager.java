package io.openems.edge.oros.ess.cluster;

import static io.openems.edge.common.channel.ChannelUtils.setValue;
import static io.openems.edge.ess.api.CalculateGridMode.aggregateGridModes;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import io.openems.common.utils.IntUtils;
import io.openems.edge.common.channel.AbstractChannelListenerManager;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.ess.api.CalculateSoc;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;

public class ChannelManager extends AbstractChannelListenerManager {
	private final EssClusterImpl parent;

	public ChannelManager(EssClusterImpl parent) {
		this.parent = parent;
	}

	/**
	 * Called on Component activate().
	 *
	 * @param esss the List of {@link EnergyStorageSystem}
	 */
	protected void activate(List<EnergyStorageSystem> esss) {
		// SymmetricEss
		this.calculateSoc(esss);
		this.calculate(INTEGER_SUM, esss, SymmetricEss.ChannelId.CAPACITY);
		this.calculateGridMode(esss);
		this.calculate(INTEGER_SUM, esss, SymmetricEss.ChannelId.ACTIVE_POWER);
		this.calculate(INTEGER_SUM, esss, SymmetricEss.ChannelId.REACTIVE_POWER);
		this.calculate(INTEGER_SUM, esss, SymmetricEss.ChannelId.MAX_APPARENT_POWER);
		this.calculate(LONG_SUM, esss, SymmetricEss.ChannelId.ACTIVE_CHARGE_ENERGY);
		this.calculate(LONG_SUM, esss, SymmetricEss.ChannelId.ACTIVE_DISCHARGE_ENERGY);
		this.calculate(INTEGER_MIN, esss, SymmetricEss.ChannelId.MIN_CELL_VOLTAGE);
		this.calculate(INTEGER_MAX, esss, SymmetricEss.ChannelId.MAX_CELL_VOLTAGE);
		this.calculate(INTEGER_MIN, esss, SymmetricEss.ChannelId.MIN_CELL_TEMPERATURE);
		this.calculate(INTEGER_MAX, esss, SymmetricEss.ChannelId.MAX_CELL_TEMPERATURE);
		// ManagedSymmetricEss
		this.calculate(INTEGER_SUM, esss, ManagedSymmetricEss.ChannelId.ALLOWED_CHARGE_POWER);
		this.calculate(INTEGER_SUM, esss, ManagedSymmetricEss.ChannelId.ALLOWED_DISCHARGE_POWER);
		// StartStoppable
		this.calculateStartStop(esss);
	}

	/**
	 * Calculate effective Grid-Mode of {@link EnergyStorageSystem}.
	 *
	 * @param esss the List of {@link EnergyStorageSystem}
	 */
	private void calculateGridMode(List<EnergyStorageSystem> esss) {
		final BiConsumer<Value<Integer>, Value<Integer>> callback = (oldValue, newValue) -> {
			var gridModes = esss.stream() //
					.map(SymmetricEss::getGridMode) //
					.toList();
			var result = aggregateGridModes(gridModes);
			setValue(this.parent, SymmetricEss.ChannelId.GRID_MODE, result);
		};
		for (EnergyStorageSystem ess : esss) {
			this.addOnChangeListener(ess, SymmetricEss.ChannelId.GRID_MODE, callback);
		}
	}

	/**
	 * Calculate effective StartStop status of {@link EnergyStorageSystem}.
	 *
	 * @param esss the List of {@link EnergyStorageSystem}
	 */
	private void calculateStartStop(List<EnergyStorageSystem> esss) {
		final var startStoppableEss = esss.stream() //
				.filter(StartStoppable.class::isInstance) //
				.map(StartStoppable.class::cast) //
				.toList();
		final BiConsumer<Value<Integer>, Value<Integer>> callback = (oldValue, newValue) -> {
			final var essMap = startStoppableEss.stream() //
					.collect(groupingBy(StartStoppable::getStartStop));

			var result = StartStop.UNDEFINED;
			if (!startStoppableEss.isEmpty()) {
				if (essMap.getOrDefault(StartStop.START, emptyList()).size() == startStoppableEss.size()) {
					result = StartStop.START;
				}
				if (essMap.getOrDefault(StartStop.STOP, emptyList()).size() == startStoppableEss.size()) {
					result = StartStop.STOP;
				}
			}
			this.parent._setStartStop(result);
		};
		startStoppableEss.forEach(ess -> {
			this.addOnChangeListener(ess, StartStoppable.ChannelId.START_STOP, callback);
		});
	}

	/**
	 * Calculate weighted State-Of-Charge of {@link EnergyStorageSystem}.
	 *
	 * @param esss the List of {@link EnergyStorageSystem}
	 */
	private void calculateSoc(List<EnergyStorageSystem> esss) {
		final BiConsumer<Value<Integer>, Value<Integer>> callback = (oldValue, newValue) -> {
			var calculateSoc = new CalculateSoc();
			for (SymmetricEss ess : esss) {
				calculateSoc.add(ess);
			}
			this.parent._setSoc(calculateSoc.calculate());
		};
		this.addOnChangeListener(this.parent, SymmetricEss.ChannelId.CAPACITY, callback);
		for (EnergyStorageSystem ess : esss) {
			this.addOnChangeListener(ess, SymmetricEss.ChannelId.SOC, callback);
			this.addOnChangeListener(ess, SymmetricEss.ChannelId.CAPACITY, callback);
		}
	}

	private static final BiFunction<Integer, Integer, Integer> INTEGER_MIN = IntUtils::minInteger;
	private static final BiFunction<Integer, Integer, Integer> INTEGER_MAX = IntUtils::maxInteger;
	private static final BiFunction<Integer, Integer, Integer> INTEGER_SUM = IntUtils::sumInteger;
	private static final BiFunction<Long, Long, Long> LONG_SUM = TypeUtils::sum;

	/**
	 * Aggregate Channels of {@link EnergyStorageSystem}s.
	 *
	 * @param <T>        the Channel Type
	 * @param aggregator the aggregator function
	 * @param esss       the List of {@link EnergyStorageSystem}
	 * @param channelId  the SymmetricEss.ChannelId
	 */
	private <T> void calculate(BiFunction<T, T, T> aggregator, List<EnergyStorageSystem> esss,
			SymmetricEss.ChannelId channelId) {
		final BiConsumer<Value<T>, Value<T>> callback = (oldValue, newValue) -> {
			T result = null;
			for (EnergyStorageSystem ess : esss) {
				Channel<T> channel = ess.channel(channelId);
				result = aggregator.apply(result, channel.getNextValue().get());
			}
			Channel<T> channel = this.parent.channel(channelId);
			channel.setNextValue(result);
		};
		for (EnergyStorageSystem ess : esss) {
			this.addOnChangeListener(ess, channelId, callback);
		}
	}

	/**
	 * Aggregate Channels of {@link ManagedSymmetricEss}s.
	 *
	 * @param <T>        the Channel Type
	 * @param aggregator the aggregator function
	 * @param esss       the List of {@link EnergyStorageSystem}
	 * @param channelId  the ManagedSymmetricEss.ChannelId
	 */
	private <T> void calculate(BiFunction<T, T, T> aggregator, List<EnergyStorageSystem> esss,
			ManagedSymmetricEss.ChannelId channelId) {
		final BiConsumer<Value<T>, Value<T>> callback = (oldValue, newValue) -> {
			T result = null;
			for (EnergyStorageSystem ess : esss) {
				if (ess instanceof ManagedSymmetricEss mse) {
					Channel<T> channel = mse.channel(channelId);
					result = aggregator.apply(result, channel.getNextValue().get());
				}
			}
			Channel<T> channel = this.parent.channel(channelId);
			channel.setNextValue(result);
		};
		for (EnergyStorageSystem ess : esss) {
			if (ess instanceof ManagedSymmetricEss mse) {
				this.addOnChangeListener(mse, channelId, callback);
			}
		}
	}
}
