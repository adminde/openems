package io.openems.edge.simulator.heat.pump.reacting;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Simulator HeatPump Reacting", //
		description = "Simulates a Heat Pump with a two-point control against the bound ThermalEss limits. "
				+ "Requested power (e.g. a PV surplus offered via TargetActivePower) charges the storage beyond "
				+ "its target temperature up to the hardware maximum: a modulating device follows the request "
				+ "between its minimum and nominal power, an on/off device runs at full power as long as the "
				+ "request covers its electrical consumption.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "hp0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Modulating?", description = "Whether the heat pump modulates. An on/off device follows a requested power only when it covers the full electrical consumption; a modulating device follows it between its minimum and nominal power.")
	boolean modulating() default false;

	@AttributeDefinition(name = "Thermal Power", description = "Nominal thermal output of the heat pump [W].")
	int thermalPower() default 8000;

	@AttributeDefinition(name = "Minimum Thermal Power", description = "Lowest dispatchable thermal output when modulating [W]. The electrical minimum for accepting a requested power is derived via the COP. Ignored for on/off devices.")
	int minThermalPower() default 2000;

	@AttributeDefinition(name = "COP", description = "Nominal Coefficient of Performance at the standard rating point W35, i.e. 35 °C flow temperature (e.g. 3.5).")
	float cop() default 3.5f;

	@AttributeDefinition(name = "Spread [K]", description = "Temperature difference between supply and return while charging in K. The return carries the water at the return connection of a simulated storage tank, or the storage temperature without a tank.")
	float spread() default 5;

	@AttributeDefinition(name = "Minimum Runtime", description = "Minimum runtime of the compressor once started [min]. When the heat demand ends earlier, the heat pump keeps serving the last storage at its lowest power, up to the hardware temperature limits.")
	int minRuntime() default 15;

	@AttributeDefinition(name = "Hysteresis", description = "Electrical hysteresis below the minimum modulation before switching off [W]. 0 derives a default of half the minimum modulation power. Ignored for on/off devices.")
	int hysteresis() default 0;

	String webconsole_configurationFactory_nameHint() default "Simulator HeatPump Reacting [{id}]";
}
