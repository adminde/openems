package io.openems.shared.timescaledb.schema;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.zaxxer.hikari.HikariDataSource;

import io.openems.shared.timescaledb.Type;

/**
 * Handles the creation and initialization of the TimescaleDB database schema.
 *
 * <p>
 * The single-tenant and multi-tenant variants share almost everything
 * (extensions, the {@code component_def}/{@code channel_def}/{@code channel}
 * tables, hypertables, compression, retention, continuous aggregates and their
 * policies). The parts that differ — the {@code edge} dimension on the
 * {@code component} table and the edge handling in the
 * {@code get_or_create_*} stored functions — are pure data variance, so the SQL
 * is composed dynamically from the {@link Tenancy} instead of subclasses (same
 * concept as {@link ChannelManager}).
 *
 * <p>
 * The continuous-aggregate tiers are built from the {@code List<Aggregate>}
 * passed in, sorted finest→coarsest, each tier reading from the raw hypertables
 * ({@code WHERE aggregate}) or cascading from a finer tier.
 * See {@link Aggregate}.
 */
public class SchemaHandler {

	private final Tenancy tenancy;
	private final HikariDataSource dataSource;
	private final int rawRetentionDays;
	private final int rawCompressionDays;
	private final List<Aggregate> aggregates;

	/**
	 * Constructor.
	 *
	 * @param tenancy            Whether the database holds one or many edges
	 * @param dataSource         The Hikari connection pool
	 * @param rawRetentionDays   Days to keep raw data before deletion
	 * @param rawCompressionDays Days to wait before compressing raw data
	 * @param aggregates         The continuous-aggregate tiers to build
	 */
	public SchemaHandler(Tenancy tenancy, HikariDataSource dataSource, int rawRetentionDays, int rawCompressionDays,
			List<Aggregate> aggregates) {
		this.tenancy = tenancy;
		this.dataSource = dataSource;
		this.rawRetentionDays = rawRetentionDays;
		this.rawCompressionDays = rawCompressionDays;
		this.aggregates = aggregates;
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

			// PostgreSQL 18 ships a native uuidv7() function. Older servers rely on
			// the pg_uuidv7 extension, which supplies uuid_generate_v7().
			var majorVersion = connection.getMetaData().getDatabaseMajorVersion();
			var uuidDefault = majorVersion >= 18 ? "uuidv7()" : "uuid_generate_v7()";

			// Activate the extensions this schema relies on. The extension binaries
			// must already be installed on the server
			statement.execute("CREATE EXTENSION IF NOT EXISTS timescaledb");
			if (majorVersion < 18) {
				statement.execute("CREATE EXTENSION IF NOT EXISTS pg_uuidv7");
			}

			// Layer 1: dimension tables
			if (this.tenancy == Tenancy.MULTI) {
				this.createEdgeTable(statement, uuidDefault);
			}
			this.createComponentDefinitionTable(statement, uuidDefault);
			this.createComponentTable(statement, uuidDefault);
			this.createChannelDefinitionTable(statement, uuidDefault);
			this.createChannelTable(statement, uuidDefault);

			// Layer 2: Hypertables
			createHypertable(statement, "data_integer", "BIGINT",           "1 day");
			createHypertable(statement, "data_float",   "DOUBLE PRECISION", "1 day");
			createHypertable(statement, "data_string",  "TEXT",             "7 days");

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

			// Retention policies (raw). Raw is the long-lived source of truth; the
			// aggregate tiers below are a pure query-acceleration layer on top.
			// rawRetentionDays <= 0 keeps raw forever (no retention policy created) —
			// the Backend default. Compression above still applies regardless.
			// Note: on an existing database this only avoids *creating* a policy; it
			// does not drop one a previous deployment already installed.
			if (this.rawRetentionDays > 0) {
				addPolicyIfAbsent(statement, "add_retention_policy", "data_integer", "INTERVAL '" + this.rawRetentionDays + " days'");
				addPolicyIfAbsent(statement, "add_retention_policy", "data_float",   "INTERVAL '" + this.rawRetentionDays + " days'");
				addPolicyIfAbsent(statement, "add_retention_policy", "data_string",  "INTERVAL '" + this.rawRetentionDays + " days'");
			}

			// Layer 3: Continuous Aggregates, built from the configured tiers.
			this.createAggregates(statement);

			// Stored functions for atomic channel registration.
			this.createGetOrCreateFunctions(statement);
		}
	}

	/**
	 * Builds the continuous-aggregate tiers from {@link #aggregates}: sorts them
	 * finest→coarsest, resolves each tier's source (an explicit finer tier by
	 * name, else the immediate finer predecessor, else the raw hypertable for the
	 * finest tier), and creates the materialized view, chunk sizing, refresh
	 * policy and — unless kept forever — the retention policy, for both the
	 * INTEGER and FLOAT value types. Strings are never aggregated.
	 *
	 * @param statement an open JDBC {@link Statement}
	 * @throws SQLException on database error, or when a tier's source cannot be
	 *                      resolved / does not evenly divide its bucket
	 */
	private void createAggregates(Statement statement) throws SQLException {
		var sorted = new ArrayList<>(this.aggregates);
		sorted.sort(Comparator.comparingLong(a -> a.bucket().getSeconds()));

		for (var i = 0; i < sorted.size(); i++) {
			var agg = sorted.get(i);

			// Resolve the source: explicit reference, else the immediate finer
			// predecessor, else RAW (the finest tier).
			Aggregate source = null;
			if (agg.source() != null) {
				for (var candidate : sorted) {
					if (candidate.name().equals(agg.source())) {
						source = candidate;
						break;
					}
				}
				if (source == null) {
					throw new SQLException("Aggregate '" + agg.name() + "' references unknown source '"
							+ agg.source() + "'");
				}
			} else if (i > 0) {
				source = sorted.get(i - 1);
			}

			// A cascaded source must be strictly finer and evenly divide this bucket,
			// or the time_bucket re-aggregation would drift.
			if (source != null) {
				var src = source.bucket().getSeconds();
				var dst = agg.bucket().getSeconds();
				if (src >= dst || dst % src != 0) {
					throw new SQLException("Aggregate '" + agg.name() + "' bucket (" + dst
							+ "s) is not an integer multiple of its source '" + source.name() + "' (" + src + "s)");
				}
			}

			var bucket = toInterval(agg.bucket());
			var refresh = agg.resolvedRefresh();
			var chunk = toInterval(agg.resolvedChunk());
			var cascaded = source != null;

			for (var type : new Type[] { Type.INTEGER, Type.FLOAT }) {
				var view = "data_" + agg.name() + "_" + type.aggInfix;
				var sourceTable = cascaded
						? "data_" + source.name() + "_" + type.aggInfix
						: type.rawTableName;

				createAggregate(statement, view, bucket, sourceTable, cascaded, cascaded ? null : "WHERE aggregate");
				setAggChunkInterval(statement, view, chunk);
				addAggPolicyIfAbsent(statement, view,
						toInterval(refresh.startOffset()), toInterval(refresh.endOffset()),
						toInterval(refresh.schedule()));
				if (agg.retentionDays() > 0) {
					addPolicyIfAbsent(statement, "add_retention_policy", view,
							"INTERVAL '" + agg.retentionDays() + " days'");
				}
			}
		}
	}

	private void createEdgeTable(Statement statement, String uuidDefault) throws SQLException {
		statement.execute("""
				CREATE TABLE IF NOT EXISTS edge (
				id         UUID        DEFAULT %s PRIMARY KEY,
				name       VARCHAR NOT NULL UNIQUE,
				created_at TIMESTAMPTZ NOT NULL DEFAULT now()
				)""".formatted(uuidDefault)
		);
	}

	private void createComponentDefinitionTable(Statement statement, String uuidDefault) throws SQLException {
		// The component nature (factory PID). Scoping channel_def by it is what
		// keeps two factories that both expose e.g. "ActivePower" from sharing one
		// value type and unit.
		statement.execute("""
				CREATE TABLE IF NOT EXISTS component_def (
					id         UUID DEFAULT %s PRIMARY KEY,
					type       VARCHAR NOT NULL UNIQUE,
					created_at TIMESTAMPTZ NOT NULL DEFAULT now()
				)""".formatted(uuidDefault)
		);
	}

	private void createComponentTable(Statement statement, String uuidDefault) throws SQLException {
		StringBuilder query = new StringBuilder()
				.append("CREATE TABLE IF NOT EXISTS component (")
				.append("id UUID DEFAULT ").append(uuidDefault).append(" PRIMARY KEY, ");
		if (this.tenancy == Tenancy.MULTI) {
			query.append("edge_id UUID NOT NULL REFERENCES edge(id) ON DELETE CASCADE, ")
					.append("name VARCHAR NOT NULL, ");
		} else {
			query.append("name VARCHAR NOT NULL UNIQUE, ");
		}
		query.append("component_def UUID NOT NULL REFERENCES component_def(id), ")
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

	private void createChannelDefinitionTable(Statement statement, String uuidDefault) throws SQLException {
		// Scoped by component_def: value type and unit are a property of the
		// Channel-ID *within one component nature*, not of the Channel-ID alone.
		statement.execute("""
				CREATE TABLE IF NOT EXISTS channel_def (
					id          UUID DEFAULT %s PRIMARY KEY,
					component_def    UUID NOT NULL REFERENCES component_def(id) ON DELETE CASCADE,
					name        VARCHAR NOT NULL,
					type        VARCHAR(16) NOT NULL CHECK (type IN ('INTEGER','FLOAT','STRING')),
					unit        VARCHAR(16),
					created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
					UNIQUE (component_def, name)
				)""".formatted(uuidDefault)
		);
	}

	private void createChannelTable(Statement statement, String uuidDefault) throws SQLException {
		// Aggregate: true = this channel is materialized into the aggregate tiers.
		statement.execute("""
				CREATE TABLE IF NOT EXISTS channel (
					id             UUID    DEFAULT %s PRIMARY KEY,
					component_id   UUID    NOT NULL REFERENCES component(id) ON DELETE CASCADE,
					channel_def UUID    NOT NULL REFERENCES channel_def(id) ON DELETE CASCADE,
					aggregate      BOOLEAN NOT NULL DEFAULT false,
					first_seen     TIMESTAMPTZ NOT NULL DEFAULT now(),
					UNIQUE (component_id, channel_def)
				)""".formatted(uuidDefault)
		);
		statement.execute("CREATE INDEX IF NOT EXISTS idx_channel_component ON channel(component_id)");
		statement.execute("CREATE INDEX IF NOT EXISTS idx_channel_def       ON channel(channel_def)");
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

		// Helper: Component-Def (tenancy-independent)
		statement.execute("""
				CREATE OR REPLACE FUNCTION get_or_create_component_def(
				    input_type VARCHAR,
				    OUT out_id UUID
				)
				LANGUAGE plpgsql AS $$
				BEGIN
				    SELECT id INTO out_id FROM component_def WHERE type = input_type;
				    IF out_id IS NULL THEN
				        INSERT INTO component_def (type) VALUES (input_type)
				            ON CONFLICT (type) DO NOTHING
				            RETURNING id INTO out_id;
				        IF out_id IS NULL THEN
				            SELECT id INTO out_id FROM component_def WHERE type = input_type;
				        END IF;
				    END IF;
				END;
				$$"""
		);

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
				.append("OUT out_id UUID, ")
				.append("OUT out_component_def UUID) ")
				.append("LANGUAGE plpgsql AS $$ ")
				.append("BEGIN ").append("SELECT id, component_def INTO out_id, out_component_def FROM component ")
				.append(componentWhere).append("; ")
				.append("IF out_id IS NULL THEN ")
				.append("IF input_component_type IS NULL THEN RETURN; END IF; ")
				.append("SELECT def_id INTO out_component_def ")
				.append("FROM get_or_create_component_def(input_component_type) AS d(def_id); ");
		if (multi) {
			componentQuery
					.append("INSERT INTO component (edge_id, name, component_def) ")
					.append("VALUES (input_edge_id, input_component_name, out_component_def) ")
					.append("ON CONFLICT (edge_id, name) DO NOTHING ");
		} else {
			componentQuery
					.append("INSERT INTO component (name, component_def) ")
					.append("VALUES (input_component_name, out_component_def) ")
					.append("ON CONFLICT (name) DO NOTHING ");
		}
		componentQuery.append("RETURNING id INTO out_id; ")
				.append("IF out_id IS NULL THEN ")
				.append("SELECT id, component_def INTO out_id, out_component_def FROM component ")
				.append(componentWhere).append("; ")
				.append("END IF; ")
				// An existing component keeps its nature. The caller only reaches this
				// function for a channel that is not cached yet, and the nature is
				// decided once, when the component is first registered.
				.append("END IF; ")
				.append("END; ")
				.append("$$");
		statement.execute(componentQuery.toString());

		// Helper: Channel Def (tenancy-independent)
		statement.execute("""
				CREATE OR REPLACE FUNCTION get_or_create_channel_def(
				    input_component_def UUID,
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
				        WHERE component_def = input_component_def AND name = input_channel_name;
				    IF out_id IS NULL THEN
				        INSERT INTO channel_def (component_def, name, type, unit)
				            VALUES (input_component_def, input_channel_name, input_type, input_unit)
				            ON CONFLICT (component_def, name) DO NOTHING
				            RETURNING id, type INTO out_id, out_type;
				        IF out_id IS NULL THEN
				            SELECT id, type, unit INTO out_id, out_type, found_unit FROM channel_def
				                WHERE component_def = input_component_def AND name = input_channel_name;
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
				    input_channel_def UUID,
				    input_aggregate BOOLEAN,
				    OUT out_id UUID,
				    OUT out_aggregate BOOLEAN
				)
				LANGUAGE plpgsql AS $$
				BEGIN
				    SELECT id, aggregate INTO out_id, out_aggregate FROM channel
				        WHERE component_id = input_component_id AND channel_def = input_channel_def;
				    IF out_id IS NULL THEN
				        INSERT INTO channel (component_id, channel_def, aggregate)
				            VALUES (input_component_id, input_channel_def, input_aggregate)
				            ON CONFLICT (component_id, channel_def) DO NOTHING
				            RETURNING id, aggregate INTO out_id, out_aggregate;
				        IF out_id IS NULL THEN
				            SELECT id, aggregate INTO out_id, out_aggregate FROM channel
				                WHERE component_id = input_component_id AND channel_def = input_channel_def;
				        END IF;
				    END IF;
				    IF input_aggregate AND NOT out_aggregate THEN
				        UPDATE channel SET aggregate = true WHERE id = out_id;
				        out_aggregate := true;
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
				.append("input_aggregate BOOLEAN DEFAULT false, ")
				.append("input_unit VARCHAR DEFAULT NULL ")
				.append(") ")
				.append("RETURNS TABLE (out_channel_id UUID, out_type VARCHAR, out_aggregate BOOLEAN) ")
				.append("LANGUAGE plpgsql AS $$ ")
				.append("DECLARE ");
		if (multi) {
			conductorQuery.append("    local_edge_id        UUID;\n");
		}
		conductorQuery
				.append("local_component_id   UUID; ")
				.append("local_component_def    UUID; ")
				.append("local_channel_def UUID; ")
				.append("local_type VARCHAR; ")
				.append("local_channel_id UUID; ")
				.append("local_aggregate BOOLEAN; ")
				.append("BEGIN ");
		if (multi) {
			conductorQuery
					.append("SELECT out_id INTO local_edge_id FROM get_or_create_edge(input_edge_name); ")
					.append("SELECT c_id, c_def_id INTO local_component_id, local_component_def ")
					.append("FROM get_or_create_component(")
					.append("local_edge_id, input_component_name, input_component_type) ")
					.append("AS c(c_id, c_def_id); ");
		} else {
			conductorQuery
					.append("SELECT c_id, c_def_id INTO local_component_id, local_component_def ")
					.append("FROM get_or_create_component(")
					.append("input_component_name, input_component_type) ")
					.append("AS c(c_id, c_def_id); ");
		}
		conductorQuery.append("IF local_component_id IS NULL THEN RETURN; END IF; ")
				.append("SELECT def_id, def_type INTO local_channel_def, local_type ")
				.append("FROM get_or_create_channel_def(")
				.append("local_component_def, input_channel_name, input_type, input_unit) ")
				.append("AS def(def_id, def_type); ")
				.append("IF local_type IS DISTINCT FROM input_type THEN ")
				.append("RAISE WARNING 'channel_def % type mismatch: stored=%, incoming=% (keeping stored)', ")
				.append("input_channel_name, local_type, input_type; ")
				.append("END IF; ")
				.append("SELECT ch_id, ch_aggregate INTO local_channel_id, local_aggregate ")
				.append("FROM get_or_create_channel(local_component_id, local_channel_def, input_aggregate) ")
				.append("AS ch(ch_id, ch_aggregate); ")
				.append("RETURN QUERY SELECT local_channel_id, local_type, local_aggregate; ")
				.append("END; ")
				.append("$$ ");
		statement.execute(conductorQuery.toString());
	}

	private static void createHypertable(Statement statement, String table, String valueType, String chunkInterval)
			throws SQLException {
		statement.execute(new StringBuilder()
				.append("CREATE TABLE IF NOT EXISTS ").append(table).append(" (")
				.append("time TIMESTAMPTZ NOT NULL, ")
				.append("channel_id UUID NOT NULL, ")
				.append("aggregate BOOLEAN NOT NULL, ")
				.append("value ").append(valueType).append(" NOT NULL)")
				.toString());
		statement.execute(new StringBuilder()
				.append("CREATE INDEX IF NOT EXISTS idx_").append(table).append("_channel ")
				.append("ON ").append(table).append(" (channel_id, time DESC)")
				.toString());
		statement.execute(new StringBuilder()
				.append("SELECT create_hypertable('").append(table).append("','time',")
				.append("chunk_time_interval => INTERVAL '").append(chunkInterval).append("',if_not_exists => TRUE)")
				.toString());
	}

	/**
	 * Creates one continuous aggregate. Real-time aggregation is enabled
	 * ({@code materialized_only = false}) so the in-progress bucket is served
	 * live, independent of the installed TimescaleDB version's default.
	 *
	 * @param statement   an open JDBC {@link Statement}
	 * @param viewName    the materialized-view name, e.g. {@code data_15m_integer}
	 * @param bucket      the {@code time_bucket} interval literal
	 * @param source      the source table: a raw hypertable or a finer aggregate
	 * @param cascaded    whether {@code source} is a finer aggregate (re-aggregate
	 *                    its columns) rather than a raw hypertable
	 * @param whereClause an optional filter (e.g. {@code WHERE aggregate}) applied
	 *                    when reading from a raw hypertable; ignored when cascaded
	 * @throws SQLException on database error
	 */
	private static void createAggregate(Statement statement, String viewName, String bucket,
			String source, boolean cascaded, String whereClause) throws SQLException {
		try (var check = statement.getConnection().prepareStatement(
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
				statement.execute("""
						CREATE MATERIALIZED VIEW %s
						WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
						SELECT time_bucket('%s', time) AS bucket, channel_id,
						       MIN(value) AS min_value,
						       MAX(value) AS max_value,
						       AVG(value) AS avg_value,
						       last(value, time) AS last_value,
						       COUNT(*) AS num_values
						FROM %s
						%s
						GROUP BY bucket, channel_id WITH NO DATA
						""".formatted(viewName, bucket, source, whereClause != null ? whereClause : ""));
			} else {
				// avg_value is weighted by each sub-bucket's num_values: a plain
				// AVG(avg_value) would give every sub-bucket equal weight and drift
				// whenever the sample density inside the bucket is uneven.
				statement.execute("""
						CREATE MATERIALIZED VIEW %s
						WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
						SELECT time_bucket('%s', bucket) AS bucket, channel_id,
						       MIN(min_value) AS min_value,
						       MAX(max_value) AS max_value,
						       SUM(avg_value * num_values) / NULLIF(SUM(num_values), 0) AS avg_value,
						       last(last_value, bucket) AS last_value,
						       SUM(num_values) AS num_values
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

	private static void addPolicyIfAbsent(Statement statement, String function, String table, String interval)
			throws SQLException {
		try {
			statement.execute(new StringBuilder()
					.append("SELECT ").append(function)
					.append("('").append(table).append("',").append(interval).append(",if_not_exists => TRUE)")
					.toString());
		} catch (SQLException e) {
			// Policy already exists
		}
	}

	private static void addAggPolicyIfAbsent(Statement statement, String view,
			String startOffset, String endOffset, String scheduleInterval) throws SQLException {
		try {
			statement.execute(new StringBuilder()
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
	 *
	 * @param statement an open JDBC {@link Statement}
	 * @param view      the continuous-aggregate view name
	 * @param interval  the chunk interval literal
	 * @throws SQLException on database error
	 */
	private static void setAggChunkInterval(Statement statement, String view, String interval) throws SQLException {
		try {
			statement.execute(new StringBuilder()
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

	/**
	 * Formats a {@link Duration} as a PostgreSQL interval literal, choosing the
	 * coarsest whole unit (days / hours / minutes / seconds). All default tiers
	 * are sub-hour and fixed-width, so no calendar (timezone-aware) bucketing is
	 * involved.
	 *
	 * @param duration the duration
	 * @return the interval literal, e.g. {@code "15 minutes"}
	 */
	private static String toInterval(Duration duration) {
		var secs = duration.getSeconds();
		if (secs % 86400 == 0) {
			return (secs / 86400) + " days";
		}
		if (secs % 3600 == 0) {
			return (secs / 3600) + " hours";
		}
		if (secs % 60 == 0) {
			return (secs / 60) + " minutes";
		}
		return secs + " seconds";
	}
}
