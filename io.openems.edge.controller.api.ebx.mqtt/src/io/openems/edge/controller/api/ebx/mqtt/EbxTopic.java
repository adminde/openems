package io.openems.edge.controller.api.ebx.mqtt;

/**
 * The topic suffixes of the EBX VPP interface below the registered topic root.
 */
public enum EbxTopic {
	COMMAND_POWER("command/power/v1"), //
	TELEMETRY_POWER("telemetry/power/v1"), //
	TELEMETRY_AVAILABILITY("telemetry/availability/v1"), //
	TELEMETRY_CONSTRAINTS("telemetry/constraints/v1");

	private final String suffix;

	private EbxTopic(String suffix) {
		this.suffix = suffix;
	}

	/**
	 * Builds the full topic below the given topic root.
	 *
	 * @param topicRoot the topic root from the EBX registration response
	 * @return the full topic
	 */
	public String fullTopic(String topicRoot) {
		return topicRoot + "/" + this.suffix;
	}
}
