package io.openems.edge.heat.pump.api;

import org.osgi.annotation.versioning.ProviderType;

import io.openems.edge.common.channel.Doc;
import io.openems.edge.heat.api.ManagedSymmetricHeating;
import io.openems.edge.heat.tess.api.ThermalEss;

/**
 * A Heat Pump that can be commanded via {@link ManagedSymmetricHeating}.
 *
 * <p>
 * Unlike a generic Heating device, a heat pump may serve multiple storages
 * exclusively — one at a time — e.g. the layers of a stratified tank charged
 * through a diverter valve. Implementations override
 * {@link ManagedSymmetricHeating#bindThermalStorage(ThermalEss)} and
 * {@link ManagedSymmetricHeating#unbindThermalStorage(ThermalEss)} to register
 * several storages instead of the default one-to-one binding. Priority between
 * the storages is derived from their {@code MinTemperature}: the storage with
 * the highest hard lower limit is served first (e.g. domestic hot water before
 * the space-heating buffer), with the Component-ID as deterministic tie-break.
 * A storage at or below its {@code MinTemperature} always precedes explicit
 * power requests (comfort and frost protection cannot be overridden).
 */
@ProviderType
public interface ManagedHeatPump extends HeatPump, ManagedSymmetricHeating {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
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
	 * Gets the storage currently served by this heat pump.
	 *
	 * <p>
	 * A heat pump may have several storages bound via
	 * {@link ManagedSymmetricHeating#bindThermalStorage(ThermalEss)}, but serves
	 * at most one at a time — this returns that one. Implementations bound to at
	 * most a single storage may leave this at its default.
	 *
	 * @return the currently served {@link ThermalEss}, or null if the heat pump
	 *         is not currently heating
	 */
	public default ThermalEss getThermalStorage() {
		return null;
	}

}
