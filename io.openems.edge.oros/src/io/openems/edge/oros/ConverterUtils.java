package io.openems.edge.oros;

import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;

public class ConverterUtils {

	public static ElementToChannelConverter CONVERT_FLOAT = new ElementToChannelConverter(v -> {
		if (v == null) {
			return null;
		}
		if (v instanceof Number n) {
			return n.floatValue();
		}
		if (v instanceof String s) {
			return Float.valueOf(s);
		}
		throw new IllegalArgumentException(
				"Type [" + v.getClass().getName() + "] not supported by float converter");
	});

}
