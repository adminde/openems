package io.openems.edge.oros.ess.core;

import static io.openems.edge.common.channel.ChannelUtils.setValue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.function.ThrowingConsumer;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.battery.api.BatteryErrorAcknowledge;
import io.openems.edge.batteryinverter.api.BatteryInverterErrorAcknowledge;
import io.openems.edge.batteryinverter.api.HybridManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.ManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.common.channel.AbstractChannelListenerManager;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.WriteChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.ess.api.AsymmetricEss;
import io.openems.edge.ess.api.EssErrorAcknowledge;
import io.openems.edge.ess.api.HybridEss;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.oros.bms.api.BatteryManagementSystem;
import io.openems.edge.oros.common.SymmetricComponent;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;
import io.openems.edge.oros.ess.core.protection.PowerLimiter;
import io.openems.edge.oros.pcs.api.PowerConversionSystem;

public class ChannelManager extends AbstractChannelListenerManager {

	private record OnSetNextWriteValueListener<T>(
			OpenemsComponent component,
			ChannelId channelId,
			ThrowingConsumer<T, OpenemsNamedException> callback) {
	}

	private final List<OnSetNextWriteValueListener<?>> onSetNextWriteValueListeners = new ArrayList<>();

	private final EnergyStorageSystem parent;

	private PowerLimiter powerLimiter;
	private CapacityListener capacityListener;
	private StateOfChargeListener stateOfChargeListener;

	public ChannelManager(EnergyStorageSystem parent) {
		super();
		this.parent = parent;
	}

	public ChannelManager setPowerLimiter(PowerLimiter limiter) {
		this.powerLimiter = limiter;
		return this;
	}

	public PowerLimiter getPowerLimiter() {
		return this.powerLimiter;
	}

	public ChannelManager setCapacityListener(CapacityListener listener) {
		this.capacityListener = listener;
		return this;
	}

	public CapacityListener getCapacityListener() {
		return this.capacityListener;
	}

	public ChannelManager setStateOfChargeListener(StateOfChargeListener listener) {
		this.stateOfChargeListener = listener;
		return this;
	}

	public StateOfChargeListener getStateOfChargeListener() {
		return this.stateOfChargeListener;
	}

	/**
	 * Called on Component activate().
	 *
	 * @param clock                      the {@link ClockProvider}
	 * @param battery                    the {@link BatteryManagementSystem}
	 * @param inverter                   the {@link PowerConversionSystem}
	 */
	public void activate(ClockProvider clock, PowerConversionSystem inverter, BatteryManagementSystem battery) {
		this.addBatteryListener(clock, battery);
		this.addInverterListener(inverter);
		this.addEssListener(clock, battery);
	}

	@Override
	public synchronized void deactivate() {
		super.deactivate();
		for (OnSetNextWriteValueListener<?> listener : this.onSetNextWriteValueListeners) {
			this.removeOnSetNextWriteCallback(listener.component, listener.channelId, listener.callback);
		}
		this.onSetNextWriteValueListeners.clear();
	}

	private void addEssListener(ClockProvider clock, BatteryManagementSystem battery) {
		this.addEssSocListener(clock, battery);
		this.addEssCapacityListener(clock, battery);
		this.addOnRelativePowerListener(this.parent,
				EnergyStorageSystem.ChannelId.SET_ACTIVE_RELATIVE_POWER_EQUALS,
				ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_EQUALS);
		this.addOnRelativePowerListener(this.parent,
				EnergyStorageSystem.ChannelId.SET_ACTIVE_RELATIVE_POWER_LESS_OR_EQUALS,
				ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_LESS_OR_EQUALS);
		this.addOnRelativePowerListener(this.parent,
				EnergyStorageSystem.ChannelId.SET_ACTIVE_RELATIVE_POWER_GREATER_OR_EQUALS,
				ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_GREATER_OR_EQUALS);

		if (this.powerLimiter != null) {
			this.addOnChangeListener(this.parent, StartStoppable.ChannelId.START_STOP, (ignored0, ignored1) ->
					this.powerLimiter.accept(clock));
		}
	}

	private void addEssCapacityListener(ClockProvider clock, BatteryManagementSystem battery) {
		if (this.capacityListener == null) {
			this.capacityListener = new CapacityListener(this.parent, battery);
		}
		final Consumer<Value<Integer>> trigger = ignored -> this.capacityListener.accept(clock);

		for (Channel<Integer> source : this.capacityListener.getCapacityChannels()) {
			source.onSetNextValue(trigger);
		}
	}

	private void addEssSocListener(ClockProvider clock, BatteryManagementSystem battery) {
		if (this.stateOfChargeListener == null) {
			this.stateOfChargeListener = new StateOfChargeListener(this.parent, battery);
		}
		final Consumer<Value<Integer>> trigger = ignored -> this.stateOfChargeListener.accept(clock);

		this.stateOfChargeListener.getStateOfChargeChannel().onSetNextValue(trigger);
		battery.getChargeMaxCurrentChannel().onSetNextValue(trigger);
		battery.getDischargeMaxCurrentChannel().onSetNextValue(trigger);
	}

	private void addInverterListener(PowerConversionSystem inverter) {
		if (inverter instanceof BatteryInverterErrorAcknowledge) {
			this.<Long>addOnSetNextMirrorListener(inverter,
					BatteryInverterErrorAcknowledge.ChannelId.TIMEOUT_START_BATTERY_INVERTER,
					EssErrorAcknowledge.ChannelId.TIMEOUT_START_BATTERY_INVERTER);
			this.<Long>addOnSetNextMirrorListener(inverter,
					BatteryInverterErrorAcknowledge.ChannelId.TIMEOUT_STOP_BATTERY_INVERTER,
					EssErrorAcknowledge.ChannelId.TIMEOUT_STOP_BATTERY_INVERTER);
		}

		this.<Long>addOnSetNextMirrorListener(inverter,
				SymmetricBatteryInverter.ChannelId.GRID_MODE,
				SymmetricEss.ChannelId.GRID_MODE);
		this.<Long>addOnSetNextMirrorListener(inverter,
				SymmetricBatteryInverter.ChannelId.MAX_APPARENT_POWER,
				SymmetricEss.ChannelId.MAX_APPARENT_POWER);
		this.<Long>addOnSetNextMirrorListener(inverter,
				PowerConversionSystem.ChannelId.MAX_ACTIVE_POWER,
				EnergyStorageSystem.ChannelId.MAX_ACTIVE_POWER);
		this.<Long>addOnSetNextMirrorListener(inverter,
				PowerConversionSystem.ChannelId.MAX_REACTIVE_POWER,
				EnergyStorageSystem.ChannelId.MAX_REACTIVE_POWER);

		if (this.parent instanceof HybridEss) {
			switch (inverter) {
				case HybridManagedSymmetricBatteryInverter hybridInverter -> {
					this.<Long>addOnSetNextMirrorListener(hybridInverter,
							HybridManagedSymmetricBatteryInverter.ChannelId.DC_CHARGE_ENERGY,
							HybridEss.ChannelId.DC_CHARGE_ENERGY);
					this.<Long>addOnSetNextMirrorListener(hybridInverter,
							HybridManagedSymmetricBatteryInverter.ChannelId.DC_DISCHARGE_ENERGY,
							HybridEss.ChannelId.DC_DISCHARGE_ENERGY);
					this.<Long>addOnSetNextMirrorListener(hybridInverter,
							HybridManagedSymmetricBatteryInverter.ChannelId.DC_DISCHARGE_POWER,
							HybridEss.ChannelId.DC_DISCHARGE_POWER);
				}
				case ManagedSymmetricBatteryInverter batteryInverter -> {
					this.<Long>addOnSetNextMirrorListener(batteryInverter,
							SymmetricBatteryInverter.ChannelId.ACTIVE_CHARGE_ENERGY,
							HybridEss.ChannelId.DC_CHARGE_ENERGY);
					this.<Long>addOnSetNextMirrorListener(batteryInverter,
							SymmetricBatteryInverter.ChannelId.ACTIVE_DISCHARGE_ENERGY,
							HybridEss.ChannelId.DC_DISCHARGE_ENERGY);
					this.<Long>addOnSetNextMirrorListener(batteryInverter,
							SymmetricBatteryInverter.ChannelId.ACTIVE_POWER,
							HybridEss.ChannelId.DC_DISCHARGE_POWER);
				}
			}
		}
		this.<Long>addOnSetNextMirrorListener(inverter,
				SymmetricBatteryInverter.ChannelId.ACTIVE_CHARGE_ENERGY,
				SymmetricEss.ChannelId.ACTIVE_CHARGE_ENERGY);
		this.<Long>addOnSetNextMirrorListener(inverter,
				SymmetricBatteryInverter.ChannelId.ACTIVE_DISCHARGE_ENERGY,
				SymmetricEss.ChannelId.ACTIVE_DISCHARGE_ENERGY);

		this.<Long>addOnSetNextMirrorListener(inverter,
				SymmetricBatteryInverter.ChannelId.ACTIVE_POWER,
				SymmetricEss.ChannelId.ACTIVE_POWER);
		this.<Long>addOnSetNextMirrorListener(inverter,
				SymmetricBatteryInverter.ChannelId.REACTIVE_POWER,
				SymmetricEss.ChannelId.REACTIVE_POWER);

		if (this.parent instanceof SymmetricComponent
				|| this.parent instanceof AsymmetricEss) {

			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.FREQUENCY,
					SymmetricComponent.ChannelId.FREQUENCY);

			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.ACTIVE_POWER_L1,
					SymmetricComponent.ChannelId.ACTIVE_POWER_L1);
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.ACTIVE_POWER_L2,
					SymmetricComponent.ChannelId.ACTIVE_POWER_L2);
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.ACTIVE_POWER_L3,
					SymmetricComponent.ChannelId.ACTIVE_POWER_L3);
	
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.REACTIVE_POWER_L1,
					SymmetricComponent.ChannelId.REACTIVE_POWER_L1);
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.REACTIVE_POWER_L2,
					SymmetricComponent.ChannelId.REACTIVE_POWER_L2);
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.REACTIVE_POWER_L3,
					SymmetricComponent.ChannelId.REACTIVE_POWER_L3);
	
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.CURRENT_L1,
					SymmetricComponent.ChannelId.CURRENT_L1);
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.CURRENT_L2,
					SymmetricComponent.ChannelId.CURRENT_L2);
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.CURRENT_L3,
					SymmetricComponent.ChannelId.CURRENT_L3);
	
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.VOLTAGE_L1,
					SymmetricComponent.ChannelId.VOLTAGE_L1);
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.VOLTAGE_L2,
					SymmetricComponent.ChannelId.VOLTAGE_L2);
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.VOLTAGE_L3,
					SymmetricComponent.ChannelId.VOLTAGE_L3);
		}
		if (this.parent instanceof SymmetricComponent) {
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.VOLTAGE_L1_L2,
					SymmetricComponent.ChannelId.VOLTAGE_L1_L2);
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.VOLTAGE_L2_L3,
					SymmetricComponent.ChannelId.VOLTAGE_L2_L3);
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.VOLTAGE_L3_L1,
					SymmetricComponent.ChannelId.VOLTAGE_L3_L1);
	
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.POWER_FACTOR,
					SymmetricComponent.ChannelId.POWER_FACTOR);
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.POWER_FACTOR_L1,
					SymmetricComponent.ChannelId.POWER_FACTOR_L1);
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.POWER_FACTOR_L2,
					SymmetricComponent.ChannelId.POWER_FACTOR_L2);
			this.<Long>addOnSetNextMirrorListener(inverter,
					SymmetricComponent.ChannelId.POWER_FACTOR_L3,
					SymmetricComponent.ChannelId.POWER_FACTOR_L3);
		}
	}

	private void addBatteryListener(ClockProvider clock, BatteryManagementSystem battery) {
		if (battery instanceof BatteryErrorAcknowledge) {
			this.<Long>addOnSetNextMirrorListener(battery,
					BatteryErrorAcknowledge.ChannelId.TIMEOUT_START_BATTERY,
					EssErrorAcknowledge.ChannelId.TIMEOUT_START_BATTERY);
			this.<Long>addOnSetNextMirrorListener(battery,
					BatteryErrorAcknowledge.ChannelId.TIMEOUT_STOP_BATTERY,
					EssErrorAcknowledge.ChannelId.TIMEOUT_STOP_BATTERY);
		}

		this.addOnSetNextMirrorListener(battery,
				Battery.ChannelId.MIN_CELL_VOLTAGE,
				SymmetricEss.ChannelId.MIN_CELL_VOLTAGE);
		this.addOnSetNextMirrorListener(battery,
				Battery.ChannelId.MAX_CELL_VOLTAGE,
				SymmetricEss.ChannelId.MAX_CELL_VOLTAGE);
		this.addOnSetNextMirrorListener(battery,
				Battery.ChannelId.MIN_CELL_TEMPERATURE,
				SymmetricEss.ChannelId.MIN_CELL_TEMPERATURE);
		this.addOnSetNextMirrorListener(battery,
				Battery.ChannelId.MAX_CELL_TEMPERATURE,
				SymmetricEss.ChannelId.MAX_CELL_TEMPERATURE);

		if (this.powerLimiter != null) {
			if (this.powerLimiter.hasOverChargeCurrentLimiter()) {
				this.addOnSetNextValueListener(battery, Battery.ChannelId.CHARGE_MAX_VOLTAGE,
						ignored -> this.powerLimiter.getOverChargeCurrentLimiter().accept(clock));
			}
			if (this.powerLimiter.hasDeepDischargeCurrentLimiter()) {
				this.addOnSetNextValueListener(battery, Battery.ChannelId.DISCHARGE_MIN_VOLTAGE,
						ignored -> this.powerLimiter.getDeepDischargeCurrentLimiter().accept(clock));
			}
			this.addOnSetNextValueListener(battery, Battery.ChannelId.CHARGE_MAX_CURRENT,
					ignored -> this.powerLimiter.accept(clock));
			this.addOnSetNextValueListener(battery, Battery.ChannelId.DISCHARGE_MAX_CURRENT,
					ignored -> this.powerLimiter.accept(clock));
			this.addOnSetNextValueListener(battery, Battery.ChannelId.VOLTAGE,
					ignored -> this.powerLimiter.accept(clock));
		}
	}

	/**
	 * Adds a Copy-Listener. It listens on setNextValue() and copies the value to the target channel.
	 *
	 * @param <T>             the Channel value type
	 * @param sourceComponent the source component - Battery or BatteryInverter
	 * @param sourceChannelId the source ChannelId
	 * @param targetChannelId the target ChannelId
	 */
	protected <T> void addOnSetNextMirrorListener(OpenemsComponent sourceComponent,
			ChannelId sourceChannelId, ChannelId targetChannelId) {
		this.<T>addOnSetNextValueListener(sourceComponent, sourceChannelId, value -> {
			setValue(this.parent, targetChannelId, value);
		});
	}

	/**
	 * Scales a per-mille command against {@link EnergyStorageSystem#getMaxActivePower()} and forwards 
	 * the resulting Watt value to the target Channel. Out-of-range values are left to the fixed value
	 * Channels constraint handling.
	 *
	 * @param sourceComponent the source component
	 * @param sourceChannelId the source ChannelId
	 * @param targetChannelId the target ChannelId
	 */
	private void addOnRelativePowerListener(EnergyStorageSystem sourceComponent,
			ChannelId sourceChannelId, ChannelId targetChannelId) {
		this.<Integer>addOnSetNextWriteValueListener(sourceComponent, sourceChannelId, value -> {
			var max = sourceComponent.getMaxActivePower();
			if (!max.isDefined()) {
				return;
			}
			IntegerWriteChannel channel = sourceComponent.channel(targetChannelId);
			channel.setNextWriteValue((int) Math.round(value / 1000F * max.get()));
		});
	}

	/**
	 * Adds a Listener. Also applies the callback once to make sure it applies
	 * already existing values.
	 *
	 * @param <T>       the Channel value type
	 * @param component the Component
	 * @param channelId the ChannelId
	 * @param callback  the callback
	 */
	protected <T> void addOnSetNextWriteValueListener(OpenemsComponent component,
			  ChannelId channelId, ThrowingConsumer<T, OpenemsNamedException> callback) {
		this.onSetNextWriteValueListeners.add(new OnSetNextWriteValueListener<>(component, channelId, callback));
		WriteChannel<T> channel = component.channel(channelId);
		channel.onSetNextWrite(callback);
		try {
			var value = channel.getNextWriteValue();
			if (value.isPresent()) {
				callback.accept(value.get());
			}
		} catch (OpenemsNamedException e) {
			// TODO: Validate if or how this should be logged
		}
	}

	/**
	 * Removes a Listener.
	 *
	 * @param <T>       the Channel value type
	 * @param component the Component
	 * @param channelId the ChannelId
	 * @param callback  the callback
	 */
	protected <T> void removeOnSetNextWriteCallback(OpenemsComponent component,
			  ChannelId channelId, ThrowingConsumer<T, OpenemsNamedException> callback) {
		WriteChannel<T> channel = component.channel(channelId);
		channel.removeOnSetNextWriteCallback(callback);
	}

	/**
	 * Copies a State-of-Charge Channel of the {@link BatteryManagementSystem} to the
	 * {@link EnergyStorageSystem}. Subclasses adjust the value on its way by overriding
	 * {@link #calculateStateOfCharge}.
	 */
	public static class StateOfChargeListener implements Consumer<ClockProvider> {

		protected final EnergyStorageSystem parent;
		protected final BatteryManagementSystem battery;
		protected final Channel<Integer> stateOfCharge;

		public StateOfChargeListener(EnergyStorageSystem parent, BatteryManagementSystem battery) {
			this(parent, battery, Battery.ChannelId.SOC);
		}

		public StateOfChargeListener(EnergyStorageSystem parent, BatteryManagementSystem battery,
		                             ChannelId stateOfChargeId) {
			this.parent = parent;
			this.battery = battery;
			this.stateOfCharge = battery.channel(stateOfChargeId);
		}

		/**
		 * Gets the Channel this listener derives the State-of-Charge from.
		 *
		 * @return the Channel
		 */
		public Channel<Integer> getStateOfChargeChannel() {
			return this.stateOfCharge;
		}

		@Override
		public void accept(ClockProvider clockProvider) {
			var socValue = this.stateOfCharge.getNextValue();

			this.parent._setSoc(socValue.isDefined()
					? this.calculateStateOfCharge(clockProvider, socValue.get())
					: null);
		}

		/**
		 * Derives the State-of-Charge to publish on the {@link EnergyStorageSystem} from the
		 * one reported by the {@link BatteryManagementSystem}.
		 *
		 * @param clockProvider the {@link ClockProvider}
		 * @param soc           the State-of-Charge reported by the Battery in %
		 * @return the State-of-Charge in %
		 */
		protected int calculateStateOfCharge(ClockProvider clockProvider, int soc) {
			return soc;
		}
	}

	/**
	 * Copies the Capacity Channel of the {@link BatteryManagementSystem} to the
	 * {@link EnergyStorageSystem}. Implementations that derive the Capacity from other
	 * Channels pass those as sources or override {@link #calculateCapacity}.
	 */
	public static class CapacityListener implements Consumer<ClockProvider> {

		protected final EnergyStorageSystem parent;
		protected final BatteryManagementSystem battery;
		protected final List<? extends Channel<Integer>> capacities;

		public CapacityListener(EnergyStorageSystem parent, BatteryManagementSystem battery) {
			this(parent, battery, List.of(battery.getCapacityChannel()));
		}

		public CapacityListener(EnergyStorageSystem parent, BatteryManagementSystem battery,
		                        List<? extends Channel<Integer>> capacities) {
			this.parent = parent;
			this.battery = battery;
			this.capacities = capacities;
		}

		/**
		 * Gets the Channels this listener derives the Capacity from.
		 *
		 * @return the Channels
		 */
		public List<? extends Channel<Integer>> getCapacityChannels() {
			return this.capacities;
		}

		@Override
		public void accept(ClockProvider clockProvider) {
			this.parent._setCapacity(this.calculateCapacity(clockProvider));
		}

		/**
		 * Derives the Capacity to publish on the {@link EnergyStorageSystem} from the source
		 * Channels. Undefined sources are considered zero, all of them undefined yields null.
		 *
		 * @param clockProvider the {@link ClockProvider}
		 * @return the Capacity in [Wh] or null if unknown
		 */
		protected Integer calculateCapacity(ClockProvider clockProvider) {
			Integer capacity = null;
			for (Channel<Integer> channel : this.capacities) {
				var value = channel.getNextValue();
				if (!value.isDefined()) {
					continue;
				}
				capacity = capacity == null ? value.get() : capacity + value.get();
			}
			return capacity;
		}
	}
}
