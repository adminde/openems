package io.openems.edge.simulator.heatpump.reacting;

import io.openems.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private int thermalPower = 4000;
		private float cop = 3.5f;
		private float supplyTemperature = 35;
		private float returnTemperature = 30;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setThermalPower(int v) {
			this.thermalPower = v;
			return this;
		}

		public Builder setCop(float v) {
			this.cop = v;
			return this;
		}

		public Builder setSupplyTemperature(float v) {
			this.supplyTemperature = v;
			return this;
		}

		public Builder setReturnTemperature(float v) {
			this.returnTemperature = v;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	@Override
	public int thermalPower() {
		return this.builder.thermalPower;
	}

	@Override
	public float cop() {
		return this.builder.cop;
	}

	@Override
	public float supplyTemperature() {
		return this.builder.supplyTemperature;
	}

	@Override
	public float returnTemperature() {
		return this.builder.returnTemperature;
	}
}
