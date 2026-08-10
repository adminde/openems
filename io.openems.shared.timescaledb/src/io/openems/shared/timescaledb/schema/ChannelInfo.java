package io.openems.shared.timescaledb.schema;

import java.util.UUID;

import io.openems.shared.timescaledb.Type;

/**
 * The resolved database metadata for one channel of one component: everything
 * the write and read paths need once a channel is known, joined from the
 * {@code channel} instance ({@code channelId}, {@code aggregate}) and its
 * {@code channel_def} ({@code type}, {@code unit}).
 *
 * @param channelId The Edge-Channel ID (Primary key in the channel table).
 *                  UUID v7 — time-ordered, globally unique across backends, safe
 *                  to copy chunk-for-chunk during migration.
 * @param type      The value {@link Type} (INTEGER, FLOAT, or STRING)
 * @param aggregate whether this channel is included in the continuous-aggregate
 *                  layer (see {@link AggregateChannels})
 * @param unit      The channel's unit symbol (e.g. "W", "Wh"), or {@code null}
 *                  if not yet known. Cached so a later write carrying a real
 *                  unit can trigger a one-off re-resolve to backfill a
 *                  previously-NULL.
 */
public record ChannelInfo(
		UUID channelId,
		Type type,
		boolean aggregate,
		String unit
) {}
