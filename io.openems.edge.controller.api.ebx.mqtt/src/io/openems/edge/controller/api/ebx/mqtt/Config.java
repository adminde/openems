package io.openems.edge.controller.api.ebx.mqtt;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Controller Api EBX MQTT", //
		description = "MQTT transport for the EBX virtual power plant interface.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "ctrlApiEbxMqtt0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Bridge-ID", description = "ID of the MQTT Bridge")
	String mqtt_id() default "mqtt0";

	@AttributeDefinition(name = "Controller-ID", description = "ID of the bound Controller ESS EBX")
	String ctrlEssEbx_id() default "ctrlEssEbx0";

	@AttributeDefinition(name = "Topic root", description = "Topic root from the EBX registration response, e.g. ebx/vpp/{vpp_asset_id}")
	String topic() default "";

	@AttributeDefinition(name = "Publish interval [ms]", description = "Minimum interval between telemetry publications")
	int publishInterval() default 1000;

	String webconsole_configurationFactory_nameHint() default "Controller Api EBX MQTT [{id}]";
}
