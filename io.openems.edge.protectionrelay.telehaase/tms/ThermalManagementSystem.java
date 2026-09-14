package io.openems.edge.ess.hyperstrong.hypercube.tms;

import io.openems.common.channel.Level;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;

public interface ThermalManagementSystem extends OpenemsComponent, ModbusComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		RUN_MODE(Doc.of(RunMode.values())),
		RUN_MODE_TARGET(Doc.of(RunModeTarget.values())),

		MAIN_CONTACTOR_STATE(Doc.of(OpenemsType.BOOLEAN)),
		COMPRESSOR_STATE(Doc.of(OpenemsType.BOOLEAN)),
		HEATING_STATE(Doc.of(OpenemsType.BOOLEAN)),

		RETURN_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)),
		SUPPLY_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)),

		RETURN_PRESSURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIBAR)),
		SUPPLY_PRESSURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIBAR)),

		FAULT_CODE(Doc.of(OpenemsType.INTEGER)),

		COMMUNICATION_ENABLED(Doc.of(OpenemsType.BOOLEAN)),
		COMMUNICATION_CONNECTED(Doc.of(OpenemsType.BOOLEAN)),
		COMMUNICATION_ABNORMAL(Doc.of(Level.INFO)),
		COMMUNICATION_FAULT(Doc.of(Level.WARNING)),
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
