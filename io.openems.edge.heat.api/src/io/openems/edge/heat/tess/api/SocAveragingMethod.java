package io.openems.edge.heat.tess.api;

/**
 * Method used to aggregate the State-of-Charge of multiple Thermal Energy
 * Storage Systems into one average value.
 */
public enum SocAveragingMethod {

	/**
	 * Capacity-weighted arithmetic mean. An empty storage lowers the average
	 * proportionally to its capacity share.
	 */
	ARITHMETIC,

	/**
	 * Capacity-weighted geometric mean. A single storage with a State-of-Charge of
	 * zero forces the aggregate to zero.
	 */
	GEOMETRIC;
}
