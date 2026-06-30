package io.openems.shared.timescaledb;

public record TimescaleDbConfig(
    String host,
    int port,
    String database,
    String username,
    String password,
    int poolSize,
    int rawRetentionDays,
    int rawCompressionDays,
    boolean createMinutelyAggregate,
    int writeWorkers,
    Deployment deployment
) {
	/**
	 * Identifies which TimescaleDB deployment a TimescaleDbHandler serves.
	 */
	public enum Deployment {
		/** Single local edge; no {@code edge} dimension table. */
		EDGE,
		/** Many edges in one database; resolves through the {@code edge} table. */
		BACKEND;
	}
}
