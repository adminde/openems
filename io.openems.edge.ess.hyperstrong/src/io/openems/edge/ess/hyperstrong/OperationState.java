package io.openems.edge.ess.hyperstrong;

import io.openems.common.types.OptionsEnum;

public enum OperationState implements OptionsEnum {
	UNDEFINED(-1, "Undefined"),
	INITIALIZED(0, "Initialized"),
	STOPPED(1, "Stopped"),
	STARTING(2, "Starting"),
	RUNNING(3, "Running"),
	STANDBY(4, "Standby"),
	FAULT(5, "Fault"),
	SHUTDOWN(6, "Shutdown"),
	DEBUG(255, "Debug");

	private final int value;
	private final String name;

	private OperationState(int value, String name) {
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