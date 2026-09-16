package io.openems.edge.controller.ess.ebx;

/**
 * The control reference point of the EBX dispatch.
 *
 * <p>
 * Setpoint and reported actual power always use the same reference. Mixing
 * them would make the reported actual deviate from the setpoint by the
 * auxiliary load, which EBX reads as a setpoint that was not executed.
 */
public enum ReferenceMode {
	/**
	 * The setpoint is applied to the storage system directly and the actual power
	 * is reported from the ESS AC power.
	 */
	ESS,
	/**
	 * The setpoint is regulated against a referenced meter and the actual power is
	 * reported from that meter.
	 */
	METER;
}
