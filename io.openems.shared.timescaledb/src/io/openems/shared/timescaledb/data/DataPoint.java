package io.openems.shared.timescaledb.data;

import io.openems.shared.timescaledb.Type;

public record DataPoint(
		long timestamp,
		String edgeName,
		String componentName,
		String componentType,
		String channelName,
		Type type,
		boolean aggregate, // whether the channel is in the continuous-aggregate layer
		Object value       // Long, Double or String, matching the type
) {}
