package io.openems.edge.scheduler.jscalendar;

import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;

import java.time.Clock;
import java.util.LinkedHashSet;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jscalendar.JSCalendar;
import io.openems.common.jscalendar.JSCalendar.Tasks.OneTask;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.jsonapi.ComponentJsonApi;
import io.openems.edge.common.jsonapi.JSCalendarApi;
import io.openems.edge.common.jsonapi.JSCalendarApi.UpdateJsCalendarRecord;
import io.openems.edge.common.jsonapi.JsonApiBuilder;
import io.openems.edge.scheduler.api.Scheduler;
import io.openems.edge.scheduler.jscalendar.Utils.Payload;

/**
 * This Scheduler returns all active Controllers from the JSCalendar including
 * the before/after controllers.
 */
@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Scheduler.JSCalendar", //
		immediate = true, //
		configurationPolicy = REQUIRE)
//CHECKSTYLE:OFF
public class SchedulerJSCalendarImpl extends AbstractOpenemsComponent
		implements SchedulerJSCalendar, Scheduler, OpenemsComponent, ComponentJsonApi {
	// CHECKSTYLE:ON

	private Config config = null;
	private JSCalendar.Tasks<Payload> tasks = JSCalendar.Tasks.empty();
	private Clock tasksClock = null;

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private ComponentManager componentManager;

	public SchedulerJSCalendarImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Scheduler.ChannelId.values(), //
				SchedulerJSCalendar.ChannelId.values());
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		this.applyConfig(config);
		super.activate(context, config.id(), config.alias(), config.enabled());
	}

	@Modified
	private void modified(ComponentContext context, Config config) throws OpenemsNamedException {
		this.applyConfig(config);
		super.modified(context, config.id(), config.alias(), config.enabled());
	}

	private synchronized void applyConfig(Config config) {
		this.config = config;
		this.parseTasks(this.componentManager.getClock());
	}

	/**
	 * Parses the configured calendar against the given {@link Clock}.
	 *
	 * @param clock the {@link Clock}
	 */
	private void parseTasks(Clock clock) {
		this.tasksClock = clock;
		this.tasks = this.config.enabled() //
				? JSCalendar.Tasks.fromStringOrEmpty(clock, this.config.jsCalendar(), Payload.serializer()) //
				: JSCalendar.Tasks.empty();
	}

	/**
	 * Gets the active {@link OneTask} under the ComponentManager's current
	 * {@link Clock}.
	 *
	 * <p>
	 * The {@link JSCalendar.Tasks} evaluate against the Clock they were parsed
	 * with. The ComponentManager may hand out a different Clock after this
	 * component has activated, and Tasks parsed before would keep evaluating the
	 * previous one. They are therefore parsed again whenever the clock changes. The
	 * system clock is handed out new but equal on every call, which leaves the
	 * Tasks as they are. The stored clock is the one asked, because a system clock
	 * only equals another system clock.
	 *
	 * @return the active {@link OneTask}; null if none is active
	 */
	private OneTask<Payload> getActiveOneTask() {
		final var clock = this.componentManager.getClock();
		if (!this.tasksClock.equals(clock)) {
			this.parseTasks(clock);
		}
		return this.tasks.getActiveOneTask();
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public synchronized LinkedHashSet<String> getControllers() {
		var result = new LinkedHashSet<String>();

		// Add "Always Run Before" Controllers
		this.addControllersById(result, this.config.alwaysRunBeforeController_ids());

		// Get and update Active-Task
		var activeTask = this.getActiveOneTask();

		// Add active controllers from JSCalendar
		if (activeTask != null) {
			this.addControllersById(result, activeTask.payload().controllerIds());
		}

		// Add "Always Run After" Controllers
		this.addControllersById(result, this.config.alwaysRunAfterController_ids());

		return result;
	}

	private void addControllersById(LinkedHashSet<String> result, String[] controllerIds) {
		for (var controllerId : controllerIds) {
			if (controllerId.isEmpty()) {
				continue;
			}
			result.add(controllerId);
		}
	}

	@Override
	public void buildJsonApiRoutes(JsonApiBuilder builder) {
		JSCalendarApi.buildJsonApiRoutes(builder, Payload.serializer(), //
				() -> this.tasks, //
				() -> new UpdateJsCalendarRecord(this.cm, this.componentManager, this.servicePid(), "jsCalendar"));
	}
}
