package io.openems.edge.sungrow.pvinverter;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "PV-Inverter Sungrow", //
		description = "Implements the Sungrow SG series PV inverter.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "pvInverter0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Read-Only mode", description = "In Read-Only mode no power-limitation commands are sent to the inverter")
	boolean readOnly() default true;

	@AttributeDefinition(name = "Modbus-ID", description = "ID of Modbus bridge.")
	String modbus_id() default "modbus0";

	@AttributeDefinition(name = "Modbus Unit-ID", description = "The Unit-ID of the Modbus device.")
	int modbusUnitId() default 1;

	@AttributeDefinition(name = "Phase Wiring", description = "Output type of the inverter. 3P4L reports phase voltages, 3P3L reports line voltages.")
	PhaseWiring phaseWiring() default PhaseWiring.THREE_PHASE_FOUR_WIRE;

	@AttributeDefinition(name = "Max Active Power [W]", description = "Rated active power of the inverter. 0 reads the value from the inverter.")
	int maxActivePower() default 0;

	String webconsole_configurationFactory_nameHint() default "PV-Inverter Sungrow [{id}]";
}
