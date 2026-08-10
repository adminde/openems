package io.openems.shared.timescaledb.data;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.openems.shared.timescaledb.schema.Aggregate;
import io.openems.shared.timescaledb.schema.SchemaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

import io.openems.shared.timescaledb.Type;

/**
 * Materializes continuous-aggregate tiers for time ranges the refresh policies
 * will never reach on their own.
 *
 * <p>
 * A continuous aggregate is not updated by the write itself: an insert into an
 * already materialized region only appends to TimescaleDB's invalidation log,
 * and that entry is processed when a refresh covers exactly that range. The
 * policies installed by {@link SchemaHandler} refresh a narrow trailing window
 * ({@code start_offset}, e.g. 10 minutes for the 1-minute tier), which is right
 * for live data and useless for backfill. Resend after an Edge outage writes
 * hours or days into the past, so without this class those points stay in the
 * raw hypertables and never appear in any tier — a history query routed to a
 * tier shows a hole that raw could have filled.
 *
 * <p>
 * Callers report the range they backfilled with
 * {@link #schedule(Instant, Instant)}. Ranges are merged into one pending
 * window, and every new report re-arms a pending period of
 * {@value #PENDING_PERIOD_SECONDS} seconds — so a resend arriving as hundreds
 * of notifications causes one refresh, not hundreds. The pending period also
 * covers the asynchronous write pipeline: points reported here are still
 * queued, and a batch closes after at most 10 seconds.
 *
 * <p>
 * Tiers are refreshed finest→coarsest because a cascaded tier reads from its
 * finer predecessor and TimescaleDB does not propagate a refresh along the
 * cascade by itself.
 */
public class RefreshHandler {

	/**
	 * How long a reported window stays pending before it is refreshed. Every
	 * further report re-arms it, so the run happens once reporting has stopped
	 * for this long — not this long after the first report.
	 */
	private static final long PENDING_PERIOD_SECONDS = 300;

	private final Logger log = LoggerFactory.getLogger(RefreshHandler.class);

	private final HikariDataSource dataSource;

	/** The tiers to refresh, sorted ascending by bucket (finest first). */
	private final List<Aggregate> aggregates;

	private final ScheduledExecutorService scheduler;

	/** Merged pending window and its timer; both guarded by {@code this}. */
	private Instant pendingFrom;
	private Instant pendingTo;
	private ScheduledFuture<?> pendingRun;

	/**
	 * Constructor.
	 *
	 * @param dataSource the Hikari connection pool
	 * @param aggregates the aggregate tiers this deployment built
	 */
	public RefreshHandler(HikariDataSource dataSource, List<Aggregate> aggregates) {
		this.dataSource = dataSource;
		this.aggregates = aggregates.stream() //
				.sorted(Comparator.comparingLong(a -> a.bucket().getSeconds())) //
				.toList();
		this.scheduler = Executors.newSingleThreadScheduledExecutor(//
				r -> new Thread(r, "TimescaleDB-AggregateRefresh"));
	}

	/**
	 * Reports a time range that was written into the past and therefore needs the
	 * aggregate tiers re-materialized. Merges into the pending window and re-arms
	 * the pending period. Returns immediately.
	 *
	 * @param from start of the backfilled range
	 * @param to   end of the backfilled range
	 */
	public synchronized void schedule(Instant from, Instant to) {
		if (from == null || to == null || from.isAfter(to)) {
			return;
		}
		this.pendingFrom = this.pendingFrom == null || from.isBefore(this.pendingFrom) ? from : this.pendingFrom;
		this.pendingTo = this.pendingTo == null || to.isAfter(this.pendingTo) ? to : this.pendingTo;

		if (this.pendingRun != null) {
			this.pendingRun.cancel(false);
		}
		try {
			this.pendingRun = this.scheduler.schedule(this::runPending, PENDING_PERIOD_SECONDS, TimeUnit.SECONDS);
		} catch (java.util.concurrent.RejectedExecutionException e) {
			// deactivate() shut the scheduler down
		}
	}

	private void runPending() {
		Instant from;
		Instant to;
		synchronized (this) {
			from = this.pendingFrom;
			to = this.pendingTo;
			this.pendingFrom = null;
			this.pendingTo = null;
			this.pendingRun = null;
		}
		if (from == null || to == null) {
			return;
		}
		this.log.info("Refreshing aggregate tiers for backfilled range [{} .. {}]", from, to);
		var start = System.nanoTime();
		for (var aggregate : this.aggregates) {
			// Widen by one bucket on each side: refresh_continuous_aggregate rejects a
			// window narrower than one bucket and would otherwise silently leave the
			// partially covered edge buckets stale.
			var bucket = aggregate.bucket();
			for (var type : new Type[] { Type.INTEGER, Type.FLOAT }) {
				this.refresh("data_" + aggregate.name() + "_" + type.aggInfix, //
						from.minus(bucket), to.plus(bucket));
			}
		}
		this.log.info("Aggregate refresh finished in {} ms", (System.nanoTime() - start) / 1_000_000);
	}

	/**
	 * Refreshes one continuous aggregate. Never throws: one failing tier must not
	 * stop the others, and the range stays recoverable by the next resend or a
	 * manual call.
	 *
	 * @param view view of the backfilled range
	 * @param from start of the backfilled range
	 * @param to   end of the backfilled range
	 */
	private void refresh(String view, Instant from, Instant to) {
		// refresh_continuous_aggregate is a procedure and must not run inside a
		// transaction block, so this relies on the pool's default autocommit.
		try (var connection = this.dataSource.getConnection(); //
				var statement = connection.prepareStatement("CALL refresh_continuous_aggregate(?::regclass, ?, ?)")) {
			statement.setString(1, view);
			statement.setObject(2, from.atOffset(java.time.ZoneOffset.UTC));
			statement.setObject(3, to.atOffset(java.time.ZoneOffset.UTC));
			statement.execute();
		} catch (SQLException | RuntimeException e) {
			this.log.warn("Unable to refresh [{}] for [{} .. {}]: {}", view, from, to, e.getMessage());
		}
	}

	/**
	 * Stops the scheduler. A pending window is dropped: it would be refreshed
	 * again on the next resend, and blocking shutdown on a multi-day refresh is
	 * worse than the stale tier.
	 */
	public synchronized void deactivate() {
		if (this.pendingRun != null) {
			this.pendingRun.cancel(false);
			this.pendingRun = null;
		}
		this.scheduler.shutdownNow();
	}
}
