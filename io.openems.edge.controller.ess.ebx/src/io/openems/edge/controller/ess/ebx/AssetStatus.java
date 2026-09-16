package io.openems.edge.controller.ess.ebx;

import io.openems.common.types.OptionsEnum;

/**
 * Asset status values of the EBX interface.
 *
 * <p>
 * The values follow the AssetStatus enum of the interface specification. No
 * availability means no operational path is left and no dispatch is possible,
 * it is also required whenever the grid switch is open and throughout an EMS
 * or device reboot until internal health checks pass. Restricted availability
 * means a component has failed or become uncontrollable in an unplanned way,
 * or active obligations reach the available capacity in one direction. Full
 * availability means all components are in service, expected operational
 * derating does not change the status.
 */
public enum AssetStatus implements OptionsEnum {
	UNDEFINED(-1, "Undefined"), //
	NO_AVAILABILITY(0, "No availability"), //
	RESTRICTED_AVAILABILITY(125, "Restricted availability"), //
	FULL_AVAILABILITY(255, "Full availability");

	private final int value;
	private final String name;

	private AssetStatus(int value, String name) {
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
