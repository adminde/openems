package io.openems.edge.oros;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
		name = "OROS Energy System",
		description = "The global OROS Energy System data.")
public @interface Config {

	@AttributeDefinition(name = "Backend API URL", description = "The OROS Energy Baclend API Url to be used as fallback")
	String backendApiUrl() default "wss://systems.oros.energy";

	@AttributeDefinition(name = "Open-Meteo API Key", description = "The Open-Meteo API Key to be used by this system")
	String openMeteoApiKey() default "";

	@AttributeDefinition(name = "OpenCage API Key", description = "The OpenCage API Key to be used by this system")
	String openCageApiKey() default "";

	String webconsole_configurationFactory_nameHint() default "OROS Energy System";

}
