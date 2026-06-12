package io.openems.edge.ess.rct.cess;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.ess.rct.cess.statemachine.StateMachine.State.UNDEFINED;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.util.LinkedList;
import java.util.List;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.serialization.EmptyObject;
import io.openems.common.session.Role;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.cycle.Cycle;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.jsonapi.EdgeGuards;
import io.openems.edge.common.jsonapi.JsonApiBuilder;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.ess.api.EssErrorAcknowledge;
import io.openems.edge.ess.api.HybridEss;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.power.api.Power;
import io.openems.edge.ess.rct.cess.battery.RctCessBattery;
import io.openems.edge.ess.rct.cess.batteryinverter.RctCessBatteryInverter;
import io.openems.edge.ess.rct.cess.charger.RctCessDcCharger;
import io.openems.edge.ess.rct.cess.jsonrpc.ClearTimeoutFailure;
import io.openems.edge.ess.rct.cess.statemachine.Context;
import io.openems.edge.ess.rct.cess.statemachine.StateMachine;
import io.openems.edge.ess.rct.cess.statemachine.StateMachine.State;
import io.openems.edge.oros.bms.api.BatteryManagementProvider;
import io.openems.edge.oros.common.SymmetricComponent;
import io.openems.edge.oros.ess.core.AbstractModbusEss;
import io.openems.edge.oros.ess.core.RuntimeChannels;
import io.openems.edge.oros.pcs.api.PowerConversionProvider;
import io.openems.edge.oros.ess.api.EnergyStorageProtection;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "Ess.Rct.CESS",
		immediate = true,
		configurationPolicy = ConfigurationPolicy.REQUIRE
)
@EventTopics({
		EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE,
})
public class RctCessImpl extends AbstractModbusEss implements RctCess,
		EnergyStorageSystem, HybridEss, ManagedSymmetricEss, SymmetricEss, SymmetricComponent, 
		EnergyStorageProtection, EssErrorAcknowledge, OpenemsComponent, ModbusComponent, ModbusSlave, RuntimeChannels, 
		PowerConversionProvider, BatteryManagementProvider, TimedataProvider, EventHandler, StartStoppable {

	private final Logger log = LoggerFactory.getLogger(RctCessImpl.class);
	private final StateMachine stateMachine = new StateMachine(UNDEFINED);

	private final CalculateEnergyFromPower calculateDcChargeEnergy = new CalculateEnergyFromPower(this,
			HybridEss.ChannelId.DC_CHARGE_ENERGY);
	private final CalculateEnergyFromPower calculateDcDischargeEnergy = new CalculateEnergyFromPower(this,
			HybridEss.ChannelId.DC_DISCHARGE_ENERGY);

	private Config config;

	@Reference
	private Cycle cycle;

	@Reference
	private Power power;

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private ComponentManager componentManager;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = OPTIONAL)
	private volatile Timedata timedata = null;

	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY)
	private volatile RctCessBatteryInverter batteryInverter;

	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY)
	private volatile RctCessBattery battery;

	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MULTIPLE)
	private List<RctCessDcCharger> chargers = new LinkedList<>();

	@Override
	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public RctCessImpl() {
		super(
				OpenemsComponent.ChannelId.values(),
				ModbusComponent.ChannelId.values(),
				StartStoppable.ChannelId.values(),
				SymmetricComponent.ChannelId.values(),
				SymmetricEss.ChannelId.values(),
				ManagedSymmetricEss.ChannelId.values(),
				EnergyStorageSystem.ChannelId.values(),
				EnergyStorageProtection.ChannelId.values(),
				EssErrorAcknowledge.ChannelId.values(),
				HybridEss.ChannelId.values(),
				RuntimeChannels.ChannelId.values(),
				RctCess.ChannelId.values()
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		if (super.activate(context, config.id(), config.alias(), config.enabled(), this.cm, 1,
				config.modbus_id(), config.pcs_id(), config.bms_id(), config.startStop(), false)) {
			return;
		}

		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "charger", config.charger_ids())) {
			return;
		}
		this.chargers.forEach(charger -> charger.bindEss(this));
		this.config = config;

		this.storageChannelManager.setStateOfChargeLimiter(
				new StateOfChargeClipper(this, this.getBatteryManagementSystem()));
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void executeErrorAcknowledge() {
		try {
			this.battery.executeBatteryErrorAcknowledge();
			this.batteryInverter.executeBatteryInverterErrorAcknowledge();

			this.stateMachine.forceNextState(UNDEFINED);
		} catch (Exception e) {
			this.logError(this.log, e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	@Override
	public void buildJsonApiRoutes(JsonApiBuilder builder) {
		builder.handleRequest(new ClearTimeoutFailure(), endpoint -> {
			endpoint.setGuards(EdgeGuards.roleIsAtleast(Role.ADMIN));
		}, call -> {
			this.executeErrorAcknowledge();
			return EmptyObject.INSTANCE;
		});
	}

	@Override
	protected void handleStateMachine() {
		// Store the current State
		this._setStateMachine(this.stateMachine.getCurrentState());

		// Initialize 'Start-Stop' Channel
		this._setStartStop(StartStop.UNDEFINED);

		// Calculate the PV- and DC Discharge Power value from DC and Charger Power
		this.calculateDcPower();

		// Calculate the Energy values from DC Discharge Power.
		this.calculateDcEnergy();

		// Prepare Context
		var context = new Context(this, this.config, this.getBatteryManagementSystem(), this.getPowerConversionSystem(),
				this.componentManager.getClock());

		// Call the StateMachine
		try {
			this.stateMachine.run(context);
			this._setRunFailed(false);

		} catch (OpenemsNamedException e) {
			this._setRunFailed(true);
			this.logError(this.log, "StateMachine failed: " + e.getMessage());
		}
	}

	private void calculateDcPower() {
		var inverter = this.getPowerConversionSystem();
		if (!inverter.getDcPower().isDefined()) {
			return;
		}
		var dcPower = inverter.getDcPower().get();

		if (this.hasDcChargers()) {
			var pvPower = 0;
			for (RctCessDcCharger charger : this.getDcChargers()) {
				pvPower += charger.getActualPower().orElse(0);
			}
			this._setPvPower(pvPower);

			dcPower = TypeUtils.subtract(dcPower, pvPower);
		}
		this._setDcDischargePower(dcPower);
	}

	private void calculateDcEnergy() {
		var dischargePower = this.getDcDischargePowerChannel().getNextValue().get();
		if (dischargePower == null) {
			// Not available
			this.calculateDcChargeEnergy.update(null);
			this.calculateDcDischargeEnergy.update(null);
		} else if (dischargePower >= 0) {
			// Load-From-Grid
			this.calculateDcChargeEnergy.update(0);
			this.calculateDcDischargeEnergy.update(dischargePower);
		} else {
			// Feed-To-Grid
			this.calculateDcChargeEnergy.update(dischargePower * -1);
			this.calculateDcDischargeEnergy.update(0);
		}
	}

	@Override
	public void setStartStop(StartStop value) {
		if (this.startStopTarget.getAndSet(value) != value) {
			this.stateMachine.forceNextState(State.UNDEFINED);
		}
	}

	@Override
	protected ComponentManager getComponentManager() {
		return this.componentManager;
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public Power getPower() {
		return this.power;
	}

	@Override
	protected float getMaxPowerIncreasePercentage() {
		return this.config.maxPowerIncreasePercentage();
	}

	@Override
	public boolean isReadOnly() {
		return this.config.readOnly();
	}

	@Override
	public RctCessBatteryInverter getPowerConversionSystem() {
		return this.batteryInverter;
	}

	@Override
	public RctCessBattery getBatteryManagementSystem() {
		return this.battery;
	}

	@Override
	public boolean hasDcChargers() {
		return !this.chargers.isEmpty();
	}

	@Override
	public List<RctCessDcCharger> getDcChargers() {
		return this.chargers;
	}

	@Override
	public final Integer getSurplusPower() {
		if (!this.hasDcChargers() || !this.getPvPowerChannel().getNextValue().isDefined()
				|| !this.getSoc().isDefined()) {
			return null;
		}
		// Is the Battery full?
		if (this.getSoc().get() < 99) {
			return null;
		}
		// Is PV producing?
		int pvPower = this.getPvPowerChannel().getNextValue().get();
		if (pvPower < 100) {
			return null;
		}
		return pvPower;
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		return new ModbusProtocol(this,
				new FC6WriteRegisterTask(0x0100,
						m(EnergyStorageSystem.ChannelId.SET_ACTIVE_POWER,
								new SignedWordElement(0x0100), SCALE_FACTOR_2)),
				new FC6WriteRegisterTask(0x0101,
						m(EnergyStorageSystem.ChannelId.SET_REACTIVE_POWER,
								new SignedWordElement(0x0101), SCALE_FACTOR_2)));
	}

	@Override
	public String debugLog() {
		return EnergyStorageSystem.generateDebugLog(this, this.stateMachine);
	}

	@Override
	public String toString() {
		return toStringHelper(this)
				.addValue(this.id())
				.toString();
	}
}
