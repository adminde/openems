package io.openems.edge.oros.simulator.auxiliary;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Simulator Auxiliary OROS", //
		description = "This simulates the auxiliary consumption of an OROS Energy Storage System.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "meter0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "Auxiliary Consumption";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "BMS-ID", description = "ID of the Battery Management System providing the Thermal Management System power.")
	String bms_id() default "bms0";

	@AttributeDefinition(name = "Standby Power [W]", description = "Electrical power of the control infrastructure, drawn around the clock.")
	int standbyPower() default AuxiliarySimulator.STANDBY_POWER;

	String webconsole_configurationFactory_nameHint() default "Simulator Auxiliary OROS [{id}]";

}
