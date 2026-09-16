package io.openems.edge.oros.simulator.bms;

import io.openems.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {
	public static class Builder {
		private String id;
		private int capacity;
		private int initialSoc;
		private float maxChargeVoltage;
		private float minDischargeVoltage;
		private int internalResistance;
		private float thermalManagementCoefficientOfPerformance;
		private int thermalManagementBasePower;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setCapacity(int capacity) {
			this.capacity = capacity;
			return this;
		}

		public Builder setInitialSoc(int initialSoc) {
			this.initialSoc = initialSoc;
			return this;
		}

		public Builder setMaxChargeVoltage(float maxChargeVoltage) {
			this.maxChargeVoltage = maxChargeVoltage;
			return this;
		}

		public Builder setMinDischargeVoltage(float minDischargeVoltage) {
			this.minDischargeVoltage = minDischargeVoltage;
			return this;
		}

		public Builder setInternalResistance(int internalResistance) {
			this.internalResistance = internalResistance;
			return this;
		}

		public Builder setThermalManagementCoefficientOfPerformance(float coefficientOfPerformance) {
			this.thermalManagementCoefficientOfPerformance = coefficientOfPerformance;
			return this;
		}

		public Builder setThermalManagementBasePower(int basePower) {
			this.thermalManagementBasePower = basePower;
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
	public int capacity() {
		return this.builder.capacity;
	}

	@Override
	public int initialSoc() {
		return this.builder.initialSoc;
	}

	@Override
	public float maxChargeVoltage() {
		return this.builder.maxChargeVoltage;
	}

	@Override
	public float minDischargeVoltage() {
		return this.builder.minDischargeVoltage;
	}

	@Override
	public int internalResistance() {
		return this.builder.internalResistance;
	}

	@Override
	public float thermalEfficiency() {
		return this.builder.thermalManagementCoefficientOfPerformance;
	}

	@Override
	public int thermalManagementPower() {
		return this.builder.thermalManagementBasePower;
	}

}
