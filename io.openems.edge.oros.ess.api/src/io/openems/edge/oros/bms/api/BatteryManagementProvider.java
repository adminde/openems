package io.openems.edge.oros.bms.api;

import io.openems.edge.oros.ess.api.EnergyStorageSystem;

/**
 * Provides access to the dedicated {@link BatteryManagementSystem} of an
 * integrated {@link EnergyStorageSystem}.
 */
public interface BatteryManagementProvider {

	/**
	 * Gets the {@link BatteryManagementSystem} of this component.
	 *
	 * @return the {@link BatteryManagementSystem}
	 */
	public BatteryManagementSystem getBatteryManagementSystem();

}
