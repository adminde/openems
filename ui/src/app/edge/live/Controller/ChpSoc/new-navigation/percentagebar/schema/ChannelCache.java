package io.openems.shared.timescaledb.schema;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.openems.shared.timescaledb.data.DataPoint;

/**
 * In-memory index of the channels this process has already resolved, so the
 * write path can skip its database round-trip and the Backend can skip reading
 * the {@code EdgeConfig} for a channel it already knows.
 *
 * <p>
 * <b>Shape.</b> Two levels: the outer map is keyed by Edge, the inner one by
 * {@code componentId/channelId}. Single-tenant deployments have exactly one
 * outer bucket. The split exists for {@link #invalidateEdge(String)}: dropping
 * one Edge's channels is a single {@code remove} instead of a scan over every
 * cached channel of every Edge, and it costs one string concatenation less per
 * lookup than a flat {@code edge/component/channel} key would.
 *
 * <p>
 * <b>Staleness.</b> A cached entry is only usable while it still satisfies the
 * incoming point, see {@link #isCurrent(ChannelInfo, DataPoint)}. Metadata
 * that changes in the Edge's configuration rather than in the data is not
 * visible here at all. The Backend drops the affected Edge with
 * {@link #invalidateEdge(String)} when its configuration changes.
 */
public class ChannelCache {

	/** Outer key for the single-tenant bucket; never collides with an Edge name. */
	private static final String SINGLE_TENANT = "";

	private final Map<String, Map<String, ChannelInfo>> cache = new ConcurrentHashMap<>();

	private final Tenancy tenancy;

	/**
	 * Constructor.
	 *
	 * @param tenancy whether this database holds one or many Edges
	 */
	public ChannelCache(Tenancy tenancy) {
		this.tenancy = tenancy;
	}

	/**
	 * Returns the cached {@link ChannelInfo}, without checking whether it still
	 * satisfies an incoming point.
	 *
	 * @param edgeName    the Edge identifier; ignored in single-tenant mode
	 * @param componentId the Component-ID
	 * @param channelId   the Channel-ID
	 * @return the cached entry, or {@code null}
	 */
	public ChannelInfo get(String edgeName, String componentId, String channelId) {
		var edge = this.cache.get(this.edgeKey(edgeName));
		return edge == null ? null : edge.get(channelKey(componentId, channelId));
	}

	/**
	 * Returns the cached {@link ChannelInfo} for a point, but only when it is
	 * still current — so a caller that gets a non-null answer can write without
	 * touching the database at all.
	 *
	 * @param data the {@link DataPoint} to resolve
	 * @return the cached entry, or {@code null} if it is missing or stale
	 */
	public ChannelInfo peek(DataPoint data) {
		var cached = this.get(data.edgeName(), data.componentName(), data.channelName());
		return cached != null && isCurrent(cached, data) ? cached : null;
	}

	/**
	 * Stores a resolved channel.
	 *
	 * @param edgeName    the Edge identifier; ignored in single-tenant mode
	 * @param componentId the Component-ID
	 * @param channelId   the Channel-ID
	 * @param info        the resolved metadata
	 */
	public void put(String edgeName, String componentId, String channelId, ChannelInfo info) {
		this.edgeBucket(edgeName).put(channelKey(componentId, channelId), info);
	}

	/**
	 * Stores a resolved channel unless one is already cached. Used by the bulk
	 * warm-up, which must not overwrite entries a concurrent write just resolved.
	 *
	 * @param edgeName    the Edge identifier; ignored in single-tenant mode
	 * @param componentId the Component-ID
	 * @param channelId   the Channel-ID
	 * @param info        the resolved metadata
	 */
	public void putIfAbsent(String edgeName, String componentId, String channelId, ChannelInfo info) {
		this.edgeBucket(edgeName).putIfAbsent(channelKey(componentId, channelId), info);
	}

	/**
	 * Drops every cached channel of one Edge, so the next write re-resolves them
	 * against the database. Called when an Edge's configuration changes, which is
	 * the only way a channel's unit or value type can change without the data
	 * itself showing it.
	 *
	 * @param edgeName the Edge identifier
	 */
	public void invalidateEdge(String edgeName) {
		this.cache.remove(this.edgeKey(edgeName));
	}

	/**
	 * Number of cached channels across all Edges.
	 *
	 * @return the entry count
	 */
	public int size() {
		return this.cache.values().stream().mapToInt(Map::size).sum();
	}

	/**
	 * Whether a cached entry still satisfies an incoming point. It does not when
	 * the point is flagged for the aggregate layer but the stored channel is not,
	 * or when the point carries a unit the stored channel does not have yet —
	 * both are one-off promotions that have to reach the database.
	 *
	 * @param cached the cached entry
	 * @param data   the incoming point
	 * @return true if the cached entry can be used as-is
	 */
	public static boolean isCurrent(ChannelInfo cached, DataPoint data) {
		var needsAggregatePromotion = data.aggregate() && !cached.aggregate();
		var needsUnitBackfill = data.unit() != null && !data.unit().equals(cached.unit());
		return !needsAggregatePromotion && !needsUnitBackfill;
	}

	private Map<String, ChannelInfo> edgeBucket(String edgeName) {
		return this.cache.computeIfAbsent(this.edgeKey(edgeName), key -> new ConcurrentHashMap<>());
	}

	private String edgeKey(String edgeName) {
		return this.tenancy == Tenancy.SINGLE ? SINGLE_TENANT : edgeName;
	}

	private static String channelKey(String componentId, String channelId) {
		return componentId + "/" + channelId;
	}
}
