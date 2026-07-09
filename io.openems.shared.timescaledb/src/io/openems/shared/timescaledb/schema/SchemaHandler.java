package io.openems.shared.timescaledb.schema;

import java.sql.SQLException;
import java.sql.Statement;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Handles the creation and initialization of the TimescaleDB database schema.
 *
 * <p>
 * The single-tenant and multi-tenant variants share almost everything
 * (extensions, the {@code channel_def}/{@code channel} tables, hypertables,
 * compression, retention, continuous aggregates and their policies). The parts
 * that differ — the {@code edge} dimension on the {@code component} table and
 * the edge handling in the {@code get_or_create_*} stored functions — are pure
 * data variance, so the SQL is composed dynamically from the {@link Tenancy}
 * instead of subclasses (same concept as {@link ChannelManager}).
 */
public class SchemaHandler {

	// Fast Lane (_rollup) continuous-aggregate retention horizons.
	public static final int RETENTION_1M_DAYS = 90;
	public static final int RETENTION_15M_DAYS = 365;
	public static final int RETENTION_1D = 3650;

	private final Tenancy tenancy;
	private final HikariDataSource dataSource;
	private final int rawRetentionDays;
	private final int rawCompressionDays;

	/**
	 * Constructor.
	 *
	 * @param tenancy            Whether the database holds one or many edges
	 * @param dataSource         The Hikari connection pool
	 * @param rawRetentionDays   Days to keep raw data before deletion
	 * @param rawCompressionDays Days to wait before compressing raw data
	 */
	public SchemaHandler(Tenancy tenancy, HikariDataSource dataSource, int rawRetentionDays, int rawCompressionDays) {
		this.tenancy = tenancy;
		this.dataSource = dataSource;
		this.rawRetentionDays = rawRetentionDays;
		this.rawCompressionDays = rawCompressionDays;
	}

	/**
	 * Applies the TimescaleDB schema to the database.
	 * Creates dimension tables, hypertables, continuous aggregates, and policies if they do not exist.
	 *
	 * @throws SQLException on database error
	 */
	public void applySchema() throws SQLException {
		try (var connection = this.dataSource.getConnection();
				var statement = connection.createStatement()) {

			// Activate the extensions this schema relies on. The extension binaries
			// must already be installed on the server
			statement.execute("CREATE EXTENSION IF NOT EXISTS timescaledb");
			statement.execute("CREATE EXTENSION IF NOT EXISTS pg_uuidv7");

			// Layer 1: dimension tables
			if (this.tenancy == Tenancy.MULTI) {
				this.createEdgeTable(statement);
			}
			this.createComponentTable(statement);
			this.createChannelDefinitionTable(statement);
			this.createChannelTable(statement);

			// Layer 2: Hypertables
			createHypertable(statement, "data_integer", "BIGINT",           "1 day");
			createHypertable(statement, "data_float",   "DOUBLE PRECISION", "1 day");
			createHypertable(statement, "data_string",  "TEXT",             "7 days");

			// Compression
			for (var table : new String[] {
					"data_integer",
					"data_float",
					"data_string" }) {
				try {
					statement.execute(new StringBuilder()
							.append("ALTER TABLE ").append(table).append(" SET (")
							.append("timescaledb.compress,")
							.append("timescaledb.compress_orderby='time DESC',")
							.append("timescaledb.compress_segmentby='channel_id')")
							.toString());
				} catch (SQLException e) {
					// Already configured
				}
			}

			// Compression policies
			addPolicyIfAbsent(statement, "add_compression_policy", "data_integer", "INTERVAL '" + this.rawCompressionDays + " days'");
			addPolicyIfAbsent(statement, "add_compression_policy", "data_float",   "INTERVAL '" + this.rawCompressionDays + " days'");
			addPolicyIfAbsent(statement, "add_compression_policy", "data_string",  "INTERVAL '" + (this.rawCompressionDays * 2) + " days'");

			// Retention policies (raw)
			addPolicyIfAbsent(statement, "add_retention_policy", "data_integer", "INTERVAL '" + this.rawRetentionDays + " days'");
			addPolicyIfAbsent(statement, "add_retention_policy", "data_float",   "INTERVAL '" + this.rawRetentionDays + " days'");
			addPolicyIfAbsent(statement, "add_retention_policy", "data_string",  "INTERVAL '" + this.rawRetentionDays + " days'");

			// Layer 3: Continuous Aggregates.

			// Fast Lane (rollup = true)
			createAgg(statement, "data_1m_rollup_integer",  "1 minute",  "data_integer",           "BIGINT",           false, "WHERE rollup");
			createAgg(statement, "data_1m_rollup_float",    "1 minute",  "data_float",             "DOUBLE PRECISION", false, "WHERE rollup");
			createAgg(statement, "data_15m_rollup_integer", "15 minutes","data_1m_rollup_integer", "BIGINT",           true, null);
			createAgg(statement, "data_15m_rollup_float",   "15 minutes","data_1m_rollup_float",   "DOUBLE PRECISION", true, null);
			createAgg(statement, "data_1d_rollup_integer",  "1 day", "data_15m_rollup_integer",    "BIGINT",           true, null);
			createAgg(statement, "data_1d_rollup_float",    "1 day", "data_15m_rollup_float",      "DOUBLE PRECISION", true, null);

			// Slow Lane (all channels)
			createAgg(statement, "data_15m_integer", "15 minutes", "data_integer",     "BIGINT",           false, null);
			createAgg(statement, "data_15m_float",   "15 minutes", "data_float",       "DOUBLE PRECISION", false, null);
			createAgg(statement, "data_1d_integer",  "1 day",      "data_15m_integer", "BIGINT",           true, null);
			createAgg(statement, "data_1d_float",    "1 day",      "data_15m_float",   "DOUBLE PRECISION", true, null);

			// Chunk sizing
			setAggChunkInterval(statement, "data_1m_rollup_integer", "7 days");
			setAggChunkInterval(statement, "data_1m_rollup_float",   "7 days");
			setAggChunkInterval(statement, "data_15m_rollup_integer", "30 days");
			setAggChunkInterval(statement, "data_15m_rollup_float",   "30 days");
			setAggChunkInterval(statement, "data_1d_rollup_integer",  "365 days");
			setAggChunkInterval(statement, "data_1d_rollup_float",    "365 days");
			setAggChunkInterval(statement, "data_15m_integer",        "90 days");
			setAggChunkInterval(statement, "data_15m_float",          "90 days");
			setAggChunkInterval(statement, "data_1d_integer",         "365 days");
			setAggChunkInterval(statement, "data_1d_float",           "365 days");

			// Refresh policies (start_offset, end_offset, schedule_interval)
			addAggPolicyIfAbsent(statement, "data_1m_rollup_integer", "10 minutes", "1 minute", "1 minute");
			addAggPolicyIfAbsent(statement, "data_1m_rollup_float",   "10 minutes", "1 minute", "1 minute");
			addAggPolicyIfAbsent(statement, "data_15m_rollup_integer", "1 hour",  "15 minutes", "15 minutes");
			addAggPolicyIfAbsent(statement, "data_15m_rollup_float",   "1 hour",  "15 minutes", "15 minutes");
			addAggPolicyIfAbsent(statement, "data_1d_rollup_integer",  "3 days",  "1 day",      "1 day");
			addAggPolicyIfAbsent(statement, "data_1d_rollup_float",    "3 days",  "1 day",      "1 day");
			addAggPolicyIfAbsent(statement, "data_15m_integer",        "2 hours", "15 minutes", "1 hour");
			addAggPolicyIfAbsent(statement, "data_15m_float",          "2 hours", "15 minutes", "1 hour");
			addAggPolicyIfAbsent(statement, "data_1d_integer",         "3 days",  "1 day",      "1 day");
			addAggPolicyIfAbsent(statement, "data_1d_float",           "3 days",  "1 day",      "1 day");

			// Retention policies (Fast Lane only; Slow Lane kept forever)
			for (var t : new String[] { "integer", "float" }) {
				addPolicyIfAbsent(statement, "add_retention_policy", "data_1m_rollup_" + t,
						"INTERVAL '" + RETENTION_1M_DAYS + " days'");
				addPolicyIfAbsent(statement, "add_retention_policy", "data_15m_rollup_" + t,
						"INTERVAL '" + RETENTION_15M_DAYS + " days'");
				addPolicyIfAbsent(statement, "add_retention_policy", "data_1d_rollup_" + t,
						"INTERVAL '" + RETENTION_1D + " days'");
			}

			// Stored functions for atomic channel registration.
			this.createGetOrCreateFunctions(statement);
		}
	}

	private void createEdgeTable(Statement statement) throws SQLException {
		statement.execute("""
				CREATE TABLE IF NOT EXISTS edge (
				id         UUID        DEFAULT uuid_generate_v7() PRIMARY KEY,
				name       VARCHAR NOT NULL UNIQUE,
				created_at TIMESTAMPTZ NOT NULL DEFAULT now()
				)"""
		);
	}

	private void createComponentTable(Statement statement) throws SQLException {
		StringBuilder query = new StringBuilder()
				.append("CREATE TABLE IF NOT EXISTS component (")
				.append("id UUID DEFAULT uuid_generate_v7() PRIMARY KEY, ");
		if (this.tenancy == Tenancy.MULTI) {
			query.append("edge_id UUID NOT NULL REFERENCES edge(id) ON DELETE CASCADE, ")
					.append("name VARCHAR NOT NULL, ");
		} else {
			query.append("name VARCHAR NOT NULL UNIQUE, ");
		}
		query.append("type VARCHAR, ")
				.append("created_at TIMESTAMPTZ NOT NULL DEFAULT now()");
		if (this.tenancy == Tenancy.MULTI) {
			query.append(", UNIQUE (edge_id, name)");
		}
		query.append(")");
		statement.execute(query.toString());

		if (this.tenancy == Tenancy.MULTI) {
			statement.execute("CREATE INDEX IF NOT EXISTS idx_component_edge_id ON component(edge_id)");
		}
	}

	private void createChannelDefinitionTable(Statement statement) throws SQLException {
		statement.execute("""
				CREATE TABLE IF NOT EXISTS channel_def (
					id          UUID DEFAULT uuid_generate_v7() PRIMARY KEY,
					name        VARCHAR NOT NULL UNIQUE,
					type        VARCHAR(16) NOT NULL CHECK (type IN ('INTEGER','FLOAT','STRING')),
					unit        VARCHAR(16),
					created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
				)"""
		);
	}

	private void createChannelTable(Statement statement) throws SQLException {
		// Rollup: true = Fast Lane, false = Slow Lane.
		statement.execute("""
				CREATE TABLE IF NOT EXISTS channel (
					id             UUID    DEFAULT uuid_generate_v7() PRIMARY KEY,
					component_id   UUID    NOT NULL REFERENCES component(id) ON DELETE CASCADE,
					channel_def_id UUID    NOT NULL REFERENCES channel_def(id) ON DELETE CASCADE,
					rollup         BOOLEAN NOT NULL DEFAULT false,
					first_seen     TIMESTAMPTZ NOT NULL DEFAULT now(),
					UNIQUE (component_id, channel_def_id)
				)"""
		);
		statement.execute("CREATE INDEX IF NOT EXISTS idx_channel_component ON channel(component_id)");
		statement.execute("CREATE INDEX IF NOT EXISTS idx_channel_def       ON channel(channel_def_id)");
	}

	/**
	 * Creates the {@code get_or_create_*} stored functions used for atomic
	 * channel registration on the write path.
	 *
	 * <p>
	 * {@code get_or_create_channel_def} and {@code get_or_create_channel} are
	 * tenancy-independent. The multi-tenant variant additionally creates
	 * {@code get_or_create_edge}, and {@code get_or_create_component} plus the
	 * {@code get_or_create_channel_id} conductor take the edge as extra first
	 * argument.
	 *
	 * @param statement an open JDBC {@link Statement}
	 * @throws SQLException on database error
	 */
	private void createGetOrCreateFunctions(Statement statement) throws SQLException {
		var multi = this.tenancy == Tenancy.MULTI;

		// Helper: Edge (multi-tenant only)
		if (multi) {
			statement.execute("""
					CREATE OR REPLACE FUNCTION get_or_create_edge(
					    input_edge_name VARCHAR,
					    OUT out_id UUID
					)
					LANGUAGE plpgsql AS $$
					BEGIN
					    SELECT id INTO out_id FROM edge WHERE name = input_edge_name;
					    IF out_id IS NULL THEN
					        INSERT INTO edge (name) VALUES (input_edge_name)
					            ON CONFLICT (name) DO NOTHING
					            RETURNING id INTO out_id;
					        IF out_id IS NULL THEN
					            SELECT id INTO out_id FROM edge WHERE name = input_edge_name;
					        END IF;
					    END IF;
					END;
					$$"""
			);
		}

		var componentWhere = "WHERE name = input_component_name";
		if (multi) {
			componentWhere += " AND edge_id = input_edge_id";
		}

		// Helper: Component. Multi-tenant scopes every lookup/insert by edge_id.
		StringBuilder componentQuery = new StringBuilder()
				.append("CREATE OR REPLACE FUNCTION get_or_create_component(");
		if (multi) {
			componentQuery.append("input_edge_id UUID, ");
		}
		componentQuery
				.append("input_component_name VARCHAR, ")
				.append("input_component_type VARCHAR, ")
				.append("OUT out_id UUID) ")
				.append("LANGUAGE plpgsql AS $$ ")
				.append("DECLARE ").append("found_type VARCHAR; ")
				.append("BEGIN ").append("SELECT id, type INTO out_id, found_type FROM component ")
				.append(componentWhere).append("; ")
				.append("IF out_id IS NULL THEN ");
		if (multi) {
			componentQuery
					.append("INSERT INTO component (edge_id, name, type) ")
					.append("VALUES (input_edge_id, input_component_name, input_component_type) ")
					.append("ON CONFLICT (edge_id, name) DO NOTHING ");
		} else {
			componentQuery
					.append("INSERT INTO component (name, type) ")
					.append("VALUES (input_component_name, input_component_type) ")
					.append("ON CONFLICT (name) DO NOTHING ");
		}
		componentQuery.append("RETURNING id INTO out_id; ")
				.append("IF out_id IS NULL THEN ")
				.append("SELECT id, type INTO out_id, found_type FROM component ")
				.append(componentWhere).append("; ")
				.append("END IF; ")
				.append("ELSIF found_type IS DISTINCT FROM input_component_type AND input_component_type <> 'backend' THEN ")
				.append("UPDATE component SET type = input_component_type WHERE id = out_id; ")
				.append("END IF; ")
				.append("END; ")
				.append("$$");
		statement.execute(componentQuery.toString());

		// Helper: Channel Def (tenancy-independent)
		statement.execute("""
				CREATE OR REPLACE FUNCTION get_or_create_channel_def(
				    input_channel_name VARCHAR,
				    input_type VARCHAR,
				    input_unit VARCHAR,
				    OUT out_id UUID,
				    OUT out_type VARCHAR
				)
				LANGUAGE plpgsql AS $$
				DECLARE
				    found_unit VARCHAR;
				BEGIN
				    SELECT id, type, unit INTO out_id, out_type, found_unit FROM channel_def
				        WHERE name = input_channel_name;
				    IF out_id IS NULL THEN
				        INSERT INTO channel_def (name, type, unit) VALUES (input_channel_name, input_type, input_unit)
				            ON CONFLICT (name) DO NOTHING
				            RETURNING id, type INTO out_id, out_type;
				        IF out_id IS NULL THEN
				            SELECT id, type, unit INTO out_id, out_type, found_unit FROM channel_def
				                WHERE name = input_channel_name;
				        END IF;
				    ELSIF input_unit IS NOT NULL AND found_unit IS DISTINCT FROM input_unit THEN
				        UPDATE channel_def SET unit = input_unit WHERE id = out_id;
				    END IF;
				END;
				$$"""
		);

		// Helper: Channel (tenancy-independent)

		statement.execute("""
				CREATE OR REPLACE FUNCTION get_or_create_channel(
				    input_component_id UUID,
				    input_channel_def_id UUID,
				    input_rollup BOOLEAN,
				    OUT out_id UUID,
				    OUT out_rollup BOOLEAN
				)
				LANGUAGE plpgsql AS $$
				BEGIN
				    SELECT id, rollup INTO out_id, out_rollup FROM channel
				        WHERE component_id = input_component_id AND channel_def_id = input_channel_def_id;
				    IF out_id IS NULL THEN
				        INSERT INTO channel (component_id, channel_def_id, rollup)
				            VALUES (input_component_id, input_channel_def_id, input_rollup)
				            ON CONFLICT (component_id, channel_def_id) DO NOTHING
				            RETURNING id, rollup INTO out_id, out_rollup;
				        IF out_id IS NULL THEN
				            SELECT id, rollup INTO out_id, out_rollup FROM channel
				                WHERE component_id = input_component_id AND channel_def_id = input_channel_def_id;
				        END IF;
				    END IF;
				    IF input_rollup AND NOT out_rollup THEN
				        UPDATE channel SET rollup = true WHERE id = out_id;
				        out_rollup := true;
				    END IF;
				END;
				$$""");

		// Conductor Function
		StringBuilder conductorQuery = new StringBuilder()
				.append("CREATE OR REPLACE FUNCTION get_or_create_channel_id")
				.append("(");
		if (multi) {
			conductorQuery.append("input_edge_name VARCHAR, ");
		}
		conductorQuery
				.append("input_component_name VARCHAR, ")
				.append("input_component_type VARCHAR, ")
				.append("input_channel_name VARCHAR, ")
				.append("input_type VARCHAR, ")
				.append("input_rollup BOOLEAN DEFAULT false, ")
				.append("input_unit VARCHAR DEFAULT NULL ")
				.append(") ")
				.append("RETURNS TABLE (out_channel_id UUID, out_type VARCHAR, out_rollup BOOLEAN) ")
				.append("LANGUAGE plpgsql AS $$ ")
				.append("DECLARE ");
		if (multi) {
			conductorQuery.append("    local_edge_id        UUID;\n");
		}
		conductorQuery
				.append("local_component_id   UUID; ")
				.append("local_channel_def_id UUID; ")
				.append("local_type VARCHAR; ")
				.append("local_channel_id UUID; ")
				.append("local_rollup BOOLEAN; ")
				.append("BEGIN ");
		if (multi) {
			conductorQuery
					.append("SELECT out_id INTO local_edge_id FROM get_or_create_edge(input_edge_name); ")
					.append("SELECT out_id INTO local_component_id FROM get_or_create_component(")
					.append("local_edge_id, input_component_name, input_component_type); ");
		} else {
			conductorQuery
					.append("SELECT out_id INTO local_component_id FROM get_or_create_component(")
					.append("input_component_name, input_component_type); ");
		}
		conductorQuery.append("SELECT def_id, def_type INTO local_channel_def_id, local_type ")
				.append("FROM get_or_create_channel_def(input_channel_name, input_type, input_unit) ")
				.append("AS def(def_id, def_type); ")
				.append("IF local_type IS DISTINCT FROM input_type THEN ")
				.append("RAISE WARNING 'channel_def % type mismatch: stored=%, incoming=% (keeping stored)', ")
				.append("input_channel_name, local_type, input_type; ")
				.append("END IF; ")
				.append("SELECT ch_id, ch_rollup INTO local_channel_id, local_rollup ")
				.append("FROM get_or_create_channel(local_component_id, local_channel_def_id, input_rollup) ")
				.append("AS ch(ch_id, ch_rollup); ")
				.append("RETURN QUERY SELECT local_channel_id, local_type, local_rollup; ")
				.append("END; ")
				.append("$$ ");
		statement.execute(conductorQuery.toString());
	}

	private static void createHypertable(Statement st, String table, String valueType, String chunkInterval)
			throws SQLException {
		st.execute(new StringBuilder()
				.append("CREATE TABLE IF NOT EXISTS ").append(table).append(" (")
				.append("time TIMESTAMPTZ NOT NULL, ")
				.append("channel_id UUID NOT NULL, ")
				.append("rollup BOOLEAN NOT NULL, ")
				.append("value ").append(valueType).append(" NOT NULL)")
				.toString());
		st.execute(new StringBuilder()
				.append("CREATE INDEX IF NOT EXISTS idx_").append(table).append("_channel ")
				.append("ON ").append(table).append(" (channel_id, time DESC)")
				.toString());
		st.execute(new StringBuilder()
				.append("SELECT create_hypertable('").append(table).append("','time',")
				.append("chunk_time_interval => INTERVAL '").append(chunkInterval).append("',if_not_exists => TRUE)")
				.toString());
	}

	private static void createAgg(Statement st, String viewName, String bucket,
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
			// Already exists
			if (!e.getMessage().contains("already exists")) {
				throw e;
			}
		}
	}

	private static void addPolicyIfAbsent(Statement st, String fn, String table, String interval)
			throws SQLException {
		try {
			st.execute(new StringBuilder()
					.append("SELECT ").append(fn)
					.append("('").append(table).append("',").append(interval).append(",if_not_exists => TRUE)")
					.toString());
		} catch (SQLException e) {
			// Policy already exists
		}
	}

	private static void addAggPolicyIfAbsent(Statement st, String view,
			String startOffset, String endOffset, String scheduleInterval) throws SQLException {
		try {
			st.execute(new StringBuilder()
					.append("SELECT add_continuous_aggregate_policy('").append(view).append("', ")
					.append("start_offset => INTERVAL '").append(startOffset).append("', ")
					.append("end_offset   => INTERVAL '").append(endOffset).append("', ")
					.append("schedule_interval => INTERVAL '").append(scheduleInterval).append("', ")
					.append("if_not_exists => TRUE)")
					.toString());
		} catch (SQLException e) {
			// Policy already exists
		}
	}

	/**
	 * Sets chunk_time_interval on a continuous aggregate's materialization
	 * hypertable.
	 */
	private static void setAggChunkInterval(Statement st, String view, String interval) throws SQLException {
		try {
			st.execute(new StringBuilder()
					.append("SELECT set_chunk_time_interval((")
					.append("SELECT format('%I.%I', materialization_hypertable_schema, materialization_hypertable_name) ")
					.append("FROM timescaledb_information.continuous_aggregates ")
					.append("WHERE view_name = '").append(view).append("'").append("), ")
					.append("INTERVAL '").append(interval).append("')")
					.toString());
		} catch (SQLException e) {
			// View does not exist yet or interval already set
		}
	}
}
