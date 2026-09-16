package io.openems.edge.oros.simulator.auxiliary;

import io.openems.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {
	protected static class Builder {
		private String id;
		private String bmsId;
		private int standbyPower;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setBmsId(String bmsId) {
			this.bmsId = bmsId;
			return this;
		}

		public Builder setStandbyPower(int standbyPower) {
			this.standbyPower = standbyPower;
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
	public String bms_id() {
		return this.builder.bmsId;
	}

	@Override
	public int standbyPower() {
		return this.builder.standbyPower;
	}
}
