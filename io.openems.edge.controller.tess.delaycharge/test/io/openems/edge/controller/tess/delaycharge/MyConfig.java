package io.openems.edge.controller.tess.delaycharge;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.utils.ConfigUtils;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String tessId = "tess0";
		private Mode mode = Mode.AUTOMATIC;
		private String manualTargetTime = "17:00";

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

		public Builder setMode(Mode v) {
			this.mode = v;
			return this;
		}

		public Builder setManualTargetTime(String v) {
			this.manualTargetTime = v;
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
	public Mode mode() {
		return this.builder.mode;
	}

	@Override
	public String manualTargetTime() {
		return this.builder.manualTargetTime;
	}

	@Override
	public String tess_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.builder.tessId);
	}
}
