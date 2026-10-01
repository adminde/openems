package io.openems.edge.controller.tess.delaycharge;

public enum Mode {
	/**
	 * The target time is derived from the predicted end of PV production.
	 */
	AUTOMATIC,
	/**
	 * The target time is configured manually.
	 */
	MANUAL;
}
