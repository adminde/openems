package io.openems.shared.timescaledb.schema;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Registry of the channels included in the continuous-aggregate layer
 * ({@code aggregate = true}): these are materialized into the
 * {@code data_<name>_*} aggregate tiers for fast frontend visualization. Every
 * other persisted channel is written to the raw hypertables only (still fully
 * queryable there, just without the accelerated aggregate tiers) — being absent
 * here only means "not accelerated", never "not stored".
 *
 * <p>
 * An entry is a {@link Namespace} (the Component-ID without its trailing
 * instance number, plus optional alias prefixes for the same nature) and a
 * Channel-ID, so {@code channel(meter, "ActivePower")} covers {@code meter0},
 * {@code meter1}, {@code pvMeter0}, … Singleton components such as
 * {@code _sum} or {@code _meta} are namespaces without instances.
 */
public final class AggregateChannels {

	/**
	 * Flattened {@code <prefix>/<channel-id>} form of {@link #CHANNELS}, with one
	 * entry per alias, for O(1) lookup on the write path.
	 */
	private static final Set<String> LOOKUP;

	private static final List<AggregateChannel> CHANNELS;

	static {
		final var meta = new Namespace("_meta");
		final var sum = new Namespace("_sum");
		final var io = new Namespace("io");
		final var ess = new Namespace("ess");
		final var evcs = new Namespace("evcs");
		final var evseChargePoint = new Namespace("evseChargePoint");
		final var pvInverter = new Namespace("pvInverter");
		final var pvCharger = new Namespace("pvCharger", "charger");
		final var meter = new Namespace("meter", "pvMeter");
		final var heat = new Namespace("heat");
		final var system = new Namespace("system");
		final var ctrlApiModbusTcp = new Namespace("ctrlApiModbusTcp");
		final var ctrlPeakShaving = new Namespace("ctrlPeakShaving");
		final var ctrlTimeslotPeakshaving = new Namespace("ctrlTimeslotPeakshaving");
		final var ctrlGridOptimizedCharge = new Namespace("ctrlGridOptimizedCharge");
		final var ctrlEssLimiter14a = new Namespace("ctrlEssLimiter14a");
		final var ctrlEssRippleControlReceiver = new Namespace("ctrlEssRippleControlReceiver");
		final var ctrlEssTimeOfUseTariff = new Namespace("ctrlEssTimeOfUseTariff");
		final var ctrlEssTimeOfUseTariffDischarge = new Namespace("ctrlEssTimeOfUseTariffDischarge");
		final var ctrlEmergencyCapacityReserve = new Namespace("ctrlEmergencyCapacityReserve");
		final var ctrlEvseSingle = new Namespace("ctrlEvseSingle");
		final var ctrlFixActivePower = new Namespace("ctrlFixActivePower");
		final var ctrlChannelThreshold = new Namespace("ctrlChannelThreshold");
		final var ctrlChpSoc = new Namespace("ctrlChpSoc");
		final var ctrlIoHeatPump = new Namespace("ctrlIoHeatPump");
		final var ctrlIoHeatingElement = new Namespace("ctrlIoHeatingElement");
		final var ctrlIoFixDigitalOutput = new Namespace("ctrlIoFixDigitalOutput");
		final var ctrlIoChannelSingleThreshold = new Namespace("ctrlIoChannelSingleThreshold",
				"ctrlChannelSingleThreshold");

		CHANNELS = List.of(//
				channel(meta, "_PropertyMaximumGridFeedInLimit"), //
				channel(meta, "GridBuySoftLimit"), //

				channel(sum, "EssSoc"), //
				channel(sum, "EssActivePower"), //
				channel(sum, "EssActivePowerL1"), //
				channel(sum, "EssActivePowerL2"), //
				channel(sum, "EssActivePowerL3"), //
				channel(sum, "EssReactivePower"), //
				channel(sum, "EssDischargePower"), //
				channel(sum, "EssCapacity"), //
				channel(sum, "GridActivePower"), //
				channel(sum, "GridActivePowerL1"), //
				channel(sum, "GridActivePowerL2"), //
				channel(sum, "GridActivePowerL3"), //
				channel(sum, "GridBuyPrice"), //
				channel(sum, "GridSellPrice"), //
				channel(sum, "ProductionActivePower"), //
				channel(sum, "ProductionAcActivePower"), //
				channel(sum, "ProductionAcActivePowerL1"), //
				channel(sum, "ProductionAcActivePowerL2"), //
				channel(sum, "ProductionAcActivePowerL3"), //
				channel(sum, "ProductionDcActualPower"), //
				channel(sum, "UnmanagedProductionActivePower"), //
				channel(sum, "ConsumptionActivePower"), //
				channel(sum, "ConsumptionActivePowerL1"), //
				channel(sum, "ConsumptionActivePowerL2"), //
				channel(sum, "ConsumptionActivePowerL3"), //
				channel(sum, "UnmanagedConsumptionActivePower"), //
				channel(sum, "GridMode"), //
				channel(sum, "GridModeOffGridTime"), //
				channel(sum, "GridModeOffGridGensetTime"), //
				channel(sum, "GridGensetActivePower"), //
				channel(sum, "GridGensetActivePowerL1"), //
				channel(sum, "GridGensetActivePowerL2"), //
				channel(sum, "GridGensetActivePowerL3"), //
				channel(sum, "EssActiveChargeEnergy"), //
				channel(sum, "EssActiveDischargeEnergy"), //
				channel(sum, "EssDcDischargeEnergy"), //
				channel(sum, "EssDcChargeEnergy"), //
				channel(sum, "GridBuyActiveEnergy"), //
				channel(sum, "GridSellActiveEnergy"), //
				channel(sum, "ProductionActiveEnergy"), //
				channel(sum, "ProductionAcActiveEnergy"), //
				channel(sum, "ProductionDcActiveEnergy"), //
				channel(sum, "ConsumptionActiveEnergy"), //
				channel(sum, "ProductionToConsumptionPower"), //
				channel(sum, "ProductionToConsumptionEnergy"), //
				channel(sum, "ProductionToGridPower"), //
				channel(sum, "ProductionToGridEnergy"), //
				channel(sum, "ProductionToEssPower"), //
				channel(sum, "ProductionToEssEnergy"), //
				channel(sum, "GridToConsumptionPower"), //
				channel(sum, "GridToConsumptionEnergy"), //
				channel(sum, "EssToConsumptionPower"), //
				channel(sum, "EssToConsumptionEnergy"), //
				channel(sum, "GridToEssPower"), //
				channel(sum, "GridToEssEnergy"), //
				channel(sum, "EssToGridEnergy"), //

				channel(io, "Relay1"), //
				channel(io, "Relay2"), //
				channel(io, "Relay3"), //
				channel(io, "Relay4"), //
				channel(io, "Relay5"), //
				channel(io, "Relay6"), //
				channel(io, "Relay7"), //
				channel(io, "Relay8"), //
				channel(io, "ActivePower"), //
				channel(io, "ActivePowerL1"), //
				channel(io, "ActivePowerL2"), //
				channel(io, "ActivePowerL3"), //
				channel(io, "Current"), //
				channel(io, "CurrentL1"), //
				channel(io, "CurrentL2"), //
				channel(io, "CurrentL3"), //
				channel(io, "Voltage"), //
				channel(io, "VoltageL1"), //
				channel(io, "VoltageL2"), //
				channel(io, "VoltageL3"), //
				channel(io, "ActiveProductionEnergy"), //
				channel(io, "ActiveProductionEnergyL1"), //
				channel(io, "ActiveProductionEnergyL2"), //
				channel(io, "ActiveProductionEnergyL3"), //
				channel(io, "ActiveConsumptionEnergy"), //
				channel(io, "ActiveConsumptionEnergyL1"), //
				channel(io, "ActiveConsumptionEnergyL2"), //
				channel(io, "ActiveConsumptionEnergyL3"), //

				channel(ess, "Soc"), //
				channel(ess, "ActivePower"), //
				channel(ess, "ReactivePower"), //
				channel(ess, "DcDischargePower"), //
				channel(ess, "ActiveChargeEnergy"), //
				channel(ess, "ActiveDischargeEnergy"), //
				channel(ess, "DcChargeEnergy"), //
				channel(ess, "DcDischargeEnergy"), //
				channel(ess, "CumulatedTimeOkState"), //
				channel(ess, "CumulatedTimeInfoState"), //
				channel(ess, "CumulatedTimeWarningState"), //
				channel(ess, "CumulatedTimeFaultState"), //

				channel(evcs, "ChargePower"), //
				channel(evcs, "ActivePower"), //
				channel(evcs, "ActivePowerL1"), //
				channel(evcs, "ActivePowerL2"), //
				channel(evcs, "ActivePowerL3"), //
				channel(evcs, "Current"), //
				channel(evcs, "CurrentL1"), //
				channel(evcs, "CurrentL2"), //
				channel(evcs, "CurrentL3"), //
				channel(evcs, "Voltage"), //
				channel(evcs, "VoltageL1"), //
				channel(evcs, "VoltageL2"), //
				channel(evcs, "VoltageL3"), //
				channel(evcs, "ActiveConsumptionEnergy"), //
				channel(evcs, "ActiveProductionEnergy"), //
				channel(evcs, "ActiveProductionEnergyL1"), //
				channel(evcs, "ActiveProductionEnergyL2"), //
				channel(evcs, "ActiveProductionEnergyL3"), //

				channel(evseChargePoint, "ActivePower"), //
				channel(evseChargePoint, "ActivePowerL1"), //
				channel(evseChargePoint, "ActivePowerL2"), //
				channel(evseChargePoint, "ActivePowerL3"), //
				channel(evseChargePoint, "Current"), //
				channel(evseChargePoint, "CurrentL1"), //
				channel(evseChargePoint, "CurrentL2"), //
				channel(evseChargePoint, "CurrentL3"), //
				channel(evseChargePoint, "Voltage"), //
				channel(evseChargePoint, "VoltageL1"), //
				channel(evseChargePoint, "VoltageL2"), //
				channel(evseChargePoint, "VoltageL3"), //
				channel(evseChargePoint, "ActiveProductionEnergy"), //
				channel(evseChargePoint, "ActiveProductionEnergyL1"), //
				channel(evseChargePoint, "ActiveProductionEnergyL2"), //
				channel(evseChargePoint, "ActiveProductionEnergyL3"), //

				channel(pvInverter, "ActivePower"), //
				channel(pvInverter, "ActivePowerL1"), //
				channel(pvInverter, "ActivePowerL2"), //
				channel(pvInverter, "ActivePowerL3"), //
				channel(pvInverter, "Current"), //
				channel(pvInverter, "CurrentL1"), //
				channel(pvInverter, "CurrentL2"), //
				channel(pvInverter, "CurrentL3"), //
				channel(pvInverter, "Voltage"), //
				channel(pvInverter, "VoltageL1"), //
				channel(pvInverter, "VoltageL2"), //
				channel(pvInverter, "VoltageL3"), //
				channel(pvInverter, "ActiveProductionEnergy"), //
				channel(pvInverter, "ActiveProductionEnergyL1"), //
				channel(pvInverter, "ActiveProductionEnergyL2"), //
				channel(pvInverter, "ActiveProductionEnergyL3"), //

				channel(pvCharger, "ActualPower"), //
				channel(pvCharger, "Current"), //
				channel(pvCharger, "Voltage"), //
				channel(pvCharger, "ActualEnergy"), //

				channel(meter, "ActivePower"), //
				channel(meter, "ActivePowerL1"), //
				channel(meter, "ActivePowerL2"), //
				channel(meter, "ActivePowerL3"), //
				channel(meter, "Current"), //
				channel(meter, "CurrentL1"), //
				channel(meter, "CurrentL2"), //
				channel(meter, "CurrentL3"), //
				channel(meter, "Voltage"), //
				channel(meter, "VoltageL1"), //
				channel(meter, "VoltageL2"), //
				channel(meter, "VoltageL3"), //
				channel(meter, "ActiveProductionEnergy"), //
				channel(meter, "ActiveProductionEnergyL1"), //
				channel(meter, "ActiveProductionEnergyL2"), //
				channel(meter, "ActiveProductionEnergyL3"), //
				channel(meter, "ActiveConsumptionEnergy"), //
				channel(meter, "ActiveConsumptionEnergyL1"), //
				channel(meter, "ActiveConsumptionEnergyL2"), //
				channel(meter, "ActiveConsumptionEnergyL3"), //

				channel(heat, "Temperature"), //
				channel(heat, "ActivePower"), //
				channel(heat, "ActiveProductionEnergy"), //
				channel(heat, "ActiveConsumptionEnergy"), // @Deprecated(use=ActiveProductionEnergy)

				channel(system, "CumulatedTimeCompressor1RunningState"), //
				channel(system, "CumulatedTimeCompressor2RunningState"), //
				channel(system, "CumulatedTimeCompressor1And2RunningState"), //
				channel(system, "CumulatedTimePumpRunningState"), //

				channel(ctrlApiModbusTcp, "Ess0SetActivePowerEquals"), //
				channel(ctrlApiModbusTcp, "Ess0SetActivePowerLessOrEquals"), //
				channel(ctrlApiModbusTcp, "Ess0SetActivePowerGreaterOrEquals"), //
				channel(ctrlApiModbusTcp, "Ess0SetReactivePowerEquals"), //
				channel(ctrlApiModbusTcp, "Ess0SetReactivePowerLessOrEquals"), //
				channel(ctrlApiModbusTcp, "Ess0SetReactivePowerGreaterOrEquals"), //
				channel(ctrlApiModbusTcp, "CumulatedActiveTime"), //
				channel(ctrlApiModbusTcp, "CumulatedInactiveTime"), //

				channel(ctrlPeakShaving, "_PropertyPeakShavingPower"), // symmetric and asymmetric
				channel(ctrlPeakShaving, "_PropertyRechargePower"), //
				channel(ctrlTimeslotPeakshaving, "StateMachine"), //
				channel(ctrlTimeslotPeakshaving, "_PropertyPeakShavingPower"), //
				channel(ctrlTimeslotPeakshaving, "_PropertyRechargePower"), //

				channel(ctrlGridOptimizedCharge, "DelayChargeMaximumChargeLimit"), //
				channel(ctrlGridOptimizedCharge, "SellToGridLimitMinimumChargeLimit"), //
				channel(ctrlGridOptimizedCharge, "_PropertyMaximumSellToGridPower"), //
				channel(ctrlGridOptimizedCharge, "AvoidLowChargingTime"), //
				channel(ctrlGridOptimizedCharge, "NoLimitationTime"), //
				channel(ctrlGridOptimizedCharge, "SellToGridLimitTime"), //
				channel(ctrlGridOptimizedCharge, "DelayChargeTime"), //

				channel(ctrlEssLimiter14a, "RestrictionMode"), //
				channel(ctrlEssLimiter14a, "CumulatedRestrictionTime"), //
				channel(ctrlEssRippleControlReceiver, "RestrictionMode"), //
				channel(ctrlEssRippleControlReceiver, "CumulatedRestrictionTime"), //

				channel(ctrlEssTimeOfUseTariff, "QuarterlyPrices"), //
				channel(ctrlEssTimeOfUseTariff, "StateMachine"), //
				channel(ctrlEssTimeOfUseTariff, "DelayedTime"), //
				channel(ctrlEssTimeOfUseTariff, "ChargedTime"), //
				channel(ctrlEssTimeOfUseTariffDischarge, "DelayedTime"), //

				channel(ctrlEmergencyCapacityReserve, "ActualReserveSoc"), //

				channel(ctrlEvseSingle, "ActualMode"), //
				channel(ctrlEvseSingle, "StateMachine"), //

				channel(ctrlFixActivePower, "CumulatedActiveTime"), //
				channel(ctrlChannelThreshold, "CumulatedActiveTime"), //
				channel(ctrlChpSoc, "CumulatedActiveTime"), //

				channel(ctrlIoHeatPump, "Status"), //
				channel(ctrlIoHeatPump, "RegularStateTime"), //
				channel(ctrlIoHeatPump, "RecommendationStateTime"), //
				channel(ctrlIoHeatPump, "ForceOnStateTime"), //
				channel(ctrlIoHeatPump, "LockStateTime"), //

				channel(ctrlIoHeatingElement, "Level"), //
				channel(ctrlIoHeatingElement, "Level1CumulatedTime"), //
				channel(ctrlIoHeatingElement, "Level2CumulatedTime"), //
				channel(ctrlIoHeatingElement, "Level3CumulatedTime"), //
				channel(ctrlIoFixDigitalOutput, "CumulatedActiveTime"), //
				channel(ctrlIoChannelSingleThreshold, "CumulatedActiveTime") //
		);

		LOOKUP = CHANNELS.stream() //
				.flatMap(entry -> Stream
						.concat(Stream.of(entry.namespace().componentId()), entry.namespace().aliases().stream()) //
						.map(prefix -> prefix + "/" + entry.channelId())) //
				.collect(Collectors.toUnmodifiableSet());
	}

	private record Namespace(//
			String componentId, //
			List<String> aliases // additional Component-ID prefixes for the same nature
	) {

		private Namespace(String componentId, String... aliases) {
			this(componentId, List.of(aliases));
		}
	}

	private record AggregateChannel(//
			Namespace namespace, //
			String channelId //
	) {
	}

	private static AggregateChannel channel(Namespace namespace, String channelId) {
		return new AggregateChannel(namespace, channelId);
	}

	private AggregateChannels() {
	}

	/**
	 * Whether the given channel is in the continuous-aggregate layer
	 * ({@code aggregate = true}). Both the Edge and Backend write paths use this
	 * single helper so they always agree on which channels are aggregated.
	 *
	 * @param componentId the Component-ID, e.g. "_sum", "meter0" or "pvMeter0"
	 * @param channelId   the Channel-ID, e.g. "EssSoc"
	 * @return {@code true} if the channel is in the include-list
	 */
	public static boolean isAggregate(String componentId, String channelId) {
		return LOOKUP.contains(stripInstanceNumber(componentId) + "/" + channelId);
	}

	private static String stripInstanceNumber(String componentId) {
		int end = componentId.length();
		while (end > 0 && Character.isDigit(componentId.charAt(end - 1))) {
			end--;
		}
		return componentId.substring(0, end);
	}
}
