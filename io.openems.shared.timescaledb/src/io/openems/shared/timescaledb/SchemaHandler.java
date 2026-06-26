package io.openems.shared.timescaledb;

import java.sql.SQLException;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Handles the creation and initialization of the TimescaleDB database schema.
 */
public class SchemaHandler {

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
	public SchemaHandler(HikariDataSource dataSource, int rawRetentionDays, int rawCompressionDays,
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

			st.execute("CREATE EXTENSION IF NOT EXISTS timescaledb");
			st.execute("CREATE EXTENSION IF NOT EXISTS tablefunc");
			st.execute("CREATE EXTENSION IF NOT EXISTS pg_uuidv7");

			// Layer 1 — dimension tables
			st.execute("""
					CREATE TABLE IF NOT EXISTS edge (
					    id         UUID        DEFAULT uuid_generate_v7() PRIMARY KEY,
					    name       VARCHAR NOT NULL UNIQUE,
					    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
					)""");

			st.execute("""
					CREATE TABLE IF NOT EXISTS component (
					    id         UUID        DEFAULT uuid_generate_v7() PRIMARY KEY,
					    edge_id    UUID        NOT NULL REFERENCES edge(id) ON DELETE CASCADE,
					    name       VARCHAR NOT NULL,
					    type       VARCHAR,
					    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
					    UNIQUE (edge_id, name)
					)""");

			st.execute("CREATE INDEX IF NOT EXISTS idx_component_edge_id ON component (edge_id)");

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
					addPolicyIfAbsent(st, "add_retention_policy", "agg_1m_core_" + t, "INTERVAL '90 days'");
				}
				addPolicyIfAbsent(st, "add_retention_policy", "agg_15m_core_" + t, "INTERVAL '1 year'");
				addPolicyIfAbsent(st, "add_retention_policy", "agg_1d_core_" + t,  "INTERVAL '10 years'");
			}

			// Stored function for atomic channel registration.
			st.execute("""
					DROP FUNCTION IF EXISTS get_or_create_channel_id(VARCHAR, VARCHAR, VARCHAR, VARCHAR, VARCHAR, BOOLEAN, VARCHAR);
					CREATE OR REPLACE FUNCTION get_or_create_channel_id(
					    p_edge_name       VARCHAR,
					    p_component_name  VARCHAR,
					    p_component_type  VARCHAR,
					    p_channel_name    VARCHAR,
					    p_type            VARCHAR,
					    p_core            BOOLEAN DEFAULT false,
					    p_unit            VARCHAR DEFAULT NULL
					)
					RETURNS TABLE (out_channel_id UUID, out_type VARCHAR, out_core BOOLEAN)
					LANGUAGE plpgsql AS $$
					DECLARE
					    v_edge_id        UUID;
					    v_component_id   UUID;
					    v_component_type VARCHAR;
					    v_channel_def_id UUID;
					    v_type           VARCHAR;
					    v_unit           VARCHAR;
					    v_channel_id     UUID;
					    v_core           BOOLEAN;
					BEGIN
					    SELECT id INTO v_edge_id FROM edge WHERE name = p_edge_name;
					    IF v_edge_id IS NULL THEN
					        INSERT INTO edge (name) VALUES (p_edge_name)
					            ON CONFLICT (name) DO NOTHING
					            RETURNING id INTO v_edge_id;
					        IF v_edge_id IS NULL THEN
					            SELECT id INTO v_edge_id FROM edge WHERE name = p_edge_name;
					        END IF;
					    END IF;

					    SELECT id, type INTO v_component_id, v_component_type FROM component
					        WHERE edge_id = v_edge_id AND name = p_component_name;
					    IF v_component_id IS NULL THEN
					        INSERT INTO component (edge_id, name, type)
					            VALUES (v_edge_id, p_component_name, p_component_type)
					            ON CONFLICT (edge_id, name) DO NOTHING
					            RETURNING id INTO v_component_id;
					        IF v_component_id IS NULL THEN
					            SELECT id, type INTO v_component_id, v_component_type FROM component
					                WHERE edge_id = v_edge_id AND name = p_component_name;
					        END IF;
					    ELSIF v_component_type IS DISTINCT FROM p_component_type AND p_component_type <> 'backend' THEN
					        UPDATE component SET type = p_component_type WHERE id = v_component_id;
					    END IF;

					    SELECT id, type, unit INTO v_channel_def_id, v_type, v_unit FROM channel_def
					        WHERE name = p_channel_name;
					    IF v_channel_def_id IS NULL THEN
					        INSERT INTO channel_def (name, type, unit) VALUES (p_channel_name, p_type, p_unit)
					            ON CONFLICT (name) DO NOTHING
					            RETURNING id, type INTO v_channel_def_id, v_type;
					        IF v_channel_def_id IS NULL THEN
					            SELECT id, type, unit INTO v_channel_def_id, v_type, v_unit FROM channel_def
					                WHERE name = p_channel_name;
					        END IF;
					    ELSIF p_unit IS NOT NULL AND v_unit IS DISTINCT FROM p_unit THEN
					        UPDATE channel_def SET unit = p_unit WHERE id = v_channel_def_id;
					    END IF;

					    IF v_type IS DISTINCT FROM p_type THEN
					        RAISE WARNING 'channel_def % type mismatch: stored=%, incoming=% (keeping stored)',
					            p_channel_name, v_type, p_type;
					    END IF;

					    SELECT id, core INTO v_channel_id, v_core FROM channel
					        WHERE component_id = v_component_id AND channel_def_id = v_channel_def_id;
					    IF v_channel_id IS NULL THEN
					        INSERT INTO channel (component_id, channel_def_id, core)
					            VALUES (v_component_id, v_channel_def_id, p_core)
					            ON CONFLICT (component_id, channel_def_id) DO NOTHING
					            RETURNING id, core INTO v_channel_id, v_core;
					        IF v_channel_id IS NULL THEN
					            SELECT id, core INTO v_channel_id, v_core FROM channel
					                WHERE component_id = v_component_id AND channel_def_id = v_channel_def_id;
					        END IF;
					    END IF;

					    IF p_core AND NOT v_core THEN
					        UPDATE channel SET core = true WHERE id = v_channel_id;
					        v_core := true;
					    END IF;

					    RETURN QUERY SELECT v_channel_id, v_type, v_core;
					END;
					$$""");
		}
	}

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
