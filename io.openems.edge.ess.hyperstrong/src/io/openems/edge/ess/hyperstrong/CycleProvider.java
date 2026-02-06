package io.openems.edge.ess.hyperstrong;

public interface CycleProvider {

	/**
	 * Gets the duration of one global OpenEMS Cycle in [ms].
	 *
	 * @return the duration in milliseconds
	 */
	public int getCycleTime();
}
