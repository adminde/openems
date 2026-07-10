package io.openems.shared.timescaledb.schema;

/**
 * Retention horizons (in days) for the continuous-aggregate tiers, chosen per
 * deployment: the storage-constrained Edge uses shorter horizons than the
 * Backend archive.
 *
 * <p>
 * The same instance feeds both {@link SchemaHandler} (which creates the
 * database retention policies from it) and
 * {@link io.openems.shared.timescaledb.data.ReadHandler} (whose
 * {@code pickSource()} routes queries away from tiers whose horizon the query
 * window has outlived). Sharing one instance keeps the read routing and the
 * actually-enforced policies in agreement by construction — never change the
 * database policies without going through this record.
 *
 * @param rollup1mDays    days to keep the 1-minute Fast-Lane aggregate
 * @param rollup15mDays   days to keep the 15-minute Fast-Lane aggregate
 * @param rollup1dDays    days to keep the daily Fast-Lane aggregate
 * @param slowLane15mDays days to keep the 15-minute Slow-Lane aggregate;
 *                        {@code 0} keeps it forever
 * @param slowLane1dDays  days to keep the daily Slow-Lane aggregate;
 *                        {@code 0} keeps it forever
 */
public record AggregateRetention(
		int rollup1mDays,
		int rollup15mDays,
		int rollup1dDays,
		int slowLane15mDays,
		int slowLane1dDays) {

	/**
	 * The Backend archive profile: generous Fast-Lane horizons, Slow Lane kept
	 * forever. This is the default when no explicit retention is configured.
	 */
	public static final AggregateRetention BACKEND_DEFAULTS = new AggregateRetention(90, 365, 3650, 0, 0);

	/**
	 * The Edge profile, sized for 32 GB eMMC devices (~11-13 GB steady state at
	 * ~300 channels): 1-minute for 90 days, 15-minute for 180 days (Fast Lane)
	 * / 3 years (Slow Lane), daily for 1 year (Fast Lane) / 10 years (Slow
	 * Lane). The dominant storage term is the uncompressed Slow-Lane 15-minute
	 * tier (~4.5 MB per channel per year) — that is why it is bounded here
	 * while the Backend keeps it forever.
	 */
	public static final AggregateRetention EDGE_DEFAULTS = new AggregateRetention(90, 180, 365, 1095, 3650);
}
