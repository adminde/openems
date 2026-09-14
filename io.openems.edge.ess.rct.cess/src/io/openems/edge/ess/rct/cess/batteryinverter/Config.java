package io.openems.edge.ess.rct.cess.batteryinverter;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.edge.common.startstop.StartStopConfig;

@ObjectClassDefinition(
		name = "ESS RCT Power CESS 200 PCS",
		description = "Implements the RCT Power CESS 200 Battery-Inverter.")
public @interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "batteryInverter0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Start/stop behaviour", description = "Should this Component be forced to start or stop?")
	StartStopConfig startStop() default StartStopConfig.AUTO;

	@AttributeDefinition(name = "Modbus-ID", description = "ID of Modbus bridge.")
	String modbus_id() default "modbus0";

	@AttributeDefinition(name = "BMS-ID", description = "ID of Battery Management System.")
	String bms_id() default "bms0";

	@AttributeDefinition(name = "Charger-IDs", description = "IDs of DC Chargers (PV).")
	String[] charger_ids() default {};

	String webconsole_configurationFactory_nameHint() default "ESS RCT Power CESS 200 PCS [{id}]";

}
