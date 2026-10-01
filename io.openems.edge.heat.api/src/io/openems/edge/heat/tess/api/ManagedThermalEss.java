package io.openems.edge.heat.tess.api;

import org.osgi.annotation.versioning.ProviderType;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.value.Value;

/**
 * Represents a controllable Thermal Energy Storage System.
 *
 * <p>
 * A ManagedThermalEss accepts thermal power targets and distributes them to its
 * registered Heating devices, converting the thermal request to electrical
 * targets via each Heating's {@code ThermalEfficiency}. All target Channels are
 * expressed in thermal Watts; positive values request charging. The constraint
 * Channels follow the {@code ManagedSymmetricEss} schema
 * (Equals/GreaterOrEquals/LessOrEquals), but use the {@code Target} prefix
 * because Heating devices are less directly controllable than a battery
 * inverter — a target is a request, not a guaranteed set-point.
 *
 * <p>
 * The effective target of a cycle resolves to
 * {@code min(LessOrEquals, max(Equals, GreaterOrEquals))}, where missing writes
 * are neutral. Without any pending write the Heating devices remain in
 * autonomous two-point operation between {@code MinTemperature} and
 * {@code TargetTemperature}; an explicit target may heat further, up to
 * {@code MaxTemperature}.
 */
@ProviderType
public interface ManagedThermalEss extends ThermalEss {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Debug channel for {@link ChannelId#TARGET_THERMAL_POWER_EQUALS}.
		 *
		 * <ul>
		 * <li>Interface: ManagedThermalEss
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		DEBUG_TARGET_THERMAL_POWER_EQUALS(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)), //

		/**
		 * The Thermal Power target for charging the storage. Positive values request
		 * heat input.
		 *
		 * <ul>
		 * <li>Interface: ManagedThermalEss
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		TARGET_THERMAL_POWER_EQUALS(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.accessMode(AccessMode.READ_WRITE) //
				.onChannelSetNextWriteMirrorToDebugChannel(ChannelId.DEBUG_TARGET_THERMAL_POWER_EQUALS) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Debug channel for {@link ChannelId#TARGET_THERMAL_POWER_GREATER_OR_EQUALS}.
		 *
		 * <ul>
		 * <li>Interface: ManagedThermalEss
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		DEBUG_TARGET_THERMAL_POWER_GREATER_OR_EQUALS(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)), //

		/**
		 * The minimum Thermal Power target for charging the storage, e.g. requested by
		 * a PV-surplus controller.
		 *
		 * <ul>
		 * <li>Interface: ManagedThermalEss
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		TARGET_THERMAL_POWER_GREATER_OR_EQUALS(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.accessMode(AccessMode.READ_WRITE) //
				.onChannelSetNextWriteMirrorToDebugChannel(ChannelId.DEBUG_TARGET_THERMAL_POWER_GREATER_OR_EQUALS) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Debug channel for {@link ChannelId#TARGET_THERMAL_POWER_LESS_OR_EQUALS}.
		 *
		 * <ul>
		 * <li>Interface: ManagedThermalEss
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		DEBUG_TARGET_THERMAL_POWER_LESS_OR_EQUALS(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)), //

		/**
		 * The maximum Thermal Power target for charging the storage, e.g. requested by
		 * a lock or peak-shaving controller.
		 *
		 * <ul>
		 * <li>Interface: ManagedThermalEss
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		TARGET_THERMAL_POWER_LESS_OR_EQUALS(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.accessMode(AccessMode.READ_WRITE) //
				.onChannelSetNextWriteMirrorToDebugChannel(ChannelId.DEBUG_TARGET_THERMAL_POWER_LESS_OR_EQUALS) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Current maximum thermal charge power intake. Aggregated from the registered
		 * Heating devices (electrical maximum times ThermalEfficiency); zero when the
		 * storage Temperature has reached MaxTemperature.
		 *
		 * <p>
		 * Conditional value: a Heating device shared with another storage (e.g. the
		 * layers of a stratified tank served by one heat pump) reports its full power
		 * to each storage, although it can only serve one at a time. Aggregators must
		 * deduplicate shared devices via {@link ManagedThermalEss#getHeatingIds()}
		 * instead of summing this Channel.
		 *
		 * <ul>
		 * <li>Interface: ManagedThermalEss
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		MAX_TARGET_THERMAL_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Current minimum dispatchable thermal charge power. The smallest thermal
		 * power step the registered Heating devices can serve, e.g. the switch-on
		 * threshold of an on/off device.
		 *
		 * <ul>
		 * <li>Interface: ManagedThermalEss
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		MIN_TARGET_THERMAL_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH)), //
		;

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
	 * Gets the Component-IDs of the Heating devices this storage distributes its
	 * targets to.
	 *
	 * <p>
	 * Heating devices may be shared between multiple storages, e.g. the layers of
	 * a stratified tank served by one heat pump. Aggregators like a storage
	 * cluster use these IDs to count a shared device only once when summing power
	 * limits. The default implementation returns an empty array: the storage is
	 * opaque and its aggregated Channels are taken as-is.
	 *
	 * @return an array of Component-IDs
	 */
	public default String[] getHeatingIds() {
		return new String[0];
	}

	/**
	 * Gets the Channel for {@link ChannelId#TARGET_THERMAL_POWER_EQUALS}.
	 *
	 * @return the Channel
	 */
	public default IntegerWriteChannel getTargetThermalPowerEqualsChannel() {
		return this.channel(ChannelId.TARGET_THERMAL_POWER_EQUALS);
	}

	/**
	 * Sets a Thermal Power target in [W] without applying a filter. See
	 * {@link ChannelId#TARGET_THERMAL_POWER_EQUALS}.
	 *
	 * <p>
	 * Use this method whenever no closed control loop is applied, e.g. when the
	 * value is derived from a schedule or user input.
	 *
	 * @param value the next write value
	 * @throws OpenemsNamedException on error
	 */
	public default void setTargetThermalPowerEqualsWithoutFilter(Integer value) throws OpenemsNamedException {
		this.getTargetThermalPowerEqualsChannel().setNextWriteValue(value);
	}

	/**
	 * Sets a Thermal Power target in [W] after applying a filter. See
	 * {@link ChannelId#TARGET_THERMAL_POWER_EQUALS}.
	 *
	 * <p>
	 * Use this method whenever a closed control loop is applied, e.g. when the
	 * value relies on measurements of a grid meter. Implementations may smooth the
	 * target (e.g. PID or PT1); the default implementation applies the value
	 * directly.
	 *
	 * @param value the next write value
	 * @throws OpenemsNamedException on error
	 */
	public default void setTargetThermalPowerEqualsWithFilter(Integer value) throws OpenemsNamedException {
		this.setTargetThermalPowerEqualsWithoutFilter(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#TARGET_THERMAL_POWER_GREATER_OR_EQUALS}.
	 *
	 * @return the Channel
	 */
	public default IntegerWriteChannel getTargetThermalPowerGreaterOrEqualsChannel() {
		return this.channel(ChannelId.TARGET_THERMAL_POWER_GREATER_OR_EQUALS);
	}

	/**
	 * Sets a minimum Thermal Power target in [W]. See
	 * {@link ChannelId#TARGET_THERMAL_POWER_GREATER_OR_EQUALS}.
	 *
	 * @param value the next write value
	 * @throws OpenemsNamedException on error
	 */
	public default void setTargetThermalPowerGreaterOrEquals(Integer value) throws OpenemsNamedException {
		this.getTargetThermalPowerGreaterOrEqualsChannel().setNextWriteValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#TARGET_THERMAL_POWER_LESS_OR_EQUALS}.
	 *
	 * @return the Channel
	 */
	public default IntegerWriteChannel getTargetThermalPowerLessOrEqualsChannel() {
		return this.channel(ChannelId.TARGET_THERMAL_POWER_LESS_OR_EQUALS);
	}

	/**
	 * Sets a maximum Thermal Power target in [W]. See
	 * {@link ChannelId#TARGET_THERMAL_POWER_LESS_OR_EQUALS}.
	 *
	 * @param value the next write value
	 * @throws OpenemsNamedException on error
	 */
	public default void setTargetThermalPowerLessOrEquals(Integer value) throws OpenemsNamedException {
		this.getTargetThermalPowerLessOrEqualsChannel().setNextWriteValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MAX_TARGET_THERMAL_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMaxTargetThermalPowerChannel() {
		return this.channel(ChannelId.MAX_TARGET_THERMAL_POWER);
	}

	/**
	 * Gets the maximum thermal charge power intake in [W]. See
	 * {@link ChannelId#MAX_TARGET_THERMAL_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMaxTargetThermalPower() {
		return this.getMaxTargetThermalPowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MAX_TARGET_THERMAL_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxTargetThermalPower(Integer value) {
		this.getMaxTargetThermalPowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MAX_TARGET_THERMAL_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxTargetThermalPower(int value) {
		this.getMaxTargetThermalPowerChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MIN_TARGET_THERMAL_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMinTargetThermalPowerChannel() {
		return this.channel(ChannelId.MIN_TARGET_THERMAL_POWER);
	}

	/**
	 * Gets the minimum dispatchable thermal charge power in [W]. See
	 * {@link ChannelId#MIN_TARGET_THERMAL_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMinTargetThermalPower() {
		return this.getMinTargetThermalPowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MIN_TARGET_THERMAL_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMinTargetThermalPower(Integer value) {
		this.getMinTargetThermalPowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MIN_TARGET_THERMAL_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMinTargetThermalPower(int value) {
		this.getMinTargetThermalPowerChannel().setNextValue(value);
	}

}
