package io.openems.edge.ess.hyperstrong.thermal;

import io.openems.common.channel.Level;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;

public interface ThermalManagementSystem extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		THERMAL_MANAGEMENT_SYSTEM_RUN_MODE(Doc.of(RunMode.values())),
		THERMAL_MANAGEMENT_SYSTEM_RUN_MODE_TARGET(Doc.of(RunModeTarget.values())),

		THERMAL_MANAGEMENT_SYSTEM_FAULT_CODE(Doc.of(OpenemsType.INTEGER)),

		THERMAL_MANAGEMENT_COMMUNICATION_ENABLED(Doc.of(OpenemsType.BOOLEAN)),
		THERMAL_MANAGEMENT_COMMUNICATION_CONNECTED(Doc.of(OpenemsType.BOOLEAN)),
		THERMAL_MANAGEMENT_COMMUNICATION_ABNORMAL(Doc.of(Level.INFO)),
		THERMAL_MANAGEMENT_COMMUNICATION_FAULT(Doc.of(Level.WARNING)),

		THERMAL_MANAGEMENT_MAIN_CONTACTOR_STATE(Doc.of(OpenemsType.BOOLEAN)),
		THERMAL_MANAGEMENT_COMPRESSOR_STATE(Doc.of(OpenemsType.BOOLEAN)),
		THERMAL_MANAGEMENT_HEATING_STATE(Doc.of(OpenemsType.BOOLEAN)),

		THERMAL_MANAGEMENT_SYSTEM_RETURN_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)),
		THERMAL_MANAGEMENT_SYSTEM_SUPPLY_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)),

		THERMAL_MANAGEMENT_RETURN_PRESSURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIBAR)),
		THERMAL_MANAGEMENT_SUPPLY_PRESSURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIBAR)),
		;

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

}
