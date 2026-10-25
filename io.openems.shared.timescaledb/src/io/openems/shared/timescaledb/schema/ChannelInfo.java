package io.openems.shared.timescaledb.schema;

import java.util.UUID;

import io.openems.shared.timescaledb.Type;

/**
 * The resolved database metadata for one channel of one component: everything
 * the write and read paths need once a channel is known, joined from the
 * {@code channel} instance ({@code channelId}, {@code aggregate}) and its
 * {@code channel_def} ({@code type}).
 *
 * @param channelId The Edge-Channel ID (Primary key in the channel table).
 *                  UUID v7 — time-ordered, globally unique across backends, safe
 *                  to copy chunk-for-chunk during migration.
 * @param type      The value {@link Type} (INTEGER, FLOAT, or STRING)
 * @param aggregate whether this channel is included in the continuous-aggregate
 *                  layer (see {@link AggregateChannels})
 */
public record ChannelInfo(
		UUID channelId,
		Type type,
		boolean aggregate
) {}
