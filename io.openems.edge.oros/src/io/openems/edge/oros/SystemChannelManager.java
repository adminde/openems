package io.openems.edge.oros;

import static io.openems.edge.common.channel.ChannelUtils.setValue;

import io.openems.edge.common.channel.AbstractChannelListenerManager;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.oros.bms.BatteryManagementSystem;
import io.openems.edge.oros.pcs.PowerConversionSystem;

public class SystemChannelManager extends AbstractChannelListenerManager {

	private final System parent;

	public SystemChannelManager(System parent) {
		super();
		this.parent = parent;
	}

	/**
	 * Called on Component activate().
	 *
	 * @param battery		the {@link BatteryManagementSystem}
	 * @param inverter		the {@link PowerConversionSystem}
	 */
	public void activate(ClockProvider clock, Sum sum) {
		this.addOnSetNextMirrorListener(sum,
				Sum.ChannelId.GRID_MODE,
				System.ChannelId.GRID_MODE);
		this.addOnSetNextMirrorListener(sum,
				Sum.ChannelId.GRID_MODE_OFF_GRID_TIME,
				System.ChannelId.GRID_MODE_OFF_GRID_TIME);

		this.addOnSetNextMirrorListener(sum,
				Sum.ChannelId.GRID_ACTIVE_POWER,
				System.ChannelId.GRID_ACTIVE_POWER);
		this.addOnSetNextMirrorListener(sum,
				Sum.ChannelId.GRID_ACTIVE_POWER_L1,
				System.ChannelId.GRID_ACTIVE_POWER_L1);
		this.addOnSetNextMirrorListener(sum,
				Sum.ChannelId.GRID_ACTIVE_POWER_L2,
				System.ChannelId.GRID_ACTIVE_POWER_L2);
		this.addOnSetNextMirrorListener(sum,
				Sum.ChannelId.GRID_ACTIVE_POWER_L3,
				System.ChannelId.GRID_ACTIVE_POWER_L3);

		this.addOnSetNextMirrorListener(sum,
				Sum.ChannelId.GRID_REACTIVE_POWER,
				System.ChannelId.GRID_REACTIVE_POWER);
		this.addOnSetNextMirrorListener(sum,
				Sum.ChannelId.GRID_REACTIVE_POWER_L1,
				System.ChannelId.GRID_REACTIVE_POWER_L1);
		this.addOnSetNextMirrorListener(sum,
				Sum.ChannelId.GRID_REACTIVE_POWER_L2,
				System.ChannelId.GRID_REACTIVE_POWER_L2);
		this.addOnSetNextMirrorListener(sum,
				Sum.ChannelId.GRID_REACTIVE_POWER_L3,
				System.ChannelId.GRID_REACTIVE_POWER_L3);

		this.addOnSetNextMirrorListener(sum,
				Sum.ChannelId.GRID_BUY_ACTIVE_ENERGY,
				System.ChannelId.GRID_IMPORT_ACTIVE_ENERGY);
		this.addOnSetNextMirrorListener(sum,
				Sum.ChannelId.GRID_SELL_ACTIVE_ENERGY,
				System.ChannelId.GRID_EXPORT_ACTIVE_ENERGY);

		this.addOnSetNextMirrorListener(sum,
				Sum.ChannelId.GRID_LAGGING_REACTIVE_ENERGY,
				System.ChannelId.GRID_LAGGING_REACTIVE_ENERGY);
		this.addOnSetNextMirrorListener(sum,
				Sum.ChannelId.GRID_LEADING_REACTIVE_ENERGY,
				System.ChannelId.GRID_LEADING_REACTIVE_ENERGY);
	}

	/**
	 * Adds a Copy-Listener. It listens on setNextValue() and copies the value to the target channel.
	 *
	 * @param <T>             the Channel-Type
	 * @param sourceComponent the source component - Battery or BatteryInverter
	 * @param sourceChannelId the source ChannelId
	 * @param targetChannelId the target ChannelId
	 */
	protected <T> void addOnSetNextMirrorListener(OpenemsComponent sourceComponent,
			ChannelId sourceChannelId, ChannelId targetChannelId) {
		this.<T>addOnSetNextValueListener(sourceComponent, sourceChannelId, value -> {
			setValue(this.parent, targetChannelId, value);
		});
	}

}
