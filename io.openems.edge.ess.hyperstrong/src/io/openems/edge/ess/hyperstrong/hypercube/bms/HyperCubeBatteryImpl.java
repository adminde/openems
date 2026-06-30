package io.openems.edge.ess.hyperstrong.hypercube.bms;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.ess.hyperstrong.AlarmAnalysis.decodeAlarm;
import static io.openems.edge.ess.hyperstrong.ModbusUtils.defineModbusAlarmRegister;
import static io.openems.edge.ess.hyperstrong.ModbusUtils.defineModbusSignedWordInputRegistersTasks;
import static io.openems.edge.ess.hyperstrong.ModbusUtils.defineModbusUnsignedWordInputRegistersTasks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.channel.Unit;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.OpenemsType;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.BitsWordElement;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.bridge.modbus.api.task.Task;
import io.openems.edge.common.channel.ChannelId.ChannelIdImpl;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.oros.bms.api.BatteryManagementSystem;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "Ess.HyperStrong.HyperCube.II.BMS",
		immediate = true,
		configurationPolicy = ConfigurationPolicy.REQUIRE
)
public class HyperCubeBatteryImpl extends AbstractOpenemsModbusComponent implements
		HyperCubeBattery, BatteryManagementSystem, Battery,
		OpenemsComponent, ModbusComponent, ModbusSlave, StartStoppable {

	/** Capacity of a HyperStrong Battery Rack in [Wh]. */
	public static final int CAPACITY = 233_000;

	@Reference
	private ConfigurationAdmin cm;

	@Override
	@Reference(policy = ReferencePolicy.STATIC,
			policyOption = ReferencePolicyOption.GREEDY,
			cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public HyperCubeBatteryImpl() {
		super(OpenemsComponent.ChannelId.values(),
				ModbusComponent.ChannelId.values(),
				StartStoppable.ChannelId.values(),
				Battery.ChannelId.values(),
				BatteryManagementSystem.ChannelId.values(),
				HyperCubeBattery.ChannelId.values(),
				AlarmChannelId.values());
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		if (super.activate(context, config.id(), config.alias(), config.enabled(),
				config.modbusUnitId(), this.cm, "Modbus", config.modbus_id())) {
			return;
		}
		this._setCapacity(CAPACITY);

		HyperCubeBattery.mirrorOpenCircuitVoltageFromPrecharge(this);
		BatteryManagementSystem.calculateRackPowerFromVoltageAndCurrent(this);
		BatteryManagementSystem.calculateMaxCurrentFromPowerAndVoltage(this);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void setStartStop(StartStop value) {
		// Unnecessary for this implementation
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		var protocol = new ModbusProtocol(this,
				new FC4ReadInputRegistersTask(10001, Priority.HIGH,
						m(BatteryManagementSystem.ChannelId.RACK_VOLTAGE,
								new SignedWordElement(10001), SCALE_FACTOR_2),
						m(BatteryManagementSystem.ChannelId.RACK_CURRENT,
								new SignedWordElement(10002), SCALE_FACTOR_2),
						new DummyRegisterElement(10003),
						m(BatteryManagementSystem.ChannelId.RACK_SOC,
								new UnsignedWordElement(10004)),
						m(BatteryManagementSystem.ChannelId.RACK_SOE,
								new UnsignedWordElement(10005)),
						m(BatteryManagementSystem.ChannelId.RACK_SOH,
								new UnsignedWordElement(10006)),
						m(BatteryManagementSystem.ChannelId.CHARGE_MAX_POWER,
								new UnsignedWordElement(10007), SCALE_FACTOR_2),
						m(BatteryManagementSystem.ChannelId.DISCHARGE_MAX_POWER,
								new UnsignedWordElement(10008), SCALE_FACTOR_2)),

				new FC4ReadInputRegistersTask(10009, Priority.LOW,
						m(new BitsWordElement(10009, this)
								.bit(0, HyperCubeBattery.ChannelId.POSITIVE_RELAY_STATUS)
								.bit(1, HyperCubeBattery.ChannelId.NEGATIVE_RELAY_STATUS)
						),
						m(SymmetricEss.ChannelId.MAX_CELL_VOLTAGE,
								new UnsignedWordElement(10010)),
						m(SymmetricEss.ChannelId.MIN_CELL_VOLTAGE,
								new UnsignedWordElement(10011)),
						m(SymmetricEss.ChannelId.MAX_CELL_TEMPERATURE,
								new SignedWordElement(10012)),
						m(SymmetricEss.ChannelId.MIN_CELL_TEMPERATURE,
								new SignedWordElement(10013)),
						m(BatteryManagementSystem.ChannelId.MAX_CELL_VOLTAGE_INDEX,
								new UnsignedWordElement(10014)),
						m(BatteryManagementSystem.ChannelId.MIN_CELL_VOLTAGE_INDEX,
								new UnsignedWordElement(10015)),
						m(BatteryManagementSystem.ChannelId.MAX_CELL_TEMPERATURE_INDEX,
								new UnsignedWordElement(10016)),
						m(BatteryManagementSystem.ChannelId.MIN_CELL_TEMPERATURE_INDEX,
								new UnsignedWordElement(10017))),

				defineModbusAlarmsTask()
		);
		protocol.addTasks(defineModbusCellAnalyticsTasks());
		protocol.addTasks(defineModbusConnectorAnalyticsTasks());

		protocol.addTask(new FC4ReadInputRegistersTask(10778, Priority.LOW,
						m(HyperCubeBattery.ChannelId.INSULATION_RESISTANCE,
								new UnsignedWordElement(10778)),
						m(HyperCubeBattery.ChannelId.PRECHARGE_VOLTAGE,
								new SignedWordElement(10779), SCALE_FACTOR_2),
						m(new BitsWordElement(10780, this)
								.bit(0, HyperCubeBattery.ChannelId.COMMUNICATION_ABNORMAL)
								.bit(1, HyperCubeBattery.ChannelId.COMMUNICATION_CONNECTED)
								.bit(2, HyperCubeBattery.ChannelId.COMMUNICATION_ENABLED)
								.bit(3, HyperCubeBattery.ChannelId.COMMUNICATION_FAULT)
						)));

		protocol.addTasks(defineModbusBusBarAnalyticsTasks());

		return protocol;
	}

	private Task defineModbusAlarmsTask() {
		return new FC4ReadInputRegistersTask(10018, Priority.LOW,
				defineModbusAlarmRegister(this, 1, 10018, this::addModbusAlarmChannel, value -> {
					decodeAlarm(0, value,
							this.channel(AlarmChannelId.HIGH_CELL_VOLTAGE_FAULT),
							this.channel(AlarmChannelId.HIGH_CELL_VOLTAGE_INFO),
							this.channel(AlarmChannelId.HIGH_CELL_VOLTAGE_WARNING));
					decodeAlarm(2, value,
							this.channel(AlarmChannelId.LOW_CELL_VOLTAGE_FAULT),
							this.channel(AlarmChannelId.LOW_CELL_VOLTAGE_INFO),
							this.channel(AlarmChannelId.LOW_CELL_VOLTAGE_WARNING));
					decodeAlarm(4, value,
							this.channel(AlarmChannelId.IMBALANCE_CELL_VOLTAGE_FAULT),
							this.channel(AlarmChannelId.IMBALANCE_CELL_VOLTAGE_WARNING),
							this.channel(AlarmChannelId.IMBALANCE_CELL_VOLTAGE_SEVERE_WARNING));
					decodeAlarm(12, value,
							this.channel(AlarmChannelId.HIGH_VOLTAGE_FAULT),
							this.channel(AlarmChannelId.HIGH_VOLTAGE_INFO),
							this.channel(AlarmChannelId.HIGH_VOLTAGE_WARNING));
					decodeAlarm(14, value,
							this.channel(AlarmChannelId.LOW_VOLTAGE_FAULT),
							this.channel(AlarmChannelId.LOW_VOLTAGE_INFO),
							this.channel(AlarmChannelId.LOW_VOLTAGE_WARNING));
				}),
				defineModbusAlarmRegister(this, 2, 10020, this::addModbusAlarmChannel, value -> {
					decodeAlarm(0, value,
							this.channel(AlarmChannelId.HIGH_DISCHARGE_CURRENT_FAULT),
							this.channel(AlarmChannelId.HIGH_DISCHARGE_CURRENT_SEVERE_FAULT),
							this.channel(AlarmChannelId.HIGH_DISCHARGE_CURRENT_CRITICAL_FAULT));
					decodeAlarm(2, value,
							this.channel(AlarmChannelId.HIGH_CHARGE_CURRENT_FAULT),
							this.channel(AlarmChannelId.HIGH_CHARGE_CURRENT_SEVERE_FAULT),
							this.channel(AlarmChannelId.HIGH_CHARGE_CURRENT_CRITICAL_FAULT));
					decodeAlarm(4, value,
							this.channel(AlarmChannelId.HIGH_TEMPERATURE_FAULT),
							this.channel(AlarmChannelId.HIGH_TEMPERATURE_INFO),
							this.channel(AlarmChannelId.HIGH_TEMPERATURE_WARNING));
					decodeAlarm(6, value,
							this.channel(AlarmChannelId.LOW_TEMPERATURE_FAULT),
							this.channel(AlarmChannelId.LOW_TEMPERATURE_INFO),
							this.channel(AlarmChannelId.LOW_TEMPERATURE_WARNING));
					decodeAlarm(8, value,
							this.channel(AlarmChannelId.HIGH_TEMPERATURE_DIFFERENTIAL_FAULT),
							this.channel(AlarmChannelId.HIGH_TEMPERATURE_DIFFERENTIAL_INFO),
							this.channel(AlarmChannelId.HIGH_TEMPERATURE_DIFFERENTIAL_WARNING));
					decodeAlarm(10, value,
							this.channel(AlarmChannelId.RAPID_TEMPERATURE_RISE_FAULT),
							this.channel(AlarmChannelId.RAPID_TEMPERATURE_RISE_WARNING),
							this.channel(AlarmChannelId.RAPID_TEMPERATURE_RISE_SEVERE_WARNING));
				}),
				defineModbusAlarmRegister(this, 3, 10022, this::addModbusAlarmChannel, value -> {
					decodeAlarm(4, value,
							this.channel(AlarmChannelId.HIGH_SOC_FAULT),
							this.channel(AlarmChannelId.HIGH_SOC_INFO),
							this.channel(AlarmChannelId.HIGH_SOC_WARNING));
					decodeAlarm(6, value,
							this.channel(AlarmChannelId.LOW_SOC_FAULT),
							this.channel(AlarmChannelId.LOW_SOC_INFO),
							this.channel(AlarmChannelId.LOW_SOC_WARNING));
					decodeAlarm(12, value,
							this.channel(AlarmChannelId.HIGH_BUSBAR_TEMPERATURE_FAULT),
							this.channel(AlarmChannelId.HIGH_BUSBAR_TEMPERATURE_INFO),
							this.channel(AlarmChannelId.HIGH_BUSBAR_TEMPERATURE_WARNING));
					decodeAlarm(14, value,
							this.channel(AlarmChannelId.EXCESSIVE_BATTERY_VOLTAGE_DIFFERENTIAL_FAULT),
							this.channel(AlarmChannelId.EXCESSIVE_BATTERY_VOLTAGE_DIFFERENTIAL_WARNING),
							this.channel(AlarmChannelId.EXCESSIVE_BATTERY_VOLTAGE_DIFFERENTIAL_SEVERE_WARNING));
				}),
				defineModbusAlarmRegister(this, 4, 10024, this::addModbusAlarmChannel, value -> {
					decodeAlarm(7, value, this.channel(AlarmChannelId.BCMS_COMMUNICATION_FAULT));
					decodeAlarm(8, value, this.channel(AlarmChannelId.BAMS_COMMUNICATION_FAULT));
					decodeAlarm(12, value, this.channel(AlarmChannelId.EXTREME_HIGH_VOLTAGE_FAULT));
					decodeAlarm(13, value, this.channel(AlarmChannelId.EXTREME_LOW_VOLTAGE_FAULT));
					decodeAlarm(14, value, this.channel(AlarmChannelId.EXTREME_HIGH_TEMPERATURE_FAULT));
					decodeAlarm(15, value, this.channel(AlarmChannelId.EXTREME_LOW_TEMPERATURE_FAULT));
				}),
				defineModbusAlarmRegister(this, 5, 10026, this::addModbusAlarmChannel, value -> {
					decodeAlarm(2, value,
							this.channel(AlarmChannelId.HIGH_CONTACTOR_TEMPERATURE_FAULT),
							this.channel(AlarmChannelId.HIGH_CONTACTOR_TEMPERATURE_WARNING),
							this.channel(AlarmChannelId.HIGH_CONTACTOR_TEMPERATURE_SEVERE_WARNING));
					decodeAlarm(4, value,
							this.channel(AlarmChannelId.HIGH_POWER_MODULE_TEMPERATURE_FAULT),
							this.channel(AlarmChannelId.HIGH_POWER_MODULE_TEMPERATURE_WARNING),
							this.channel(AlarmChannelId.HIGH_POWER_MODULE_TEMPERATURE_SEVERE_WARNING));
					decodeAlarm(8, value,
							this.channel(AlarmChannelId.HIGH_CONNECTOR_TEMPERATURE_FAULT),
							this.channel(AlarmChannelId.HIGH_CONNECTOR_TEMPERATURE_WARNING),
							this.channel(AlarmChannelId.HIGH_CONNECTOR_TEMPERATURE_SEVERE_WARNING));
				}),
				defineModbusAlarmRegister(this, 6, 10028, this::addModbusAlarmChannel, value -> {
					decodeAlarm(0, value, this.channel(AlarmChannelId.INSULATION_MODULE_COMMUNICATION_WARNING));
					decodeAlarm(1, value, this.channel(AlarmChannelId.PARAMETER_CONFIGURATION_WARNING));
					decodeAlarm(2, value, this.channel(AlarmChannelId.HALL_SENSOR_OPEN_CIRCUIT_WARNING));
					decodeAlarm(3, value, this.channel(AlarmChannelId.TEMPERATURE_SENSOR_OPEN_CIRCUIT_WARNING));
					decodeAlarm(4, value, this.channel(AlarmChannelId.TEMPERATURE_SENSOR_SHORT_CIRCUIT_WARNING));
					decodeAlarm(5, value, this.channel(AlarmChannelId.MAIN_POSITIVE_CONTACTOR_FAULT));
					decodeAlarm(9, value, this.channel(AlarmChannelId.FIRE_DETECTOR_COMMUNICATION_TIMEOUT));
					decodeAlarm(10, value, this.channel(AlarmChannelId.THERMAL_MANAGEMENT_COMMUNICATION_TIMEOUT));
					decodeAlarm(11, value, this.channel(AlarmChannelId.AEROSOL_SIGNAL_DISCONNECT_WARNING));
					decodeAlarm(12, value, this.channel(AlarmChannelId.HIGH_VOLTAGE_CALIBRATION_WARNING));
					decodeAlarm(13, value, this.channel(AlarmChannelId.CURRENT_CALIBRATION_WARNING));
					decodeAlarm(14, value, this.channel(AlarmChannelId.FIRE_DETECTOR_DISCONNECT_FAULT));
					decodeAlarm(15, value, this.channel(AlarmChannelId.MSD_DISCONNECT_FAULT));
				}),
				defineModbusAlarmRegister(this, 7, 10030, this::addModbusAlarmChannel, value -> {
					decodeAlarm(0, value, this.channel(AlarmChannelId.FIRE_DETECTOR_1_FAULT));
					decodeAlarm(2, value, this.channel(AlarmChannelId.FIRE_DETECTOR_2_FAULT));
					decodeAlarm(4, value, this.channel(AlarmChannelId.FIRE_DETECTOR_3_FAULT));
					decodeAlarm(6, value, this.channel(AlarmChannelId.FIRE_DETECTOR_4_FAULT));
					decodeAlarm(8, value, this.channel(AlarmChannelId.FIRE_DETECTOR_5_FAULT));
					decodeAlarm(10, value, this.channel(AlarmChannelId.FIRE_DETECTOR_6_FAULT));
				}),
				defineModbusAlarmRegister(this, 8, 10032, this::addModbusAlarmChannel),
				defineModbusAlarmRegister(this, 9, 10034, this::addModbusAlarmChannel, value -> {
					decodeAlarm(0, value,
							this.channel(AlarmChannelId.IMBALANCE_CELL_CHARGE_VOLTAGE_FAULT),
							this.channel(AlarmChannelId.IMBALANCE_CELL_CHARGE_VOLTAGE_WARNING),
							this.channel(AlarmChannelId.IMBALANCE_CELL_CHARGE_VOLTAGE_SEVERE_WARNING));
					decodeAlarm(2, value,
							this.channel(AlarmChannelId.IMBALANCE_CELL_DISCHARGE_VOLTAGE_FAULT),
							this.channel(AlarmChannelId.IMBALANCE_CELL_DISCHARGE_VOLTAGE_WARNING),
							this.channel(AlarmChannelId.IMBALANCE_CELL_DISCHARGE_VOLTAGE_SEVERE_WARNING));
					decodeAlarm(4, value,
							this.channel(AlarmChannelId.HIGH_PACK_VOLTAGE_FAULT),
							this.channel(AlarmChannelId.HIGH_PACK_VOLTAGE_INFO),
							this.channel(AlarmChannelId.HIGH_PACK_VOLTAGE_WARNING));
					decodeAlarm(6, value,
							this.channel(AlarmChannelId.LOW_PACK_VOLTAGE_FAULT),
							this.channel(AlarmChannelId.LOW_PACK_VOLTAGE_INFO),
							this.channel(AlarmChannelId.LOW_PACK_VOLTAGE_WARNING));
					decodeAlarm(8, value,
							this.channel(AlarmChannelId.THERMAL_MANAGEMENT_SYSTEM_FAULT),
							this.channel(AlarmChannelId.THERMAL_MANAGEMENT_SYSTEM_SEVERE_FAULT),
							this.channel(AlarmChannelId.THERMAL_MANAGEMENT_SYSTEM_WARNING));
				}),
				defineModbusAlarmRegister(this, 10, 10036, this::addModbusAlarmChannel)
		);
	}

	private Task[] defineModbusCellAnalyticsTasks() {
		List<Task> tasks = new ArrayList<Task>();

		tasks.addAll(defineModbusUnsignedWordInputRegistersTasks(10057, 260,
				this.addModbusAnalyticsChannel("CELL_VOLTAGE", "%s_%03d", Unit.MILLIVOLT),
				this::m));
		tasks.addAll(defineModbusSignedWordInputRegistersTasks(10490, 210,
				this.addModbusAnalyticsChannel("CELL_TEMPERATURE", "%s_%03d", Unit.DEZIDEGREE_CELSIUS),
				this::m));

		return tasks.toArray(Task[]::new);
	}

	private Task[] defineModbusConnectorAnalyticsTasks() { //, HyperCubeModel model) {
		List<Task> tasks = new ArrayList<Task>(
				defineModbusSignedWordInputRegistersTasks(10700, 72,
						this.addModbusAnalyticsChannel("CONNECTOR_TEMPERATURE", "%s_%02d", Unit.DEZIDEGREE_CELSIUS),
						this::m)
		);
		tasks.add(new FC4ReadInputRegistersTask(10772, Priority.LOW,
				m(HyperCubeBattery.ChannelId.MAX_CONNECTOR_TEMPERATURE,
						new SignedWordElement(10772)),
				m(HyperCubeBattery.ChannelId.MIN_CONNECTOR_TEMPERATURE,
						new SignedWordElement(10773)),
				m(HyperCubeBattery.ChannelId.MAX_CONNECTOR_TEMPERATURE_INDEX,
						new UnsignedWordElement(10774)),
				m(HyperCubeBattery.ChannelId.MIN_CONNECTOR_TEMPERATURE_INDEX,
						new UnsignedWordElement(10775)),
				m(HyperCubeBattery.ChannelId.MAX_CONNECTOR_MODULE_TEMPERATURE_INDEX,
						new UnsignedWordElement(10776)),
				m(HyperCubeBattery.ChannelId.MIN_CONNECTOR_MODULE_TEMPERATURE_INDEX,
						new UnsignedWordElement(10777))
		));
		return tasks.toArray(Task[]::new);
	}

	private Task[] defineModbusBusBarAnalyticsTasks() { //, HyperCubeModel model) {
		List<Task> tasks = new ArrayList<Task>(
				defineModbusSignedWordInputRegistersTasks(10781, 100,
						this.addModbusAnalyticsChannel("BUSBAR_TEMPERATURE", "%s_%03d", Unit.DEZIDEGREE_CELSIUS),
						this::m)
		);
		tasks.add(new FC4ReadInputRegistersTask(10881, Priority.LOW,
				m(HyperCubeBattery.ChannelId.MAX_BUSBAR_TEMPERATURE,
						new SignedWordElement(10881)),
				m(HyperCubeBattery.ChannelId.MIN_BUSBAR_TEMPERATURE,
						new SignedWordElement(10882)),
				m(HyperCubeBattery.ChannelId.MAX_BUSBAR_TEMPERATURE_INDEX,
						new UnsignedWordElement(10883)),
				m(HyperCubeBattery.ChannelId.MIN_BUSBAR_TEMPERATURE_INDEX,
						new UnsignedWordElement(10884))
		));
		return tasks.toArray(Task[]::new);
	}

	private Function<Integer, io.openems.edge.common.channel.ChannelId> addModbusAnalyticsChannel(String namePrefix,
			String nameFormat, Unit unit) {
		return number -> {
			var channelId = new ChannelIdImpl(String.format(nameFormat, namePrefix, number),
					Doc.of(OpenemsType.INTEGER)
							.unit(unit));
			this.addChannel(channelId);
			return channelId;
		};
	}

	private io.openems.edge.common.channel.ChannelId addModbusAlarmChannel(int number) {
		var channelId = new ChannelIdImpl(String.format("%s_%02d", "ALARM", number), Doc.of(OpenemsType.LONG));
		this.addChannel(channelId);
		return channelId;
	}

	@Override
	public String debugLog() {
		return BatteryManagementSystem.generateDebugLog(this);
	}
}
