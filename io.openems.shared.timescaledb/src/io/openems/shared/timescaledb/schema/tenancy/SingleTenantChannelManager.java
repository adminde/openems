package io.openems.shared.timescaledb.schema.tenancy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import io.openems.common.types.ChannelAddress;
import io.openems.shared.timescaledb.DataPoint;
import io.openems.shared.timescaledb.schema.ChannelDefinition;
import io.openems.shared.timescaledb.schema.ChannelManager;

/**
 * Single-tenant {@link ChannelManager}: the database stores data for exactly
 * one edge, so there is no {@code edge} dimension table and the edge name
 * passed in is ignored. Components are keyed by name alone.
 */
public class SingleTenantChannelManager extends ChannelManager {

	@Override
	protected ChannelDefinition doLookupChannel(Connection con, String edgeName, ChannelAddress addr)
			throws SQLException {
		var sql = """
				SELECT c.id, cd.type, c.core, cd.unit
				FROM channel c
				JOIN component   co ON co.id = c.component_id
				JOIN channel_def cd ON cd.id = c.channel_def_id
				WHERE co.name = ? AND cd.name = ?
				""";
		try (var pst = con.prepareStatement(sql)) {
			pst.setString(1, addr.getComponentId());
			pst.setString(2, addr.getChannelId());
			try (var rs = pst.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				return new ChannelDefinition(rs.getObject(1, UUID.class), rs.getString(2), rs.getBoolean(3),
						rs.getString(4));
			}
		}
	}

	@Override
	protected ChannelDefinition doResolveChannel(Connection con, DataPoint p) throws SQLException {
		var sql = "SELECT * FROM get_or_create_channel_id(?,?,?,?,?,?)";
		try (var pst = con.prepareStatement(sql)) {
			pst.setString(1, p.componentAlias());
			pst.setString(2, p.componentType());
			pst.setString(3, p.channelName());
			pst.setString(4, p.dataType());
			pst.setBoolean(5, p.core());
			pst.setString(6, p.unit());
			try (var rs = pst.executeQuery()) {
				rs.next();
				return new ChannelDefinition(rs.getObject(1, UUID.class), rs.getString(2), rs.getBoolean(3), p.unit());
			}
		}
	}
}