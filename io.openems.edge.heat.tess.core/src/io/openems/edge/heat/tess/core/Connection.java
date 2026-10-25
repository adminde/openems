package io.openems.edge.heat.tess.core;

/**
 * How a heat source is connected to a storage.
 */
public enum Connection {

	/**
	 * The water of the heat source flows through the storage: it enters at the
	 * supply connection and leaves at the return connection, e.g. a buffer tank
	 * charged directly by a heat pump. Supply and return temperatures are storage
	 * water temperatures.
	 */
	DIRECT,

	/**
	 * The water of the heat source flows through an internal heat exchanger coil
	 * between the supply and the return connection, e.g. a domestic hot water
	 * tank. Supply and return temperatures are those of the coil, not of the
	 * storage water.
	 */
	HEAT_EXCHANGER;
}
