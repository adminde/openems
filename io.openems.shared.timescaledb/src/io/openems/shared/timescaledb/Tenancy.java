package io.openems.shared.timescaledb;

/**
 * Identifies how many edges one TimescaleDB database holds.
 */
public enum Tenancy {
	/**
	 * Exactly one edge per database (e.g. locally on an edge device); no
	 * {@code edge} dimension table.
	 */
	SINGLE,
	/**
	 * Many edges in one database (e.g. the central backend database); resolves
	 * through the {@code edge} dimension table.
	 */
	MULTI;
}
