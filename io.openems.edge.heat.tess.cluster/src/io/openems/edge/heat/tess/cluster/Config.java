package io.openems.edge.heat.tess.cluster;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.edge.heat.tess.api.SocAveragingMethod;

@ObjectClassDefinition(//
		name = "Thermal ESS Cluster", //
		description = "Combines several thermal energy storage systems to one.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "tess0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "SoC averaging method", description = "How the average State-of-Charge of the cluster is calculated. "
			+ "ARITHMETIC: capacity-weighted average. "
			+ "GEOMETRIC: capacity-weighted geometric mean — a single empty storage forces the cluster SoC to zero.")
	SocAveragingMethod socAveragingMethod() default SocAveragingMethod.ARITHMETIC;

	@AttributeDefinition(name = "TESS-IDs", description = "IDs of ThermalEss components.")
	String[] tess_ids();

	String webconsole_configurationFactory_nameHint() default "Thermal ESS Cluster [{id}]";
}
