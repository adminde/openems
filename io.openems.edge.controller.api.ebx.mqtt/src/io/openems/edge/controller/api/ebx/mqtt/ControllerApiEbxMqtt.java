package io.openems.edge.controller.api.ebx.mqtt;

import io.openems.common.channel.Level;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.LongReadChannel;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;

/**
 * MQTT transport for the EBX virtual power plant interface.
 *
 * <p>
 * Transport only: connection, topics, frame encoding and decoding, duplicate
 * and ordering validation and publication timing. All control logic lives in
 * the bound Controller ESS EBX.
 */
public interface ControllerApiEbxMqtt extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * A frame could not be decoded.
		 *
		 * <ul>
		 * <li>Interface: ControllerApiEbxMqtt
		 * <li>Type: State
		 * <li>Level: WARNING
		 * </ul>
		 */
		DECODE_ERROR(Doc.of(Level.WARNING) //
				.text("A frame could not be decoded")),

		/**
		 * A frame was discarded as duplicate or out of order.
		 *
		 * <ul>
		 * <li>Interface: ControllerApiEbxMqtt
		 * <li>Type: State
		 * <li>Level: INFO
		 * </ul>
		 */
		FRAME_DISCARDED(Doc.of(Level.INFO) //
				.text("Frame discarded as duplicate or out of order")),
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
	 * Gets the Channel for {@link ChannelId#DECODE_ERROR}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getDecodeErrorChannel() {
		return this.channel(ChannelId.DECODE_ERROR);
	}

	/**
	 * Gets whether a frame could not be decoded. See
	 * {@link ChannelId#DECODE_ERROR}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getDecodeError() {
		return this.getDecodeErrorChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#DECODE_ERROR}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setDecodeError(boolean value) {
		this.getDecodeErrorChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#FRAME_DISCARDED}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getFrameDiscardedChannel() {
		return this.channel(ChannelId.FRAME_DISCARDED);
	}

	/**
	 * Gets whether a frame was discarded as duplicate or out of order. See
	 * {@link ChannelId#FRAME_DISCARDED}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getFrameDiscarded() {
		return this.getFrameDiscardedChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#FRAME_DISCARDED}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setFrameDiscarded(boolean value) {
		this.getFrameDiscardedChannel().setNextValue(value);
	}
}
