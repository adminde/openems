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
	 * Decodes a 16-bit alarm registers 2-bit pair and dispatches one of three
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
	 * Each channel parameter may be {@code null} if that code is not used for
	 * this alarm. When {@code register} is {@code null}, all non-null channels
	 * are reset to {@code null}.
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
	 * @param bit           the bit-pair index (0–14)
	 * @param register      the register value; if {@code null} all non-null
	 *                      channels are set to {@code null}
	 * @param code1Channel  channel set {@code true} on code 1, may be {@code null}
	 * @param code2Channel  channel set {@code true} on code 2, may be {@code null}
	 * @param code3Channel  channel set {@code true} on code 3, may be {@code null}
	 */
	public static void convertAlarm(int bit, Integer register,
			Channel<Level> code1Channel,
			Channel<Level> code2Channel,
			Channel<Level> code3Channel) {
		if (register == null) {
			if (code1Channel != null) {
				code1Channel.setNextValue(null);
			}
			if (code2Channel != null) {
				code2Channel.setNextValue(null);
			}
			if (code3Channel != null) {
				code3Channel.setNextValue(null);
			}
			return;
		}
		int code = convertAlarmInteger(bit, register);
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
