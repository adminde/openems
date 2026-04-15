package io.openems.edge.oros;

import io.openems.common.types.OptionsEnum;

public enum OrosModel implements OptionsEnum {
	OROS_CI_233("OROS C&I 233", 220, 115F, 105F);

	private final int capacity;
	private final int maxChargePower;
	private final int maxDischargePower;
	private final String name;

	private OrosModel(String name, float capacity, float maxChargePower, float maxDischargePower) {
		this.name = name;
		this.capacity = (int) capacity * 1000;
		this.maxChargePower = (int) maxChargePower * 1000;
		this.maxDischargePower = (int) maxDischargePower * 1000;
	}

	public int getCapacity() {
		return this.capacity;
	}

	public int getMaxChargePower() {
		return this.maxChargePower;
	}

	public int getMaxDischargePower() {
		return this.maxDischargePower;
	}

	@Override
	public int getValue() {
		return this.capacity;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public OptionsEnum getUndefined() {
		return null;
	}
}
