package io.openems.edge.controller.tess.delaycharge;

import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;

public interface TessDelayChargeController extends Controller, OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/**
		 * The current thermal charge power limit, so that the storage becomes full at
		 * the target time.
		 *
		 * <ul>
		 * <li>Interface: TessDelayChargeController
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: zero or positive value
		 * </ul>
		 */
		DELAY_CHARGE_LIMIT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Thermal charge power limit to become full at the target time")), //

		/**
		 * The target minute of the day at which the storage should be fully charged.
		 *
		 * <ul>
		 * <li>Interface: TessDelayChargeController
		 * <li>Type: Integer
		 * <li>Range: 0..1440
		 * </ul>
		 */
		TARGET_MINUTE(Doc.of(OpenemsType.INTEGER) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Target minute of the day at which the storage should be fully charged")); //

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
	 * Gets the Channel for {@link ChannelId#DELAY_CHARGE_LIMIT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getDelayChargeLimitChannel() {
		return this.channel(ChannelId.DELAY_CHARGE_LIMIT);
	}

	/**
	 * Gets the thermal charge power limit in [W]. See
	 * {@link ChannelId#DELAY_CHARGE_LIMIT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getDelayChargeLimit() {
		return this.getDelayChargeLimitChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#DELAY_CHARGE_LIMIT} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDelayChargeLimit(Integer value) {
		this.getDelayChargeLimitChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#TARGET_MINUTE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getTargetMinuteChannel() {
		return this.channel(ChannelId.TARGET_MINUTE);
	}

	/**
	 * Gets the target minute of the day. See {@link ChannelId#TARGET_MINUTE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getTargetMinute() {
		return this.getTargetMinuteChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#TARGET_MINUTE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setTargetMinute(Integer value) {
		this.getTargetMinuteChannel().setNextValue(value);
	}
}
