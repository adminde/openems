package io.openems.edge.heat.tess.cluster;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.edge.heat.tess.api.SocAveragingMethod;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {
	protected static class Builder {
		private String id;
		private String[] tessIds;
		private SocAveragingMethod socAveragingMethod = SocAveragingMethod.ARITHMETIC;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setTessIds(String... tessIds) {
			this.tessIds = tessIds;
			return this;
		}

		public Builder setSocAveragingMethod(SocAveragingMethod socAveragingMethod) {
			this.socAveragingMethod = socAveragingMethod;
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
	public String[] tess_ids() {
		return this.builder.tessIds;
	}

	@Override
	public SocAveragingMethod socAveragingMethod() {
		return this.builder.socAveragingMethod;
	}
}
