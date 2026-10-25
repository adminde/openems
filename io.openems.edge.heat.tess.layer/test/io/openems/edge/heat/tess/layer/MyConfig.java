package io.openems.edge.heat.tess.layer;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.utils.ConfigUtils;
import io.openems.edge.heat.tess.core.Connection;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private int volume = 300;
		private float maxTemperature = 80;
		private float targetTemperature = 70;
		private float minTemperature = 20;
		private String sensorChannelAddress = "thermometer0/Temperature";
		private float sensorHeight = 50;
		private Connection connection = Connection.DIRECT;
		private float supplyHeight = 90;
		private float returnHeight = 10;
		private String[] heatingIds = {};

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setVolume(int volume) {
			this.volume = volume;
			return this;
		}

		public Builder setMaxTemperature(float maxTemperature) {
			this.maxTemperature = maxTemperature;
			return this;
		}

		public Builder setTargetTemperature(float targetTemperature) {
			this.targetTemperature = targetTemperature;
			return this;
		}

		public Builder setMinTemperature(float minTemperature) {
			this.minTemperature = minTemperature;
			return this;
		}

		public Builder setSensorChannelAddress(String sensorChannelAddress) {
			this.sensorChannelAddress = sensorChannelAddress;
			return this;
		}

		public Builder setSensorHeight(float sensorHeight) {
			this.sensorHeight = sensorHeight;
			return this;
		}

		public Builder setConnection(Connection connection) {
			this.connection = connection;
			return this;
		}

		public Builder setSupplyHeight(float supplyHeight) {
			this.supplyHeight = supplyHeight;
			return this;
		}

		public Builder setReturnHeight(float returnHeight) {
			this.returnHeight = returnHeight;
			return this;
		}

		public Builder setHeatingIds(String... heatingIds) {
			this.heatingIds = heatingIds;
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
	public int volume() {
		return this.builder.volume;
	}

	@Override
	public float maxTemperature() {
		return this.builder.maxTemperature;
	}

	@Override
	public float targetTemperature() {
		return this.builder.targetTemperature;
	}

	@Override
	public float minTemperature() {
		return this.builder.minTemperature;
	}

	@Override
	public String sensorChannelAddress() {
		return this.builder.sensorChannelAddress;
	}

	@Override
	public float sensorHeight() {
		return this.builder.sensorHeight;
	}

	@Override
	public Connection connection() {
		return this.builder.connection;
	}

	@Override
	public float supplyHeight() {
		return this.builder.supplyHeight;
	}

	@Override
	public float returnHeight() {
		return this.builder.returnHeight;
	}

	@Override
	public String[] heating_ids() {
		return this.builder.heatingIds;
	}

	@Override
	public String Heating_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.builder.heatingIds);
	}
}
