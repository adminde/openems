package io.openems.shared.timescaledb.schema;

import java.util.UUID;

import io.openems.shared.timescaledb.Type;

/**
 * Represents the resolved database metadata for a specific channel.
 *
 * @param channelId The Edge-Channel ID (Primary key in the channel table).
 *                  UUID v7 — time-ordered, globally unique across backends, safe
 *                  to copy chunk-for-chunk during migration.
 * @param type      The value {@link Type} (INTEGER, FLOAT, or STRING)
 * @param rollup    true = Fast Lane, false = Slow Lane.
 * @param unit      The channel's unit symbol (e.g. "W", "Wh"), or {@code null}
 *                  if not yet known. Cached so a later write carrying a real
 *                  unit can trigger a one-off re-resolve to backfill a
 *                  previously-NULL.
 */
public record ChannelInfo(
		UUID channelId,
		Type type,
		boolean rollup,
		String unit
) {}
