package io.openems.edge.ess.hyperstrong;

import io.openems.common.types.OptionsEnum;

public enum RunMode implements OptionsEnum {
	UNDEFINED(-1, "Undefined"),
	STOP(0, "Stop"),
	RUN(1, "Run"),
	SHUTDOWN(3, "Shutdown");

	private final int value;
	private final String name;

	private RunMode(int value, String name) {
		this.value = value;
		this.name = name;
	}

	@Override
	public int getValue() {
		return this.value;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public OptionsEnum getUndefined() {
		return UNDEFINED;
	}
}