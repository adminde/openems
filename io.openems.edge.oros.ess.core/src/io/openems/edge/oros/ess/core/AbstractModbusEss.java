package io.openems.edge.oros.ess.core;

import static io.openems.edge.common.channel.ChannelUtils.setValue;
import static io.openems.edge.common.type.Phase.SingleOrAllPhase.ALL;
import static io.openems.edge.ess.power.api.Pwr.ACTIVE;
import static io.openems.edge.ess.power.api.Pwr.REACTIVE;
import static io.openems.edge.ess.power.api.Relationship.EQUALS;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.serialization.EmptyObject;
import io.openems.common.session.Role;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.jsonapi.ComponentJsonApi;
import io.openems.edge.common.jsonapi.EdgeGuards;
import io.openems.edge.common.jsonapi.JsonApiBuilder;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStopConfig;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.ess.api.EssErrorAcknowledge;
import io.openems.edge.ess.api.EssErrorAcknowledgeRequest;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.power.api.Constraint;
import io.openems.edge.oros.bms.api.BatteryManagementProvider;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;
import io.openems.edge.oros.pcs.api.PowerConversionProvider;
import io.openems.edge.oros.pcs.api.PowerConversionSystem;
import io.openems.edge.timedata.api.TimedataProvider;

/**
 * Parent class for different implementations of Managed Energy Storage Systems,
 * consisting of a Power Conversion System component and a Battery Management System component.
 */
public abstract class AbstractModbusEss extends AbstractOpenemsModbusComponent implements
		EnergyStorageSystem, ManagedSymmetricEss, SymmetricEss, EssErrorAcknowledge,
		OpenemsComponent, ModbusComponent, ModbusSlave, ComponentJsonApi, RuntimeComponent, StartStoppable,
		PowerConversionProvider, BatteryManagementProvider, TimedataProvider, EventHandler {

	protected final ChannelManager channelManager;
    protected final RuntimeManager runtimeManager;

	protected final AtomicReference<StartStop> startStopTarget = new AtomicReference<>(StartStop.UNDEFINED);

	private StartStopConfig startStopConfig;

	protected AbstractModbusEss(io.openems.edge.common.channel.ChannelId[] firstInitialChannelIds,
									io.openems.edge.common.channel.ChannelId[]... furtherInitialChannelIds) {
		super(firstInitialChannelIds, furtherInitialChannelIds);
        this.runtimeManager = new RuntimeManager(this);
        this.channelManager = new ChannelManager(this);
	}

	@Override
	protected void activate(ComponentContext context, String id, String alias, boolean enabled) {
		throw new IllegalArgumentException("Invalid activate() method");
	}

	protected boolean activate(ComponentContext context, String id, String alias, boolean enabled, ConfigurationAdmin cm,
			int unitId, String modbusId, String pcsId, String bmsId,
			StartStopConfig startStop) throws OpenemsException {
		return activate(context, id, alias, enabled, cm, unitId, modbusId, pcsId, bmsId, startStop, true);
	}

	protected boolean activate(ComponentContext context, String id, String alias, boolean enabled, ConfigurationAdmin cm,
			int unitId, String modbusId, String pcsId, String bmsId,
			StartStopConfig startStop, boolean activateChannelManager) throws OpenemsException {
		if (super.activate(context, id, alias, enabled, unitId, cm, "Modbus", modbusId)) {
			return true;
		}
		this.startStopConfig = startStop;

		// update filter for 'PowerConversionSystem'
		if (OpenemsComponent.updateReferenceFilter(cm, this.servicePid(), "pcs", pcsId)) {
			return true;
		}

		// update filter for 'BatteryManagementSystem'
		if (OpenemsComponent.updateReferenceFilter(cm, this.servicePid(), "bms", bmsId)) {
			return true;
		}

		if (activateChannelManager) {
			this.getChannelManager().activate(this.getComponentManager(),
					this.getPowerConversionSystem(),
					this.getBatteryManagementSystem());
		}
		return false;
	}

	@Override
	protected void deactivate() {
		this.getChannelManager().deactivate();
		super.deactivate();
	}

	@Override
	public void buildJsonApiRoutes(JsonApiBuilder builder) {
		builder.handleRequest(new EssErrorAcknowledgeRequest(), endpoint -> {
			endpoint.setGuards(EdgeGuards.roleIsAtleast(Role.ADMIN));

		}, call -> {
			this.executeErrorAcknowledge();
			return EmptyObject.INSTANCE;
		});
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		if (event.getTopic().equals(EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE)) {
			this.handleStateMachine();
			this.runtimeManager.updateStateTime(this.getState());
		}
	}

	protected abstract void handleStateMachine();

	/**
	 * Forwards the power request to the {@link PowerConversionSystem}.
	 *
	 * {@inheritDoc}
	 */
	@Override
	public void applyPower(int activePower, int reactivePower) throws OpenemsNamedException {
		setValue(this, ManagedSymmetricEss.ChannelId.DEBUG_SET_ACTIVE_POWER, activePower);
		setValue(this, ManagedSymmetricEss.ChannelId.DEBUG_SET_REACTIVE_POWER, reactivePower);

		if (this.isReadOnly()) {
			return;
		}
		this.getPowerConversionSystem().run(this.getBatteryManagementSystem(), activePower, reactivePower);
	}

	/**
	 * Retrieves Power Precision from {@link SymmetricBatteryInverter}.
	 *
	 * {@inheritDoc}
	 */
	@Override
	public int getPowerPrecision() {
		return this.getPowerConversionSystem().getPowerPrecision();
	}

	protected abstract ComponentManager getComponentManager();

	/**
	 * Helper wrapping class to handle everything related to Channels.
	 *
	 * @return the {@link ChannelManager}
	 */
	protected ChannelManager getChannelManager() {
		return this.channelManager;
	}

	/**
	 * Retrieves StaticConstraints from {@link SymmetricBatteryInverter}.
	 *
	 * {@inheritDoc}
	 */
	@Override
	public Constraint[] getStaticConstraints() throws OpenemsNamedException {
		var constraints = new ArrayList<Constraint>();

		// If the generic system is not in State "STARTED" block ACTIVE and REACTIVE Power!
		if (!this.isStarted()) {
			constraints.add(this.createPowerConstraint("Active Power Constraint ESS not Started",
					ALL, ACTIVE, EQUALS, 0));
			constraints.add(this.createPowerConstraint("Reactive Power Constraint ESS not Started",
					ALL, REACTIVE, EQUALS, 0));
		}
		else if (this.isReadOnly()) {
			constraints.add(this.createPowerConstraint("Active Power Constraint ESS Read-Only Mode",
					ALL, ACTIVE, EQUALS, 0));
			constraints.add(this.createPowerConstraint("Reactive Power Constraint ESS Read-Only Mode",
					ALL, REACTIVE, EQUALS, 0));
		}
		else {
			// Get PCS constraints
			var pcsConstraints = this.getPowerConversionSystem().getStaticConstraints();
			for (var constraint : pcsConstraints) {
				constraints.add(this.getPower().createSimpleConstraint(constraint.description(),
						this, constraint.phase(), constraint.pwr(), constraint.relationship(), constraint.value()));
			}
		}
		return constraints.toArray(new Constraint[constraints.size()]);
	}

	public StartStop getStartStopTarget() {
		return switch (this.startStopConfig) {
		case AUTO -> this.startStopTarget.get();
		case START -> StartStop.START;
		case STOP -> StartStop.STOP;
		};
	}
}
