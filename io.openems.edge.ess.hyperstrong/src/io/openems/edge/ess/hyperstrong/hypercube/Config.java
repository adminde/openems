package io.openems.edge.ess.hyperstrong.hypercube;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.edge.common.startstop.StartStopConfig;

@ObjectClassDefinition(
		name = "ESS HyperStrong HyperCube II",
		description = "Implements the HyperStrong HyperCube II Energy Storage System.")
public @interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "ess0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "HyperCube II";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Read-Only", description = "is this Component in Read-Only mode?")
	boolean readOnly() default false;

	@AttributeDefinition(name = "Start/stop behaviour", description = "Should this Component be forced to start or stop?")
	StartStopConfig startStop() default StartStopConfig.START;

	@AttributeDefinition(name = "Modbus-ID", description = "ID of Modbus bridge.")
	String modbus_id() default "modbus0";

	@AttributeDefinition(name = "PCS-ID", description = "ID of Power Conversion System.")
	String pcs_id() default "pcs0";

	@AttributeDefinition(name = "BMS-ID", description = "ID of Battery Management System.")
	String bms_id() default "bms0";

	@AttributeDefinition(name = "TMS-ID", description = "ID of Thermal Management System.")
	String tms_id() default "tms0";

	String webconsole_configurationFactory_nameHint() default "ESS HyperStrong HyperCube II [{id}]";

}