package io.openems.edge.controller.api.ebx.mqtt;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.referencetarget.GenerateTargetsFromReferences;
import io.openems.edge.bridge.mqtt.api.BridgeMqtt;
import io.openems.edge.bridge.mqtt.api.BridgeMqtt.MqttSubscription;
import io.openems.edge.bridge.mqtt.api.MqttMessage;
import io.openems.edge.bridge.mqtt.api.MqttVersion;
import io.openems.edge.bridge.mqtt.api.QoS;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.controller.ess.ebx.ControllerEssEbx;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Api.Ebx.Mqtt", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@EventTopics({ //
		EdgeEventConstants.TOPIC_CYCLE_AFTER_WRITE //
})
@GenerateTargetsFromReferences({ "mqtt", "ctrl" })
public class ControllerApiEbxMqttImpl extends AbstractOpenemsComponent
		implements ControllerApiEbxMqtt, OpenemsComponent, EventHandler {

	private final Logger log = LoggerFactory.getLogger(ControllerApiEbxMqttImpl.class);

	@Reference
	private ComponentManager componentManager;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY, //
			target = "(&(id=${config.mqtt_id})(enabled=true))")
	private BridgeMqtt mqtt;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY, //
			target = "(&(id=${config.ctrlEssEbx_id})(enabled=true))")
	private ControllerEssEbx ctrl;

	private Config config;
	private MqttSubscription commandSubscription = null;

	private volatile Long receivedCommandSourceTime = null;

	private Instant lastPublish = null;

	public ControllerApiEbxMqttImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ControllerApiEbxMqtt.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		if (!config.enabled()) {
			return;
		}
		if (config.topic().isBlank()) {
			this.logError(this.log, "Topic root is not configured");
			return;
		}
		if (this.mqtt.getMqttVersion() != MqttVersion.V5) {
			this.logError(this.log, "The referenced bridge is not configured for MQTT 5");
			return;
		}

		this.commandSubscription = this.mqtt.subscribe(//
				EbxTopic.COMMAND_POWER.fullTopic(config.topic()), QoS.AT_LEAST_ONCE, this::handleCommandFrame);

		// Overwrite the retained telemetry topics once with the current channel
		// values, so pre-restart frames on the broker never outlive a restart. The
		// asset status reports no availability while the ESS is still starting, which
		// withholds EBX dispatch until the asset is genuinely ready.
		this.handlePublishAvailabilityAndConstraints();
	}

	@Override
	@Deactivate
	protected void deactivate() {
		if (this.commandSubscription != null) {
			this.commandSubscription.unsubscribe();
			this.commandSubscription = null;
		}
		super.deactivate();
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		if (event.getTopic().equals(EdgeEventConstants.TOPIC_CYCLE_AFTER_WRITE)) {
			this.handlePublishFrames();
		}
	}

	private void handleCommandFrame(MqttMessage message) {
		final EbxFrameCodec.PowerCommand frame;
		try {
			frame = EbxFrameCodec.decodePowerCommand(message.payload());
		} catch (IOException e) {
			this._setDecodeError(true);
			return;
		}
		if (frame.sourceTsMs() == 0) {
			this._setDecodeError(true);
			return;
		}

		// Duplicate and out-of-order detection relies on the source timestamp. The
		// baseline follows the received stream: an older frame is discarded but still
		// becomes the new baseline, so a sender clock step backwards costs one frame
		// instead of blocking the interface.
		var previous = this.receivedCommandSourceTime;
		this.receivedCommandSourceTime = frame.sourceTsMs();
		if (previous != null && frame.sourceTsMs() <= previous) {
			this._setFrameDiscarded(true);
			return;
		}
		this._setFrameDiscarded(false);
		this.ctrl.reportHeartbeat(Instant.ofEpochMilli(frame.sourceTsMs()));

		var powerSetpointKw = frame.powerSetpointKw();
		if (!Float.isFinite(powerSetpointKw)) {
			this._setDecodeError(true);
			return;
		}
		this._setDecodeError(false);
		this.ctrl.applyCommand(Math.round(powerSetpointKw * 1000), Instant.ofEpochMilli(frame.validUntilMs()));
	}

	private void handlePublishFrames() {
		var now = Instant.now(this.componentManager.getClock());
		// The 90 percent margin keeps scheduler jitter from sporadically doubling a
		// publication gap.
		if (this.lastPublish != null
				&& Duration.between(this.lastPublish, now).toMillis() < this.config.publishInterval() * 9L / 10L) {
			return;
		}
		this.lastPublish = now;
		this.handlePublishPower();
		this.handlePublishAvailabilityAndConstraints();
	}

	private void handlePublishPower() {
		try {
			// The values come from the process image of the ESS nature and the meter,
			// which the devices feed directly. A referenced meter defines the POC and
			// follows the grid-meter sign convention, so its value is inverted to the
			// interface convention of positive discharge. The frequency channel carries
			// millihertz, the interface wants hertz.
			var ess = this.ctrl.getEnergyStorageSystem();
			var meter = this.ctrl.getMeter();
			var meterPower = meter == null ? null : meter.getActivePower().get();
			var pocPower = meter == null //
					? toKilo(ess.getActivePower().get()) //
					: meterPower == null ? null : toKilo(-meterPower);
			var frequency = meter == null ? null : meter.getFrequency().get();
			var payload = EbxFrameCodec.encodePowerTelemetry(//
					this.sourceTimestamp(), //
					pocPower, //
					toKilo(ess.getActivePower().get()), //
					toKilo(ess.getDcDischargePower().get()), //
					frequency == null ? null : frequency / 1000f);
			this.mqtt.publish(EbxTopic.TELEMETRY_POWER.fullTopic(this.config.topic()), payload, QoS.AT_MOST_ONCE,
					false);
		} catch (IOException e) {
			this.logError(this.log, "Encoding power telemetry failed: " + e.getMessage());
		}
	}

	private void handlePublishAvailabilityAndConstraints() {
		try {
			// The asset status and available-power channels are read with next-value
			// semantics for coherence with this cycle's control decision. The energies
			// come from the process image of the ESS nature, which the device feeds
			// directly.
			var assetStatus = this.ctrl.getAssetStatusChannel().getNextValue().get();
			if (assetStatus != null && assetStatus < 0) {
				assetStatus = null;
			}
			var ess = this.ctrl.getEnergyStorageSystem();
			var availability = EbxFrameCodec.encodeAvailabilityTelemetry(//
					this.sourceTimestamp(), //
					assetStatus, //
					toKilo(ess.getAvailableDischargeEnergy().get()), //
					toKilo(ess.getAvailableChargeEnergy().get()), //
					toKilo(this.ctrl.getAvailableDischargePowerChannel().getNextValue().get()), //
					toKilo(this.ctrl.getAvailableChargePowerChannel().getNextValue().get()));
			this.mqtt.publish(EbxTopic.TELEMETRY_AVAILABILITY.fullTopic(this.config.topic()), availability,
					QoS.AT_MOST_ONCE, true);

			// The DSO constraint fields are omitted, which the interface defines as
			// unknown. The frame itself is still published, because omission is a
			// per-field statement, not a per-topic one.
			var constraints = EbxFrameCodec.encodeConstraintsTelemetry(//
					this.sourceTimestamp(), null, null);
			this.mqtt.publish(EbxTopic.TELEMETRY_CONSTRAINTS.fullTopic(this.config.topic()), constraints,
					QoS.AT_MOST_ONCE, true);
		} catch (IOException e) {
			this.logError(this.log, "Encoding availability telemetry failed: " + e.getMessage());
		}
	}

	private long sourceTimestamp() {
		return Instant.now(this.componentManager.getClock()).toEpochMilli();
	}

	private static Float toKilo(Integer value) {
		return value == null ? null : value / 1000f;
	}
}
