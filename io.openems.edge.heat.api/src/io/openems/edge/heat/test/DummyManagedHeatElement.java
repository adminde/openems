package io.openems.edge.heat.test;

import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.heat.api.AsymmetricHeating;
import io.openems.edge.heat.api.Heat;
import io.openems.edge.heat.api.ManagedHeatElement;
import io.openems.edge.heat.api.ManagedSymmetricHeating;
import io.openems.edge.heat.api.SymmetricHeating;

public class DummyManagedHeatElement extends AbstractOpenemsComponent
		implements ManagedHeatElement, Heat, ManagedSymmetricHeating, AsymmetricHeating, SymmetricHeating,
		OpenemsComponent {

	/**
	 * Instantiates a disabled {@link DummyManagedHeatElement}.
	 *
	 * @param id the Component-ID
	 * @return a new {@link DummyManagedHeatElement}
	 */
	public static DummyManagedHeatElement ofDisabled(String id) {
		return new DummyManagedHeatElement(id, false);
	}

	public DummyManagedHeatElement(String id) {
		this(id, true);
	}

	private DummyManagedHeatElement(String id, boolean isEnabled) {
		super(//
				OpenemsComponent.ChannelId.values(), //
				SymmetricHeating.ChannelId.values(), //
				AsymmetricHeating.ChannelId.values(), //
				ManagedSymmetricHeating.ChannelId.values(), //
				Heat.ChannelId.values(), //
				ManagedHeatElement.ChannelId.values() //
		);
		super.activate(null, id, "", isEnabled);
	}

}
