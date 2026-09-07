package io.openems.edge.sungrow.pvinverter;

import io.openems.common.types.OptionsEnum;

public enum WorkState implements OptionsEnum {
	UNDEFINED(-1, "Undefined"), //
	RUN(0x0000, "Run"), //
	UNINITIALIZED(0x1111, "Uninitialized"), //
	INITIAL_STANDBY(0x1200, "Initial standby"), //
	KEY_STOP(0x1300, "Key stop"), //
	STANDBY(0x1400, "Standby"), //
	EMERGENCY_STOP(0x1500, "Emergency stop"), //
	STARTING(0x1600, "Starting"), //
	COMMUNICATE_FAULT(0x2500, "Communicate fault"), //
	FAULT(0x5500, "Fault"), //
	STOP(0x8000, "Stop"), //
	DERATING_RUN(0x8100, "Derating run"), //
	DISPATCH_RUN(0x8200, "Dispatch run"), //
	ALARM_RUN(0x9100, "Alarm run"); //

	private final int value;
	private final String name;

	private WorkState(int value, String name) {
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
