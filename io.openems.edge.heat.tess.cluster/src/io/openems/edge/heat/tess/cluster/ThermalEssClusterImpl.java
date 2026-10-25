package io.openems.edge.heat.tess.cluster;

import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_BEFORE_WRITE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
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
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.heat.tess.api.ManagedThermalEss;
import io.openems.edge.heat.tess.api.MetaThermalEss;
import io.openems.edge.heat.tess.api.ThermalEss;
import io.openems.edge.heat.tess.api.utils.TargetPowerDistributor;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Tess.Cluster", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@EventTopics({ //
		TOPIC_CYCLE_BEFORE_WRITE })
public class ThermalEssClusterImpl extends AbstractOpenemsComponent
		implements ThermalEssCluster, ManagedThermalEss, ThermalEss, MetaThermalEss, OpenemsComponent, EventHandler {

	private final Logger log = LoggerFactory.getLogger(ThermalEssClusterImpl.class);
	private final ChannelManager channelManager = new ChannelManager(this);
	private final List<ThermalEss> tesss = new CopyOnWriteArrayList<>();

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private ComponentManager componentManager;

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
		this.channelManager.activate(this.componentManager, this.tesss, this.config.socAveragingMethod());
	}

	private Config config;

	public ThermalEssClusterImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ThermalEss.ChannelId.values(), //
				ManagedThermalEss.ChannelId.values(), //
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
		this.channelManager.activate(this.componentManager, this.tesss, config.socAveragingMethod());
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
	public synchronized String[] getHeatingIds() {
		var result = new LinkedHashSet<String>();
		for (var tess : this.tesss) {
			if (tess instanceof ManagedThermalEss managed) {
				Collections.addAll(result, managed.getHeatingIds());
			}
		}
		return result.toArray(String[]::new);
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case TOPIC_CYCLE_BEFORE_WRITE //
			-> this.distributeTarget();
		}
	}

	/**
	 * Resolves the effective thermal target of this cycle and distributes it to
	 * the managed members in the configured priority order. Members distribute to
	 * their Heating devices in the following EXECUTE_WRITE phase.
	 */
	private void distributeTarget() {
		var target = TargetPowerDistributor.resolveEffectiveTarget(this);
		if (target == null) {
			return;
		}
		var receivers = this.orderedManagedMembers().stream() //
				.map(TargetPowerDistributor::ofThermalEss) //
				.toList();
		try {
			TargetPowerDistributor.distribute(target, receivers);
		} catch (OpenemsNamedException e) {
			this.logWarn(this.log, "Unable to distribute thermal target: " + e.getMessage());
		}
	}

	/**
	 * Gets the managed members in the priority order of the configured Tess-IDs.
	 * Dynamic references bind in arbitrary order, so the configured order is
	 * authoritative.
	 *
	 * @return the ordered List of {@link ManagedThermalEss} members
	 */
	private synchronized List<ManagedThermalEss> orderedManagedMembers() {
		var result = new ArrayList<ManagedThermalEss>();
		for (var tessId : this.config.tess_ids()) {
			for (var tess : this.tesss) {
				if (tess.id().equals(tessId) && tess instanceof ManagedThermalEss managed) {
					result.add(managed);
					break;
				}
			}
		}
		return result;
	}

	@Override
	public String debugLog() {
		return "SoC:" + this.getSoc().asString() //
				+ "|L:" + this.getThermalPower().asString();
	}
}
