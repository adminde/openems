package io.openems.shared.timescaledb;

import io.openems.common.channel.PersistencePriority;

/**
 * Maps the OpenEMS {@link PersistencePriority} enum onto the boolean Fast/Slow
 * Lane flag (`core`) used by the TimescaleDB schema.
 *
 * <p>
 * Only {@link PersistencePriority#VERY_HIGH} channels enter the Fast Lane
 * (`core = true`); everything else stays in the Slow Lane. Both the Edge and
 * Backend write paths use this single helper so they always agree on which lane
 * a channel belongs to.
 */
public final class Utils {

	private Utils() {
	}

	/**
	 * Whether a channel of the given {@link PersistencePriority} belongs in the
	 * Fast Lane.
	 *
	 * @param p the persistence priority
	 * @return {@code true} only for {@link PersistencePriority#VERY_HIGH}
	 */
	public static boolean isCore(PersistencePriority p) {
		return p == PersistencePriority.VERY_HIGH;
	}
}
