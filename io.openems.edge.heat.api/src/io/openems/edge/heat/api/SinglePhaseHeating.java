package io.openems.edge.heat.api;

import org.osgi.annotation.versioning.ProviderType;

import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.type.Phase.SinglePhase;

/**
 * Represents a single-phase Heating device, i.e. an {@link AsymmetricHeating}
 * that is only ever active on one of the three phases.
 */
@ProviderType
public interface SinglePhaseHeating extends AsymmetricHeating {

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
	 * Gets the Phase this Heating is connected to.
	 *
	 * @return the {@link SinglePhase}
	 */
	public SinglePhase getPhase();

	/**
	 * Initializes Channel listeners. Copies the Active-Power Phase-Channel value to
	 * the {@code ACTIVE_POWER} Channel.
	 *
	 * @param heating the {@link AsymmetricHeating}
	 * @param phase   the {@link SinglePhase}
	 */
	public static void initializeCopyPhaseChannel(AsymmetricHeating heating, SinglePhase phase) {
		switch (phase) {
		case L1:
			heating.getActivePowerL1Channel().onSetNextValue(value -> {
				heating._setActivePower(value.get());
			});
			break;
		case L2:
			heating.getActivePowerL2Channel().onSetNextValue(value -> {
				heating._setActivePower(value.get());
			});
			break;
		case L3:
			heating.getActivePowerL3Channel().onSetNextValue(value -> {
				heating._setActivePower(value.get());
			});
			break;
		}
	}
}
