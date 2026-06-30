package io.openems.shared.timescaledb;

import java.sql.SQLException;
import java.sql.Statement;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Handles the creation and initialization of the TimescaleDB database schema.
 *
 * <p>
 * This base class creates everything the Edge and Backend deployments share
 * (extensions, the {@code channel_def}/{@code channel} tables, hypertables,
 * compression, retention, continuous aggregates and their policies). The parts
 * that differ — the {@code edge} dimension and the {@code component} table, plus
 * the {@code get_or_create_channel_id} stored function — are deferred to
 * {@link #createDimensionTables} and {@link #createGetOrCreateFunction}, which
 * {@link EdgeSchemaHandler} and {@link BackendSchemaHandler} implement.
 */
public abstract class SchemaHandler {

	// Fast Lane (_core) continuous-aggregate retention horizons.
	public static final int AGG_1M_CORE_DAYS = 90;
	public static final int AGG_15M_CORE_DAYS = 365;
	public static final int AGG_1D_CORE_DAYS = 3650;

	private final HikariDataSource dataSource;
	private final int rawRetentionDays;
	private final int rawCompressionDays;
	private final boolean createMinutelyAggregate;

	/**
	 * Constructor.
	 *
	 * @param dataSource              The Hikari connection pool
	 * @param rawRetentionDays        Days to keep raw data before deletion
	 * @param rawCompressionDays      Days to wait before compressing raw data
	 * @param createMinutelyAggregate Whether to build the 1-minute Fast Lane
	 *                                aggregate (refreshes every 60s; disable on
	 *                                resource-limited Edge devices). When false,
	 *                                the 15-minute core aggregate reads raw data
	 *                                directly instead of cascading from 1m.
	 */
	protected SchemaHandler(HikariDataSource dataSource, int rawRetentionDays, int rawCompressionDays,
			boolean createMinutelyAggregate) {
		this.dataSource = dataSource;
		this.rawRetentionDays = rawRetentionDays;
		this.rawCompressionDays = rawCompressionDays;
		this.createMinutelyAggregate = createMinutelyAggregate;
	}

	/**
	 * Applies the TimescaleDB schema to the database.
	 * Creates dimension tables, hypertables, continuous aggregates, and policies if they do not exist.
	 *
	 * @throws SQLException on database error
	 */
	public void applySchema() throws SQLException {
		try (var con = this.dataSource.getConnection();
				var st = con.createStatement()) {

			// Activate the extensions this schema relies on. The extension binaries
			// must already be installed on the server
			st.execute("CREATE EXTENSION IF NOT EXISTS timescaledb");
			st.execute("CREATE EXTENSION IF NOT EXISTS pg_uuidv7");

			// Layer 1 — dimension tables.
			// The edge dimension and the component table differ per deployment.
			this.createDimensionTables(st);

			st.execute("""
					CREATE TABLE IF NOT EXISTS channel_def (
					    id          UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
					    name        VARCHAR NOT NULL UNIQUE,
					    type        VARCHAR(16) NOT NULL CHECK (type IN ('INTEGER','FLOAT','STRING')),
					    unit        VARCHAR(16),
					    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
					)""");

			// core: true = Fast Lane (VERY_HIGH channel), false = Slow Lane.
			st.execute("""
					CREATE TABLE IF NOT EXISTS channel (
					    id             UUID    DEFAULT uuid_generate_v7() PRIMARY KEY,
					    component_id   UUID    NOT NULL REFERENCES component(id) ON DELETE CASCADE,
					    channel_def_id UUID    NOT NULL REFERENCES channel_def(id) ON DELETE CASCADE,
					    core           BOOLEAN NOT NULL DEFAULT false,
					    first_seen     TIMESTAMPTZ NOT NULL DEFAULT now(),
					    UNIQUE (component_id, channel_def_id)
					)""");

			st.execute("CREATE INDEX IF NOT EXISTS idx_channel_component ON channel (component_id)");
			st.execute("CREATE INDEX IF NOT EXISTS idx_channel_def       ON channel (channel_def_id)");

			// Layer 2 — hypertables
			createHypertable(st, "data_integer", "BIGINT",           "1 day");
			createHypertable(st, "data_float",   "DOUBLE PRECISION", "1 day");
			createHypertable(st, "data_string",  "TEXT",             "7 days");

			// Compression
			for (var t : new String[] {
					"data_integer",
					"data_float",
					"data_string" }) {
				try {
					st.execute("ALTER TABLE " + t + " SET (" +
							"timescaledb.compress," +
							"timescaledb.compress_orderby='time DESC'," +
							"timescaledb.compress_segmentby='channel_id')");
				} catch (SQLException e) {
					// already configured - ignore
				}
			}

			// Compression policies
			addPolicyIfAbsent(st, "add_compression_policy", "data_integer", "INTERVAL '" + this.rawCompressionDays + " days'");
			addPolicyIfAbsent(st, "add_compression_policy", "data_float",   "INTERVAL '" + this.rawCompressionDays + " days'");
			addPolicyIfAbsent(st, "add_compression_policy", "data_string",  "INTERVAL '" + (this.rawCompressionDays * 2) + " days'");

			// Retention policies (raw)
			addPolicyIfAbsent(st, "add_retention_policy", "data_integer", "INTERVAL '" + this.rawRetentionDays + " days'");
			addPolicyIfAbsent(st, "add_retention_policy", "data_float",   "INTERVAL '" + this.rawRetentionDays + " days'");
			addPolicyIfAbsent(st, "add_retention_policy", "data_string",  "INTERVAL '" + this.rawRetentionDays + " days'");

			// Layer 3 — Continuous Aggregates.

			// Fast Lane (core = true)
			if (this.createMinutelyAggregate) {
				createAgg(st, "agg_1m_core_integer", "1 minute",  "data_integer", "BIGINT",           false, "WHERE core");
				createAgg(st, "agg_1m_core_float",   "1 minute",  "data_float",   "DOUBLE PRECISION", false, "WHERE core");
				createAgg(st, "agg_15m_core_integer","15 minutes","agg_1m_core_integer", "BIGINT",           true, null);
				createAgg(st, "agg_15m_core_float",  "15 minutes","agg_1m_core_float",   "DOUBLE PRECISION", true, null);
			} else {
				createAgg(st, "agg_15m_core_integer","15 minutes","data_integer", "BIGINT",           false, "WHERE core");
				createAgg(st, "agg_15m_core_float",  "15 minutes","data_float",   "DOUBLE PRECISION", false, "WHERE core");
			}
			createAgg(st, "agg_1d_core_integer", "1 day", "agg_15m_core_integer","BIGINT",           true, null);
			createAgg(st, "agg_1d_core_float",   "1 day", "agg_15m_core_float",  "DOUBLE PRECISION", true, null);

			// Slow Lane (all channels)
			createAgg(st, "agg_15m_integer", "15 minutes", "data_integer", "BIGINT",           false, null);
			createAgg(st, "agg_15m_float",   "15 minutes", "data_float",   "DOUBLE PRECISION", false, null);
			createAgg(st, "agg_1d_integer", "1 day", "agg_15m_integer", "BIGINT",           true, null);
			createAgg(st, "agg_1d_float",   "1 day", "agg_15m_float",   "DOUBLE PRECISION", true, null);

			// Chunk sizing
			if (this.createMinutelyAggregate) {
				setAggChunkInterval(st, "agg_1m_core_integer", "7 days");
				setAggChunkInterval(st, "agg_1m_core_float",   "7 days");
			}
			setAggChunkInterval(st, "agg_15m_core_integer", "30 days");
			setAggChunkInterval(st, "agg_15m_core_float",   "30 days");
			setAggChunkInterval(st, "agg_1d_core_integer",  "365 days");
			setAggChunkInterval(st, "agg_1d_core_float",    "365 days");
			setAggChunkInterval(st, "agg_15m_integer", "90 days");
			setAggChunkInterval(st, "agg_15m_float",   "90 days");
			setAggChunkInterval(st, "agg_1d_integer",  "365 days");
			setAggChunkInterval(st, "agg_1d_float",    "365 days");

			// Refresh policies (start_offset, end_offset, schedule_interval)
			if (this.createMinutelyAggregate) {
				addAggPolicyIfAbsent(st, "agg_1m_core_integer", "10 minutes", "1 minute", "1 minute");
				addAggPolicyIfAbsent(st, "agg_1m_core_float",   "10 minutes", "1 minute", "1 minute");
			}
			addAggPolicyIfAbsent(st, "agg_15m_core_integer","1 hour",     "15 minutes","15 minutes");
			addAggPolicyIfAbsent(st, "agg_15m_core_float",  "1 hour",     "15 minutes","15 minutes");
			addAggPolicyIfAbsent(st, "agg_1d_core_integer", "3 days",     "1 day",     "1 day");
			addAggPolicyIfAbsent(st, "agg_1d_core_float",   "3 days",     "1 day",     "1 day");
			addAggPolicyIfAbsent(st, "agg_15m_integer", "2 hours", "15 minutes", "1 hour");
			addAggPolicyIfAbsent(st, "agg_15m_float",   "2 hours", "15 minutes", "1 hour");
			addAggPolicyIfAbsent(st, "agg_1d_integer",  "3 days",  "1 day",      "1 day");
			addAggPolicyIfAbsent(st, "agg_1d_float",    "3 days",  "1 day",      "1 day");

			// Retention policies (Fast Lane only; Slow Lane kept forever)
			for (var t : new String[] { "integer", "float" }) {
				if (this.createMinutelyAggregate) {
					addPolicyIfAbsent(st, "add_retention_policy", "agg_1m_core_" + t,
							"INTERVAL '" + AGG_1M_CORE_DAYS + " days'");
				}
				addPolicyIfAbsent(st, "add_retention_policy", "agg_15m_core_" + t,
						"INTERVAL '" + AGG_15M_CORE_DAYS + " days'");
				addPolicyIfAbsent(st, "add_retention_policy", "agg_1d_core_" + t,
						"INTERVAL '" + AGG_1D_CORE_DAYS + " days'");
			}

			// Stored function for atomic channel registration (deployment-specific).
			this.createGetOrCreateFunction(st);
		}
	}

	/**
	 * Creates the dimension tables that differ between deployments.
	 *
	 * <p>
	 * The Backend creates an {@code edge} table plus an {@code edge_id} foreign key
	 * on {@code component} (so one database can hold many edges); the Edge creates
	 * the {@code component} table alone, keyed by component name.
	 *
	 * @param st an open JDBC {@link Statement}
	 * @throws SQLException on database error
	 */
	protected abstract void createDimensionTables(Statement st) throws SQLException;

	/**
	 * Creates the {@code get_or_create_channel_id} stored function used for atomic
	 * channel registration on the write path.
	 *
	 * <p>
	 * The Backend variant takes an edge name as its first argument and upserts the
	 * edge; the Edge variant omits it.
	 *
	 * @param st an open JDBC {@link Statement}
	 * @throws SQLException on database error
	 */
	protected abstract void createGetOrCreateFunction(Statement st) throws SQLException;

	private static void createHypertable(java.sql.Statement st, String table, String valueType, String chunkInterval)
			throws SQLException {
		var valueCol = valueType.equals("TEXT") ? "TEXT" : valueType;
		st.execute("CREATE TABLE IF NOT EXISTS " + table + " (" +
				"time       TIMESTAMPTZ NOT NULL," +
				"channel_id UUID        NOT NULL," +
				"core       BOOLEAN     NOT NULL," +
				"value " + valueCol + " NOT NULL)");
		st.execute("CREATE INDEX IF NOT EXISTS idx_" + table + "_channel ON " + table + " (channel_id, time DESC)");
		st.execute("SELECT create_hypertable('" + table + "','time'," +
				"chunk_time_interval => INTERVAL '" + chunkInterval + "',if_not_exists => TRUE)");
	}

	private static void createAgg(java.sql.Statement st, String viewName, String bucket,
			String source, String valueType, boolean cascaded, String whereClause) throws SQLException {
		try (var check = st.getConnection().prepareStatement(
				"SELECT 1 FROM timescaledb_information.continuous_aggregates WHERE view_name = ?")) {
			check.setString(1, viewName);
			try (var rs = check.executeQuery()) {
				if (rs.next()) {
					return;
				}
			}
		}
		try {
			if (!cascaded) {
				st.execute("""
						CREATE MATERIALIZED VIEW %s WITH (timescaledb.continuous) AS
						SELECT time_bucket('%s', time) AS bucket, channel_id,
						       MIN(value) AS min_val, MAX(value) AS max_val,
						       AVG(value) AS avg_val, last(value,time) AS last_val,
						       COUNT(*) AS sample_count
						FROM %s
						%s
						GROUP BY bucket, channel_id WITH NO DATA
						""".formatted(viewName, bucket, source, whereClause != null ? whereClause : ""));
			} else {
				st.execute("""
						CREATE MATERIALIZED VIEW %s WITH (timescaledb.continuous) AS
						SELECT time_bucket('%s', bucket) AS bucket, channel_id,
						       MIN(min_val) AS min_val, MAX(max_val) AS max_val,
						       AVG(avg_val) AS avg_val, last(last_val,bucket) AS last_val,
						       SUM(sample_count) AS sample_count
						FROM %s GROUP BY time_bucket('%s', bucket), channel_id WITH NO DATA
						""".formatted(viewName, bucket, source, bucket));
			}
		} catch (SQLException e) {
			// already exists — fine
			if (!e.getMessage().contains("already exists")) {
				throw e;
			}
		}
	}

	private static void addPolicyIfAbsent(java.sql.Statement st, String fn, String table, String interval)
			throws SQLException {
		try {
			st.execute("SELECT " + fn + "('" + table + "'," + interval + ",if_not_exists => TRUE)");
		} catch (SQLException e) {
			// policy already exists — ignore
		}
	}

	private static void addAggPolicyIfAbsent(java.sql.Statement st, String view,
			String startOffset, String endOffset, String scheduleInterval) throws SQLException {
		try {
			st.execute("SELECT add_continuous_aggregate_policy('" + view + "'," +
					"start_offset => INTERVAL '" + startOffset + "'," +
					"end_offset   => INTERVAL '" + endOffset + "'," +
					"schedule_interval => INTERVAL '" + scheduleInterval + "'," +
					"if_not_exists => TRUE)");
		} catch (SQLException e) {
			// policy already exists — ignore
		}
	}

	/**
	 * Sets chunk_time_interval on a continuous aggregate's materialization
	 * hypertable.
	 */
	private static void setAggChunkInterval(java.sql.Statement st, String view, String interval) throws SQLException {
		try {
			st.execute("SELECT set_chunk_time_interval(("
					+ " SELECT format('%I.%I', materialization_hypertable_schema, materialization_hypertable_name)"
					+ " FROM timescaledb_information.continuous_aggregates"
					+ " WHERE view_name = '" + view + "'"
					+ "), INTERVAL '" + interval + "')");
		} catch (SQLException e) {
			// view does not exist yet or interval already set — ignore
		}
	}
}
