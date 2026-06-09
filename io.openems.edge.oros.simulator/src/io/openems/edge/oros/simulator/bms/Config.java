package io.openems.edge.oros.simulator.bms;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition( //
		name = "Simulator OROS BMS", //
		description = "This simulates an OROS Energy Battery Management System.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "bms0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Capacity [Wh]")
	int capacity() default 220000;

	@AttributeDefinition(name = "Initial State of Charge [%]")
	int initialSoc() default 50;

	@AttributeDefinition(name = "Maximum Rack Voltage [V]", description = "Charge cut-off voltage of the Battery Rack")
	float maxChargeVoltage() default 960F;

	@AttributeDefinition(name = "Minimum Discharge Voltage [V]", description = "Discharge cut-off voltage of the Battery Rack")
	float minDischargeVoltage() default 665;

	String webconsole_configurationFactory_nameHint() default "Simulator OROS BMS [{id}]";

}
