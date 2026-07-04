package io.openems.edge.simulator.heatpump.reacting;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Simulator HeatPump Reacting", //
		description = "Simulates a Heat Pump with a simple two-point control against the bound ThermalEss limits.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "hp0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Thermal Power", description = "Thermal output of the heat pump when running [W].")
	int thermalPower() default 4000;

	@AttributeDefinition(name = "COP", description = "Coefficient of Performance (e.g. 3.5).")
	float cop() default 3.5f;

	@AttributeDefinition(name = "Supply Temperature", description = "Flow temperature when running [°C].")
	float supplyTemperature() default 35;

	@AttributeDefinition(name = "Return Temperature", description = "Return temperature when running [°C].")
	float returnTemperature() default 30;

	String webconsole_configurationFactory_nameHint() default "Simulator HeatPump Reacting [{id}]";
}
