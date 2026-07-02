package io.openems.edge.timedata.timescaledb;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Timedata TimescaleDB", //
		description = "Stores channel data in TimescaleDB using normalized hypertables, split by data type and persistence priority.")
@interface Config {

	@AttributeDefinition(name = "Component-ID")
	String id() default "timescaledb0";

	@AttributeDefinition(name = "Alias")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Host", description = "TimescaleDB host")
	String host() default "localhost";

	@AttributeDefinition(name = "Port", description = "TimescaleDB port")
	int port() default 5432;

	@AttributeDefinition(name = "Retention Days", description = "Days to keep raw high resolution data before deletion")
	int retentionDays() default 30;

	@AttributeDefinition(name = "Compression Days", description = "Days to wait before compressing raw high resolution data")
	int compressionDays() default 7;

	@AttributeDefinition(name = "Database", description = "Database name")
	String database() default "data";

	@AttributeDefinition(name = "Username")
	String username() default "postgres";

	@AttributeDefinition(name = "Password")
	String password() default "password";

	@AttributeDefinition(name = "Pool Size", description = "HikariCP connection pool size")
	int poolSize() default 10;

	@AttributeDefinition(name = "Write Workers", description = "Number of background threads draining the write queue into the database. Keep below Pool Size so reads still get a connection.")
	int writeWorkers() default 4;

	@AttributeDefinition(name = "No of Cycles", description = "How many OpenEMS cycles between each DB flush")
	int noOfCycles() default 10;

	@AttributeDefinition(name = "Persistence Priority", description = "Store only Channels with a Persistence Priority above this. Be aware that too many writes can wear-out your flash storage.")
	io.openems.common.channel.PersistencePriority persistencePriority() default io.openems.common.channel.PersistencePriority.MEDIUM;

	String webconsole_configurationFactory_nameHint() default "Timedata TimescaleDB [{id}]";
}
