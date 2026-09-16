package io.openems.edge.controller.symmetric.limitgridconnectionpower;

import static io.openems.common.channel.PersistencePriority.HIGH;
import static io.openems.common.channel.Unit.WATT;
import static io.openems.common.types.OpenemsType.INTEGER;

import io.openems.common.channel.Level;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;

public interface ControllerEssLimitGridConnectionPower extends Controller, OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/**
		 * Upper limit for the ESS active power. Applied as
		 * SetActivePowerLessOrEquals constraint.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssLimitGridConnectionPower
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: negative values for Charge; positive for Discharge
		 * </ul>
		 */
		ACTIVE_POWER_UPPER_LIMIT(Doc.of(INTEGER) //
				.unit(WATT) //
				.persistencePriority(HIGH)),
		/**
		 * Lower limit for the ESS active power. Applied as
		 * SetActivePowerGreaterOrEquals constraint.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssLimitGridConnectionPower
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: negative values for Charge; positive for Discharge
		 * </ul>
		 */
		ACTIVE_POWER_LOWER_LIMIT(Doc.of(INTEGER) //
				.unit(WATT) //
				.persistencePriority(HIGH)),
		/**
		 * Grid or ESS active power is undefined. Static limits are applied that only
		 * consider the ESS power itself.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssLimitGridConnectionPower
		 * <li>Type: State
		 * </ul>
		 */
		STATIC_LIMIT_FALLBACK(Doc.of(Level.WARNING) //
				.text("Grid or ESS active power is undefined. Static limits are applied")),
		/**
		 * The measured grid import power exceeds the Grid-Buy Hard-Limit.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssLimitGridConnectionPower
		 * <li>Type: State
		 * </ul>
		 */
		GRID_IMPORT_LIMIT_EXCEEDED(Doc.of(Level.WARNING) //
				.text("Grid import power exceeds the Grid-Buy Hard-Limit")),
		/**
		 * The measured grid export power exceeds the Grid-Sell Hard-Limit.
		 *
		 * <ul>
		 * <li>Interface: ControllerEssLimitGridConnectionPower
		 * <li>Type: State
		 * </ul>
		 */
		GRID_EXPORT_LIMIT_EXCEEDED(Doc.of(Level.WARNING) //
				.text("Grid export power exceeds the Grid-Sell Hard-Limit"));

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_POWER_UPPER_LIMIT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getActivePowerUpperLimitChannel() {
		return this.channel(ChannelId.ACTIVE_POWER_UPPER_LIMIT);
	}

	/**
	 * Gets the upper limit for the ESS active power in [W]. See
	 * {@link ChannelId#ACTIVE_POWER_UPPER_LIMIT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getActivePowerUpperLimit() {
		return this.getActivePowerUpperLimitChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#ACTIVE_POWER_UPPER_LIMIT} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerUpperLimit(Integer value) {
		this.getActivePowerUpperLimitChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#ACTIVE_POWER_UPPER_LIMIT} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerUpperLimit(int value) {
		this.getActivePowerUpperLimitChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_POWER_LOWER_LIMIT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getActivePowerLowerLimitChannel() {
		return this.channel(ChannelId.ACTIVE_POWER_LOWER_LIMIT);
	}

	/**
	 * Gets the lower limit for the ESS active power in [W]. See
	 * {@link ChannelId#ACTIVE_POWER_LOWER_LIMIT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getActivePowerLowerLimit() {
		return this.getActivePowerLowerLimitChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#ACTIVE_POWER_LOWER_LIMIT} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerLowerLimit(Integer value) {
		this.getActivePowerLowerLimitChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#ACTIVE_POWER_LOWER_LIMIT} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerLowerLimit(int value) {
		this.getActivePowerLowerLimitChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#STATIC_LIMIT_FALLBACK}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getStaticLimitFallbackChannel() {
		return this.channel(ChannelId.STATIC_LIMIT_FALLBACK);
	}

	/**
	 * Gets whether static limits are applied. See
	 * {@link ChannelId#STATIC_LIMIT_FALLBACK}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getStaticLimitFallback() {
		return this.getStaticLimitFallbackChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#STATIC_LIMIT_FALLBACK} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setStaticLimitFallback(boolean value) {
		this.getStaticLimitFallbackChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#GRID_IMPORT_LIMIT_EXCEEDED}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getGridImportLimitExceededChannel() {
		return this.channel(ChannelId.GRID_IMPORT_LIMIT_EXCEEDED);
	}

	/**
	 * Gets whether the grid import power exceeds the Grid-Buy Hard-Limit. See
	 * {@link ChannelId#GRID_IMPORT_LIMIT_EXCEEDED}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getGridImportLimitExceeded() {
		return this.getGridImportLimitExceededChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#GRID_IMPORT_LIMIT_EXCEEDED} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setGridImportLimitExceeded(boolean value) {
		this.getGridImportLimitExceededChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#GRID_EXPORT_LIMIT_EXCEEDED}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getGridExportLimitExceededChannel() {
		return this.channel(ChannelId.GRID_EXPORT_LIMIT_EXCEEDED);
	}

	/**
	 * Gets whether the grid export power exceeds the Grid-Sell Hard-Limit. See
	 * {@link ChannelId#GRID_EXPORT_LIMIT_EXCEEDED}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getGridExportLimitExceeded() {
		return this.getGridExportLimitExceededChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#GRID_EXPORT_LIMIT_EXCEEDED} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setGridExportLimitExceeded(boolean value) {
		this.getGridExportLimitExceededChannel().setNextValue(value);
	}
}
