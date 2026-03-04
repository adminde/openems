package io.openems.edge.ess.hyperstrong;

import io.openems.common.channel.Level;
import io.openems.edge.common.channel.Channel;

/**
 * Utility for decoding HyperStrong Alarm registers.
 *
 * <p>
 * Each 16-bit alarm register is divided into 8 two-bit pairs. Each pair
 * may encode the severity of one alarm as an integer status code:
 * <ul>
 * <li>{@code 0} = no alarm</li>
 * <li>{@code ≥ threshold} = alarm</li>
 * </ul>
 */
public class AlarmAnalysis {

	/**
	 * Decodes a 16-bit alarm register and sets an alarm channel,
	 * using the default fault threshold of {@code 1}.
	 *
	 * @param bit            the bit-pair index (0–14)
	 * @param register       the register value; if {@code null} both channels are
	 *                       set to {@code null}
	 * @param channel        the channel set to {@code true} when the 2-bit pair is
	 *                       above a specified threshold
	 */
	public static void convertAlarm(int bit, Integer register, Channel<Level> channel) {
		convertAlarm(bit, register, channel, 1);
	}

	/**
	 * Decodes a 16-bit alarm register and sets an alarm channel
	 * with a configurable alarm threshold.
	 *
	 * <p>
	 * Each 2-bit pair is interpreted as an integer status code:
	 * <ul>
	 * <li>{@code 0} = no alarm</li>
	 * <li>{@code ≥ threshold} = alarm</li>
	 * </ul>
	 *
	 * @param bit            the bit-pair index (0–14)
	 * @param register       the register value; if {@code null} both channels are
	 *                       set to {@code null}
	 * @param channel        the channel set to {@code true} when the 2-bit pair is
	 *                       above a specified threshold
	 * @param threshold      the minimum status code considered an alarm (typically
	 *                       {@code 1} to {@code 3})
	 */
	public static void convertAlarm(int bit, Integer register,
			Channel<Level> channel, int threshold) {
		if (register == null) {
			channel.setNextValue(null);
			return;
		}
		int alarm = convertAlarmInteger(bit, register);

		channel.setNextValue(alarm >= threshold);
	}

	/**
	 * Decodes a 16-bit alarm register and sets warning and fault channels
	 * with an alarm threshold of 2.
	 *
	 * <p>
	 * Each 2-bit pair is interpreted as an integer status code:
	 * <ul>
	 * <li>{@code 0} = no alarm</li>
	 * <li>{@code < threshold} = fault</li>
	 * <li>{@code ≥ threshold} = warning</li>
	 * </ul>
	 *
	 * @param bit            the bit-pair index (0–14)
	 * @param register       the register value; if {@code null} both channels are
	 *                       set to {@code null}
	 * @param warnChannel    the channel set to {@code true} when the 2-bit pair is
	 *                       above a specified threshold
	 * @param faultChannel   the channel set to {@code true} when the 2-bit pair is
	 *                       above a specified threshold
	 */
	public static void convertAlarm(int bit, Integer register,
				Channel<Level> warnChannel, Channel<Level> faultChannel) {
		convertAlarm(bit, register, warnChannel, faultChannel, 2);
	}

	/**
	 * Decodes a 16-bit alarm register and sets warning and fault channels
	 * with a configurable alarm threshold.
	 *
	 * <p>
	 * Each 2-bit pair is interpreted as an integer status code:
	 * <ul>
	 * <li>{@code 0} = no alarm</li>
	 * <li>{@code < threshold} = fault</li>
	 * <li>{@code ≥ threshold} = warning</li>
	 * </ul>
	 *
	 * @param bit            the bit-pair index (0–14)
	 * @param register       the register value; if {@code null} both channels are
	 *                       set to {@code null}
	 * @param warnChannel    the channel set to {@code true} when the 2-bit pair is
	 *                       above a specified threshold
	 * @param faultChannel   the channel set to {@code true} when the 2-bit pair is
	 *                       above a specified threshold
	 * @param threshold      the minimum status code considered an alarm (typically
	 *                       {@code 2} or {@code 3})
	 */
	public static void convertAlarm(int bit, Integer register,
				Channel<Level> warnChannel, Channel<Level> faultChannel, int threshold) {
		if (register == null) {
			warnChannel.setNextValue(null);
			faultChannel.setNextValue(null);
			return;
		}
		int alarm = convertAlarmInteger(bit, register);
		boolean warning = false;
		boolean fault = false;
		if (alarm > 0) {
			fault = alarm < threshold;
			warning = alarm >= threshold;
		}
		warnChannel.setNextValue(warning);
		faultChannel.setNextValue(fault);
	}

	/**
	 * Extracts the 2-bit alarm integer from a specific bit-pair position within a
	 * register.
	 *
	 * @param bit      the bit-pair index (0–14)
	 * @param register the 16-bit register value
	 * @return the 2-bit value as an integer
	 * @throws IllegalArgumentException if {@code bitPair} is not in range 0–14
	 */
	public static int convertAlarmInteger(int bit, int register) {
		if (bit < 0 || bit > 14) {
			throw new IllegalArgumentException("Alarm bit-pair has to be between 0 and 14");
		}
		return (register >> bit) & 0b11;
	}

}
