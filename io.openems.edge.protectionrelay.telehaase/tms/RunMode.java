package io.openems.edge.ess.hyperstrong.hypercube.tms;

import io.openems.common.types.OptionsEnum;

public enum RunMode implements OptionsEnum {
	UNDEFINED(-1, "Undefined"),
	CIRCULATING(0, "Circulating"),
	COOLING(1, "Cooling"),
	HEATING(2, "Heating"),
	STANDBY(10, "Standby");

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
