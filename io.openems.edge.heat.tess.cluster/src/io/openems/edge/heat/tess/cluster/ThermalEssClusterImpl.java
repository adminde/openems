package io.openems.edge.heat.tess.cluster;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.heat.tess.api.MetaThermalEss;
import io.openems.edge.heat.tess.api.ThermalEss;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Tess.Cluster", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class ThermalEssClusterImpl extends AbstractOpenemsComponent
		implements ThermalEssCluster, ThermalEss, MetaThermalEss, OpenemsComponent {

	private final ChannelManager channelManager = new ChannelManager(this);
	private final List<ThermalEss> tesss = new CopyOnWriteArrayList<>();

	@Reference
	private ConfigurationAdmin cm;

	@Reference(//
			policy = ReferencePolicy.DYNAMIC, //
			policyOption = ReferencePolicyOption.GREEDY, //
			cardinality = ReferenceCardinality.MULTIPLE, //
			target = "(&(enabled=true)(!(service.factoryPid=Tess.Cluster)))")
	protected synchronized void addTess(ThermalEss tess) {
		this.tesss.add(tess);
		this.reactivateChannelManager();
	}

	protected synchronized void removeTess(ThermalEss tess) {
		this.tesss.remove(tess);
		this.reactivateChannelManager();
	}

	private synchronized void reactivateChannelManager() {
		// References may bind before activate(); the ChannelManager is then
		// activated once the Config is available.
		if (this.config == null) {
			return;
		}
		this.channelManager.deactivate();
		this.channelManager.activate(this.tesss, this.config.socAveragingMethod());
	}

	private Config config;

	public ThermalEssClusterImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ThermalEss.ChannelId.values(), //
				ThermalEssCluster.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		this.config = config;
		this.activate(context, config.id(), config.alias(), config.enabled());
		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "Tess", config.tess_ids())) {
			return;
		}
		this.channelManager.activate(this.tesss, config.socAveragingMethod());
	}

	@Override
	@Deactivate
	protected void deactivate() {
		this.channelManager.deactivate();
		super.deactivate();
	}

	@Override
	public synchronized String[] getTessIds() {
		return this.config.tess_ids();
	}

	@Override
	public String debugLog() {
		return "SoC:" + this.getSoc().asString() //
				+ "|L:" + this.getThermalPower().asString();
	}
}
