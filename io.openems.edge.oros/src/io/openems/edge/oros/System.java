package io.openems.edge.oros;

import static io.openems.common.channel.PersistencePriority.VERY_LOW;
import static io.openems.common.channel.Unit.CUMULATED_SECONDS;
import static io.openems.common.channel.Unit.CUMULATED_WATT_HOURS;
import static io.openems.common.channel.Unit.VOLT_AMPERE_REACTIVE;
import static io.openems.common.channel.Unit.VOLT_AMPERE_REACTIVE_HOURS;
import static io.openems.common.channel.Unit.WATT;
import static io.openems.common.types.OpenemsType.INTEGER;
import static io.openems.common.types.OpenemsType.LONG;

import io.openems.common.channel.AccessMode;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.LongReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveNatureTable;
import io.openems.edge.common.modbusslave.ModbusType;
import io.openems.edge.common.sum.GridMode;

/**
 * Enables access to OROS Energy specific system data.
 */
public interface System extends OpenemsComponent, ModbusSlave {

	public static final String SINGLETON_SERVICE_PID = "Core.System";
	public static final String SINGLETON_COMPONENT_ID = "system";

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Grid-Mode.
		 *
		 * <ul>
		 * <li>Interface: Sum
		 * <li>Type: Integer
		 * <li>Values:
		 * <ul>
		 * <li>'-1' = UNDEFINED
		 * <li>'1' = On-Grid
		 * <li>'2' = Off-Grid
		 * <li>'3' = 'Off-Grid Genset'
		 * </ul>
		 * </ul>
		 */
		GRID_MODE(Doc.of(GridMode.values())
				.persistencePriority(VERY_LOW)),

		/**
		 * Cumulated Off-Grid time.
		 *
		 * <ul>
		 * <li>Interface: Sum
		 * <li>Type: Cumulated Seconds
		 * </ul>
		 */
		GRID_MODE_OFF_GRID_TIME(Doc.of(LONG)
				.unit(CUMULATED_SECONDS)
				.persistencePriority(VERY_LOW)
				.text("Total Off-Grid time")),

		/**
		 * Grid: Active Power.
		 *
		 * <ul>
		 * <li>Interface: Sum (origin: ElectricityMeter))
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: Negative values for Export (power that is 'leaving the
		 * system', e.g. feed-to-grid); Positive for Import (power that is 'entering
		 * the system')
		 * </ul>
		 */
		GRID_ACTIVE_POWER(Doc.of(INTEGER)
				.unit(WATT)
				.persistencePriority(VERY_LOW)
				.text("Active power of the Grid. "
						+ "Negative values for export-to-grid; Positive for import-from-grid")),

		/**
		 * Grid: Active Power L1.
		 *
		 * <ul>
		 * <li>Interface: Sum (origin: ElectricityMeter)
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: Negative values for Export (power that is 'leaving the
		 * system', e.g. feed-to-grid); Positive for Import (power that is 'entering
		 * the system')
		 * </ul>
		 */
		GRID_ACTIVE_POWER_L1(Doc.of(INTEGER)
				.unit(WATT)
				.persistencePriority(VERY_LOW)
				.text("Active power of the Grid on phase L1. "
						+ "Negative values for export-to-grid; Positive for import-from-grid")),

		/**
		 * Grid: Active Power L2.
		 *
		 * <ul>
		 * <li>Interface: Sum (origin: ElectricityMeter)
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: Negative values for Export (power that is 'leaving the
		 * system', e.g. feed-to-grid); Positive for Import (power that is 'entering
		 * the system')
		 * </ul>
		 */
		GRID_ACTIVE_POWER_L2(Doc.of(INTEGER)
				.unit(WATT)
				.persistencePriority(VERY_LOW)
				.text("Active power of the Grid on phase L2. "
						+ "Negative values for export-to-grid; Positive for import-from-grid")),

		/**
		 * Grid: Active Power L3.
		 *
		 * <ul>
		 * <li>Interface: Sum (origin: ElectricityMeter)
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: Negative values for Export (power that is 'leaving the
		 * system', e.g. feed-to-grid); Positive for Import (power that is 'entering
		 * the system')
		 * </ul>
		 */
		GRID_ACTIVE_POWER_L3(Doc.of(INTEGER)
				.unit(WATT)
				.persistencePriority(VERY_LOW)
				.text("Active power of the Grid on phase L3. "
						+ "Negative values for export-to-grid; Positive for import-from-grid")),

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
				.persistencePriority(VERY_LOW)
				.text("Accumulated electrical active energy imported from the grid")),

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
				.persistencePriority(VERY_LOW)
				.text("Accumulated electrical active energy exported to the grid")),

		/**
		 * Grid: Reactive Power.
		 *
		 * <ul>
		 * <li>Interface: Sum (origin: ElectricityMeter))
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: Negative values for capacitive-; positive for inductive reactive power
		 * </ul>
		 */
		GRID_REACTIVE_POWER(Doc.of(INTEGER)//
				.unit(VOLT_AMPERE_REACTIVE)//
				.persistencePriority(VERY_LOW)//
				.text("Reactive power of the Grid. "//
						+ "Negative values for capacitive-; positive for inductive reactive power")),

		/**
		 * Grid: Reactive Power L1.
		 *
		 * <ul>
		 * <li>Interface: Sum (origin: ElectricityMeter)
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: Negative values for capacitive-; positive for inductive reactive power
		 * </ul>
		 */
		GRID_REACTIVE_POWER_L1(Doc.of(INTEGER)//
				.unit(VOLT_AMPERE_REACTIVE)//
				.persistencePriority(VERY_LOW)//
				.text("Reactive power of the Grid on phase L1. "//
						+ "Negative values for capacitive-; positive for inductive reactive power")),

		/**
		 * Grid: Reactive Power L2.
		 *
		 * <ul>
		 * <li>Interface: Sum (origin: ElectricityMeter)
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: Negative values for capacitive-; positive for inductive reactive power
		 * </ul>
		 */
		GRID_REACTIVE_POWER_L2(Doc.of(INTEGER)//
				.unit(VOLT_AMPERE_REACTIVE)//
				.persistencePriority(VERY_LOW)//
				.text("Reactive power of the Grid on phase L2. "//
						+ "Negative values for capacitive-; positive for inductive reactive power")),

		/**
		 * Grid: Reactive Power L3.
		 *
		 * <ul>
		 * <li>Interface: Sum (origin: ElectricityMeter)
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: Negative values for capacitive-; positive for inductive reactive power
		 * </ul>
		 */
		GRID_REACTIVE_POWER_L3(Doc.of(INTEGER)//
				.unit(VOLT_AMPERE_REACTIVE)//
				.persistencePriority(VERY_LOW)//
				.text("Reactive power of the Grid on phase L3. "//
						+ "Negative values for capacitive-; positive for inductive reactive power")),

		/**
		 * Grid: Lagging Reactive Energy.
		 *
		 * <ul>
		 * <li>Interface: Sum (origin: ElectricityMeter)
		 * <li>Type: Long
		 * <li>Unit: varh_Σ
		 * </ul>
		 */
		GRID_LAGGING_REACTIVE_ENERGY(Doc.of(LONG)//
				.unit(VOLT_AMPERE_REACTIVE_HOURS)//
				.persistencePriority(VERY_LOW)//
				.text("Accumulated electrical energy of lagging (inductive) reactive power to the grid")),

		/**
		 * Grid: Leading Reactive Energy.
		 *
		 * <ul>
		 * <li>Interface: Sum (origin: ElectricityMeter)
		 * <li>Type: Long
		 * <li>Unit: varh_Σ
		 * </ul>
		 */
		GRID_LEADING_REACTIVE_ENERGY(Doc.of(LONG)//
				.unit(VOLT_AMPERE_REACTIVE_HOURS)//
				.persistencePriority(VERY_LOW)//
				.text("Accumulated electrical energy of leading (capacitive) reactive power to the grid")),
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
	 * Gets the Channel for {@link ChannelId#GRID_ACTIVE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getGridActivePowerChannel() {
		return this.channel(ChannelId.GRID_ACTIVE_POWER);
	}

	/**
	 * Gets the Total Grid Active Power in [W]. See
	 * {@link ChannelId#GRID_ACTIVE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getGridActivePower() {
		return this.getGridActivePowerChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#GRID_ACTIVE_POWER_L1}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getGridActivePowerL1Channel() {
		return this.channel(ChannelId.GRID_ACTIVE_POWER_L1);
	}

	/**
	 * Gets the Total Grid Active Power on L1 in [W]. See
	 * {@link ChannelId#GRID_ACTIVE_POWER_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getGridActivePowerL1() {
		return this.getGridActivePowerL1Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#GRID_ACTIVE_POWER_L2}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getGridActivePowerL2Channel() {
		return this.channel(ChannelId.GRID_ACTIVE_POWER_L2);
	}

	/**
	 * Gets the Total Grid Active Power on L2 in [W]. See
	 * {@link ChannelId#GRID_ACTIVE_POWER_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getGridActivePowerL2() {
		return this.getGridActivePowerL2Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#GRID_ACTIVE_POWER_L3}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getGridActivePowerL3Channel() {
		return this.channel(ChannelId.GRID_ACTIVE_POWER_L3);
	}

	/**
	 * Gets the Total Grid Active Power on L3 in [W]. See
	 * {@link ChannelId#GRID_ACTIVE_POWER_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getGridActivePowerL3() {
		return this.getGridActivePowerL3Channel().value();
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
	 * Gets the Channel for {@link ChannelId#GRID_REACTIVE_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getGridReactivePowerChannel() {
		return this.channel(ChannelId.GRID_REACTIVE_POWER);
	}

	/**
	 * Gets the Total Grid Reactive Power in [var]. See
	 * {@link ChannelId#GRID_REACTIVE_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getGridReactivePower() {
		return this.getGridReactivePowerChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#GRID_REACTIVE_POWER_L1}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getGridReactivePowerL1Channel() {
		return this.channel(ChannelId.GRID_REACTIVE_POWER_L1);
	}

	/**
	 * Gets the Total Grid Reactive Power on L1 in [var]. See
	 * {@link ChannelId#GRID_REACTIVE_POWER_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getGridReactivePowerL1() {
		return this.getGridReactivePowerL1Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#GRID_REACTIVE_POWER_L2}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getGridReactivePowerL2Channel() {
		return this.channel(ChannelId.GRID_REACTIVE_POWER_L2);
	}

	/**
	 * Gets the Total Grid Reactive Power on L2 in [var]. See
	 * {@link ChannelId#GRID_REACTIVE_POWER_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getGridReactivePowerL2() {
		return this.getGridReactivePowerL2Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#GRID_REACTIVE_POWER_L3}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getGridReactivePowerL3Channel() {
		return this.channel(ChannelId.GRID_REACTIVE_POWER_L3);
	}

	/**
	 * Gets the Total Grid Active Power on L3 in [var]. See
	 * {@link ChannelId#GRID_REACTIVE_POWER_L3}.
	 *
	 * @return the Channel {@link Value}
	 */	
	public default Value<Integer> getGridReactivePowerL3() {
		return this.getGridReactivePowerL3Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#GRID_LAGGING_REACTIVE_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getGridLaggingReactiveEnergyChannel() {
		return this.channel(ChannelId.GRID_LAGGING_REACTIVE_ENERGY);
	}

	/**
	 * Gets the Total Grid Lagging Reactive Energy in [varh_Σ]. See
	 * {@link ChannelId#GRID_LAGGING_REACTIVE_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getGridLaggingReactiveEnergy() {
		return this.getGridLaggingReactiveEnergyChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#GRID_LEADING_REACTIVE_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getGridLeadingReactiveEnergyChannel() {
		return this.channel(ChannelId.GRID_LEADING_REACTIVE_ENERGY);
	}

	/**
	 * Gets the Total Grid Leading Reactive Energy in [varh_Σ]. See
	 * {@link ChannelId#GRID_LEADING_REACTIVE_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Long> getGridLeadingReactiveEnergy() {
		return this.getGridLeadingReactiveEnergyChannel().value();
	}

	/**
	 * Used for Modbus/TCP Api Controller. Provides a Modbus table for the Channels
	 * of this Component.
	 *
	 * @param accessMode filters the Modbus-Records that should be shown
	 * @return the {@link ModbusSlaveNatureTable}
	 */
	public static ModbusSlaveNatureTable getModbusSlaveNatureTable(AccessMode accessMode) {
		return ModbusSlaveNatureTable.of(System.class, accessMode, 920)
				.channel(0, System.ChannelId.GRID_MODE, ModbusType.ENUM16)
				.channel(1, System.ChannelId.GRID_MODE_OFF_GRID_TIME, ModbusType.UINT16)
				.int16Reserved(2, 99)
				.channel(100, System.ChannelId.GRID_ACTIVE_POWER, ModbusType.INT32)
				.channel(102, System.ChannelId.GRID_ACTIVE_POWER_L1, ModbusType.INT32)
				.channel(104, System.ChannelId.GRID_ACTIVE_POWER_L2, ModbusType.INT32)
				.channel(106, System.ChannelId.GRID_ACTIVE_POWER_L3, ModbusType.INT32)
				.int16Reserved(108, 119)
				.channel(120, System.ChannelId.GRID_REACTIVE_POWER, ModbusType.INT32)
				.channel(122, System.ChannelId.GRID_REACTIVE_POWER_L1, ModbusType.INT32)
				.channel(124, System.ChannelId.GRID_REACTIVE_POWER_L2, ModbusType.INT32)
				.channel(126, System.ChannelId.GRID_REACTIVE_POWER_L3, ModbusType.INT32)
				.int16Reserved(128, 199)
				.channel(200, System.ChannelId.GRID_IMPORT_ACTIVE_ENERGY, ModbusType.UINT64)
				.channel(204, System.ChannelId.GRID_EXPORT_ACTIVE_ENERGY, ModbusType.UINT64)
				.int16Reserved(208, 219)
				.channel(220, System.ChannelId.GRID_LAGGING_REACTIVE_ENERGY, ModbusType.UINT64)
				.channel(224, System.ChannelId.GRID_LEADING_REACTIVE_ENERGY, ModbusType.UINT64)
				.build();
	}

}
