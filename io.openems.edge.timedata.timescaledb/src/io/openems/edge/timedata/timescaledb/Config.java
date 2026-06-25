package io.openems.edge.timedata.timescaledb;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Timedata TimescaleDB", //
		description = "Stores channel data in TimescaleDB using normalized hypertables, split by data type and persistence priority.")
@interface Config {

	@AttributeDefinition(name = "Component-ID")
	String id() default "Timedata.Timescaledb0";

	@AttributeDefinition(name = "Alias")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Host", description = "TimescaleDB host")
	String host() default "localhost";

	@AttributeDefinition(name = "Port", description = "TimescaleDB port")
	int port() default 5432;

	@AttributeDefinition(name = "Raw Retention Days", description = "Days to keep raw high-res data before deletion")
	int rawRetentionDays() default 30;

	@AttributeDefinition(name = "Raw Compression Days", description = "Days to wait before compressing raw high-res data")
	int rawCompressionDays() default 7;

	@AttributeDefinition(name = "Database", description = "Database name")
	String database() default "openems_edge";

	@AttributeDefinition(name = "Username")
	String username() default "postgres";

	@AttributeDefinition(name = "Password")
	String password() default "password";

	@AttributeDefinition(name = "Pool Size", description = "HikariCP connection pool size")
	int poolSize() default 5;

	@AttributeDefinition(name = "Edge Name", description = "Identifier for this edge in the database, e.g. edge-site-01")
	String edgeName() default "edge0";

	@AttributeDefinition(name = "No of Cycles", description = "How many OpenEMS cycles between each DB flush")
	int noOfCycles() default 10;

	@AttributeDefinition(name = "Persistence Priority", description = "Minimum channel persistence priority to store. HIGH stores only critical channels; LOW stores everything.")
	io.openems.common.channel.PersistencePriority persistencePriority() default io.openems.common.channel.PersistencePriority.MEDIUM;

	String webconsole_configurationFactory_nameHint() default "Timedata TimescaleDB [{id}]";
}
