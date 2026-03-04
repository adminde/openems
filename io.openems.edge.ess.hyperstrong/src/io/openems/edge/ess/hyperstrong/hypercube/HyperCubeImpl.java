package io.openems.edge.ess.hyperstrong.hypercube;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.common.startstop.StartStop;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.jsonapi.ComponentJsonApi;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.ess.api.EssErrorAcknowledge;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.hyperstrong.CycleProvider;
import io.openems.edge.ess.hyperstrong.cooling.LiquidCoolingSystem;
import io.openems.edge.timedata.api.TimedataProvider;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "ESS.HyperStrong.HyperCube.II",
		immediate = true,
		configurationPolicy = ConfigurationPolicy.REQUIRE
)
@EventTopics({
		EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE,
})
public class HyperCubeImpl extends AbstractHyperCubeComponent implements HyperCube,
		ManagedSymmetricEss, SymmetricEss, EssErrorAcknowledge, LiquidCoolingSystem,
		OpenemsComponent, ModbusComponent, ModbusSlave, ComponentJsonApi,
		CycleProvider, TimedataProvider, EventHandler, StartStoppable {

	private Config config;

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		super.activate(context, config.id(), config.alias(), config.enabled(), 1,
				"Modbus", config.modbus_id());
		this.config = config;
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	protected boolean isReadOnly() {
		return this.config.readOnly();
	}

	public HyperCubeModel getModel() {
		return HyperCubeModel.HSL2C2912_0232_EU;
	};

	@Override
	public StartStop getStartStopTarget() {
		return switch (this.config.startStop()) {
			case AUTO -> this.startStopTarget.get(); // read StartStop-Channel
			case START -> StartStop.START; // force START
			case STOP -> StartStop.STOP; // force STOP
		};
	}
}
