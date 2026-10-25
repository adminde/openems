package io.openems.edge.heat.api;

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
import io.openems.edge.heat.tess.api.ThermalEss;

@ProviderType
public interface ManagedSymmetricHeating extends SymmetricHeating {

	/**
	 * Binds a {@link ThermalEss} that aggregates this Heating.
	 *
	 * <p>
	 * Implementations may override this method to react to the storage reference
	 * (e.g. read target temperature limits or current temperature for local control
	 * logic). The default implementation is a no-op so existing implementations are
	 * unaffected.
	 *
	 * <p>
	 * This binding is strictly one-to-one for a generic Heating device. Devices
	 * that can serve multiple storages exclusively (e.g. a heat pump charging the
	 * layers of a stratified tank through a diverter valve) override this and
	 * {@link #unbindThermalStorage(ThermalEss)} to register several storages
	 * instead — see {@code ManagedHeatPump}.
	 *
	 * @param thermalStorage the storage to bind
	 */
	public default void bindThermalStorage(ThermalEss thermalStorage) {
		// optional — no-op by default
	}

	/**
	 * Unbinds a {@link ThermalEss} previously bound via
	 * {@link #bindThermalStorage(ThermalEss)}.
	 *
	 * @param thermalStorage the storage to unbind
	 */
	public default void unbindThermalStorage(ThermalEss thermalStorage) {
		// optional — no-op by default
	}

	/**
	 * Applies the thermal charge request of a {@link ThermalEss} as an electrical
	 * Active Power target.
	 *
	 * <p>
	 * Unlike {@code ManagedSymmetricEss.applyPower()}, which the Power solver
	 * calls with a decided set-point, this is a request: Heating devices are less
	 * directly controllable than a battery inverter, and a device serving
	 * multiple storages may refuse or defer the request in favor of a
	 * higher-priority storage. The {@code storage} parameter identifies the
	 * requesting storage; implementations bound to a single storage may ignore
	 * it. The default implementation writes the target to the
	 * {@link ChannelId#TARGET_ACTIVE_POWER} Channel.
	 *
	 * @param storage     the requesting {@link ThermalEss}
	 * @param targetPower the electrical Active Power target in [W]
	 * @throws OpenemsNamedException on error
	 */
	public default void applyPower(ThermalEss storage, int targetPower) throws OpenemsNamedException {
		this.getTargetActivePowerChannel().setNextWriteValue(targetPower);
	}

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		CONTROL_NOT_ALLOWED(Doc.of(OpenemsType.BOOLEAN) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Debug channel for {@link ChannelId#TARGET_ACTIVE_POWER}.
		 *
		 * <ul>
		 * <li>Interface: ManagedSymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		DEBUG_TARGET_ACTIVE_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)), //
		/**
		 * The Active Power target for electrical consumption of the heating.
		 *
		 * <ul>
		 * <li>Interface: ManagedSymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: positive values
		 * </ul>
		 */
		TARGET_ACTIVE_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.accessMode(AccessMode.READ_WRITE) //
				.onChannelSetNextWriteMirrorToDebugChannel(ChannelId.DEBUG_TARGET_ACTIVE_POWER) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Current maximum allowed Active Power consumption target.
		 *
		 * <ul>
		 * <li>Interface: ManagedSymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		MAX_TARGET_ACTIVE_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH)), //

		/**
		 * Current minimum allowed Active Power consumption target. Dynamically larger
		 * when thermal demand is high.
		 *
		 * <ul>
		 * <li>Interface: ManagedSymmetricHeating
		 * <li>Type: Integer
		 * <li>Unit: W
		 * </ul>
		 */
		MIN_TARGET_ACTIVE_POWER(Doc.of(OpenemsType.INTEGER) //
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
	 * Gets the Channel for {@link ChannelId#TARGET_ACTIVE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerWriteChannel getTargetActivePowerChannel() {
		return this.channel(ChannelId.TARGET_ACTIVE_POWER);
	}

	/**
	 * Gets the Active Power Target in [W]. See {@link ChannelId#TARGET_ACTIVE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getTargetActivePower() {
		return this.getTargetActivePowerChannel().value();
	}

	/**
	 * Sets the Active Power Target in [W]. See {@link ChannelId#TARGET_ACTIVE_POWER}.
	 *
	 * @param value the next write value
	 * @throws OpenemsNamedException on error
	 */
	public default void _setTargetActivePower(Integer value) throws OpenemsNamedException {
		this.getTargetActivePowerChannel().setNextWriteValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#TARGET_ACTIVE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTargetActivePower(int value) {
		this.getTargetActivePowerChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#DEBUG_TARGET_ACTIVE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getDebugTargetActivePowerChannel() {
		return this.channel(ChannelId.DEBUG_TARGET_ACTIVE_POWER);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MAX_TARGET_ACTIVE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMaxTargetActivePowerChannel() {
		return this.channel(ChannelId.MAX_TARGET_ACTIVE_POWER);
	}

	/**
	 * Gets the maximum allowed Active Power Target in [W]. See
	 * {@link ChannelId#MAX_TARGET_ACTIVE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMaxTargetActivePower() {
		return this.getMaxTargetActivePowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MAX_TARGET_ACTIVE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxTargetActivePower(Integer value) {
		this.getMaxTargetActivePowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MAX_TARGET_ACTIVE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxTargetActivePower(int value) {
		this.getMaxTargetActivePowerChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MIN_TARGET_ACTIVE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMinTargetActivePowerChannel() {
		return this.channel(ChannelId.MIN_TARGET_ACTIVE_POWER);
	}

	/**
	 * Gets the minimum allowed Active Power Target in [W]. See
	 * {@link ChannelId#MIN_TARGET_ACTIVE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMinTargetActivePower() {
		return this.getMinTargetActivePowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MIN_TARGET_ACTIVE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMinTargetActivePower(Integer value) {
		this.getMinTargetActivePowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MIN_TARGET_ACTIVE_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMinTargetActivePower(int value) {
		this.getMinTargetActivePowerChannel().setNextValue(value);
	}

}
