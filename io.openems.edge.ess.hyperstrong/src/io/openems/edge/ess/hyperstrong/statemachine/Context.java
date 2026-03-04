package io.openems.edge.ess.hyperstrong.statemachine;

import java.time.Clock;

import io.openems.edge.common.statemachine.AbstractContext;
import io.openems.edge.ess.hyperstrong.hypercube.HyperCube;

public class Context extends AbstractContext<HyperCube> {

	protected final Clock clock;

	public Context(HyperCube parent, Clock clock) {
		super(parent);
		this.clock = clock;
	}
}
