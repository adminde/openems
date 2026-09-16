package io.openems.edge.oros.simulator.pcs;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Simulator PowerConversionSystem OROS", //
		description = "This simulates an OROS Power Conversion System.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "pcs0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "Power Conversion System";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Max Active Power [W]")
	int maxActivePower() default 125000;

	@AttributeDefinition(name = "Efficiency [%]", description = "Efficiency of the AC/DC conversion.")
	float efficiency() default PowerConversionSimulator.EFFICIENCY_FACTOR;

	String webconsole_configurationFactory_nameHint() default "Simulator PowerConversionSystem OROS [{id}]";

}
