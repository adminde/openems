package io.openems.edge.ess.hyperstrong.hypercube;

import io.openems.common.types.OptionsEnum;

public enum HyperCubeModel implements OptionsEnum {
	HSL2C2912_0232_EU(220, 115F, 105F, "HSL2C2912-0232-EU");

	private final int capacity;
	private final int maxChargePower;
	private final int maxDischargePower;
	private final String name;

	private HyperCubeModel(float capacity, float maxChargePower, float maxDischargePower, String name) {
		this.capacity = (int) capacity * 1000;
		this.maxChargePower = (int) maxChargePower * 1000;
		this.maxDischargePower = (int) maxDischargePower * 1000;
		this.name = name;
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