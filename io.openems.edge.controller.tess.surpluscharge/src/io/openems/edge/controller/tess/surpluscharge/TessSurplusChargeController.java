package io.openems.edge.controller.tess.surpluscharge;

import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;

public interface TessSurplusChargeController extends Controller, OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/**
		 * The electrical surplus power available for charging the storage.
		 *
		 * <ul>
		 * <li>Interface: TessSurplusChargeController
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		SURPLUS_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Electrical surplus power available for charging the storage")); //

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
	 * Gets the Channel for {@link ChannelId#SURPLUS_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getSurplusPowerChannel() {
		return this.channel(ChannelId.SURPLUS_POWER);
	}

	/**
	 * Gets the electrical surplus power in [W]. See
	 * {@link ChannelId#SURPLUS_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getSurplusPower() {
		return this.getSurplusPowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#SURPLUS_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setSurplusPower(Integer value) {
		this.getSurplusPowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#SURPLUS_POWER}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setSurplusPower(int value) {
		this.getSurplusPowerChannel().setNextValue(value);
	}
}
