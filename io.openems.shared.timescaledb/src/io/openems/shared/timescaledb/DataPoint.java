package io.openems.shared.timescaledb;

public record DataPoint(
		long timestamp,
		String edgeName,
		String componentAlias,
		String componentType,
		String channelName,
		String dataType,   // "INTEGER", "FLOAT", or "STRING"
		boolean core,      // true = Fast Lane (VERY_HIGH), false = Slow Lane
		String unit,       // e.g. "W", "Wh", "%", "V" — may be null
		Object value
) {}
