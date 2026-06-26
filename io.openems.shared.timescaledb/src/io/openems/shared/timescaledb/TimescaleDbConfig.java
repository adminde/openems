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
    int writeWorkers
) {}
