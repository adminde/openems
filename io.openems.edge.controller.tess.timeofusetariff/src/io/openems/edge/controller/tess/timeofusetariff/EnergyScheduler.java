package io.openems.edge.controller.tess.timeofusetariff;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static io.openems.common.jsonrpc.serialization.JsonSerializerUtil.jsonObjectSerializer;
import static io.openems.common.utils.JsonUtils.buildJsonObject;
import static io.openems.edge.controller.tess.timeofusetariff.StateMachine.CHARGE_GRID;
import static io.openems.edge.controller.tess.timeofusetariff.StateMachine.NONE;
import static io.openems.edge.energy.api.EnergyUtils.findValleyIndexes;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;

import io.openems.common.jsonrpc.serialization.JsonSerializer;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.energy.api.handler.DifferentModes.InitialPopulation;
import io.openems.edge.energy.api.handler.DifferentModes.Modes;
import io.openems.edge.energy.api.handler.DifferentModes.Modes.Mode;
import io.openems.edge.energy.api.handler.EnergyScheduleHandler;
import io.openems.edge.energy.api.handler.EshWithDifferentModes;
import io.openems.edge.energy.api.simulation.GlobalOptimizationContext;
import io.openems.edge.energy.api.simulation.GlobalOptimizationContext.Period;
import io.openems.edge.energy.api.simulation.periods.PeriodData.Price;
import io.openems.edge.heat.tess.api.ManagedThermalEss;
import io.openems.edge.predictor.api.prediction.Prediction;

public class EnergyScheduler {

	public static record EshConfig(double efficiency, int thermalDemand) {

		/**
		 * Returns a {@link JsonSerializer} for a {@link EshConfig}.
		 *
		 * @return the created {@link JsonSerializer}
		 */
		public static JsonSerializer<EshConfig> serializer() {
			return jsonObjectSerializer(EshConfig.class, json -> {
				return new EshConfig(//
						json.getDouble("efficiency"), //
						json.getInt("thermalDemand"));
			}, obj -> {
				return buildJsonObject() //
						.addProperty("efficiency", obj.efficiency) //
						.addProperty("thermalDemand", obj.thermalDemand) //
						.build();
			});
		}
	}

	/**
	 * All energies are thermal [Wh]; the conversion to grid energy happens via
	 * {@link #efficiency()}. Energy zero corresponds to the storage's
	 * MinTemperature, {@link #totalEnergy()} to its hardware MaxTemperature.
	 *
	 * @param totalEnergy     the storage capacity in [Wh]
	 * @param targetEnergy    the energy at TargetTemperature in [Wh]; the
	 *                        switch-off point of autonomous reheating
	 * @param initialEnergy   the current stored energy in [Wh]
	 * @param maxChargePower  the maximum thermal charge power in [W]
	 * @param efficiency      thermal output per electrical input
	 * @param dischargePowers the predicted thermal discharge power in [W] per
	 *                        period index
	 */
	public static record OptimizationContext(//
			int totalEnergy, //
			int targetEnergy, //
			int initialEnergy, //
			int maxChargePower, //
			double efficiency, //
			ImmutableMap<Integer, Integer> dischargePowers) {

		protected static OptimizationContext from(GlobalOptimizationContext goc, ManagedThermalEss tess,
				Prediction prediction, EshConfig config) {
			final var capacity = tess.getCapacity().orElse(0);
			final var soc = tess.getSoc().orElse(0);
			final var initialEnergy = round(capacity * soc / 100F);
			final var targetEnergy = calculateTargetEnergy(tess, capacity);
			final var maxChargePower = tess.getMaxTargetThermalPower().orElse(0);
			final var efficiency = config != null && config.efficiency() > 0 //
					? config.efficiency() //
					: 1.;
			final Integer thermalDemand = config != null ? config.thermalDemand() : 0;
			final var dischargePowers = goc.periods().stream() //
					.collect(toImmutableMap(Period::index, //
							p -> prediction.getAtOrElse(p.time(), thermalDemand)));
			return new OptimizationContext(capacity, targetEnergy, initialEnergy, maxChargePower, efficiency,
					dischargePowers);
		}

		private static int calculateTargetEnergy(ManagedThermalEss tess, int capacity) {
			var minTemperature = tess.getMinTemperature().get();
			var targetTemperature = tess.getTargetTemperature().get();
			var maxTemperature = tess.getMaxTemperature().get();
			if (minTemperature == null || targetTemperature == null || maxTemperature == null
					|| maxTemperature <= minTemperature) {
				return capacity;
			}
			var ratio = (targetTemperature - minTemperature) / (float) (maxTemperature - minTemperature);
			return max(0, min(capacity, round(capacity * ratio)));
		}
	}

	/**
	 * Tracks the simulated thermal energy state of the storage across the periods
	 * of one schedule.
	 */
	public static class ScheduleContext {

		private final int targetEnergy;

		private int energy;
		private boolean autonomousHeating = false;

		public ScheduleContext(int initialEnergy, int targetEnergy) {
			this.energy = max(0, initialEnergy);
			this.targetEnergy = targetEnergy;
		}

		protected void discharge(int dischargeEnergy) {
			this.energy = max(0, this.energy - dischargeEnergy);
		}

		/**
		 * Adds charged energy. The autonomous reheating latch switches off the moment
		 * the target energy is reached, exactly like the real two-point control does
		 * at TargetTemperature.
		 *
		 * @param chargeEnergy the charged thermal energy in [Wh]
		 * @param totalEnergy  the storage capacity in [Wh]
		 */
		protected void charge(int chargeEnergy, int totalEnergy) {
			this.energy = min(totalEnergy, this.energy + chargeEnergy);
			if (this.energy >= this.targetEnergy) {
				this.autonomousHeating = false;
			}
		}

		/**
		 * Evaluates the autonomous two-point reheating latch: switches on when the
		 * energy reaches zero (MinTemperature); switched off by {@link #charge(int,
		 * int)} when the target energy (TargetTemperature) is reached.
		 *
		 * @return whether the Heating device is autonomously reheating
		 */
		protected boolean isAutonomousHeating() {
			if (this.energy <= 0) {
				this.autonomousHeating = true;
			} else if (this.energy >= this.targetEnergy) {
				this.autonomousHeating = false;
			}
			return this.autonomousHeating;
		}

		public int getEnergy() {
			return this.energy;
		}
	}

	/**
	 * Builds the {@link EnergyScheduleHandler}.
	 *
	 * <p>
	 * This is public so that it can be used by the EnergyScheduler integration
	 * test.
	 *
	 * @param parent             the parent {@link OpenemsComponent}
	 * @param configSupplier     supplier for {@link EshConfig}; null when the
	 *                           Controller is disabled
	 * @param tessSupplier       supplier for the {@link ManagedThermalEss}
	 * @param predictionSupplier supplier for the thermal discharge power
	 *                           {@link Prediction}
	 * @return a {@link EnergyScheduleHandler}
	 */
	public static EshWithDifferentModes<StateMachine, OptimizationContext, ScheduleContext> buildEnergyScheduleHandler(
			OpenemsComponent parent, Supplier<EshConfig> configSupplier, Supplier<ManagedThermalEss> tessSupplier,
			Supplier<Prediction> predictionSupplier) {
		return EnergyScheduleHandler.WithDifferentModes.<StateMachine, OptimizationContext, ScheduleContext>create(parent) //
				.setSerializer(EshConfig.serializer(), configSupplier) //

				.setModes(() -> {
					var config = configSupplier.get();
					return Modes.of(Arrays.stream(StateMachine.values()) //
							.map(m -> new Mode<StateMachine>(//
									m, //
									m == NONE || config != null, //
									preferenceRankFor(m))) //
							.collect(toImmutableList()));
				})

				.setInitialPopulationsProvider((goc, coc, modes) -> {
					// Prepare initial population with CHARGE_GRID on the cheapest grid-buy
					// price per valley
					final var result = ImmutableSortedSet.<InitialPopulation<StateMachine>>naturalOrder();
					final var gridBuyPrices = goc.periods().stream() //
							.map(p -> p.data().gridBuyPrice()) //
							.flatMap(Optional::stream) //
							.mapToDouble(Price::actual) //
							.toArray();
					Arrays.stream(findValleyIndexes(gridBuyPrices)) //
							.mapToObj(i -> goc.periods().stream() //
									.map(p -> p.index() == i //
											? CHARGE_GRID //
											: NONE) //
									.toArray(StateMachine[]::new)) //
							.map(InitialPopulation::new) //
							.forEach(result::add);
					return result.build();
				})

				.setOptimizationContext(goc -> OptimizationContext.from(goc, //
						tessSupplier.get(), predictionSupplier.get(), configSupplier.get()))

				.setScheduleContext(coc -> new ScheduleContext(coc.initialEnergy(), coc.targetEnergy()))

				.setSimulator((id, period, gsc, coc, csc, ef, mode, fitness, isFinalRun) -> {
					if (mode == null) {
						mode = NONE;
					}

					var dischargePower = coc.dischargePowers().getOrDefault(period.index(), 0);
					csc.discharge(period.duration().convertPowerToEnergy(dischargePower));

					var maxChargeEnergy = period.duration().convertPowerToEnergy(coc.maxChargePower());
					var chargeEnergy = 0;
					if (mode == CHARGE_GRID) {
						if (csc.getEnergy() >= coc.totalEnergy()) {
							// Storage is already full; a CHARGE_GRID period is useless
							fitness.addHardConstraintViolation();
						}
						chargeEnergy = min(maxChargeEnergy, coc.totalEnergy() - csc.getEnergy());
					}

					// Autonomous reheating runs regardless of the schedule and pays the price of
					// the current period — this is what makes pre-charging in cheap periods
					// profitable.
					if (csc.isAutonomousHeating()) {
						chargeEnergy = max(chargeEnergy, min(maxChargeEnergy, coc.targetEnergy() - csc.getEnergy()));
					}

					if (chargeEnergy > 0) {
						var electricalEnergy = (int) round(chargeEnergy / coc.efficiency());
						var actualElectricalEnergy = ef.addManagedConsumption(id, electricalEnergy);
						csc.charge((int) round(actualElectricalEnergy * coc.efficiency()), coc.totalEnergy());
					} else if (isFinalRun && mode == CHARGE_GRID) {
						// Post-process: demote useless CHARGE_GRID periods
						mode = NONE;
					}
					return mode;
				})

				.build();
	}

	private static Integer preferenceRankFor(StateMachine m) {
		return switch (m) {
		case CHARGE_GRID -> 1;
		case NONE -> 2;
		};
	}
}
