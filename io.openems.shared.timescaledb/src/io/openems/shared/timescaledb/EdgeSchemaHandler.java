package io.openems.shared.timescaledb;

import java.sql.SQLException;
import java.sql.Statement;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Single-edge {@link SchemaHandler}: the local Edge database stores data for
 * exactly one edge, so there is no {@code edge} dimension table. The
 * {@code component} table is keyed by name alone and the
 * {@code get_or_create_channel_id} function takes no edge argument.
 */
public class EdgeSchemaHandler extends SchemaHandler {

	public EdgeSchemaHandler(HikariDataSource dataSource, int rawRetentionDays, int rawCompressionDays,
			boolean createMinutelyAggregate) {
		super(dataSource, rawRetentionDays, rawCompressionDays, createMinutelyAggregate);
	}

	@Override
	protected void createDimensionTables(Statement st) throws SQLException {
		// Single-edge deployment: no `edge` dimension; component name is unique.
		st.execute("""
				CREATE TABLE IF NOT EXISTS component (
				    id         UUID        DEFAULT uuid_generate_v7() PRIMARY KEY,
				    name       VARCHAR NOT NULL UNIQUE,
				    type       VARCHAR,
				    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
				)""");
	}

	@Override
	protected void createGetOrCreateFunction(Statement st) throws SQLException {
		// DROP first so the RETURNS TABLE signature can evolve across versions
		// (CREATE OR REPLACE alone cannot change a function's return type).

		// Helper: Component
		st.execute("DROP FUNCTION IF EXISTS get_or_create_component(VARCHAR, VARCHAR)");
		st.execute("""
				CREATE OR REPLACE FUNCTION get_or_create_component(
				    input_component_name VARCHAR,
				    input_component_type VARCHAR,
				    OUT out_id UUID
				)
				LANGUAGE plpgsql AS $$
				DECLARE
				    found_type VARCHAR;
				BEGIN
				    SELECT id, type INTO out_id, found_type FROM component
				        WHERE name = input_component_name;
				    IF out_id IS NULL THEN
				        INSERT INTO component (name, type)
				            VALUES (input_component_name, input_component_type)
				            ON CONFLICT (name) DO NOTHING
				            RETURNING id INTO out_id;
				        IF out_id IS NULL THEN
				            SELECT id, type INTO out_id, found_type FROM component
				                WHERE name = input_component_name;
				        END IF;
				    ELSIF found_type IS DISTINCT FROM input_component_type AND input_component_type <> 'backend' THEN
				        UPDATE component SET type = input_component_type WHERE id = out_id;
				    END IF;
				END;
				$$""");

		// Helper: Channel Def
		st.execute("DROP FUNCTION IF EXISTS get_or_create_channel_def(VARCHAR, VARCHAR, VARCHAR)");
		st.execute("""
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
				$$""");

		// Helper: Channel
		st.execute("DROP FUNCTION IF EXISTS get_or_create_channel(UUID, UUID, BOOLEAN)");
		st.execute("""
				CREATE OR REPLACE FUNCTION get_or_create_channel(
				    input_component_id UUID,
				    input_channel_def_id UUID,
				    input_core BOOLEAN,
				    OUT out_id UUID,
				    OUT out_core BOOLEAN
				)
				LANGUAGE plpgsql AS $$
				BEGIN
				    SELECT id, core INTO out_id, out_core FROM channel
				        WHERE component_id = input_component_id AND channel_def_id = input_channel_def_id;
				    IF out_id IS NULL THEN
				        INSERT INTO channel (component_id, channel_def_id, core)
				            VALUES (input_component_id, input_channel_def_id, input_core)
				            ON CONFLICT (component_id, channel_def_id) DO NOTHING
				            RETURNING id, core INTO out_id, out_core;
				        IF out_id IS NULL THEN
				            SELECT id, core INTO out_id, out_core FROM channel
				                WHERE component_id = input_component_id AND channel_def_id = input_channel_def_id;
				        END IF;
				    END IF;

				    IF input_core AND NOT out_core THEN
				        UPDATE channel SET core = true WHERE id = out_id;
				        out_core := true;
				    END IF;
				END;
				$$""");

		// Conductor Function
		st.execute("DROP FUNCTION IF EXISTS get_or_create_channel_id"
				+ "(VARCHAR, VARCHAR, VARCHAR, VARCHAR, BOOLEAN, VARCHAR)");
		st.execute("""
				CREATE OR REPLACE FUNCTION get_or_create_channel_id(
				    input_component_name  VARCHAR,
				    input_component_type  VARCHAR,
				    input_channel_name    VARCHAR,
				    input_type            VARCHAR,
				    input_core            BOOLEAN DEFAULT false,
				    input_unit            VARCHAR DEFAULT NULL
				)
				RETURNS TABLE (out_channel_id UUID, out_type VARCHAR, out_core BOOLEAN)
				LANGUAGE plpgsql AS $$
				DECLARE
				    local_component_id   UUID;
				    local_channel_def_id UUID;
				    local_type           VARCHAR;
				    local_channel_id     UUID;
				    local_core           BOOLEAN;
				BEGIN
				    SELECT out_id INTO local_component_id FROM get_or_create_component(input_component_name, input_component_type);
				    
				    SELECT def_id, def_type INTO local_channel_def_id, local_type 
				        FROM get_or_create_channel_def(input_channel_name, input_type, input_unit)
				             AS def(def_id, def_type);

				    IF local_type IS DISTINCT FROM input_type THEN
				        RAISE WARNING 'channel_def % type mismatch: stored=%, incoming=% (keeping stored)',
				            input_channel_name, local_type, input_type;
				    END IF;

				    SELECT ch_id, ch_core INTO local_channel_id, local_core 
				        FROM get_or_create_channel(local_component_id, local_channel_def_id, input_core)
				             AS ch(ch_id, ch_core);

				    RETURN QUERY SELECT local_channel_id, local_type, local_core;
				END;
				$$""");
	}
}
