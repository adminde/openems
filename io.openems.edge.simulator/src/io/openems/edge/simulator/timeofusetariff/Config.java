package io.openems.edge.simulator.timeofusetariff;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Simulator Time-Of-Use Tariff", //
		description = "Time-Of-Use Tariff implementation that reads market prices from a data source.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "timeOfUseTariff0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Datasource-ID", description = "ID of Simulator Datasource. Expects a quarter-hourly column named 'Price' [Currency/MWh].")
	String datasource_id() default "datasource0";

	@AttributeDefinition(name = "Start time", required = true, description = "MANDATORY. Simulated time of the first CSV row (ISO-8601, e.g. '2025-01-01T00:00:00+01:00'). There is no fallback: at activation the clock is still the real system clock, so a blank value fails activation rather than anchoring row 0 to today.")
	String startTime();

	@AttributeDefinition(name = "Ancillary Costs JSON", description = "Ancillary Costs in JSON format [Currency/MWh]")
	String ancillaryCosts() default "";

	@AttributeDefinition(name = "Mathematical expression to calculate gross price", description = "[x] is the market price in [Currency/MWh], [y] the ancillary cost per period; defaults to \"x + y\"")
	String calculateExpression() default "";

	@AttributeDefinition(name = "Mathematical expression to calculate grid-sell price", description = "[x] is the market price in [Currency/MWh]; blank serves no grid-sell prices")
	String sellCalculateExpression() default "";

	String webconsole_configurationFactory_nameHint() default "Simulator Time-Of-Use Tariff [{id}]";
}
