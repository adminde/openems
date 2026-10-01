package io.openems.edge.heat.tess.api.utils;

import io.openems.edge.heat.tess.api.ThermalEss;

/**
 * Splits the bidirectional net thermal power of a {@link ThermalEss} into its
 * charge and discharge components and writes the {@code ThermalChargePower} and
 * {@code ThermalDischargePower} channels.
 *
 * <p>
 * The discharge component isolates the uncontrollable heat extraction
 * (including standing losses) from the storage's own charging activity, so
 * predictors can learn the heat demand without feedback from controlled
 * charging.
 *
 * <p>
 * Usage: create an instance bound to the {@link ThermalEss}, then call
 * {@link #update(Integer, Integer)} each cycle.
 */
public class CalculateChargeDischargePower {

	private final ThermalEss component;

	public CalculateChargeDischargePower(ThermalEss component) {
		this.component = component;
	}

	/**
	 * Calculates both power components and writes them to the channels.
	 *
	 * <p>
	 * When the total heat input of the Heating devices is known, the discharge
	 * component follows from {@code discharge = charge - net}. Without it, the
	 * components are derived from the sign of the net thermal power alone.
	 *
	 * @param thermalPower  the net thermal power in [W]; positive for charging, or
	 *                      null
	 * @param generatorPower the total heat input of the Heating devices in [W], or
	 *                      null if unknown
	 */
	public void update(Integer thermalPower, Integer generatorPower) {
		final Integer charge;
		final Integer discharge;
		if (generatorPower != null) {
			charge = Math.max(0, generatorPower);
			discharge = thermalPower != null //
					? Math.max(0, charge - thermalPower) //
					: null;
		} else if (thermalPower != null) {
			charge = Math.max(0, thermalPower);
			discharge = Math.max(0, -thermalPower);
		} else {
			charge = null;
			discharge = null;
		}
		this.component._setThermalChargePower(charge);
		this.component._setThermalDischargePower(discharge);
	}
}
