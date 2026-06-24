package io.openems.edge.ess.rct.cess.batteryinverter.statemachine;

import java.time.Clock;

import io.openems.edge.common.statemachine.AbstractContext;
import io.openems.edge.ess.rct.cess.batteryinverter.Config;
import io.openems.edge.ess.rct.cess.batteryinverter.RctCessBatteryInverter;

public class Context extends AbstractContext<RctCessBatteryInverter> {

	protected final Config config;
	protected final Clock clock;

	public Context(RctCessBatteryInverter parent, Config config, Clock clock) {
		super(parent);
		this.clock = clock;
		this.config = config;
	}
}
