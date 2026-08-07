package io.openems.shared.timescaledb.schema;

import io.openems.shared.timescaledb.data.RefreshHandler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * One continuous-aggregate tier: a bucket resolution, where it reads from, how
 * long it is kept, and its refresh/chunk tuning. The full aggregation layer is
 * described by an ordered {@code List<Aggregate>} that the
 * {@link io.openems.shared.timescaledb.TimescaleDbConnector} builder consumes.
 *
 * <p>
 * The aggregates are a pure query-acceleration layer for frontend
 * visualization over the {@code aggregate}-flagged channels (see
 * {@link AggregateChannels}); the raw hypertables remain the long-lived source
 * of truth. Each tier materializes into {@code data_<name>_<type>} (e.g.
 * {@code data_15m_integer}).
 *
 * <p>
 * <b>Source resolution.</b> {@link #source()} names the finer tier this one
 * reads from. When it is {@code null} the {@link SchemaHandler} sorts the list
 * ascending by {@link #bucket()} and links each tier to its immediate finer
 * predecessor; the finest tier (no predecessor) reads from the raw hypertable
 * with a {@code WHERE aggregate} filter. A non-null {@code source} is an
 * explicit override for a non-linear topology. The build fails fast if a
 * source's bucket does not evenly divide this tier's bucket (the
 * {@code time_bucket} cascade would otherwise drift).
 *
 * <p>
 * <b>Defaults.</b> {@link #refresh()} and {@link #chunk()} may be {@code null}
 * to derive sensible values from the bucket/retention (see
 * {@link #resolvedRefresh()} / {@link #resolvedChunk()}); {@link #of(Tenancy)}
 * ships explicit, hand-tuned values. Retention {@code 0} keeps the tier as long
 * as the raw data (no retention policy created).
 *
 * @param name          short label used in the table name, e.g. {@code "15m"}
 * @param bucket        the {@code time_bucket} width of this tier
 * @param source        name of the finer tier to read from, or {@code null} to
 *                      read from the immediate finer predecessor (raw for the
 *                      finest tier)
 * @param retentionDays days to keep this tier; {@code 0} = keep as long as raw
 * @param refresh       refresh policy, or {@code null} to derive from the bucket
 * @param chunk         chunk_time_interval, or {@code null} to derive from the
 *                      retention
 */
public record Aggregate(
		String name,
		Duration bucket,
		String source,
		int retentionDays,
		Refresh refresh,
		Duration chunk) {

	/**
	 * The refresh policy of a continuous aggregate.
	 *
	 * @param startOffset how far back each incremental refresh re-scans. Sized for
	 *                    out-of-order jitter only. Anything older is invisible to
	 *                    the policy and has to be refreshed explicitly through
	 *                    {@link RefreshHandler}
	 * @param endOffset   how far back from now the refresh stops; must be
	 *                    {@code >= bucket} so only complete buckets are
	 *                    materialized (the in-progress bucket is served live by
	 *                    real-time aggregation)
	 * @param schedule    how often the refresh job runs
	 */
	public record Refresh(Duration startOffset, Duration endOffset, Duration schedule) {
	}

	/**
	 * Convenience constructor that derives refresh and chunk from the bucket and
	 * retention.
	 *
	 * @param name          short label used in the table name
	 * @param bucket        the {@code time_bucket} width
	 * @param source        finer tier to read from, or {@code null}
	 * @param retentionDays days to keep; {@code 0} = keep as long as raw
	 */
	public Aggregate(String name, Duration bucket, String source, int retentionDays) {
		this(name, bucket, source, retentionDays, null, null);
	}

	/**
	 * The refresh policy, deriving a default from the bucket if none was given:
	 * {@code schedule = endOffset = bucket}, {@code startOffset = 10 x bucket}.
	 *
	 * @return the effective {@link Refresh}
	 */
	public Refresh resolvedRefresh() {
		if (this.refresh != null) {
			return this.refresh;
		}
		return new Refresh(this.bucket.multipliedBy(10), this.bucket, this.bucket);
	}

	/**
	 * The chunk_time_interval, deriving a default from the retention if none was
	 * given: {@code ~retention / 12} (a handful of chunks per tier, so retention
	 * drops whole chunks cleanly), or 365 days when kept forever.
	 *
	 * @return the effective chunk interval
	 */
	public Duration resolvedChunk() {
		if (this.chunk != null) {
			return this.chunk;
		}
		var days = this.retentionDays > 0 ? Math.max(1, this.retentionDays / 12) : 365;
		return Duration.ofDays(days);
	}

	/**
	 * The default aggregation tiers for a deployment. Returns a fresh, mutable
	 * list so the Edge/Backend implementation can edit it before handing it to
	 * the builder.
	 *
	 * <p>
	 * Both profiles keep a 1-minute tier (90 days) for zoomed-in recent charts
	 * and a 15-minute tier for everything coarser (re-bucketed to the requested
	 * calendar resolution on read; 15 minutes divides every real-world timezone
	 * offset, so day/month/year buckets stay exact). The Edge bounds the
	 * 15-minute tier to 3 years; the storage-unconstrained Backend keeps it
	 * forever.
	 *
	 * @param tenancy the deployment tenancy
	 * @return a mutable list of the default {@link Aggregate} tiers
	 */
	public static List<Aggregate> of(Tenancy tenancy) {
		var oneMinute = new Aggregate("1m", Duration.ofMinutes(1), null, 90,
				new Refresh(Duration.ofMinutes(10), Duration.ofMinutes(1), Duration.ofMinutes(1)),
				Duration.ofDays(7));

		var list = new ArrayList<Aggregate>();
		list.add(oneMinute);
		switch (tenancy) {
		case SINGLE -> // Edge: storage-constrained, 15-minute bounded to 3 years.
			list.add(new Aggregate("15m", Duration.ofMinutes(15), "1m", 1095,
					new Refresh(Duration.ofHours(1), Duration.ofMinutes(15), Duration.ofMinutes(15)),
					Duration.ofDays(90)));
		case MULTI -> // Backend: archive, 15-minute kept forever.
			list.add(new Aggregate("15m", Duration.ofMinutes(15), "1m", 0,
					new Refresh(Duration.ofHours(1), Duration.ofMinutes(15), Duration.ofMinutes(15)),
					Duration.ofDays(90)));
		}
		return list;
	}
}
