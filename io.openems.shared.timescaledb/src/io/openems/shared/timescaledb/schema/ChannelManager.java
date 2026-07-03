package io.openems.shared.timescaledb.schema;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.openems.common.types.ChannelAddress;
import io.openems.shared.timescaledb.Type;
import io.openems.shared.timescaledb.data.DataPoint;

/**
 * Resolves OpenEMS channel addresses to database channel ids and caches the
 * results in memory to avoid costly Database JOINs.
 *
 * <p>
 * The only difference between the tenancy variants is the SQL: multi-tenant
 * resolves through the {@code edge} dimension table (so one database can hold
 * many edges) and binds the edge name as first parameter; single-tenant has no
 * {@code edge} table and ignores the edge name. This is pure data variance, so
 * it is handled here with a {@link Tenancy} field instead of subclasses.
 */
public class ChannelManager {

	// Key: "[edgeName/]componentName/channelName"
	private final Map<String, ChannelInfo> cache = new ConcurrentHashMap<>();

	private final Tenancy tenancy;
	private final String warmupQuery;
	private final String lookupQuery;
	private final String resolveQuery;

	public ChannelManager(Tenancy tenancy) {
		this.tenancy = tenancy;

		StringBuilder warmupQuery = new StringBuilder().append("SELECT ");
		if (tenancy == Tenancy.MULTI) {
			warmupQuery.append("e.name AS edgeName, ");
		}
		warmupQuery.append("co.name AS componentName, cd.name AS channelName, ch.id, cd.type, ch.rollup, cd.unit ")
				.append("FROM channel ch ")
				.append("JOIN component co ON co.id = ch.component_id ");
		if (tenancy == Tenancy.MULTI) {
			// `co` must be joined before the edge join may reference it
			warmupQuery.append("JOIN edge e ON e.id = co.edge_id ");
		}
		warmupQuery.append("JOIN channel_def cd ON cd.id = ch.channel_def_id");

		StringBuilder lookupQuery = new StringBuilder()
				.append("SELECT ch.id, cd.type, ch.rollup, cd.unit ")
				.append("FROM channel ch ")
				.append("JOIN component co ON co.id = ch.component_id ");
		if (tenancy == Tenancy.MULTI) {
			lookupQuery.append("JOIN edge e ON e.id = co.edge_id ");
		}
		lookupQuery.append("JOIN channel_def cd ON cd.id = ch.channel_def_id ");
		if (tenancy == Tenancy.MULTI) {
			lookupQuery.append("WHERE e.name = ? AND co.name = ? AND cd.name = ?");
		} else {
			lookupQuery.append("WHERE co.name = ? AND cd.name = ?");
		}

		String resolveQuery = "SELECT * FROM get_or_create_channel_id" +
				IntStream.range(0, tenancy == Tenancy.MULTI ? 7 : 6)
						.mapToObj(i -> "?")
						.collect(Collectors.joining(",", "(", ")"));

		this.warmupQuery = warmupQuery.toString();
		this.lookupQuery = lookupQuery.toString();
		this.resolveQuery = resolveQuery;
	}

	/**
	 * Preloads the whole channel cache with one bulk query, so the first writes
	 * and reads after a restart skip their per-channel database round-trips.
	 *
	 * <p>
	 * Cache keys are built exactly like {@link #lookupChannel} and
	 * {@link #encodeChannelKey} build them; in single-tenant mode the edge name is
	 * {@code null} there, so it is {@code null} here too.
	 *
	 * @param connection An open JDBC connection
	 * @return the number of preloaded channels
	 * @throws SQLException on database error
	 */
	public int warmUpCache(Connection connection) throws SQLException {
		var count = 0;
		try (var statement = connection.prepareStatement(this.warmupQuery);
				var result = statement.executeQuery()) {
			while (result.next()) {
				String edgeName = this.tenancy == Tenancy.MULTI ? result.getString("edgeName") : null;
				this.cache.putIfAbsent(
						this.encodeChannelKey(edgeName,
								result.getString("componentName"),
								result.getString("channelName")
						),
						new ChannelInfo(
								result.getObject("id", UUID.class),
								Type.valueOf(result.getString("type")),
								result.getBoolean("rollup"),
								result.getString("unit")
						)
				);
				count++;
			}
		}
		return count;
	}

	/**
	 * Looks up the {@link ChannelInfo} (channel id, type, rollup flag, unit)
	 * for one channel address on a given edge.
	 *
	 * <p>
	 * Tries the in-memory cache first (populated during writes). On miss, queries
	 * the database. Returns {@code null} if the channel has never been written.
	 *
	 * @param connection An open JDBC connection
	 * @param edgeName   The edge identifier, e.g. "edge0" (ignored in single-tenant mode)
	 * @param channel    OpenEMS channel address (componentId/channelName)
	 * @return The resolved channel info or {@code null} if unknown
	 * @throws SQLException on database error
	 */
	public ChannelInfo lookupChannel(Connection connection, String edgeName, ChannelAddress channel)
			throws SQLException {

		// TODO: Validate tenancy mode and raise exception if edgeName is null
		var key = this.encodeChannelKey(edgeName, channel.getComponentId(), channel.getChannelId());
		var cached = this.cache.get(key);
		if (cached != null) {
			return cached;
		}
		var info = this.doLookupChannel(connection, edgeName, channel);
		if (info != null) {
			this.cache.put(key, info);
		}
		return info;
	}

	private ChannelInfo doLookupChannel(Connection connection, String edgeName, ChannelAddress channel)
			throws SQLException {

		try (var statement = connection.prepareStatement(this.lookupQuery)) {
			int i = 1;
			if (this.tenancy == Tenancy.MULTI) {
				statement.setString(i++, edgeName);
			}
			statement.setString(i++, channel.getComponentId());
			statement.setString(i, channel.getChannelId());
			try (var result = statement.executeQuery()) {
				if (!result.next()) {
					return null;
				}
				return new ChannelInfo(result.getObject(1, UUID.class), Type.valueOf(result.getString(2)),
						result.getBoolean(3), result.getString(4));
			}
		}
	}

	/**
	 * Resolves (creating if necessary) the {@link ChannelInfo} for a write.
	 *
	 * <p>
	 * Tries the cache first; on a miss, or when a re-resolve is needed (rollup
	 * promotion or unit backfill), calls the {@code get_or_create_channel_id}
	 * stored function.
	 *
	 * @param connection An open JDBC connection
	 * @param data       The DataPoint being written
	 * @return the resolved {@link ChannelInfo}
	 * @throws SQLException on database error
	 */
	public ChannelInfo resolveChannel(Connection connection, DataPoint data) throws SQLException {
		var key = this.encodeChannelKey(data);
		var cached = this.cache.get(key);
		if (cached != null && isReresolved(cached, data)) {
			return cached;
		}
		var info = this.doResolveChannel(connection, data);
		this.cache.put(key, info);
		return info;
	}

	private ChannelInfo doResolveChannel(Connection connection, DataPoint data) throws SQLException {
		try (var statement = connection.prepareStatement(this.resolveQuery)) {
			int i = 1;
			if (this.tenancy == Tenancy.MULTI) {
				statement.setString(i++, data.edgeName());
			}
			statement.setString(i++, data.componentName());
			statement.setString(i++, data.componentType());
			statement.setString(i++, data.channelName());
			statement.setString(i++, data.type().name());
			statement.setBoolean(i++, data.rollup());
			statement.setString(i, data.unit());
			try (var rs = statement.executeQuery()) {
				rs.next();
				return new ChannelInfo(rs.getObject(1, UUID.class), Type.valueOf(rs.getString(2)),
						rs.getBoolean(3), data.unit());
			}
		}
	}

	/**
	 * Cache-only resolution: returns the {@link ChannelInfo} for a point when
	 * it is already cached and fully up to date, letting a batch writer skip
	 * opening a Database connection entirely when every channel is already warm.
	 *
	 * @param data The DataPoint to resolve
	 * @return The cached {@link ChannelInfo}, or {@code null} if a DB resolve
	 *         is needed
	 */
	public ChannelInfo peekResolved(DataPoint data) {
		var cached = this.cache.get(this.encodeChannelKey(data));
		return cached != null && isReresolved(cached, data) ? cached : null;
	}

	/**
	 * Builds the cache key ("[edgeName/]componentName/channelName") for a {@link DataPoint}.
	 *
	 * @param data The DataPoint
	 * @return the cache key
	 */
	public String encodeChannelKey(DataPoint data) {
		return this.encodeChannelKey(data.edgeName(), data.componentName(), data.channelName());
	}

	/**
	 * Builds the cache key ("[edgeName/]componentName/channelName").
	 *
	 * @param edgeName The DataPoint
	 * @param componentName The DataPoint
	 * @param channelName The DataPoint
	 * @return the cache key
	 */
	private String encodeChannelKey(String edgeName, String componentName, String channelName) {
		if (this.tenancy == Tenancy.SINGLE) {
			return componentName + "/" + channelName;
		}
		return edgeName + "/" + componentName + "/" + channelName;
	}

	private static boolean isReresolved(ChannelInfo cached, DataPoint data) {
		var needsRollupPromotion = data.rollup() && !cached.rollup();
		var needsUnitBackfill = data.unit() != null && !data.unit().equals(cached.unit());
		return !needsRollupPromotion && !needsUnitBackfill;
	}
}
