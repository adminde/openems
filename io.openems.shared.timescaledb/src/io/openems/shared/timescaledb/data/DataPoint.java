package io.openems.shared.timescaledb.data;

import io.openems.shared.timescaledb.Type;

public record DataPoint(
		long timestamp,
		String edgeName,
		String componentName,
		String componentType,
		String channelName,
		Type type,
		boolean rollup,    // true = Fast Lane false = Slow Lane
		String unit,       // e.g. "W", "Wh", "%", "V" — may be null
		Object value       // Long, Double or String, matching the type
) {}
