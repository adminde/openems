package io.openems.edge.ess.rct.cess.batteryinverter;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.edge.common.startstop.StartStopConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private StartStopConfig startStop;
		private String modbusId;
		private String bmsId;
		private String[] chargerIds;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setStartStop(StartStopConfig startStop) {
			this.startStop = startStop;
			return this;
		}

		public Builder setModbusId(String modbusId) {
			this.modbusId = modbusId;
			return this;
		}

		public Builder setBmsId(String bmsId) {
			this.bmsId = bmsId;
			return this;
		}

		public Builder setChargerIds(String... chargerIds) {
			this.chargerIds = chargerIds;
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
	public StartStopConfig startStop() {
		return this.builder.startStop;
	}

	@Override
	public String modbus_id() {
		return this.builder.modbusId;
	}

	@Override
	public String bms_id() {
		return this.builder.bmsId;
	}

	@Override
	public String[] charger_ids() {
		return this.builder.chargerIds;
	}

}
