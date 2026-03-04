package io.openems.edge.ess.hyperstrong;

import io.openems.common.types.OptionsEnum;

public enum ChargingMode implements OptionsEnum {
	UNDEFINED(-1, "Undefined"),
	IDLE(0, "Idle"),
	CHARGE(1, "Charge"),
	DISCHARGE(2, "Discharge"),
	OFF_GRID(3, "Off-Grid");

	private final int value;
	private final String name;

	private ChargingMode(int value, String name) {
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