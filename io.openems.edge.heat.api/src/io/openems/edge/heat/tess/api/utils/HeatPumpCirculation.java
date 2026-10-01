package io.openems.edge.heat.tess.api.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

import io.openems.edge.heat.api.SymmetricHeating;
import io.openems.edge.heat.pump.api.ManagedHeatPump;
import io.openems.edge.heat.tess.api.ThermalEss;

/**
 * Tracks the supply and return temperatures of a heat pump circulating through
 * a storage.
 *
 * <p>
 * The temperatures describe the storage only while the heat pump runs and
 * serves this storage. After a start, the circulation needs a settling time
 * before the return line carries storage water instead of the water that stood
 * in the pipes. Outside of a settled circulation, the last valid temperatures
 * are held.
 */
public class HeatPumpCirculation {

	private final Duration settlingTime;

	private Instant circulatingSince = null;
	private boolean settled = false;
	private Integer supplyTemperature = null;
	private Integer returnTemperature = null;

	public HeatPumpCirculation(Duration settlingTime) {
		this.settlingTime = settlingTime;
	}

	/**
	 * Whether the heat output of a Heating device bound to a storage currently
	 * goes into that storage. A heat pump delivers to the storage it reports as
	 * currently served, or to every bound storage if it reports none; any other
	 * Heating device always delivers to the storage it is bound to.
	 *
	 * @param heating the Heating device
	 * @param storage the {@link ThermalEss}
	 * @return true if the heat output goes into the storage
	 */
	public static boolean deliversTo(SymmetricHeating heating, ThermalEss storage) {
		if (heating instanceof ManagedHeatPump heatPump) {
			var served = heatPump.getThermalStorage();
			return served == null || storage.equals(served);
		}
		return true;
	}

	/**
	 * Updates the held temperatures from the Heating devices bound to a storage.
	 *
	 * @param storage  the {@link ThermalEss}
	 * @param heatings the Heating devices bound to the storage
	 * @param now      the current time
	 */
	public void update(ThermalEss storage, Collection<? extends SymmetricHeating> heatings, Instant now) {
		var heatPump = heatings.stream() //
				.filter(ManagedHeatPump.class::isInstance) //
				.map(ManagedHeatPump.class::cast) //
				.filter(hp -> hp.isStarted() && deliversTo(hp, storage)) //
				.findFirst() //
				.orElse(null);
		if (heatPump == null) {
			this.circulatingSince = null;
			this.settled = false;
			return;
		}
		if (this.circulatingSince == null) {
			this.circulatingSince = now;
		}
		this.settled = Duration.between(this.circulatingSince, now).compareTo(this.settlingTime) >= 0;
		if (!this.settled) {
			return;
		}
		var supply = heatPump.getSupplyTemperature().get();
		var ret = heatPump.getReturnTemperature().get();
		if (supply != null && ret != null) {
			this.supplyTemperature = supply;
			this.returnTemperature = ret;
		}
	}

	/**
	 * Whether a heat pump currently circulates through the storage and its
	 * circulation has settled, i.e. the temperatures are live instead of held.
	 *
	 * @return true if the circulation is settled
	 */
	public boolean isSettled() {
		return this.settled;
	}

	/**
	 * Gets the supply temperature of the last settled circulation.
	 *
	 * @return the temperature in [deci-°C], or null if there was none yet
	 */
	public Integer getSupplyTemperature() {
		return this.supplyTemperature;
	}

	/**
	 * Gets the return temperature of the last settled circulation.
	 *
	 * @return the temperature in [deci-°C], or null if there was none yet
	 */
	public Integer getReturnTemperature() {
		return this.returnTemperature;
	}
}
