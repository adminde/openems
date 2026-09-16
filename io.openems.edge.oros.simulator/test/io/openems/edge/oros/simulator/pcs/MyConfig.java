package io.openems.edge.oros.simulator.pcs;

import io.openems.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {
	public static class Builder {
		private String id;
		private int maxActivePower;
		private float efficiency;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setMaxActivePower(int maxActivePower) {
			this.maxActivePower = maxActivePower;
			return this;
		}

		public Builder setEfficiency(float efficiency) {
			this.efficiency = efficiency;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	/**
	 * Create a configuration builder.
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
	public int maxActivePower() {
		return this.builder.maxActivePower;
	}

	@Override
	public float efficiency() {
		return this.builder.efficiency;
	}
}
