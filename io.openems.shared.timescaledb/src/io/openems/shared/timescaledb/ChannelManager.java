package io.openems.shared.timescaledb;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.openems.common.types.ChannelAddress;

/**
 * Manages the in-memory cache of Channel metadata to avoid costly Database
 * JOINs.
 *
 * <p>
 * This base class holds the deployment-agnostic caching logic. The actual SQL
 * differs between deployments and is provided by subclasses:
 * <ul>
 * <li>{@link BackendChannelManager} resolves through the {@code edge} dimension
 * table, so one Backend database can hold many edges.
 * <li>{@link EdgeChannelManager} omits the {@code edge} table entirely — the
 * local Edge database stores data for exactly one edge, so the edge name is
 * ignored.
 * </ul>
 */
public abstract class ChannelManager {

	// Key: "edgeName/componentName/channelName"
	protected final Map<String, ChannelDefinition> channelCache = new ConcurrentHashMap<>();

	/**
	 * Looks up the {@link ChannelDefinition} (channel id, type, core flag, unit)
	 * for one channel address on a given edge.
	 *
	 * <p>
	 * Tries the in-memory cache first (populated during writes). On miss, delegates
	 * to {@link #doLookupChannel}. Returns {@code null} if the channel has never
	 * been written.
	 *
	 * @param con      An open JDBC connection
	 * @param edgeName The edge identifier, e.g. "edge0" (ignored by single-edge
	 *                 deployments)
	 * @param addr     OpenEMS channel address (componentId/channelName)
	 * @return The resolved channel info or {@code null} if unknown
	 * @throws SQLException on database error
	 */
	public ChannelDefinition lookupChannel(Connection con, String edgeName, ChannelAddress addr) throws SQLException {
		var key = edgeName + "/" + addr.getComponentId() + "/" + addr.getChannelId();
		var cached = this.channelCache.get(key);
		if (cached != null) {
			return cached;
		}
		var info = this.doLookupChannel(con, edgeName, addr);
		if (info != null) {
			this.channelCache.put(key, info);
		}
		return info;
	}

	/**
	 * Deployment-specific read-path lookup. No caching — the base class handles
	 * that.
	 *
	 * @param con      An open JDBC connection
	 * @param edgeName The edge identifier (ignored by single-edge deployments)
	 * @param addr     OpenEMS channel address
	 * @return the resolved {@link ChannelDefinition} or {@code null} if unknown
	 * @throws SQLException on database error
	 */
	protected abstract ChannelDefinition doLookupChannel(Connection con, String edgeName, ChannelAddress addr)
			throws SQLException;

	/**
	 * Resolves (creating if necessary) the {@link ChannelDefinition} for a write.
	 *
	 * <p>
	 * Tries the cache first; on a miss, or when a re-resolve is needed (core
	 * promotion or unit backfill), delegates to {@link #doResolveChannel}.
	 *
	 * @param con An open JDBC connection
	 * @param p   The DataPoint being written
	 * @return the resolved {@link ChannelDefinition}
	 * @throws SQLException on database error
	 */
	public ChannelDefinition resolveChannel(Connection con, DataPoint p) throws SQLException {
		var key = channelKey(p);
		var cached = this.channelCache.get(key);
		if (cached != null && !needsReresolve(cached, p)) {
			return cached;
		}
		var info = this.doResolveChannel(con, p);
		this.channelCache.put(key, info);
		return info;
	}

	/**
	 * Deployment-specific write-path resolve (get-or-create). No caching — the base
	 * class handles that.
	 *
	 * @param con An open JDBC connection
	 * @param p   The DataPoint being written
	 * @return the resolved {@link ChannelDefinition}
	 * @throws SQLException on database error
	 */
	protected abstract ChannelDefinition doResolveChannel(Connection con, DataPoint p) throws SQLException;

	/**
	 * Cache-only resolution: returns the {@link ChannelDefinition} for a point when
	 * it is already cached and fully up to date, letting a batch writer skip
	 * opening a Database connection entirely when every channel is already warm.
	 *
	 * @param p The DataPoint to resolve
	 * @return The cached {@link ChannelDefinition}, or {@code null} if a DB resolve
	 *         is needed
	 */
	public ChannelDefinition peekResolved(DataPoint p) {
		var cached = this.channelCache.get(channelKey(p));
		return cached != null && !needsReresolve(cached, p) ? cached : null;
	}

	static String channelKey(DataPoint p) {
		return p.edgeName() + "/" + p.componentAlias() + "/" + p.channelName();
	}

	private static boolean needsReresolve(ChannelDefinition cached, DataPoint p) {
		var needsCorePromotion = p.core() && !cached.core();
		var needsUnitBackfill = p.unit() != null && !p.unit().equals(cached.unit());
		return needsCorePromotion || needsUnitBackfill;
	}
}
