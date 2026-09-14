package io.openems.edge.sungrow.pvinverter;

import io.openems.common.types.OptionsEnum;

public enum PowerLimitationSwitch implements OptionsEnum {
	UNDEFINED(-1, "Undefined"), //
	DISABLE(0x55, "Disable"), //
	ENABLE(0xAA, "Enable"); //

	private final int value;
	private final String name;

	private PowerLimitationSwitch(int value, String name) {
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
