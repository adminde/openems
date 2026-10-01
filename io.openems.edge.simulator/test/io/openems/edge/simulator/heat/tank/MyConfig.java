package io.openems.edge.simulator.heat.tank;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.utils.ConfigUtils;
import io.openems.edge.heat.tess.core.Connection;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String thermalEssId = "tess0";
		private int volume = 300;
		private int nodes = 10;
		private float initialTemperature = 40;
		private float sensorHeight = 50;
		private Connection connection = Connection.DIRECT;
		private float supplyHeight = 90;
		private float returnHeight = 10;
		private float consumerReturnTemperature = 30;
		private String datasourceId = "";

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setThermalEssId(String thermalEssId) {
			this.thermalEssId = thermalEssId;
			return this;
		}

		public Builder setVolume(int volume) {
			this.volume = volume;
			return this;
		}

		public Builder setNodes(int nodes) {
			this.nodes = nodes;
			return this;
		}

		public Builder setInitialTemperature(float initialTemperature) {
			this.initialTemperature = initialTemperature;
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

		public Builder setConsumerReturnTemperature(float consumerReturnTemperature) {
			this.consumerReturnTemperature = consumerReturnTemperature;
			return this;
		}

		public Builder setDatasourceId(String datasourceId) {
			this.datasourceId = datasourceId;
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
	public String thermalEssId() {
		return this.builder.thermalEssId;
	}

	@Override
	public int volume() {
		return this.builder.volume;
	}

	@Override
	public int nodes() {
		return this.builder.nodes;
	}

	@Override
	public float initialTemperature() {
		return this.builder.initialTemperature;
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
	public float consumerReturnTemperature() {
		return this.builder.consumerReturnTemperature;
	}

	@Override
	public String datasource_id() {
		return this.builder.datasourceId;
	}

	@Override
	public String datasource_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.builder.datasourceId);
	}
}
