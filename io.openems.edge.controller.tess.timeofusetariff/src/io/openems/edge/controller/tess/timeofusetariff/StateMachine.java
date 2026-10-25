package io.openems.edge.controller.tess.timeofusetariff;

import io.openems.common.types.OptionsEnum;

public enum StateMachine implements OptionsEnum {
	/*
	 * NONE is first = default. The Controller writes nothing; Heating devices
	 * operate autonomously within their temperature band.
	 */
	NONE(0, "No control"), //
	CHARGE_GRID(1, "Charge from grid") //
	;

	private final int value;
	private final String name;

	private StateMachine(int value, String name) {
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
		return NONE;
	}

}
