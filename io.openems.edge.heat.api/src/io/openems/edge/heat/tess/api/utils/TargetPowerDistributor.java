package io.openems.edge.heat.tess.api.utils;

import java.util.Collection;
import java.util.List;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.function.ThrowingConsumer;
import io.openems.edge.heat.api.ManagedSymmetricHeating;
import io.openems.edge.heat.api.SymmetricHeating;
import io.openems.edge.heat.tess.api.ManagedThermalEss;
import io.openems.edge.heat.tess.api.ThermalEss;

/**
 * Resolves and distributes thermal power targets of a
 * {@link ManagedThermalEss}.
 *
 * <p>
 * The effective thermal target of a cycle is
 * {@code min(LessOrEquals, max(Equals, GreaterOrEquals))} over the pending
 * write values; missing writes are neutral. The target is then distributed to
 * the receivers (Heating devices or member storages) in priority order: each
 * receiver is allotted up to its maximum, on/off devices receive their
 * allotment only if it reaches their minimum, and receivers that already carry
 * a foreign pending write are skipped ("first write wins") — this arbitrates
 * Heating devices shared between multiple storages.
 *
 * <p>
 * All values are thermal Watts; the conversion to the electrical target of a
 * Heating device happens inside its {@link Receiver} via the device's
 * ThermalEfficiency.
 */
public final class TargetPowerDistributor {

	private TargetPowerDistributor() {
	}

	/**
	 * A prioritized target receiver with its thermal limits.
	 *
	 * @param maxPower        the maximum thermal power intake in [W], or null if
	 *                        unknown
	 * @param minPower        the minimum dispatchable thermal power in [W], or
	 *                        null if the receiver has no lower step
	 * @param hasPendingWrite whether the receiver already carries a foreign
	 *                        pending write in this cycle
	 * @param applyTarget     applies the allotted thermal power in [W] to the
	 *                        receiver
	 */
	public record Receiver(Integer maxPower, Integer minPower, boolean hasPendingWrite,
			ThrowingConsumer<Integer, OpenemsNamedException> applyTarget) {
	}

	/**
	 * Creates a {@link Receiver} for a {@link ManagedSymmetricHeating}. The
	 * thermal budget is the electrical limit times the device's
	 * ThermalEfficiency; the allotted thermal power is converted back to an
	 * electrical target and applied via
	 * {@link ManagedSymmetricHeating#applyPower(ThermalEss, int)} on behalf of
	 * the requesting storage.
	 *
	 * @param heating the Heating device
	 * @param storage the requesting storage
	 * @return the Receiver
	 */
	public static Receiver ofHeating(ManagedSymmetricHeating heating, ThermalEss storage) {
		final var efficiency = efficiencyOf(heating);
		return new Receiver(//
				multiply(heating.getMaxTargetActivePower().get(), efficiency), //
				multiply(heating.getMinTargetActivePower().get(), efficiency), //
				heating.getTargetActivePowerChannel().getNextWriteValue().isPresent(), //
				thermalPower -> heating.applyPower(storage, Math.round(thermalPower / efficiency)));
	}

	/**
	 * Creates a {@link Receiver} for a member {@link ManagedThermalEss}, e.g. of
	 * a storage cluster. The allotted thermal power is passed on as
	 * {@code TargetThermalPowerEquals}.
	 *
	 * @param tess the member storage
	 * @return the Receiver
	 */
	public static Receiver ofThermalEss(ManagedThermalEss tess) {
		return new Receiver(//
				tess.getMaxTargetThermalPower().get(), //
				tess.getMinTargetThermalPower().get(), //
				tess.getTargetThermalPowerEqualsChannel().getNextWriteValue().isPresent(), //
				tess::setTargetThermalPowerEqualsWithoutFilter);
	}

	/**
	 * Resolves the effective thermal target from the pending write values of the
	 * given storage and resets them.
	 *
	 * @param tess the {@link ManagedThermalEss}
	 * @return the effective thermal target in [W], or null if neither an Equals
	 *         nor a GreaterOrEquals write is pending
	 */
	public static Integer resolveEffectiveTarget(ManagedThermalEss tess) {
		var equals = tess.getTargetThermalPowerEqualsChannel().getNextWriteValueAndReset();
		var greaterOrEquals = tess.getTargetThermalPowerGreaterOrEqualsChannel().getNextWriteValueAndReset();
		var lessOrEquals = tess.getTargetThermalPowerLessOrEqualsChannel().getNextWriteValueAndReset();
		if (equals.isEmpty() && greaterOrEquals.isEmpty()) {
			return null;
		}
		var target = Math.max(//
				equals.orElse(Integer.MIN_VALUE), //
				greaterOrEquals.orElse(Integer.MIN_VALUE));
		if (lessOrEquals.isPresent()) {
			target = Math.min(target, lessOrEquals.get());
		}
		return Math.max(0, target);
	}

	/**
	 * Distributes a thermal target to the receivers in priority order. Receivers
	 * with a foreign pending write are skipped; all others receive an explicit
	 * target, possibly zero.
	 *
	 * @param target    the effective thermal target in [W]
	 * @param receivers the receivers in priority order
	 * @return the remaining thermal power in [W] that could not be allotted
	 * @throws OpenemsNamedException on write error
	 */
	public static int distribute(int target, List<Receiver> receivers) throws OpenemsNamedException {
		var remaining = Math.max(0, target);
		for (var receiver : receivers) {
			if (receiver.hasPendingWrite()) {
				continue;
			}
			var max = receiver.maxPower() != null ? Math.max(0, receiver.maxPower()) : 0;
			var allocation = Math.min(remaining, max);
			if (receiver.minPower() != null && allocation < receiver.minPower()) {
				allocation = 0;
			}
			receiver.applyTarget().accept(allocation);
			remaining -= allocation;
		}
		return remaining;
	}

	/**
	 * Gets the ThermalEfficiency of a Heating device as a ratio. The channel holds
	 * a percentage; missing or non-positive values resolve to 100&nbsp;% (ratio
	 * 1.0).
	 *
	 * @param heating the Heating device
	 * @return the efficiency as thermal output per electrical input
	 */
	public static float efficiencyOf(SymmetricHeating heating) {
		var efficiency = heating.getThermalEfficiency().orElse(100);
		return (efficiency > 0 ? efficiency : 100) / 100F;
	}

	/**
	 * Sums the maximum thermal power intake of the given Heating devices
	 * (electrical maximum times ThermalEfficiency).
	 *
	 * @param heatings the Heating devices
	 * @return the sum in [W], or null if no device reports a maximum
	 */
	public static Integer sumMaxTargetThermalPower(Collection<? extends ManagedSymmetricHeating> heatings) {
		Integer sum = null;
		for (var heating : heatings) {
			var max = multiply(heating.getMaxTargetActivePower().get(), efficiencyOf(heating));
			if (max == null) {
				continue;
			}
			sum = sum == null ? max : sum + max;
		}
		return sum;
	}

	/**
	 * Gets the smallest dispatchable thermal power step of the given Heating
	 * devices (electrical minimum times ThermalEfficiency).
	 *
	 * @param heatings the Heating devices
	 * @return the minimum in [W], or null if no device reports a minimum
	 */
	public static Integer minTargetThermalPower(Collection<? extends ManagedSymmetricHeating> heatings) {
		Integer min = null;
		for (var heating : heatings) {
			var value = multiply(heating.getMinTargetActivePower().get(), efficiencyOf(heating));
			if (value == null) {
				continue;
			}
			min = min == null ? value : Math.min(min, value);
		}
		return min;
	}

	private static Integer multiply(Integer power, float efficiency) {
		if (power == null) {
			return null;
		}
		return Math.round(power * efficiency);
	}
}
