package io.openems.edge.controller.ess.ebx;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Controller ESS EBX", //
		description = "Executes the aggregate active-power dispatch of the EBX virtual power plant on an Energy Storage System.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "ctrlEssEbx0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Ess-ID", description = "ID of the controlled Energy Storage System")
	String ess_id() default "ess";

	@AttributeDefinition(name = "Reference mode", description = "Control reference point for the setpoint and the reported actual power")
	ReferenceMode referenceMode() default ReferenceMode.ESS;

	@AttributeDefinition(name = "Meter-ID", description = "ID of the Point of Connection meter, only used with reference mode METER")
	String meter_id() default "meter0";

	@AttributeDefinition(name = "Ramp rate [%/s]", description = "Ramp rate in percent of the maximum active power per second. Always applied during fail-safe ramp-down. Keep consistent with the rate registered with EBX.")
	double rampRate() default 100;

	@AttributeDefinition(name = "Always apply ramp", description = "Also apply the ramp rate to ordinary setpoint changes")
	boolean alwaysApplyRamp() default false;

	@AttributeDefinition(name = "Heartbeat timeout [s]", description = "Age of the last heartbeat event above which the EBX side counts as unavailable")
	int heartbeatTimeout() default 10;

	String webconsole_configurationFactory_nameHint() default "Controller ESS EBX [{id}]";
}
