package io.openems.edge.simulator.heat.pump.reacting;

import io.openems.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private int thermalPower = 4000;
		private boolean modulating = false;
		private int minThermalPower = 2000;
		private float cop = 3.5f;
		private int minRuntime = 15;
		private int hysteresis = 0;

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

		public Builder setModulating(boolean v) {
			this.modulating = v;
			return this;
		}

		public Builder setMinThermalPower(int v) {
			this.minThermalPower = v;
			return this;
		}

		public Builder setCop(float v) {
			this.cop = v;
			return this;
		}

		public Builder setMinRuntime(int v) {
			this.minRuntime = v;
			return this;
		}

		public Builder setHysteresis(int v) {
			this.hysteresis = v;
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
	public boolean modulating() {
		return this.builder.modulating;
	}

	@Override
	public int minThermalPower() {
		return this.builder.minThermalPower;
	}

	@Override
	public float cop() {
		return this.builder.cop;
	}

	@Override
	public int minRuntime() {
		return this.builder.minRuntime;
	}

	@Override
	public int hysteresis() {
		return this.builder.hysteresis;
	}
}
