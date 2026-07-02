package io.openems.shared.timescaledb.worker;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.zaxxer.hikari.HikariDataSource;

import io.openems.common.types.ChannelAddress;
import io.openems.shared.timescaledb.schema.ChannelDefinition;
import io.openems.shared.timescaledb.schema.ChannelManager;
import io.openems.shared.timescaledb.schema.Schema;

/**
 * Handles read queries for TimescaleDB, including historic data, energy totals, and latest values.
 */
public class ReadHandler {

	private final HikariDataSource dataSource;
	private final ChannelManager channelManager;
	private final boolean minutelyAggregateAvailable;

	/**
	 * Constructor.
	 *
	 * @param dataSource                 The Hikari connection pool
	 * @param channelManager             The ChannelManager to resolve channel
	 *                                   metadata
	 * @param minutelyAggregateAvailable Whether the 1-minute Fast Lane aggregate
	 *                                   exists; when false, sub-15m core queries
	 *                                   fall back to raw tables
	 */
	public ReadHandler(HikariDataSource dataSource, ChannelManager channelManager,
	                   boolean minutelyAggregateAvailable) {
		this.dataSource = dataSource;
		this.channelManager = channelManager;
		this.minutelyAggregateAvailable = minutelyAggregateAvailable;
	}

	/**
	 * Returns the most recent value for a channel.
	 *
	 * <p>
	 * Used by Timedata.getLatestValue(). Picks the right raw hypertable based
	 * on the channel's type and core flag, then runs a single-row reverse
	 * scan on the (channel_id, time DESC) index.
	 * 
	 * @param edgeName The Edge identifier
	 * @param addr     The ChannelAddress
	 * @return The latest value if present
	 * @throws SQLException on database error
	 */
	public Optional<Object> queryLatestValue(String edgeName, ChannelAddress addr) throws SQLException {
		try (Connection con = this.dataSource.getConnection()) {
			ChannelDefinition info = this.channelManager.lookupChannel(con, edgeName, addr);
			if (info == null) {
				return Optional.empty();
			}
			String table = "data_" + dataTypeFolder(info.dataType());
			String sql = "SELECT value FROM " + table + " WHERE channel_id = ? ORDER BY time DESC LIMIT 1";
			try (var pst = con.prepareStatement(sql)) {
				pst.setObject(1, info.channelId());
				try (ResultSet rs = pst.executeQuery()) {
					if (rs.next()) {
						return Optional.ofNullable(rs.getObject(1));
					}
					return Optional.empty();
				}
			}
		}
	}

	/**
	 * Returns time-series data, bucketed by the requested resolution.
	 *
	 * <p>
	 * For each requested channel, picks the best source view and runs a single
	 * grouped SELECT to produce one row per (bucket, channel).
	 *
	 * @param edgeName       The Edge identifier
	 * @param from           Start time
	 * @param to             End time
	 * @param channels       Set of requested channels
	 * @param bucketSecs  Resolution in seconds
	 * @return sorted map keyed by bucket timestamp (UTC)
	 * @throws SQLException on database error
	 */
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(
			String edgeName, ZonedDateTime from, ZonedDateTime to,
			Set<ChannelAddress> channels, long bucketSecs) throws SQLException {

		SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> result = new java.util.TreeMap<>();
		if (channels.isEmpty()) {
			return result;
		}

		try (Connection con = this.dataSource.getConnection()) {
			
			Map<String, List<ChannelAddr>> byView = new HashMap<>();
			for (ChannelAddress addr : channels) {
				ChannelDefinition info = this.channelManager.lookupChannel(con, edgeName, addr);
				if (info == null) {
					continue;
				}
				String view = this.pickSource(info.dataType(), info.core(), bucketSecs, from);
				byView.computeIfAbsent(view, k -> new ArrayList<>()).add(new ChannelAddr(info.channelId(), addr));
			}
			for (var entry : byView.entrySet()) {
				String view = entry.getKey();
				List<ChannelAddr> channelAddrs = entry.getValue();
				UUID[] channelIds = channelAddrs.stream().map(ChannelAddr::channelId).toArray(UUID[]::new);

				String timeCol = view.startsWith("agg_") ? "bucket" : "time";
				String aggExpr = view.startsWith("agg_") ? "AVG(avg_val)" : "AVG(value)";
				if (view.contains("string")) {
					aggExpr = view.startsWith("agg_") ? "last(last_val, bucket)" : "last(value, time)";
				}
				String sql = "SELECT time_bucket(?::interval, " + timeCol + ") AS b, channel_id, "
						+ aggExpr + " AS v FROM " + view + " "
						+ "WHERE channel_id = ANY(?) AND " + timeCol + " >= ? AND " + timeCol + " < ? "
						+ "GROUP BY b, channel_id ORDER BY b";

				try (var pst = con.prepareStatement(sql)) {
					pst.setString(1, bucketSecs + " seconds");
					pst.setArray(2, con.createArrayOf("uuid", channelIds));
					pst.setObject(3, from.toOffsetDateTime());
					pst.setObject(4, to.toOffsetDateTime());

					try (ResultSet rs = pst.executeQuery()) {
						while (rs.next()) {
							ZonedDateTime bucketTs = rs.getObject(1, OffsetDateTime.class).atZoneSameInstant(from.getZone());
							UUID channelId = rs.getObject(2, UUID.class);
							Object value = rs.getObject(3);

							ChannelAddress addr = channelAddrs.stream().filter(e -> e.channelId().equals(channelId))
									.findFirst().map(ChannelAddr::addr).orElse(null);
							if (addr == null) {
								continue;
							}
							result.computeIfAbsent(bucketTs, k -> new java.util.TreeMap<>())
									.put(addr, toJson(value));
						}
					}
				}
			}
		}
		return result;
	}

	/**
	 * Returns the energy delta (last - first) for each requested channel over
	 * the entire [from, to) window.
	 *
	 * <p>
	 * Always queries raw tables to preserve precision. Energy totals must not
	 * be smoothed by aggregates.
	 * 
	 * @param edgeName The Edge identifier
	 * @param from     Start time
	 * @param to       End time
	 * @param channels Set of requested channels
	 * @return A map of channels to their energy deltas
	 * @throws SQLException on database error
	 */
	public SortedMap<ChannelAddress, JsonElement> queryHistoricEnergy(
			String edgeName, ZonedDateTime from, ZonedDateTime to,
			Set<ChannelAddress> channels) throws SQLException {

		SortedMap<ChannelAddress, JsonElement> result = new java.util.TreeMap<>();
		try (Connection con = this.dataSource.getConnection()) {
			for (ChannelAddress addr : channels) {
				ChannelDefinition info = this.channelManager.lookupChannel(con, edgeName, addr);
				if (info == null) {
					result.put(addr, com.google.gson.JsonNull.INSTANCE);
					continue;
				}

				// Sum positive deltas, treating any downward jump as a counter reset
				// where the post-reset value itself counts as accumulation.
				String table = "data_" + dataTypeFolder(info.dataType());
				String sql = """
						WITH ordered AS (
						    SELECT value, LAG(value) OVER (ORDER BY time) AS prev
						    FROM %s
						    WHERE channel_id = ? AND time >= ? AND time < ?
						)
						SELECT SUM(CASE
						    WHEN prev IS NULL  THEN 0
						    WHEN value >= prev THEN value - prev
						    ELSE value
						END) AS delta
						FROM ordered
						""".formatted(table);
				try (var pst = con.prepareStatement(sql)) {
					pst.setObject(1, info.channelId());
					pst.setObject(2, from.toOffsetDateTime());
					pst.setObject(3, to.toOffsetDateTime());
					try (ResultSet rs = pst.executeQuery()) {
						if (rs.next()) {
							Object v = rs.getObject(1);
							result.put(addr, toJson(v));
						} else {
							result.put(addr, com.google.gson.JsonNull.INSTANCE);
						}
					}
				}
			}
		}
		return result;
	}

	/**
	 * Returns the energy delta per bucket.
	 *
	 * <p>
	 * Implemented as a window query over the appropriate aggregate using
	 * `last(value, time)` per bucket and LAG() to subtract neighbors.
	 * 
	 * @param edgeName       The Edge identifier
	 * @param from           Start time
	 * @param to             End time
	 * @param channels       Set of requested channels
	 * @param bucketSecs  Resolution in seconds
	 * @return A map of timestamps to channel energy deltas
	 * @throws SQLException on database error
	 */
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyPerPeriod(
			String edgeName, ZonedDateTime from, ZonedDateTime to,
			Set<ChannelAddress> channels, long bucketSecs) throws SQLException {

		SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> result = new java.util.TreeMap<>();
		if (channels.isEmpty()) {
			return result;
		}

		try (Connection con = this.dataSource.getConnection()) {
			for (ChannelAddress addr : channels) {
				ChannelDefinition info = this.channelManager.lookupChannel(con, edgeName, addr);
				if (info == null) {
					continue;
				}
				String table = "data_" + dataTypeFolder(info.dataType());
				String sql = """
						WITH per_bucket AS (
						    SELECT time_bucket(?::interval, time) AS b,
						           last(value, time)              AS last_val
						    FROM %s
						    WHERE channel_id = ? AND time >= ? AND time < ?
						    GROUP BY b
						)
						SELECT b,
						       CASE
						           WHEN LAG(last_val) OVER (ORDER BY b) IS NULL THEN NULL
						           WHEN last_val >= LAG(last_val) OVER (ORDER BY b)
						               THEN last_val - LAG(last_val) OVER (ORDER BY b)
						           ELSE last_val
						       END AS delta
						FROM per_bucket
						ORDER BY b;
						""".formatted(table);

				try (var pst = con.prepareStatement(sql)) {
					pst.setString(1, bucketSecs + " seconds");
					pst.setObject(2, info.channelId());
					pst.setObject(3, from.toOffsetDateTime());
					pst.setObject(4, to.toOffsetDateTime());
					try (ResultSet rs = pst.executeQuery()) {
						while (rs.next()) {
							ZonedDateTime bucketTs = rs.getObject(1, OffsetDateTime.class).atZoneSameInstant(from.getZone());
							Object delta = rs.getObject(2);
							if (delta == null) {
								continue; // first bucket has no LAG()
							}
							result.computeIfAbsent(bucketTs, k -> new java.util.TreeMap<>())
									.put(addr, toJson(delta));
						}
					}
				}
			}
		}
		return result;
	}

	/**
	 * Returns the epoch-second timestamps of "data not sent" markers for the
	 * given {@code notSendChannel}, newer than {@code lastResendTimestamp}.
	 *
	 * <p>
	 * Convention (matches RRD4j): {@code value == 0} means the cycle's data was
	 * sent successfully; any non-zero value marks a failed send.
	 *
	 * @param edgeName            The Edge identifier
	 * @param notSendChannel      The channel that records send success/failure
	 * @param lastResendTimestamp Epoch seconds; negative means "from the start"
	 * @return Ordered list of failed-send timestamps in epoch seconds
	 * @throws SQLException on database error
	 */
	public java.util.List<Long> getResendTimestamps(String edgeName,
			ChannelAddress notSendChannel, long lastResendTimestamp) throws SQLException {
		var result = new ArrayList<Long>();
		try (Connection con = this.dataSource.getConnection()) {
			ChannelDefinition info = this.channelManager.lookupChannel(con, edgeName, notSendChannel);
			if (info == null) {
				return result;
			}
			String table = "data_" + dataTypeFolder(info.dataType());
			String sql = "SELECT EXTRACT(EPOCH FROM time)::bigint FROM " + table
					+ " WHERE channel_id = ? AND time > to_timestamp(?) AND value <> 0 ORDER BY time";
			try (var pst = con.prepareStatement(sql)) {
				pst.setObject(1, info.channelId());
				pst.setLong(2, Math.max(lastResendTimestamp, 0L));
				try (ResultSet rs = pst.executeQuery()) {
					while (rs.next()) {
						result.add(rs.getLong(1));
					}
				}
			}
		}
		return result;
	}

	/**
	 * Returns raw samples for the given channels in {@code [from, to)}, keyed by
	 * epoch-second timestamp. Used to assemble resend payloads after an outage.
	 *
	 * @param edgeName The Edge identifier
	 * @param from     Start time (inclusive)
	 * @param to       End time (exclusive)
	 * @param channels Channels to fetch
	 * @return Map of epoch-second timestamp → map of channel → value
	 * @throws SQLException on database error
	 */
	public SortedMap<Long, SortedMap<ChannelAddress, JsonElement>> queryResendData(
			String edgeName, ZonedDateTime from, ZonedDateTime to,
			Set<ChannelAddress> channels) throws SQLException {

		SortedMap<Long, SortedMap<ChannelAddress, JsonElement>> result = new java.util.TreeMap<>();
		if (channels.isEmpty()) {
			return result;
		}
		try (Connection con = this.dataSource.getConnection()) {
			// Group channels by raw table so we run at most three queries.
			Map<String, List<ChannelAddr>> byTable = new HashMap<>();
			for (ChannelAddress addr : channels) {
				ChannelDefinition info = this.channelManager.lookupChannel(con, edgeName, addr);
				if (info == null) {
					continue;
				}
				String table = "data_" + dataTypeFolder(info.dataType());
				byTable.computeIfAbsent(table, k -> new ArrayList<>())
						.add(new ChannelAddr(info.channelId(), addr));
			}
			for (var entry : byTable.entrySet()) {
				String table = entry.getKey();
				List<ChannelAddr> chans = entry.getValue();
				UUID[] ids = chans.stream().map(ChannelAddr::channelId).toArray(UUID[]::new);
				String sql = "SELECT EXTRACT(EPOCH FROM time)::bigint, channel_id, value FROM " + table
						+ " WHERE channel_id = ANY(?) AND time >= ? AND time < ? ORDER BY time";
				try (var pst = con.prepareStatement(sql)) {
					pst.setArray(1, con.createArrayOf("uuid", ids));
					pst.setObject(2, from.toOffsetDateTime());
					pst.setObject(3, to.toOffsetDateTime());
					try (ResultSet rs = pst.executeQuery()) {
						while (rs.next()) {
							long ts = rs.getLong(1);
							UUID cid = rs.getObject(2, UUID.class);
							Object value = rs.getObject(3);
							ChannelAddress addr = chans.stream()
									.filter(e -> e.channelId().equals(cid))
									.findFirst().map(ChannelAddr::addr).orElse(null);
							if (addr == null) {
								continue;
							}
							result.computeIfAbsent(ts, k -> new java.util.TreeMap<>())
									.put(addr, toJson(value));
						}
					}
				}
			}
		}
		return result;
	}

	private static String dataTypeFolder(String dataType) {
		return switch (dataType) {
		case "INTEGER" -> "integer";
		case "FLOAT"   -> "float";
		default        -> "string";
		};
	}

	/**
	 * Picks the best source view/table for a given resolution: the coarsest view
	 * whose bucket size is &lt;= the requested resolution. When the 1-minute
	 * aggregate is unavailable (e.g. on Edge devices), sub-15m core queries fall
	 * back to raw tables.
	 *
	 * <p>
	 * For core channels the choice is also time-aware: the Fast Lane (_core) views
	 * expire on their retention policies, while the shared (Slow Lane) views are
	 * kept forever. If the query window reaches further back than the picked _core
	 * tier's retention horizon, this routes to the shared view instead, so old
	 * core history remains reachable (at the same or the next-coarser resolution).
	 *
	 * @param dataType       INTEGER / FLOAT / STRING
	 * @param core           true = Fast Lane (VERY_HIGH), false = Slow Lane
	 * @param bucketSeconds  desired bucket size in seconds
	 * @param from           start of the query window (its oldest point)
	 * @return the unqualified table or materialized-view name
	 */
	private String pickSource(String dataType, boolean core, long bucketSeconds, ZonedDateTime from) {
		String typePart = switch (dataType) {
		case "INTEGER" -> "integer";
		case "FLOAT"   -> "float";
		default        -> "string";
		};

		// Strings cannot be aggregated mathematically, so they always use raw data.
		if ("string".equals(typePart)) {
			return "data_" + typePart;
		}

		// Fast Lane (core = true): tiers 1m, 15m, 1d, each falling back to the
		// never-expiring shared view once the window predates its retention.
		if (core) {
			// Daily tier.
			if (bucketSeconds >= 86400) {
				return reachesBefore(from, Schema.AGG_1D_CORE_DAYS)
						? "agg_1d_" + typePart          // shared, kept forever
						: "agg_1d_core_" + typePart;
			}
			// 15-minute tier — also where a sub-15m request lands once it predates
			// the 1-minute retention (no 1-minute data exists that far back).
			if (bucketSeconds >= 900 || reachesBefore(from, Schema.AGG_1M_CORE_DAYS)) {
				return reachesBefore(from, Schema.AGG_15M_CORE_DAYS)
						? "agg_15m_" + typePart         // shared, kept forever
						: "agg_15m_core_" + typePart;
			}
			// 1-minute tier (recent window only).
			if (bucketSeconds >= 60 && this.minutelyAggregateAvailable) {
				return "agg_1m_core_" + typePart;
			}
			return "data_" + typePart;
		}

		// Slow Lane (core = false): tiers 15m, 1d. Sub-15m falls back to raw.
		if (bucketSeconds >= 86400) {
			return "agg_1d_" + typePart;
		}
		if (bucketSeconds >= 900) {
			return "agg_15m_" + typePart;
		}
		return "data_" + typePart;
	}

	/**
	 * Whether {@code from} is older than {@code days} ago — i.e. the query window
	 * reaches into a region a _core view's retention policy may already have
	 * dropped.
	 */
	private static boolean reachesBefore(ZonedDateTime from, long days) {
		return from.toInstant().isBefore(Instant.now().minus(Duration.ofDays(days)));
	}

	/** Helper record pairing an channel_id (UUID v7) with its ChannelAddress. */
	private record ChannelAddr(UUID channelId, ChannelAddress addr) {}

	/** Convert a raw JDBC value into the JsonElement form OpenEMS expects. */
	private static JsonElement toJson(Object value) {
		if (value == null) {
			return com.google.gson.JsonNull.INSTANCE;
		}
		if (value instanceof Number n) {
			return new com.google.gson.JsonPrimitive(n);
		}
		if (value instanceof Boolean b) {
			return new com.google.gson.JsonPrimitive(b);
		}
		return new com.google.gson.JsonPrimitive(value.toString());
	}
}
