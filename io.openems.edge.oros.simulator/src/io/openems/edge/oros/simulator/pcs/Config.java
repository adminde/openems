package io.openems.edge.oros.simulator.pcs;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition( //
		name = "Simulator PowerConversionSystem OROS", //
		description = "This simulates an OROS Power Conversion System.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "pcs0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Max Apparent Power [VA]")
	int maxApparentPower() default 100000;

	@AttributeDefinition(name = "Max Charge Power [W]")
	int maxChargePower() default 100000;

	@AttributeDefinition(name = "Max Discharge Power [W]")
	int maxDischargePower() default 100000;

	String webconsole_configurationFactory_nameHint() default "Simulator PowerConversionSystem OROS [{id}]";

}
