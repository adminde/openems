package io.openems.edge.simulator.timeofusetariff;

import io.openems.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String datasourceId;
		private String startTime;
		private String ancillaryCosts = "";
		private String calculateExpression = "";
		private String sellCalculateExpression = "";

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setDatasourceId(String datasourceId) {
			this.datasourceId = datasourceId;
			return this;
		}

		public Builder setStartTime(String startTime) {
			this.startTime = startTime;
			return this;
		}

		public Builder setAncillaryCosts(String ancillaryCosts) {
			this.ancillaryCosts = ancillaryCosts;
			return this;
		}

		public Builder setCalculateExpression(String calculateExpression) {
			this.calculateExpression = calculateExpression;
			return this;
		}

		public Builder setSellCalculateExpression(String sellCalculateExpression) {
			this.sellCalculateExpression = sellCalculateExpression;
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
	public String datasource_id() {
		return this.builder.datasourceId;
	}

	@Override
	public String startTime() {
		return this.builder.startTime;
	}

	@Override
	public String ancillaryCosts() {
		return this.builder.ancillaryCosts;
	}

	@Override
	public String calculateExpression() {
		return this.builder.calculateExpression;
	}

	@Override
	public String sellCalculateExpression() {
		return this.builder.sellCalculateExpression;
	}
}
