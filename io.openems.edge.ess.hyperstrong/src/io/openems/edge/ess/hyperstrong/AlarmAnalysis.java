package io.openems.edge.ess.hyperstrong;

import io.openems.common.channel.Level;
import io.openems.edge.common.channel.Channel;

/**
 * Utility for decoding HyperStrong Alarm registers.
 *
 * <p>
 * Each 32-bit alarm register is divided into 16 two-bit pairs. Each pair
 * may encode the severity of one alarm as an integer status code:
 * <ul>
 * <li>{@code 0} = no alarm</li>
 * <li>{@code ≥ threshold} = alarm</li>
 * </ul>
 */
public class AlarmAnalysis {

	/**
	 * Decodes a single bit of a 32-bit alarm register into one {@link Level}
	 * StateChannel.
	 *
	 * <p>
	 * Used for alarm registers that encode each alarm as a single flag bit
	 * ({@code 1} = active) rather than as a 2-bit severity code. The channel is set
	 * {@code true} when the bit is set.
	 *
	 * @param bit          the bit index (0–31)
	 * @param register     the 32-bit register value. If {@code null} the channel is
	 *                     set to {@code null}
	 * @param channel 	   the channel set {@code true} when the bit is set, may be
	 *                     {@code null}
	 */
	public static void decodeAlarm(int bit, long register, Channel<Level> channel) {
		if (bit < 0 || bit > 31) {
			throw new IllegalArgumentException("Alarm bit has to be between 0 and 31");
		}
		if (channel != null) {
			channel.setNextValue(((register >> bit) & 0b1) == 1);
		}
	}

	/**
	 * Decodes a 32-bit alarm register's 2-bit pair and dispatches one of three
	 * severity-channels based on the encoded code value.
	 *
	 * <p>
	 * Each 2-bit pair is interpreted as an integer status code:
	 * <ul>
	 * <li>{@code 0} = no alarm (all channels false)</li>
	 * <li>{@code 1} = {@code code1Channel} is set true</li>
	 * <li>{@code 2} = {@code code2Channel} is set true</li>
	 * <li>{@code 3} = {@code code3Channel} is set true</li>
	 * </ul>
	 *
	 * <p>
	 * The mapping of code values to OpenEMS {@link Level} severities is encoded
	 * by the caller via the channel's {@code Doc.of(Level.…)}. Common
	 * HyperStrong patterns:
	 * <ul>
	 * <li>Variant A (Default, {@code 0:Normal, 1:Fault, 2~3:Warning}): pass
	 *     channels with Levels FAULT / INFO / WARNING.</li>
	 * <li>Variant A-Strict (safety-critical): pass channels with Levels
	 *     FAULT / WARNING / WARNING (suffixed {@code _SEVERE_WARNING}).</li>
	 * <li>Variant B ({@code 1~3:Fault}): pass channels with Levels
	 *     FAULT / FAULT / FAULT (suffixed {@code _SEVERE_FAULT} /
	 *     {@code _CRITICAL_FAULT}).</li>
	 * <li>Variant C ({@code 1~2:Fault, 3:Warning}): pass channels with Levels
	 *     FAULT / FAULT / WARNING.</li>
	 * </ul>
	 *
	 * @param bit           the bit-pair index (0–30)
	 * @param register      the register value
	 * @param code1Channel  channel set {@code true} on code 1
	 * @param code2Channel  channel set {@code true} on code 2
	 * @param code3Channel  channel set {@code true} on code 3
	 */
	public static void decodeAlarm(int bit, long register,
			Channel<Level> code1Channel,
			Channel<Level> code2Channel,
			Channel<Level> code3Channel) {
		int code = decodeAlarmInteger(bit, register);
		if (code1Channel != null) {
			code1Channel.setNextValue(code == 1);
		}
		if (code2Channel != null) {
			code2Channel.setNextValue(code == 2);
		}
		if (code3Channel != null) {
			code3Channel.setNextValue(code == 3);
		}
	}

	/**
	 * Extracts the 2-bit alarm integer from a specific bit-pair position within a
	 * register.
	 *
	 * @param bit      the bit-pair index (0–30)
	 * @param register the 32-bit register value
	 * @return the 2-bit value as an integer
	 * @throws IllegalArgumentException if {@code bitPair} is not in range 0–30
	 */
	public static int decodeAlarmInteger(int bit, long register) {
		if (bit < 0 || bit > 30) {
			throw new IllegalArgumentException("Alarm bit-pair has to be between 0 and 30");
		}
		return (int) ((register >> bit) & 0b11);
	}

}
