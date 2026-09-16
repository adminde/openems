package io.openems.edge.oros.simulator.bms;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Simulator BatteryManagementSystem OROS", //
		description = "This simulates an OROS Energy Battery Management System.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "bms0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "Battery Management System";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Capacity [Wh]")
	int capacity() default 261000;

	@AttributeDefinition(name = "Initial State of Charge [%]")
	int initialSoc() default 50;

	@AttributeDefinition(name = "Maximum Rack Voltage [V]", description = "Charge cut-off voltage of the Battery Rack")
	float maxChargeVoltage() default BatteryManagementSimulator.MAX_CHARGE_VOLTAGE;

	@AttributeDefinition(name = "Minimum Discharge Voltage [V]", description = "Discharge cut-off voltage of the Battery Rack")
	float minDischargeVoltage() default BatteryManagementSimulator.MIN_DISCHARGE_VOLTAGE;

	@AttributeDefinition(name = "Internal Resistance [mOhm]", description = "Internal resistance of the Battery Rack. Setting it to zero simulates a loss-free Battery.")
	int internalResistance() default BatteryManagementSimulator.INTERNAL_RESISTANCE;

	@AttributeDefinition(name = "Thermal Efficiency", description = "The Coefficient of Performance of the Thermal Management System.")
	float thermalEfficiency() default BatteryManagementSimulator.THERMAL_COEFFICIENT_OF_PERFORMANCE;

	@AttributeDefinition(name = "Thermal Management Power [W]", description = "Electrical power of pump and controls, drawn while the Battery carries a current.")
	int thermalManagementPower() default BatteryManagementSimulator.THERMAL_MANAGEMENT_POWER;

	String webconsole_configurationFactory_nameHint() default "Simulator BatteryManagementSystem OROS [{id}]";

}
