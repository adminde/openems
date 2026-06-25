package io.openems.edge.ess.hyperstrong.hypercube;

import io.openems.common.types.OptionsEnum;

public enum OperatingTarget implements OptionsEnum {
	UNDEFINED(-1, "Undefined"),
	STOP(1, "Stop"),
	RUN(3, "Run"),
	SHUTDOWN(9, "Shutdown");

	private final int value;
	private final String name;

	private OperatingTarget(int value, String name) {
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