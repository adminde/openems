package io.openems.edge.oros.pcs.api;

import io.openems.edge.oros.ess.api.EnergyStorageSystem;

/**
 * Provides access to the dedicated {@link PowerConversionSystem} of an
 * integrated {@link EnergyStorageSystem}.
 */
public interface PowerConversionProvider {

	/**
	 * Gets the {@link PowerConversionSystem} of this component.
	 *
	 * @return the {@link PowerConversionSystem}
	 */
	public PowerConversionSystem getPowerConversionSystem();

}
