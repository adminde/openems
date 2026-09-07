package io.openems.edge.sungrow.pvinverter;

import io.openems.common.types.OptionsEnum;

public enum OutputType implements OptionsEnum {
	UNDEFINED(-1, "Undefined"), //
	TWO_PHASE(0, "Two phase"), //
	THREE_PHASE_FOUR_LINE(1, "Three phase four line (3P4L)"), //
	THREE_PHASE_THREE_LINE(2, "Three phase three line (3P3L)"); //

	private final int value;
	private final String name;

	private OutputType(int value, String name) {
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
