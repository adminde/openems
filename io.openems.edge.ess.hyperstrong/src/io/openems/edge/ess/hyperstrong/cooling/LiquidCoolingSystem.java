package io.openems.edge.ess.hyperstrong;

import io.openems.common.channel.Level;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;

public interface LiquidCoolingSystem extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		COOLING_SYSTEM_MODE(Doc.of(LiquidCoolingSystemMode.values())),

		COOLING_SYSTEM_FAULT_CODE(Doc.of(OpenemsType.INTEGER)),

		COOLING_SYSTEM_COMMUNICATION_ENABLED(Doc.of(OpenemsType.BOOLEAN)),
		COOLING_SYSTEM_COMMUNICATION_CONNECTED(Doc.of(OpenemsType.BOOLEAN)),
		COOLING_SYSTEM_COMMUNICATION_ABNORMAL(Doc.of(Level.INFO)),
		COOLING_SYSTEM_COMMUNICATION_FAULT(Doc.of(Level.WARNING)),

		COOLING_SYSTEM_MAIN_CONTACTOR_STATE(Doc.of(OpenemsType.BOOLEAN)),
		COOLING_SYSTEM_COMPRESSOR_STATE(Doc.of(OpenemsType.BOOLEAN)),
		COOLING_SYSTEM_HEATING_STATE(Doc.of(OpenemsType.BOOLEAN)),

		COOLING_SYSTEM_RETURN_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)),
		COOLING_SYSTEM_SUPPLY_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEZIDEGREE_CELSIUS)),

		COOLING_SYSTEM_RETURN_PRESSURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIBAR)),
		COOLING_SYSTEM_SUPPLY_PRESSURE(Doc.of(OpenemsType.INTEGER)
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
