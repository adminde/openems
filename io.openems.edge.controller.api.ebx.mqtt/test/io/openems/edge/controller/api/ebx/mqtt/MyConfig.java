package io.openems.edge.controller.api.ebx.mqtt;

import io.openems.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String mqttId = "mqtt0";
		private String ctrlEssEbxId = "ctrlEssEbx0";
		private String topic = "";
		private int publishInterval = 1000;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setMqttId(String mqttId) {
			this.mqttId = mqttId;
			return this;
		}

		public Builder setCtrlEssEbxId(String ctrlEssEbxId) {
			this.ctrlEssEbxId = ctrlEssEbxId;
			return this;
		}

		public Builder setTopic(String topic) {
			this.topic = topic;
			return this;
		}

		public Builder setPublishInterval(int publishInterval) {
			this.publishInterval = publishInterval;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	/**
	 * Create a Config builder.
	 *
	 * @return a {@link Builder}
	 */
	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	@Override
	public String mqtt_id() {
		return this.builder.mqttId;
	}

	@Override
	public String ctrlEssEbx_id() {
		return this.builder.ctrlEssEbxId;
	}

	@Override
	public String topic() {
		return this.builder.topic;
	}

	@Override
	public int publishInterval() {
		return this.builder.publishInterval;
	}
}
