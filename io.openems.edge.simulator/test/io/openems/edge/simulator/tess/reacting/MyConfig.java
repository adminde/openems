package io.openems.edge.simulator.tess.reacting;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.utils.ConfigUtils;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private int volume = 300;
		private int initialSoc = 50;
		private float maxTemperature = 80;
		private float minTemperature = 20;
		private float maxTargetTemperature = 70;
		private float minTargetTemperature = 50;
		private String[] heatingIds = {};
		private String datasourceId = "";

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setVolume(int v) {
			this.volume = v;
			return this;
		}

		public Builder setInitialSoc(int v) {
			this.initialSoc = v;
			return this;
		}

		public Builder setMaxTemperature(float v) {
			this.maxTemperature = v;
			return this;
		}

		public Builder setMinTemperature(float v) {
			this.minTemperature = v;
			return this;
		}

		public Builder setMaxTargetTemperature(float v) {
			this.maxTargetTemperature = v;
			return this;
		}

		public Builder setMinTargetTemperature(float v) {
			this.minTargetTemperature = v;
			return this;
		}

		public Builder setHeatingIds(String... v) {
			this.heatingIds = v;
			return this;
		}

		public Builder setDatasourceId(String v) {
			this.datasourceId = v;
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
	public int volume() {
		return this.builder.volume;
	}

	@Override
	public int initialSoc() {
		return this.builder.initialSoc;
	}

	@Override
	public float maxTemperature() {
		return this.builder.maxTemperature;
	}

	@Override
	public float minTemperature() {
		return this.builder.minTemperature;
	}

	@Override
	public float maxTargetTemperature() {
		return this.builder.maxTargetTemperature;
	}

	@Override
	public float minTargetTemperature() {
		return this.builder.minTargetTemperature;
	}

	@Override
	public String[] heating_ids() {
		return this.builder.heatingIds;
	}

	@Override
	public String Heating_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.builder.heatingIds);
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
