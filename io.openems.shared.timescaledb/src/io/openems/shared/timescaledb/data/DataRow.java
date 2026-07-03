package io.openems.shared.timescaledb.data;

import java.util.UUID;

/**
 * A {@link DataPoint} whose channel has been resolved to its database identity
 * by the {@link DataPointRouter}, ready to be written to a hypertable without
 * any further lookup.
 *
 * @param timestamp epoch milliseconds
 * @param channelId the resolved {@code channel.id} (UUID v7)
 * @param rollup    true = Fast Lane, false = Slow Lane
 * @param value     Long, Double or String, matching the target hypertable
 */
public record DataRow(
		long timestamp,
		UUID channelId,
		boolean rollup,
		Object value
) {}
