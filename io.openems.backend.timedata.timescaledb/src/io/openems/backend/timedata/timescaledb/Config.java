package io.openems.backend.timedata.timescaledb;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
		name = "Timedata TimescaleDB",
		description = "Stores channel data in TimescaleDB using normalized hypertables.")
@interface Config {

	@AttributeDefinition(name = "Component-ID")
	String id() default "Timedata.TimescaleDB";

	@AttributeDefinition(name = "Host", description = "TimescaleDB host")
	String host() default "localhost";

	@AttributeDefinition(name = "Port", description = "TimescaleDB port")
	int port() default 5432;

	@AttributeDefinition(name = "Raw Retention Days", description = "Days to keep raw high-res data before deletion")
	int rawRetentionDays() default 90;

	@AttributeDefinition(name = "Raw Compression Days", description = "Days to wait before compressing raw high-res data")
	int rawCompressionDays() default 7;

	@AttributeDefinition(name = "Database", description = "Database name")
	String database() default "openems";

	@AttributeDefinition(name = "Username")
	String username() default "postgres";

	@AttributeDefinition(name = "Password")
	String password() default "password";

	@AttributeDefinition(name = "Pool Size", description = "HikariCP connection pool size")
	int poolSize() default 50;

	@AttributeDefinition(name = "Write Workers", description = "Number of background threads draining the write queue into the database. Keep below Pool Size so reads still get a connection.")
	int writeWorkers() default 10;

	@AttributeDefinition(name = "Read-Only mode", description = "Activates the read-only mode. Then no data is written to TimescaleDB.")
	boolean isReadOnly() default false;

	@AttributeDefinition(name = "Startdate", description = "Reject writes and queries before this date, for example: 2025-12-30; optional", required = false)
	String startDate();

	@AttributeDefinition(name = "Enddate", description = "Reject writes and queries after this date, for example: 2025-12-31; optional", required = false)
	String endDate();

	@AttributeDefinition(name = "List of blacklisted ChannelAddresses", description = "Blacklisted ChannelAddresses which should not be written to the database. e.g. \"kacoCore0/Serialnumber\"")
	String[] blacklistedChannels() default {};

	@AttributeDefinition(name = "List of blacklisted ChannelIds", description = "Blacklisted ChannelIds which should not be written to the database. e.g. \"_PropertyAlias\"")
	String[] blacklistedChannelIds() default {};

	String webconsole_configurationFactory_nameHint() default "Timedata TimescaleDB [{id}]";
}
