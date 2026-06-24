package io.openems.edge.oros.ess.api;

import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.test.AbstractDummyManagedSymmetricEss;

/**
 * Provides a simple, simulated {@link EnergyStorageSystem} component that can be
 * used together with the OpenEMS Component test framework.
 */
public class DummyEnergyStorageSystem extends AbstractDummyManagedSymmetricEss<DummyEnergyStorageSystem> implements EnergyStorageSystem, 
		EnergyStorageProtection, ManagedSymmetricEss, SymmetricEss, StartStoppable, OpenemsComponent {

	public DummyEnergyStorageSystem(String id) {
		super(id, //
				OpenemsComponent.ChannelId.values(), //
				SymmetricEss.ChannelId.values(), //
				ManagedSymmetricEss.ChannelId.values(), //
				EnergyStorageProtection.ChannelId.values(), //
				EnergyStorageSystem.ChannelId.values(), //
				StartStoppable.ChannelId.values());
	}

	@Override
	protected final DummyEnergyStorageSystem self() {
		return this;
	}
}
