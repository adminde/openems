package io.openems.shared.timescaledb.data;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.zaxxer.hikari.HikariDataSource;

import io.openems.common.timedata.Resolution;
import io.openems.common.types.ChannelAddress;
import io.openems.shared.timescaledb.Type;
import io.openems.shared.timescaledb.Utils;
import io.openems.shared.timescaledb.schema.Aggregate;
import io.openems.shared.timescaledb.schema.ChannelInfo;
import io.openems.shared.timescaledb.schema.ChannelManager;

/**
 * Handles read queries for TimescaleDB, including historic data, energy totals, and latest values.
 */
public class ReadHandler {

	private final Logger log = LoggerFactory.getLogger(ReadHandler.class);

	private final HikariDataSource dataSource;
	private final ChannelManager channelManager;

	/**
	 * The aggregate tiers this deployment's {@code SchemaHandler} built, sorted
	 * ascending by bucket — the single source of truth for read routing, so
	 * {@code pickSource()} never targets a tier that does not exist or whose
	 * retention has already dropped the queried window.
	 */
	private final List<Aggregate> aggregates;

	/**
	 * Channels already reported by the not-aggregated warning, so each one is
	 * logged only once per runtime.
	 */
	private final Set<String> notAggregatedWarned = ConcurrentHashMap.newKeySet();

	/**
	 * Constructor.
	 *
	 * @param dataSource     The Hikari connection pool
	 * @param channelManager The ChannelManager to resolve channel metadata
	 * @param aggregates     The aggregate tiers this deployment's SchemaHandler
	 *                       enforces — used by pickSource() to route queries and
	 *                       to avoid already-expired tiers
	 */
	public ReadHandler(HikariDataSource dataSource, ChannelManager channelManager,
			List<Aggregate> aggregates) {
		this.dataSource = dataSource;
		this.channelManager = channelManager;
		this.aggregates = aggregates.stream()
				.sorted(Comparator.comparingLong(a -> a.bucket().getSeconds()))
				.toList();
	}

	/**
	 * Returns the most recent value for a channel.
	 *
	 * <p>
	 * Used by Timedata.getLatestValue(). Picks the right raw hypertable based
	 * on the channel's type, then runs a single-row reverse scan on the
	 * (channel_id, time DESC) index.
	 * 
	 * @param edgeName The Edge identifier
	 * @param channel  The ChannelAddress
	 * @return The latest value if present
	 * @throws SQLException on database error
	 */
	public Optional<Object> queryLatestValue(String edgeName, ChannelAddress channel) throws SQLException {
		try (Connection connection = this.dataSource.getConnection()) {
			ChannelInfo info = this.channelManager.lookupChannel(connection, edgeName, channel);
			if (info == null) {
				return Optional.empty();
			}
			String sql = new StringBuilder()
					.append("SELECT value FROM ").append(info.type().rawTableName)
					.append(" WHERE channel_id = ? ORDER BY time DESC LIMIT 1")
					.toString();
			try (var pst = connection.prepareStatement(sql)) {
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
	 * grouped SELECT to produce one row per (bucket, channel). The result map is
	 * prefilled with JsonNull for every bucket/channel, matching the dense map
	 * the OpenEMS API expects. Calendar-based resolutions (days and coarser) are
	 * bucketed timezone-aware, so month buckets and DST transitions are correct.
	 *
	 * @param edgeName   The Edge identifier
	 * @param from       Start time
	 * @param to         End time
	 * @param channels   Set of requested channels
	 * @param resolution The bucket {@link Resolution}
	 * @return sorted map keyed by bucket timestamp
	 * @throws SQLException on database error
	 */
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(
			String edgeName, ZonedDateTime from, ZonedDateTime to,
			Set<ChannelAddress> channels, Resolution resolution) throws SQLException {

		if (channels.isEmpty()) {
			return new java.util.TreeMap<>();
		}
		var result = Utils.prepareDataMap(from, to, channels, resolution);
		var approxBucketSecs = Utils.approxSeconds(resolution);
		var interval = Utils.toSqlInterval(resolution);
		var calendarBucket = resolution.getUnit().isDateBased();

		try (Connection connection = this.dataSource.getConnection()) {
			Map<String, Map<UUID, ChannelAddress>> byView = new HashMap<>();
			for (ChannelAddress channel : channels) {
				ChannelInfo info = this.channelManager.lookupChannel(connection, edgeName, channel);
				if (info == null) {
					continue;
				}

				// A fine-grained query on a non-aggregated channel degrades to a raw
				// scan; warn once per channel so missing include-list entries surface
				// instead of silently running slow.
				if (!info.aggregate() && approxBucketSecs < 900 && this.notAggregatedWarned.add(channel.toString())) {
					this.log.warn("Channel [{}] queried at [{}s] resolution but not aggregated; "
							+ "consider adding it to AggregateChannels", channel, approxBucketSecs);
				}

				String view = this.pickSource(info.type(), info.aggregate(), approxBucketSecs, from);
				byView.computeIfAbsent(view, k -> new HashMap<>()).put(info.channelId(), channel);
			}
			for (var entry : byView.entrySet()) {
				String view = entry.getKey();
				Map<UUID, ChannelAddress> addrById = entry.getValue();
				UUID[] channelIds = addrById.keySet().toArray(UUID[]::new);

				// Raw hypertables expose (time, value); the continuous-aggregate
				// views expose (bucket, avg_val/last_val/sample_count). Re-bucketing
				// aggregate rows weights by sample_count — a plain AVG(avg_val)
				// would count sparse and dense sub-buckets equally.
				boolean raw = isRawTable(view);
				String timeCol = raw ? "time" : "bucket";
				String aggExpr = view.contains("string")
						? (raw ? "last(value, time)" : "last(last_val, bucket)")
						: (raw ? "AVG(value)"
								: "SUM(avg_val * sample_count) / NULLIF(SUM(sample_count), 0)");

				// time_bucket with a timezone argument aligns calendar buckets (days,
				// months, ...) to local midnight incl. DST; requires TimescaleDB >= 2.8.
				StringBuilder sql = new StringBuilder()
						.append("SELECT time_bucket(?::interval, ").append(timeCol);
				if (calendarBucket) {
					sql.append(", ?");
				}
				sql.append(") AS b, channel_id, ").append(aggExpr).append(" AS v ")
						.append("FROM ").append(view).append(" ")
						.append("WHERE channel_id = ANY(?)")
						.append(" AND ").append(timeCol).append(" >= ?")
						.append(" AND ").append(timeCol).append(" < ? ")
						.append("GROUP BY b, channel_id ORDER BY b");

				try (var pst = connection.prepareStatement(sql.toString())) {
					var i = 1;
					pst.setString(i++, interval);
					if (calendarBucket) {
						pst.setString(i++, from.getZone().getId());
					}
					pst.setArray(i++, connection.createArrayOf("uuid", channelIds));
					pst.setObject(i++, from.toOffsetDateTime());
					pst.setObject(i, to.toOffsetDateTime());

					try (ResultSet rs = pst.executeQuery()) {
						while (rs.next()) {
							ZonedDateTime bucketTs = rs.getObject(1, OffsetDateTime.class).atZoneSameInstant(from.getZone());
							UUID channelId = rs.getObject(2, UUID.class);
							Object value = rs.getObject(3);

							ChannelAddress addr = addrById.get(channelId);
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

		SortedMap<ChannelAddress, JsonElement> result = Utils.prepareEnergyMap(channels);
		try (Connection con = this.dataSource.getConnection()) {
			for (ChannelAddress channel : channels) {
				ChannelInfo info = this.channelManager.lookupChannel(con, edgeName, channel);
				if (info == null) {
					continue; // stays JsonNull from the prefill
				}

				// Sum positive deltas, treating any downward jump as a counter reset
				// where the post-reset value itself counts as accumulation.
				String table = info.type().rawTableName;
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
							result.put(channel, toJson(v));
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
	 * Implemented as a window query over the raw table using `last(value, time)`
	 * per bucket and LAG() to subtract neighbors. The query window is extended by
	 * one bucket before {@code from} so the first requested bucket gets a real
	 * delta. The result map is prefilled with JsonNull for every bucket/channel.
	 *
	 * @param edgeName   The Edge identifier
	 * @param from       Start time
	 * @param to         End time
	 * @param channels   Set of requested channels
	 * @param resolution The bucket {@link Resolution}
	 * @return A map of timestamps to channel energy deltas
	 * @throws SQLException on database error
	 */
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyPerPeriod(
			String edgeName, ZonedDateTime from, ZonedDateTime to,
			Set<ChannelAddress> channels, Resolution resolution) throws SQLException {

		if (channels.isEmpty()) {
			return new java.util.TreeMap<>();
		}
		var result = Utils.prepareDataMap(from, to, channels, resolution);
		var interval = Utils.toSqlInterval(resolution);
		var calendarBucket = resolution.getUnit().isDateBased();
		var extendedFrom = from.minus(resolution.getValue(), resolution.getUnit());

		try (Connection con = this.dataSource.getConnection()) {
			for (ChannelAddress channel : channels) {
				ChannelInfo info = this.channelManager.lookupChannel(con, edgeName, channel);
				if (info == null) {
					continue;
				}
				String bucketExpr = calendarBucket
						? "time_bucket(?::interval, time, ?)"
						: "time_bucket(?::interval, time)";
				String sql = """
						WITH per_bucket AS (
						    SELECT %s AS b,
						           last(value, time) AS last_val
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
						""".formatted(bucketExpr, info.type().rawTableName);

				try (var pst = con.prepareStatement(sql)) {
					var i = 1;
					pst.setString(i++, interval);
					if (calendarBucket) {
						pst.setString(i++, from.getZone().getId());
					}
					pst.setObject(i++, info.channelId());
					pst.setObject(i++, extendedFrom.toOffsetDateTime());
					pst.setObject(i, to.toOffsetDateTime());
					try (ResultSet rs = pst.executeQuery()) {
						while (rs.next()) {
							ZonedDateTime bucketTs = rs.getObject(1, OffsetDateTime.class).atZoneSameInstant(from.getZone());
							Object delta = rs.getObject(2);
							if (delta == null) {
								continue; // bucket before the window has no LAG()
							}
							if (bucketTs.isBefore(from)) {
								continue; // t-1 bucket only feeds the first LAG()
							}
							result.computeIfAbsent(bucketTs, k -> new java.util.TreeMap<>())
									.put(channel, toJson(delta));
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
		try (Connection connection = this.dataSource.getConnection()) {
			ChannelInfo info = this.channelManager.lookupChannel(connection, edgeName, notSendChannel);
			if (info == null) {
				return result;
			}
			String sql = new StringBuilder()
					.append("SELECT EXTRACT(EPOCH FROM time)::bigint FROM ").append(info.type().rawTableName)
					.append(" WHERE channel_id = ? AND time > to_timestamp(?) AND value <> 0 ORDER BY time")
					.toString();
			try (var pst = connection.prepareStatement(sql)) {
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
			Map<String, Map<UUID, ChannelAddress>> byTable = new HashMap<>();
			for (ChannelAddress addr : channels) {
				ChannelInfo info = this.channelManager.lookupChannel(con, edgeName, addr);
				if (info == null) {
					continue;
				}
				byTable.computeIfAbsent(info.type().rawTableName, k -> new HashMap<>())
						.put(info.channelId(), addr);
			}
			for (var entry : byTable.entrySet()) {
				String table = entry.getKey();
				Map<UUID, ChannelAddress> addrById = entry.getValue();
				UUID[] ids = addrById.keySet().toArray(UUID[]::new);
				String sql = new StringBuilder()
						.append("SELECT EXTRACT(EPOCH FROM time)::bigint, channel_id, value FROM ").append(table)
						.append(" WHERE channel_id = ANY(?) AND time >= ? AND time < ? ORDER BY time")
						.toString();
				try (var pst = con.prepareStatement(sql)) {
					pst.setArray(1, con.createArrayOf("uuid", ids));
					pst.setObject(2, from.toOffsetDateTime());
					pst.setObject(3, to.toOffsetDateTime());
					try (ResultSet rs = pst.executeQuery()) {
						while (rs.next()) {
							long ts = rs.getLong(1);
							UUID cid = rs.getObject(2, UUID.class);
							Object value = rs.getObject(3);
							ChannelAddress addr = addrById.get(cid);
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

	/**
	 * Picks the source table/view for a channel at the requested resolution.
	 *
	 * <p>
	 * The aggregates are a pure acceleration layer over the aggregate-flagged
	 * channels; the raw hypertable is always the correct fallback (and the
	 * long-lived source of truth). For an aggregated numeric channel this returns
	 * the coarsest aggregate tier whose bucket is &lt;= the requested resolution
	 * and whose retention still covers the query window; if none qualifies — the
	 * resolution is finer than the finest tier, or the window predates every
	 * fitting tier's retention — it falls back to raw. Strings and non-aggregated
	 * channels always use raw.
	 *
	 * @param type          INTEGER / FLOAT / STRING
	 * @param aggregate     whether the channel is in the aggregate layer
	 * @param bucketSeconds desired bucket size in seconds
	 * @param from          start of the query window (its oldest point)
	 * @return the unqualified table or materialized-view name
	 */
	private String pickSource(Type type, boolean aggregate, long bucketSeconds, ZonedDateTime from) {
		// Strings are never aggregated; non-aggregated channels live in raw only.
		if (type == Type.STRING || !aggregate) {
			return type.rawTableName;
		}
		// Aggregates are ordered ascending by bucket: the last tier that both fits
		// the requested resolution and still holds the window is the coarsest match.
		Aggregate chosen = null;
		for (var agg : this.aggregates) {
			if (agg.bucket().getSeconds() <= bucketSeconds && !this.expired(from, agg.retentionDays())) {
				chosen = agg;
			}
		}
		if (chosen == null) {
			return type.rawTableName;
		}
		return "data_" + chosen.name() + "_" + type.aggInfix;
	}

	/**
	 * Whether the query window starts before the given retention horizon, i.e.
	 * the picked tier no longer holds data that far back. A horizon of {@code 0}
	 * means "kept forever" and never expires.
	 */
	private boolean expired(ZonedDateTime from, int retentionDays) {
		return retentionDays > 0 && reachesBefore(from, retentionDays);
	}

	/**
	 * Whether {@code source} is one of the raw hypertables (as opposed to a
	 * continuous-aggregate view): raw tables expose {@code (time, value)}, the
	 * aggregate views expose {@code (bucket, avg_val/last_val)}.
	 */
	private static boolean isRawTable(String source) {
		for (var type : Type.values()) {
			if (type.rawTableName.equals(source)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether {@code from} is older than {@code days} ago — i.e. the query window
	 * reaches into a region an aggregate tier's retention policy may already have
	 * dropped.
	 */
	private static boolean reachesBefore(ZonedDateTime from, long days) {
		return from.toInstant().isBefore(Instant.now().minus(Duration.ofDays(days)));
	}

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
