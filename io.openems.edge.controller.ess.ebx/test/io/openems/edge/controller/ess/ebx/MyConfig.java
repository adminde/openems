package io.openems.edge.controller.ess.ebx;

import io.openems.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String essId;
		private ReferenceMode referenceMode = ReferenceMode.ESS;
		private String meterId = "";
		private double rampRate = 20.0;
		private boolean alwaysApplyRamp = false;
		private int heartbeatTimeout = 10;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setEssId(String essId) {
			this.essId = essId;
			return this;
		}

		public Builder setReferenceMode(ReferenceMode referenceMode) {
			this.referenceMode = referenceMode;
			return this;
		}

		public Builder setMeterId(String meterId) {
			this.meterId = meterId;
			return this;
		}

		public Builder setRampRate(double rampRate) {
			this.rampRate = rampRate;
			return this;
		}

		public Builder setAlwaysApplyRamp(boolean alwaysApplyRamp) {
			this.alwaysApplyRamp = alwaysApplyRamp;
			return this;
		}

		public Builder setHeartbeatTimeout(int heartbeatTimeout) {
			this.heartbeatTimeout = heartbeatTimeout;
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
	public String ess_id() {
		return this.builder.essId;
	}

	@Override
	public ReferenceMode referenceMode() {
		return this.builder.referenceMode;
	}

	@Override
	public String meter_id() {
		return this.builder.meterId;
	}

	@Override
	public double rampRate() {
		return this.builder.rampRate;
	}

	@Override
	public boolean alwaysApplyRamp() {
		return this.builder.alwaysApplyRamp;
	}

	@Override
	public int heartbeatTimeout() {
		return this.builder.heartbeatTimeout;
	}
}
