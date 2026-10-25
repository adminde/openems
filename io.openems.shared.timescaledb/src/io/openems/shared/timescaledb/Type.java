package io.openems.shared.timescaledb;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

import io.openems.common.types.OpenemsType;

/**
 * The three value types the TimescaleDB schema distinguishes. Each type knows
 * its raw hypertable, its aggregate-view name fragment and how to convert
 * values between Java, JSON and SQL — so no caller has to switch on type name
 * strings.
 *
 * <p>
 * The enum names are also the values stored in {@code channel_def.type}
 * (matching the CHECK constraint), so {@link #name()} / {@link #valueOf}
 * convert to and from the database representation.
 */
public enum Type {
	INTEGER("BIGINT" /* 8 bytes; covers Java byte, short, int and long */, "data_integer", "integer"),
	FLOAT("DOUBLE PRECISION" /* 8 bytes; covers Java float and double */, "data_float", "float"),
	STRING("TEXT" /* variable-length character string */, "data_string", "string");

	/** The SQL column type of the {@code value} column. */
	public final String sqlType;
	/** The raw hypertable, e.g. "data_integer". */
	public final String rawTableName;
	/** The type fragment in aggregate view names, e.g. "integer" in "data_15m_integer". */
	public final String aggInfix;

	private Type(String sqlType, String rawTableName, String aggInfix) {
		this.sqlType = sqlType;
		this.rawTableName = rawTableName;
		this.aggInfix = aggInfix;
	}

	/**
	 * Maps an OpenEMS channel type to its database {@link Type}.
	 *
	 * @param type the {@link OpenemsType}
	 * @return the matching {@link Type}
	 */
	public static Type fromOpenemsType(OpenemsType type) {
		return switch (type) {
		case BOOLEAN, SHORT, INTEGER, LONG -> INTEGER;
		case FLOAT, DOUBLE -> FLOAT;
		case STRING -> STRING;
		};
	}

	/**
	 * Tries to detect the {@link Type} of a {@link JsonElement} value. Used when
	 * no channel definition is available (e.g. the EdgeConfig is not yet known).
	 *
	 * @param value the value
	 * @return the detected type; null for JsonNull
	 */
	public static Type detect(JsonElement value) {
		// Null is undetectable
		if (value == null || value.isJsonNull()) {
			return null;
		}
		if (value.isJsonPrimitive()) {
			var p = value.getAsJsonPrimitive();
			if (p.isNumber()) {
				var n = p.getAsNumber();
				if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) {
					return INTEGER;
				}
				if (n instanceof Float || n instanceof Double) {
					return FLOAT;
				}
				// LazilyParsedNumber (raw JSON): decide by textual representation
				return isIntegerString(n.toString()) ? INTEGER : FLOAT;
			}
			if (p.isBoolean()) {
				// Booleans are stored as integer (0/1)
				return INTEGER;
			}
			// Strings that hold a number are stored numerically
			var s = p.getAsString();
			if (isIntegerString(s)) {
				return INTEGER;
			}
			try {
				Double.parseDouble(s);
				return FLOAT;
			} catch (NumberFormatException e) {
				// not numeric
			}
		}
		return STRING;
	}

	private static boolean isIntegerString(String s) {
		try {
			Long.parseLong(s);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/**
	 * Coerces a raw Java channel value to the Java type matching this database
	 * type (Long, Double or String), so a channel's stored values are consistent
	 * with its hypertable.
	 *
	 * @param raw the raw value, e.g. a Boolean, Short or Float
	 * @return the coerced value
	 */
	public Object coerce(Object raw) {
		return switch (this) {
		case INTEGER -> switch (raw) {
			case Boolean b -> b.booleanValue() ? 1L : 0L;
			case Number n -> n.longValue();
			default -> Long.parseLong(raw.toString());
		};
		case FLOAT -> raw instanceof Number n ? n.doubleValue() : Double.parseDouble(raw.toString());
		case STRING -> raw.toString();
		};
	}

	/**
	 * Coerces a JSON value to the Java type matching this database type (Long,
	 * Double or String).
	 *
	 * @param primitive the JSON value
	 * @return the coerced value
	 */
	public Object coerce(JsonPrimitive primitive) {
		return switch (this) {
		case INTEGER -> primitive.isBoolean() ? (primitive.getAsBoolean() ? 1L : 0L) : primitive.getAsLong();
		case FLOAT -> primitive.getAsDouble();
		case STRING -> primitive.getAsString();
		};
	}

	/**
	 * Parses a value of this type from a {@link ResultSet} to a
	 * {@link JsonElement}.
	 *
	 * @param rs          the {@link ResultSet}
	 * @param columnIndex the first column is 1, the second is 2, ...
	 * @return a {@link JsonElement}, {@link JsonNull} for SQL NULL
	 * @throws SQLException on database error
	 */
	public JsonElement parseValueFromResultSet(ResultSet rs, int columnIndex) throws SQLException {
		switch (this) {
		case INTEGER -> {
			var value = rs.getLong(columnIndex);
			return rs.wasNull() ? JsonNull.INSTANCE : new JsonPrimitive(value);
		}
		case FLOAT -> {
			var value = rs.getDouble(columnIndex);
			return rs.wasNull() ? JsonNull.INSTANCE : new JsonPrimitive(value);
		}
		default -> {
			var value = rs.getString(columnIndex);
			return rs.wasNull() ? JsonNull.INSTANCE : new JsonPrimitive(value);
		}
		}
	}
}
