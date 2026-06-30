package io.openems.shared.timescaledb;

import java.util.UUID;

/**
 * Represents the resolved database metadata for a specific channel.
 *
 * @param channelId The Edge-Channel ID (Primary key in the channel table).
 *                  UUID v7 — time-ordered, globally unique across backends, safe
 *                  to copy chunk-for-chunk during migration.
 * @param dataType  The SQL data type (INTEGER, FLOAT, or STRING)
 * @param core      true = Fast Lane (VERY_HIGH channel), false = Slow Lane
 * @param unit      The channel's unit symbol (e.g. "W", "Wh"), or {@code null}
 *                  if not yet known. Cached so a later write carrying a real
 *                  unit can trigger a one-off re-resolve to backfill a
 *                  previously-NULL {@code channel_def.unit} without a restart.
 */
public record ChannelDefinition(UUID channelId, String dataType, boolean core, String unit) {}
