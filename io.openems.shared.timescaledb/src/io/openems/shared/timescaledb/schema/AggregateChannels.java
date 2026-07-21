package io.openems.shared.timescaledb.schema;

import java.util.Set;

/**
 * Registry of the channels included in the continuous-aggregate layer
 * ({@code aggregate = true}): these are materialized into the
 * {@code data_<name>_*} aggregate tiers for fast frontend visualization. Every
 * other persisted channel is written to the raw hypertables only (still fully
 * queryable there, just without the accelerated aggregate tiers) — being absent
 * here only means "not accelerated", never "not stored".
 *
 * <p>
 * This is an include-list, deliberately compiled into the bundle (same pattern
 * as {@code AllowedChannels} in the official OpenEMS
 * {@code io.openems.backend.timedata.aggregatedinflux}): aggregate membership
 * is a fleet-wide schema decision, so all Edges and the Backend must agree —
 * per-site configuration would let deployments drift.
 *
 * <p>
 * GENERATED from {@code OpenEMS-Channel-Priorities.xlsx}, sheet "Nature
 * Channels" (2026-07-09). Change the sheet first, then regenerate/edit this
 * list to match. If a channel is queried at fine resolution but missing here,
 * {@code ReadHandler} logs a warning naming it — that is the signal to add it.
 */
public final class AggregateChannels {

	/**
	 * Exact channel addresses of singleton components ({@code _sum}).
	 */
	private static final Set<String> EXACT = Set.of(//
			"_sum/EssSoc", //
			"_sum/EssActivePower", //
			"_sum/EssReactivePower", //
			"_sum/EssDischargePower", //
			"_sum/EssCapacity", //
			"_sum/GridActivePower", //
			"_sum/GridActivePowerL1", //
			"_sum/GridActivePowerL2", //
			"_sum/GridActivePowerL3", //
			"_sum/GridBuyPrice", //
			"_sum/GridSellPrice", //
			"_sum/ProductionActivePower", //
			"_sum/ProductionAcActivePowerL1", //
			"_sum/ProductionAcActivePowerL2", //
			"_sum/ProductionAcActivePowerL3", //
			"_sum/ProductionDcActualPower", //
			"_sum/UnmanagedProductionActivePower", //
			"_sum/ConsumptionActivePower", //
			"_sum/UnmanagedConsumptionActivePower", //
			"_sum/GridMode", //
			"_sum/GridModeOffGridTime", //
			"_sum/GridModeOffGridGensetTime", //
			"_sum/GridGensetActivePower", //
			"_sum/GridGensetActivePowerL1", //
			"_sum/GridGensetActivePowerL2", //
			"_sum/GridGensetActivePowerL3", //
			"_sum/EssActiveChargeEnergy", //
			"_sum/EssActiveDischargeEnergy", //
			"_sum/EssDcDischargeEnergy", //
			"_sum/EssDcChargeEnergy", //
			"_sum/GridBuyActiveEnergy", //
			"_sum/GridSellActiveEnergy", //
			"_sum/ProductionActiveEnergy", //
			"_sum/ProductionAcActiveEnergy", //
			"_sum/ProductionDcActiveEnergy", //
			"_sum/ConsumptionActiveEnergy", //
			"_sum/ProductionToConsumptionPower", //
			"_sum/ProductionToConsumptionEnergy", //
			"_sum/ProductionToGridPower", //
			"_sum/ProductionToGridEnergy", //
			"_sum/ProductionToEssPower", //
			"_sum/ProductionToEssEnergy", //
			"_sum/GridToConsumptionPower", //
			"_sum/GridToConsumptionEnergy", //
			"_sum/EssToConsumptionPower", //
			"_sum/EssToConsumptionEnergy", //
			"_sum/GridToEssPower", //
			"_sum/GridToEssEnergy", //
			"_sum/EssToGridEnergy");

	/**
	 * Channels matched for every instance of a component family: the key is
	 * {@code <component-id-without-trailing-digits>/<channel-id>}, so
	 * {@code meter/ActiveProductionEnergy} matches {@code meter0}, {@code meter1},
	 * ... Relies on the OpenEMS component-naming convention (family prefix +
	 * instance number).
	 */
	private static final Set<String> BY_FAMILY = Set.of(//
			"meter/ActiveProductionEnergy", //
			"meter/ActiveProductionEnergyL1", //
			"meter/ActiveProductionEnergyL2", //
			"meter/ActiveProductionEnergyL3", //
			"meter/ActiveConsumptionEnergy", //
			"meter/ActiveConsumptionEnergyL1", //
			"meter/ActiveConsumptionEnergyL2", //
			"meter/ActiveConsumptionEnergyL3");

	private AggregateChannels() {
	}

	/**
	 * Whether the given channel is in the continuous-aggregate layer
	 * ({@code aggregate = true}). Both the Edge and Backend write paths use this
	 * single helper so they always agree on which channels are aggregated.
	 *
	 * @param componentId the Component-ID, e.g. "_sum" or "meter0"
	 * @param channelId   the Channel-ID, e.g. "EssSoc"
	 * @return {@code true} if the channel is in the include-list
	 */
	public static boolean isAggregate(String componentId, String channelId) {
		if (EXACT.contains(componentId + "/" + channelId)) {
			return true;
		}
		return BY_FAMILY.contains(stripInstanceNumber(componentId) + "/" + channelId);
	}

	private static String stripInstanceNumber(String componentId) {
		int end = componentId.length();
		while (end > 0 && Character.isDigit(componentId.charAt(end - 1))) {
			end--;
		}
		return componentId.substring(0, end);
	}
}
