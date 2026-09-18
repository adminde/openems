package io.openems.edge.controller.symmetric.limitgridconnectionpower;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Controller Limit Grid-Connection Symmetric Power", //
		description = "Limits the charge and discharge power of a symmetric ESS so that the power at the Grid Connection Point stays within the limits configured in Core.Meta.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "ctrlLimitGridConnectionPower0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "Grid Connection Point Limits";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Ess-ID", description = "ID of Ess device.")
	String ess_id();

	String webconsole_configurationFactory_nameHint() default "Controller Limit Grid-Connection Symmetric Power [{id}]";
}
