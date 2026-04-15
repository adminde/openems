package io.openems.edge.oros;

import static io.openems.common.channel.PersistencePriority.VERY_HIGH;
import static io.openems.common.channel.Unit.CUMULATED_WATT_HOURS;
import static io.openems.common.types.OpenemsType.LONG;

import io.openems.common.channel.AccessMode;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.LongReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveNatureTable;
import io.openems.edge.common.modbusslave.ModbusType;
import io.openems.edge.common.sum.Sum;

/**
 * Enables access to OROS Energy specific system data.
 */
public interface OrosEdgeSystem extends OpenemsComponent, ModbusSlave {

	public static final String SINGLETON_SERVICE_PID = "OROS.System";
	public static final String SINGLETON_COMPONENT_ID = "oros";

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/**
		 * Grid: Import-from-grid Energy.
		 *
		 * <ul>
		 * <li>Interface: OrosEdgeSystem (origin: Sum)
		 * <li>Type: Long
		 * <li>Unit: Wh_Σ
		 * </ul>
		 */
		GRID_IMPORT_ACTIVE_ENERGY(Doc.of(LONG)
				.unit(CUMULATED_WATT_HOURS)
				.persistencePriority(VERY_HIGH)
				.text("Accumulated electrical energy imported from the grid")),
		/**
		 * Grid: Export-to-grid Energy.
		 *
		 * <ul>
		 * <li>Interface: OrosEdgeSystem (origin: Sum)
		 * <li>Type: Long
		 * <li>Unit: Wh_Σ
		 * </ul>
		 */
		GRID_EXPORT_ACTIVE_ENERGY(Doc.of(LONG)
				.unit(CUMULATED_WATT_HOURS)
				.persistencePriority(VERY_HIGH)
				.text("Accumulated electrical energy exported to the grid"))
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

	/**
	 * Gets the Channel for {@link ChannelId#GRID_IMPORT_ACTIVE_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getGridImportActiveEnergyChannel() {
		return this.channel(ChannelId.GRID_IMPORT_ACTIVE_ENERGY);
	}

	/**
	 * Gets the Total Grid Import Active Energy in [Wh_Σ]. See
	 * {@link ChannelId#GRID_IMPORT_ACTIVE_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getGridImportActiveEnergy() {
		return this.getGridImportActiveEnergyChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#GRID_IMPORT_ACTIVE_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setGridImportActiveEnergy(Long value) {
		this.getGridImportActiveEnergyChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#GRID_IMPORT_ACTIVE_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setGridImportActiveEnergy(long value) {
		this.getGridImportActiveEnergyChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#GRID_EXPORT_ACTIVE_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getGridExportActiveEnergyChannel() {
		return this.channel(ChannelId.GRID_EXPORT_ACTIVE_ENERGY);
	}

	/**
	 * Gets the Total Grid Export Active Energy in [Wh_Σ]. See
	 * {@link ChannelId#GRID_EXPORT_ACTIVE_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getGridExportActiveEnergy() {
		return this.getGridExportActiveEnergyChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#GRID_EXPORT_ACTIVE_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setGridExportActiveEnergy(Long value) {
		this.getGridExportActiveEnergyChannel().setNextValue(value);
	}

	/**
	 * Used for Modbus/TCP Api Controller. Provides a Modbus table for the Channels
	 * of this Component.
	 *
	 * @param accessMode filters the Modbus-Records that should be shown
	 * @return the {@link ModbusSlaveNatureTable}
	 */
	public static ModbusSlaveNatureTable getModbusSlaveNatureTable(AccessMode accessMode) {
		return ModbusSlaveNatureTable.of(OrosEdgeSystem.class, accessMode, 220)
				.channel(0, Sum.ChannelId.ESS_SOC, ModbusType.UINT16)
				.channel(1, Sum.ChannelId.ESS_ACTIVE_POWER, ModbusType.FLOAT32)
				.float32Reserved(3) // ChannelId.ESS_MIN_ACTIVE_POWER
				.float32Reserved(5) // ChannelId.ESS_MAX_ACTIVE_POWER
				.channel(7, Sum.ChannelId.ESS_REACTIVE_POWER, ModbusType.FLOAT32)
				.float32Reserved(9) // ChannelId.ESS_MIN_REACTIVE_POWER
				.float32Reserved(11) // ChannelId.ESS_MAX_REACTIVE_POWER
				.channel(13, Sum.ChannelId.GRID_ACTIVE_POWER, ModbusType.FLOAT32)
				.channel(15, Sum.ChannelId.GRID_MIN_ACTIVE_POWER, ModbusType.FLOAT32)
				.channel(17, Sum.ChannelId.GRID_MAX_ACTIVE_POWER, ModbusType.FLOAT32)
				.float32Reserved(19) // ChannelId.GRID_REACTIVE_POWER
				.float32Reserved(21) // ChannelId.GRID_MIN_REACTIVE_POWER
				.float32Reserved(23) // ChannelId.GRID_MAX_REACTIVE_POWER
				.channel(25, Sum.ChannelId.PRODUCTION_ACTIVE_POWER, ModbusType.FLOAT32)
				.channel(27, Sum.ChannelId.PRODUCTION_MAX_ACTIVE_POWER, ModbusType.FLOAT32)
				.channel(29, Sum.ChannelId.PRODUCTION_AC_ACTIVE_POWER, ModbusType.FLOAT32)
				.float32Reserved(31) // ChannelId.PRODUCTION_MAX_AC_ACTIVE_POWER
				.float32Reserved(33) // ChannelId.PRODUCTION_AC_REACTIVE_POWER
				.float32Reserved(35) // ChannelId.PRODUCTION_MAX_AC_REACTIVE_POWER
				.channel(37, Sum.ChannelId.PRODUCTION_DC_ACTUAL_POWER, ModbusType.FLOAT32)
				.float32Reserved(39) // ChannelId.PRODUCTION_MAX_DC_ACTUAL_POWER
				.channel(41, Sum.ChannelId.CONSUMPTION_ACTIVE_POWER, ModbusType.FLOAT32)
				.channel(43, Sum.ChannelId.CONSUMPTION_MAX_ACTIVE_POWER, ModbusType.FLOAT32)
				.float32Reserved(45) // ChannelId.CONSUMPTION_REACTIVE_POWER
				.float32Reserved(47) // ChannelId.CONSUMPTION_MAX_REACTIVE_POWER
				.channel(49, Sum.ChannelId.ESS_ACTIVE_CHARGE_ENERGY, ModbusType.FLOAT64)
				.channel(53, Sum.ChannelId.ESS_ACTIVE_DISCHARGE_ENERGY, ModbusType.FLOAT64)
				.channel(57, OrosEdgeSystem.ChannelId.GRID_IMPORT_ACTIVE_ENERGY, ModbusType.FLOAT64)
				.channel(61, OrosEdgeSystem.ChannelId.GRID_EXPORT_ACTIVE_ENERGY, ModbusType.FLOAT64)
				.channel(65, Sum.ChannelId.PRODUCTION_ACTIVE_ENERGY, ModbusType.FLOAT64)
				.channel(69, Sum.ChannelId.PRODUCTION_AC_ACTIVE_ENERGY, ModbusType.FLOAT64)
				.channel(73, Sum.ChannelId.PRODUCTION_DC_ACTIVE_ENERGY, ModbusType.FLOAT64)
				.channel(77, Sum.ChannelId.CONSUMPTION_ACTIVE_ENERGY, ModbusType.FLOAT64)
				.channel(81, Sum.ChannelId.ESS_DC_CHARGE_ENERGY, ModbusType.FLOAT64)
				.channel(85, Sum.ChannelId.ESS_DC_DISCHARGE_ENERGY, ModbusType.FLOAT64)
				.channel(89, Sum.ChannelId.ESS_ACTIVE_POWER_L1, ModbusType.FLOAT32)
				.channel(91, Sum.ChannelId.ESS_ACTIVE_POWER_L2, ModbusType.FLOAT32)
				.channel(93, Sum.ChannelId.ESS_ACTIVE_POWER_L3, ModbusType.FLOAT32)
				.channel(95, Sum.ChannelId.GRID_ACTIVE_POWER_L1, ModbusType.FLOAT32)
				.channel(97, Sum.ChannelId.GRID_ACTIVE_POWER_L2, ModbusType.FLOAT32)
				.channel(99, Sum.ChannelId.GRID_ACTIVE_POWER_L3, ModbusType.FLOAT32)
				.channel(101, Sum.ChannelId.PRODUCTION_AC_ACTIVE_POWER_L1, ModbusType.FLOAT32)
				.channel(103, Sum.ChannelId.PRODUCTION_AC_ACTIVE_POWER_L2, ModbusType.FLOAT32)
				.channel(105, Sum.ChannelId.PRODUCTION_AC_ACTIVE_POWER_L3, ModbusType.FLOAT32)
				.channel(107, Sum.ChannelId.CONSUMPTION_ACTIVE_POWER_L1, ModbusType.FLOAT32)
				.channel(109, Sum.ChannelId.CONSUMPTION_ACTIVE_POWER_L2, ModbusType.FLOAT32)
				.channel(111, Sum.ChannelId.CONSUMPTION_ACTIVE_POWER_L3, ModbusType.FLOAT32)
				.channel(113, Sum.ChannelId.ESS_DISCHARGE_POWER, ModbusType.FLOAT32)
				.channel(115, Sum.ChannelId.GRID_MODE, ModbusType.ENUM16)
				.channel(116, Sum.ChannelId.GRID_MODE_OFF_GRID_TIME, ModbusType.FLOAT32)
				.channel(118, Sum.ChannelId.ESS_CAPACITY, ModbusType.FLOAT32)
				.build();
	}

}
