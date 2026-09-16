package io.openems.edge.controller.api.ebx.mqtt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

import org.junit.Test;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;

import io.openems.edge.controller.api.ebx.mqtt.EbxFrameCodec.PowerCommand;

public class EbxFrameCodecTest {

	@Test
	public void testPowerCommandRoundtrip() throws Exception {
		var command = new PowerCommand(1_755_900_000_000L, 1_755_900_005_000L, 123.5f);
		var decoded = EbxFrameCodec.decodePowerCommand(EbxFrameCodec.encodePowerCommand(command));
		assertEquals(command, decoded);
	}

	@Test
	public void testPowerCommandZeroSetpointIsSuppressedOnTheWire() throws Exception {
		var command = new PowerCommand(1_755_900_000_000L, 1_755_900_005_000L, 0f);
		var payload = EbxFrameCodec.encodePowerCommand(command);
		var fields = new ArrayList<Integer>();
		var in = CodedInputStream.newInstance(payload);
		int tag;
		while ((tag = in.readTag()) != 0) {
			fields.add(WireFormat.getTagFieldNumber(tag));
			in.skipField(tag);
		}
		assertEquals(java.util.List.of(1, 2), fields);
		var decoded = EbxFrameCodec.decodePowerCommand(payload);
		assertEquals(0f, decoded.powerSetpointKw(), 0f);
		assertEquals(command, decoded);
	}

	@Test
	public void testUnknownAdditiveFieldIsIgnored() throws Exception {
		var command = new PowerCommand(1_755_900_000_000L, 1_755_900_005_000L, -50f);
		var bytes = new ByteArrayOutputStream();
		bytes.write(EbxFrameCodec.encodePowerCommand(command));
		var out = CodedOutputStream.newInstance(bytes);
		out.writeUInt32(99, 7);
		out.flush();
		var decoded = EbxFrameCodec.decodePowerCommand(bytes.toByteArray());
		assertEquals(command, decoded);
	}

	@Test
	public void testConstraintsOmitsUnknownFields() throws Exception {
		var payload = EbxFrameCodec.encodeConstraintsTelemetry(1_755_900_000_000L, null, null);
		var fields = new ArrayList<Integer>();
		var in = CodedInputStream.newInstance(payload);
		int tag;
		while ((tag = in.readTag()) != 0) {
			fields.add(WireFormat.getTagFieldNumber(tag));
			in.skipField(tag);
		}
		assertEquals(java.util.List.of(1), fields);
	}

	@Test
	public void testAvailabilityRoundtrip() throws Exception {
		var payload = EbxFrameCodec.encodeAvailabilityTelemetry(1_755_900_000_000L, //
				255, 800f, 200f, 500f, 500f);
		var in = CodedInputStream.newInstance(payload);
		Integer assetStatus = null;
		Float energyDischarge = null;
		Float powerCharge = null;
		int tag;
		while ((tag = in.readTag()) != 0) {
			switch (WireFormat.getTagFieldNumber(tag)) {
			case 2 -> assetStatus = in.readEnum();
			case 3 -> energyDischarge = in.readFloat();
			case 6 -> powerCharge = in.readFloat();
			default -> in.skipField(tag);
			}
		}
		assertEquals(Integer.valueOf(255), assetStatus);
		assertEquals(800f, energyDischarge, 0.001f);
		assertEquals(500f, powerCharge, 0.001f);
	}

	@Test
	public void testPowerTelemetryOmitsMissingFields() throws Exception {
		var payload = EbxFrameCodec.encodePowerTelemetry(1_755_900_000_000L, 248.5f, 255f, null, 49.998f);
		var in = CodedInputStream.newInstance(payload);
		Float pocPower = null;
		Float bessAcPower = null;
		Float bessDcPower = null;
		Float frequency = null;
		int tag;
		while ((tag = in.readTag()) != 0) {
			switch (WireFormat.getTagFieldNumber(tag)) {
			case 2 -> pocPower = in.readFloat();
			case 3 -> bessAcPower = in.readFloat();
			case 4 -> bessDcPower = in.readFloat();
			case 5 -> frequency = in.readFloat();
			default -> in.skipField(tag);
			}
		}
		assertEquals(248.5f, pocPower, 0.001f);
		assertEquals(255f, bessAcPower, 0.001f);
		assertNull(bessDcPower);
		assertEquals(49.998f, frequency, 0.001f);
	}
}
