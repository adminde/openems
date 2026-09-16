package io.openems.edge.controller.api.ebx.mqtt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;

/**
 * Encodes and decodes the Protobuf frames of the EBX VPP interface.
 *
 * <p>
 * The field numbers follow the official schema of the interface, package
 * {@code ebx.vpp.mqtt.v1}, a copy of which lives as {@code frames.proto} in
 * this bundle and has to be kept in sync by hand. Unknown fields are skipped
 * on decode, as the interface requires for compatible schema versions, and
 * absent proto3 scalar fields decode as their default value. The common
 * envelope carries only the source timestamp, ordering and duplicate
 * detection rely on it.
 */
public final class EbxFrameCodec {

	private static final int ENVELOPE_SOURCE_TS_MS = 1;

	private static final int COMMAND_VALID_UNTIL_MS = 2;
	private static final int COMMAND_POWER_SETPOINT_KW = 3;

	private static final int POWER_POC_POWER_ACTUAL_AC_KW = 2;
	private static final int POWER_BESS_POWER_ACTUAL_AC_KW = 3;
	private static final int POWER_BESS_POWER_ACTUAL_DC_KW = 4;
	private static final int POWER_FREQUENCY_HZ = 5;

	private static final int AVAILABILITY_ASSET_STATUS = 2;
	private static final int AVAILABILITY_ENERGY_DISCHARGE_KWH = 3;
	private static final int AVAILABILITY_ENERGY_CHARGE_KWH = 4;
	private static final int AVAILABILITY_POWER_DISCHARGE_KW = 5;
	private static final int AVAILABILITY_POWER_CHARGE_KW = 6;

	private static final int CONSTRAINTS_ALLOWED_EXPORT_POWER_KW = 2;
	private static final int CONSTRAINTS_ALLOWED_IMPORT_POWER_KW = 3;

	/**
	 * A decoded frame of the command topic.
	 *
	 * @param sourceTsMs      the source timestamp as Unix epoch milliseconds
	 * @param validUntilMs    the hard execution expiry as Unix epoch milliseconds
	 * @param powerSetpointKw the aggregate active-power target in [kW]. The field
	 *                        is not optional in the schema, so an absent field
	 *                        decodes as 0 following proto3 scalar semantics.
	 */
	public record PowerCommand(long sourceTsMs, long validUntilMs, float powerSetpointKw) {
	}

	private EbxFrameCodec() {
	}

	/**
	 * Encodes a frame of the command topic, the counterpart of
	 * {@link #decodePowerCommand(byte[])} for tests and simulation.
	 *
	 * @param command the {@link PowerCommand}
	 * @return the encoded payload
	 * @throws IOException on encoding error
	 */
	public static byte[] encodePowerCommand(PowerCommand command) throws IOException {
		var bytes = new ByteArrayOutputStream();
		var out = CodedOutputStream.newInstance(bytes);
		out.writeInt64(ENVELOPE_SOURCE_TS_MS, command.sourceTsMs());
		out.writeInt64(COMMAND_VALID_UNTIL_MS, command.validUntilMs());
		if (command.powerSetpointKw() != 0f) {
			out.writeFloat(COMMAND_POWER_SETPOINT_KW, command.powerSetpointKw());
		}
		out.flush();
		return bytes.toByteArray();
	}

	/**
	 * Decodes a frame of the command topic.
	 *
	 * @param payload the raw payload
	 * @return the decoded {@link PowerCommand}
	 * @throws IOException on a payload that is no valid Protobuf message
	 */
	public static PowerCommand decodePowerCommand(byte[] payload) throws IOException {
		var in = CodedInputStream.newInstance(payload);
		var sourceTsMs = 0L;
		var validUntilMs = 0L;
		var powerSetpointKw = 0f;
		int tag;
		while ((tag = in.readTag()) != 0) {
			switch (WireFormat.getTagFieldNumber(tag)) {
			case ENVELOPE_SOURCE_TS_MS -> sourceTsMs = in.readInt64();
			case COMMAND_VALID_UNTIL_MS -> validUntilMs = in.readInt64();
			case COMMAND_POWER_SETPOINT_KW -> powerSetpointKw = in.readFloat();
			default -> in.skipField(tag);
			}
		}
		return new PowerCommand(sourceTsMs, validUntilMs, powerSetpointKw);
	}

	/**
	 * Encodes a frame of the power telemetry topic.
	 *
	 * @param sourceTsMs          the source timestamp as Unix epoch milliseconds
	 * @param pocPowerActualAcKw  the actual active power at the POC in [kW],
	 *                            omitted when null
	 * @param bessPowerActualAcKw the actual AC power of the battery in [kW],
	 *                            omitted when null
	 * @param bessPowerActualDcKw the actual DC power of the battery in [kW],
	 *                            omitted when null
	 * @param frequencyHz         the locally measured grid frequency in [Hz],
	 *                            omitted when null
	 * @return the encoded payload
	 * @throws IOException on encoding error
	 */
	public static byte[] encodePowerTelemetry(long sourceTsMs, Float pocPowerActualAcKw, Float bessPowerActualAcKw,
			Float bessPowerActualDcKw, Float frequencyHz) throws IOException {
		var bytes = new ByteArrayOutputStream();
		var out = CodedOutputStream.newInstance(bytes);
		out.writeInt64(ENVELOPE_SOURCE_TS_MS, sourceTsMs);
		if (pocPowerActualAcKw != null) {
			out.writeFloat(POWER_POC_POWER_ACTUAL_AC_KW, pocPowerActualAcKw);
		}
		if (bessPowerActualAcKw != null) {
			out.writeFloat(POWER_BESS_POWER_ACTUAL_AC_KW, bessPowerActualAcKw);
		}
		if (bessPowerActualDcKw != null) {
			out.writeFloat(POWER_BESS_POWER_ACTUAL_DC_KW, bessPowerActualDcKw);
		}
		if (frequencyHz != null) {
			out.writeFloat(POWER_FREQUENCY_HZ, frequencyHz);
		}
		out.flush();
		return bytes.toByteArray();
	}

	/**
	 * Encodes a frame of the availability telemetry topic.
	 *
	 * @param sourceTsMs                  the source timestamp as Unix epoch
	 *                                    milliseconds
	 * @param assetStatus                 the asset status enum value, omitted when
	 *                                    null
	 * @param availableEnergyDischargeKwh the energy available for discharge in
	 *                                    [kWh], omitted when null
	 * @param availableEnergyChargeKwh    the energy available for charge in [kWh],
	 *                                    omitted when null
	 * @param availablePowerDischargeKw   the currently available discharge power
	 *                                    in [kW], omitted when null
	 * @param availablePowerChargeKw      the currently available charge power in
	 *                                    [kW], omitted when null
	 * @return the encoded payload
	 * @throws IOException on encoding error
	 */
	public static byte[] encodeAvailabilityTelemetry(long sourceTsMs, Integer assetStatus,
			Float availableEnergyDischargeKwh, Float availableEnergyChargeKwh, Float availablePowerDischargeKw,
			Float availablePowerChargeKw) throws IOException {
		var bytes = new ByteArrayOutputStream();
		var out = CodedOutputStream.newInstance(bytes);
		out.writeInt64(ENVELOPE_SOURCE_TS_MS, sourceTsMs);
		if (assetStatus != null) {
			out.writeEnum(AVAILABILITY_ASSET_STATUS, assetStatus);
		}
		if (availableEnergyDischargeKwh != null) {
			out.writeFloat(AVAILABILITY_ENERGY_DISCHARGE_KWH, availableEnergyDischargeKwh);
		}
		if (availableEnergyChargeKwh != null) {
			out.writeFloat(AVAILABILITY_ENERGY_CHARGE_KWH, availableEnergyChargeKwh);
		}
		if (availablePowerDischargeKw != null) {
			out.writeFloat(AVAILABILITY_POWER_DISCHARGE_KW, availablePowerDischargeKw);
		}
		if (availablePowerChargeKw != null) {
			out.writeFloat(AVAILABILITY_POWER_CHARGE_KW, availablePowerChargeKw);
		}
		out.flush();
		return bytes.toByteArray();
	}

	/**
	 * Encodes a frame of the constraints telemetry topic.
	 *
	 * @param sourceTsMs           the source timestamp as Unix epoch milliseconds
	 * @param allowedExportPowerKw the maximum permitted export power at the POC in
	 *                             [kW], omitted when null which the interface
	 *                             defines as unknown
	 * @param allowedImportPowerKw the maximum permitted import power at the POC in
	 *                             [kW], omitted when null which the interface
	 *                             defines as unknown
	 * @return the encoded payload
	 * @throws IOException on encoding error
	 */
	public static byte[] encodeConstraintsTelemetry(long sourceTsMs, Float allowedExportPowerKw,
			Float allowedImportPowerKw) throws IOException {
		var bytes = new ByteArrayOutputStream();
		var out = CodedOutputStream.newInstance(bytes);
		out.writeInt64(ENVELOPE_SOURCE_TS_MS, sourceTsMs);
		if (allowedExportPowerKw != null) {
			out.writeFloat(CONSTRAINTS_ALLOWED_EXPORT_POWER_KW, allowedExportPowerKw);
		}
		if (allowedImportPowerKw != null) {
			out.writeFloat(CONSTRAINTS_ALLOWED_IMPORT_POWER_KW, allowedImportPowerKw);
		}
		out.flush();
		return bytes.toByteArray();
	}
}
