package io.openems.edge.ess.rct.cess.battery.statemachine;

import java.time.Clock;

import io.openems.edge.common.statemachine.AbstractContext;
import io.openems.edge.ess.rct.cess.battery.Config;
import io.openems.edge.ess.rct.cess.battery.RctCessBattery;

public class Context extends AbstractContext<RctCessBattery> {

	protected final Config config;
	protected final Clock clock;

	public Context(RctCessBattery parent, Config config, Clock clock) {
		super(parent);
		this.clock = clock;
		this.config = config;
	}
}
