package io.openems.edge.oros.ess;

import static io.openems.edge.common.channel.ChannelUtils.setValue;

import java.util.function.Consumer;

import io.openems.edge.battery.api.Battery;
import io.openems.edge.battery.api.BatteryErrorAcknowledge;
import io.openems.edge.batteryinverter.api.BatteryInverterErrorAcknowledge;
import io.openems.edge.batteryinverter.api.HybridManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.ManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.common.channel.AbstractChannelListenerManager;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.ess.api.EssErrorAcknowledge;
import io.openems.edge.ess.api.HybridEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.oros.SymmetricComponent;
import io.openems.edge.oros.bms.BatteryManagementSystem;
import io.openems.edge.oros.ess.protection.DeepDischargeCurrentLimiter;
import io.openems.edge.oros.ess.protection.OverChargeCurrentLimiter;
import io.openems.edge.oros.ess.protection.PowerLimiter;
import io.openems.edge.oros.ess.protection.StateOfChargeLimiter;
import io.openems.edge.oros.pcs.PowerConversionSystem;

public class SystemChannelManager extends AbstractChannelListenerManager {

	private final EnergyStorageSystem parent;
	private final PowerLimiter powerLimiter;
	private final OverChargeCurrentLimiter overChargeCurrentLimiter;
	private final DeepDischargeCurrentLimiter deepDischargeCurrentLimiter;

	private StateOfChargeLimiter stateOfChargeLimiter;

	public SystemChannelManager(EnergyStorageSystem parent) {
		super();
		this.stateOfChargeLimiter = new StateOfChargeLimiter(parent);
		this.overChargeCurrentLimiter = new OverChargeCurrentLimiter(parent);
		this.deepDischargeCurrentLimiter = new DeepDischargeCurrentLimiter(parent);
		this.powerLimiter = new PowerLimiter(parent, this.overChargeCurrentLimiter, this.deepDischargeCurrentLimiter);
		this.parent = parent;
	}

	public SystemChannelManager setStateOfChargeLimiter(StateOfChargeLimiter limiter) {
		this.stateOfChargeLimiter = limiter;
		return this;
	}

	public StateOfChargeLimiter getStateOfChargeLimiter() {
		return this.stateOfChargeLimiter;
	}

	public DeepDischargeCurrentLimiter getDeepDischargeCurrentLimiter() {
		return this.deepDischargeCurrentLimiter;
	}

	public OverChargeCurrentLimiter getOverChargeCurrentLimiter() {
		return this.overChargeCurrentLimiter;
	}

	public PowerLimiter getPowerLimiter() {
		return this.powerLimiter;
	}

	/**
	 * Called on Component activate().
	 *
	 * @param battery		the {@link BatteryManagementSystem}
	 * @param inverter		the {@link PowerConversionSystem}
	 */
	public void activate(ClockProvider clock, BatteryManagementSystem battery, PowerConversionSystem inverter) {
		this.addBatteryListener(clock, battery);
		this.addInverterListener(inverter);
		this.addEssListener(clock);
	}

	private void addEssListener(ClockProvider clock) {
		this.addEssSocListener(clock, this.parent.getBatteryManagementSystem());
		this.addOnChangeListener(this.parent, StartStoppable.ChannelId.START_STOP, (ignored0, ignored1) ->
				this.powerLimiter.accept(clock));
	}

	private void addEssSocListener(ClockProvider clock, BatteryManagementSystem battery) {
		final Consumer<Value<Integer>> trigger = ignored -> getStateOfChargeLimiter().accept(clock);
		battery.getSocChannel().onSetNextValue(trigger);
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
				SymmetricComponent.ChannelId.ACTIVE_POWER_L1,
				SymmetricComponent.ChannelId.ACTIVE_POWER_L1);
		this.<Long>addOnSetNextMirrorListener(inverter,
				SymmetricComponent.ChannelId.ACTIVE_POWER_L2,
				SymmetricComponent.ChannelId.ACTIVE_POWER_L2);
		this.<Long>addOnSetNextMirrorListener(inverter,
				SymmetricComponent.ChannelId.ACTIVE_POWER_L3,
				SymmetricComponent.ChannelId.ACTIVE_POWER_L3);

		this.<Long>addOnSetNextMirrorListener(inverter,
				SymmetricBatteryInverter.ChannelId.REACTIVE_POWER,
				SymmetricEss.ChannelId.REACTIVE_POWER);
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
				SymmetricComponent.ChannelId.VOLTAGE_L1,
				SymmetricComponent.ChannelId.VOLTAGE_L1);
		this.<Long>addOnSetNextMirrorListener(inverter,
				SymmetricComponent.ChannelId.VOLTAGE_L2,
				SymmetricComponent.ChannelId.VOLTAGE_L2);
		this.<Long>addOnSetNextMirrorListener(inverter,
				SymmetricComponent.ChannelId.VOLTAGE_L3,
				SymmetricComponent.ChannelId.VOLTAGE_L3);

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
				SymmetricComponent.ChannelId.FREQUENCY,
				SymmetricComponent.ChannelId.FREQUENCY);

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

	private void addBatteryListener(ClockProvider clock, BatteryManagementSystem battery) {
		if (battery instanceof BatteryErrorAcknowledge) {
			this.<Long>addOnSetNextMirrorListener(battery,
					BatteryErrorAcknowledge.ChannelId.TIMEOUT_START_BATTERY,
					EssErrorAcknowledge.ChannelId.TIMEOUT_START_BATTERY);
			this.<Long>addOnSetNextMirrorListener(battery,
					BatteryErrorAcknowledge.ChannelId.TIMEOUT_STOP_BATTERY,
					EssErrorAcknowledge.ChannelId.TIMEOUT_STOP_BATTERY);
		}
		this.addOnSetNextValueListener(battery, Battery.ChannelId.CHARGE_MAX_VOLTAGE,
				ignored -> this.overChargeCurrentLimiter.accept(clock));
		this.addOnSetNextValueListener(battery, Battery.ChannelId.DISCHARGE_MIN_VOLTAGE,
				ignored -> this.deepDischargeCurrentLimiter.accept(clock));

		this.addOnSetNextValueListener(battery, Battery.ChannelId.CHARGE_MAX_CURRENT,
				ignored -> this.powerLimiter.accept(clock));
		this.addOnSetNextValueListener(battery, Battery.ChannelId.DISCHARGE_MAX_CURRENT,
				ignored -> this.powerLimiter.accept(clock));
		this.addOnSetNextValueListener(battery, Battery.ChannelId.VOLTAGE,
				ignored -> this.powerLimiter.accept(clock));

		this.addOnSetNextMirrorListener(battery,
				Battery.ChannelId.CAPACITY,
				SymmetricEss.ChannelId.CAPACITY);
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
	}

	/**
	 * Adds a Copy-Listener. It listens on setNextValue() and copies the value to the target channel.
	 *
	 * @param <T>             the Channel-Type
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

}
