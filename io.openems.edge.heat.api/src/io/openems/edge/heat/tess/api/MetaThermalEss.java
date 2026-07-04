package io.openems.edge.heat.tess.api;

import io.openems.edge.common.sum.SumOptions;

/**
 * A MetaThermalEss is a wrapper for physical thermal energy storage systems.
 * It is not a physical ThermalEss itself. This is used to distinguish e.g. a
 * ThermalEssCluster from an actual ThermalEss — most importantly to avoid
 * counting the cluster and its members twice in Sum.
 */
public interface MetaThermalEss extends ThermalEss, SumOptions {

	/**
	 * Get the Component-IDs of thermal energy storage systems that are handled by
	 * this MetaThermalEss.
	 *
	 * @return an array of Component-IDs
	 */
	public String[] getTessIds();

}
