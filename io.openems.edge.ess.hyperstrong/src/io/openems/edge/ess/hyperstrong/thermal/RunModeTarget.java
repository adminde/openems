package io.openems.edge.ess.hyperstrong.thermal;

import io.openems.common.types.OptionsEnum;

public enum RunModeTarget implements OptionsEnum {
	UNDEFINED(-1, "Undefined"),
	STOP(0, "Stop"),
	COOLING(1, "Cooling"),
	HEATING(2, "Heating"),
	CIRCULATING(3, "Circulating");

	private final int value;
	private final String name;

	private RunModeTarget(int value, String name) {
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