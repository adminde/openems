package io.openems.edge.controller.tess.surpluscharge;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.utils.ConfigUtils;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String tessId = "tess0";
		private double efficiency = 1.0;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setTessId(String v) {
			this.tessId = v;
			return this;
		}

		public Builder setEfficiency(double v) {
			this.efficiency = v;
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
	public String tess_id() {
		return this.builder.tessId;
	}

	@Override
	public double efficiency() {
		return this.builder.efficiency;
	}

	@Override
	public String tess_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.builder.tessId);
	}
}
