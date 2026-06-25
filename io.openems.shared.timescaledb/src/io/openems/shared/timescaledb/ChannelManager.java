package io.openems.shared.timescaledb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet; 
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.openems.common.types.ChannelAddress;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the in-memory cache of Channel metadata to avoid costly Database JOINs.
 */
public class ChannelManager {

	// Key: "edgeName/componentName/channelName"
	private final Map<String, ChannelInfo> channelCache = new ConcurrentHashMap<>();

	/**
	 * Looks up the {@link ChannelInfo} (ec_id, type, core flag) for one
	 * channel address on a given edge.
	 *
	 * <p>
	 * Tries the in-memory cache first (populated during writes). On miss, joins the
	 * four dimension tables to find the channel. Returns {@code null} if the
	 * channel has never been written.
	 *
	 * @param con      An open JDBC connection
	 * @param edgeName The edge identifier, e.g. "edge0"
	 * @param addr     OpenEMS channel address (componentAlias/channelName)
	 * @return The resolved channel info or {@code null} if unknown
	 * @throws SQLException on database error
	 */
	public ChannelInfo lookupChannel(Connection con, String edgeName, ChannelAddress addr) throws SQLException {
		var key = edgeName + "/" + addr.getComponentId() + "/" + addr.getChannelId();
		var cached = this.channelCache.get(key);
		if (cached != null) {
			return cached;
		}
		var sql = """
				SELECT c.id, cd.type, c.core, cd.unit
				FROM channel c
				JOIN component   co ON co.id = c.component_id
				JOIN edge        e  ON e.id  = co.edge_id
				JOIN channel_def cd ON cd.id = c.channel_def_id
				WHERE e.name = ? AND co.name = ? AND cd.name = ?
				""";
		try (var pst = con.prepareStatement(sql)) {
			pst.setString(1, edgeName);
			pst.setString(2, addr.getComponentId());
			pst.setString(3, addr.getChannelId());
			try (var rs = pst.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				var info = new ChannelInfo(rs.getObject(1, UUID.class), rs.getString(2), rs.getBoolean(3),
						rs.getString(4));
				this.channelCache.put(key, info);
				return info;
			}
		}
	}

	/**
	 * Resolves a channel, creating it in the database if it doesn't exist yet.
	 * Calls the PostgreSQL stored function `get_or_create_channel_id`.
	 *
	 * @param con An open JDBC connection
	 * @param p   The DataPoint containing edge and channel information
	 * @return The resolved {@link ChannelInfo}
	 * @throws SQLException on database error
	 */
	public ChannelInfo resolveChannel(Connection con, DataPoint p) throws SQLException {
		var key = channelKey(p);
		var cached = this.channelCache.get(key);
		if (cached != null && !needsReresolve(cached, p)) {
			return cached;
		}
		var sql = "SELECT * FROM get_or_create_channel_id(?,?,?,?,?,?,?)";
		try (var pst = con.prepareStatement(sql)) {
			pst.setString(1, p.edgeName());
			pst.setString(2, p.componentAlias());
			pst.setString(3, p.componentType());
			pst.setString(4, p.channelName());
			pst.setString(5, p.dataType());
			pst.setBoolean(6, p.core());
			pst.setString(7, p.unit());
			try (var rs = pst.executeQuery()) {
				rs.next();
				var info = new ChannelInfo(rs.getObject(1, UUID.class), rs.getString(2), rs.getBoolean(3), p.unit());
				this.channelCache.put(key, info);
				return info;
			}
		}
	}

	/**
	 * Cache-only resolution: returns the {@link ChannelInfo} for a point when it
	 * is already cached and fully up to date (no Fast Lane promotion or unit
	 * backfill pending). Returns {@code null} on a cache miss or when a re-resolve
	 * is required, signalling the caller to route the point through
	 * {@link #resolveChannel} (which performs a Database round-trip).
	 *
	 * <p>
	 * Lets a batch writer skip opening a Database connection entirely when every
	 * channel is already warm.
	 *
	 * @param p The DataPoint to resolve
	 * @return The cached {@link ChannelInfo}, or {@code null} if a DB resolve is needed
	 */
	public ChannelInfo peekResolved(DataPoint p) {
		var cached = this.channelCache.get(channelKey(p));
		return cached != null && !needsReresolve(cached, p) ? cached : null;
	}

	/**
	 * The in-memory cache key for a channel: {@code "edge/component/channel"}.
	 * Exposed so a caller resolving a batch can key its own lookup map identically.
	 *
	 * @param p The DataPoint
	 * @return the cache key
	 */
	public static String channelKey(DataPoint p) {
		return p.edgeName() + "/" + p.componentAlias() + "/" + p.channelName();
	}

	/**
	 * Whether a cached entry must be re-resolved for this point — either a Fast
	 * Lane promotion (core false-&gt;true, "highest wins") or a unit backfill (a
	 * real unit differs from the cached one, e.g. the first writes landed before
	 * the EdgeConfig was known). A {@code null} incoming unit never forces a
	 * re-resolve, so there is no thrashing once a unit is known.
	 *
	 * @param cached the cached info
	 * @param p      the incoming point
	 * @return true if {@link #resolveChannel} must hit the Database
	 */
	private static boolean needsReresolve(ChannelInfo cached, DataPoint p) {
		var needsCorePromotion = p.core() && !cached.core();
		var needsUnitBackfill = p.unit() != null && !p.unit().equals(cached.unit());
		return needsCorePromotion || needsUnitBackfill;
	}
}
