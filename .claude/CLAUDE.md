# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

### Java (Edge & Backend)

Run from repository root

```bash
./gradlew buildEdge              # Fat-Jar → build/openems-edge.jar
./gradlew buildBackend           # Fat-Jar → build/openems-backend.jar
./gradlew buildBackendEdge       # Fat-Jar → build/openems-backend-edge.jar

./gradlew testEdge               # JUnit tests for all Edge bundles
./gradlew testBackend            # JUnit tests for all Backend bundles
./gradlew :io.openems.edge.<module>:test   # Single module test
./gradlew :io.openems.edge.<module>:checkstyleMain   # Lint a module

./gradlew checkstyleEdge         # Checkstyle all Edge bundles
./gradlew checkstyleBackend      # Checkstyle all Backend bundles
```

### UI (Angular)

Run from local directory `ui/`

```bash
# Development serve
ng serve -o -c openems-edge-dev     # Edge (port 4200, ws 8085)
ng serve -o -c openems-backend-dev  # Backend (port 4200, ws 8082)

# Production build
ng build -c "openems,openems-edge-prod,prod"
ng build -c "openems,openems-backend-prod,prod"

# Tests
ng test
ng test -c "local"    # With Karma UI
```

### Docker Builds

Run from repository root

```bash
docker build . -t openems_edge         -f tools/docker/edge/Dockerfile
docker build . -t openems_backend      -f tools/docker/backend/Dockerfile
docker build . -t openems_backend-edge -f tools/docker/backend-edge/Dockerfile
docker build . -t openems_backend-ui   -f tools/docker/ui/Dockerfile
```


## Architecture Overview

OpenEMS is an energy management system (EMS) with three deployable components:

### Edge

Runs directly on hardware (e.g. Revolution Pi, Modberry).
Manages and controls physical devices (Batteries, Inverters, Meters, EV chargers).
Exposes channels over WebSocket (port 8082 by default) and REST.
Configurable via Apache Felix Web Console or directly in the UI

### Backend

Cloud/server component that aggregates multiple Edge connections, 
stores time-series data, handles user authentication, and provides a multi-edge UI interface.
Default ports: WebSocket 8082, Felix Console 8079.

### UI

Angular application that can connect either directly to an Edge (`Controller.Api.Websocket`) or through a Backend (`Ui.Websocket`).

---


## Java/OSGi Module System

All Java code uses OSGi (Apache Felix) with BND Workspace. Each feature is a separate Gradle subproject.

**Module naming conventions:**
- `io.openems.oem.*` — OEM-specific customizations
- `io.openems.edge.*` — Edge bundles (Device drivers, Controllers, Schedulers, Bridges)
- `io.openems.backend.*` — Backend bundles (Metadata, Timedata, Websocket, OAuth)
- `io.openems.common.*` — Shared code (Channel types, JSON-RPC, Types)
- `io.openems.shared.*` — Shared utilities (e.g. InfluxDB Client)
- `io.openems.wrapper.*` — OSGi wrappers for non-OSGi libraries

### Component Pattern

Every component implements `OpenemsComponent` and extends `AbstractOpenemsComponent`:

```java
@Designate(ocd = Config.class, factory = true)
@Component(name = "My.Component", immediate = true,
           configurationPolicy = ConfigurationPolicy.REQUIRE)
public class MyComponentImpl extends AbstractOpenemsComponent
        implements MyComponent, OpenemsComponent {

    @Reference private ConfigurationAdmin cm;
    @Reference private SomeDependency dep;   // OSGi service injection

    public MyComponentImpl() {
        super(OpenemsComponent.ChannelId.values(),
              MyComponent.ChannelId.values());
    }

    @Activate
    private void activate(ComponentContext context, Config config) {
        super.activate(context, config.id(), config.alias(), config.enabled());
    }

    @Override @Deactivate
    protected void deactivate() { super.deactivate(); }
}
```

- **`Config.java`** — `@ObjectClassDefinition` interface, defines OSGi configuration properties.
- **Channel IDs** — declared as enums implementing `ChannelId` inside the component interface (e.g. `MyComponent.ChannelId`). Each enum value has a `Doc` describing type, unit, access mode.
- **Internal setters** follow convention `_setChannelName(value)` and are called from within the bundle.

#### Channel Authoring Convention

When adding a new `ChannelId` enum value, ALWAYS follow the established OpenEMS schema in the same file:

1. **Javadoc DocString** above the enum value with these bullets, in this order:
   - `Interface:` — the interface this channel belongs to
   - `Type:` — Integer / Long / Float / Boolean / String
   - `Unit:` — physical unit (use `Unit.*` constant in code)
   - `Range:` — value range, if relevant
   - `Implementation Note:` — only when the channel triggers side-effects (e.g. mirrors to another channel via `onChannelSetNextValue`)
2. **Default getter for the Channel** — `getFooChannel()` returning `IntegerReadChannel` / etc., as a `default` method on the interface.
3. **Default getter for the Value** — `getFoo()` returning `Value<T>`, with Javadoc referencing the `ChannelId` and stating the unit.
4. **Default internal setters** — both `_setFoo(Integer value)` and `_setFoo(int value)` (or appropriate type pairs), each delegating to `getFooChannel().setNextValue(value)`.
5. For derived/mirror channels, use `IntegerDoc().onChannelSetNextValue((self, v) -> {...})` to update the dependent channel — see `EssDcCharger.ChannelId.ACTUAL_POWER` as canonical example.

Mirror this exact pattern (channel ID block + the four default-method blocks) for every new channel added to an interface.

#### Naming: no unit suffixes

ChannelId constants, getters/setters, and Config-Property method names must NOT carry unit suffixes such as `*Deci`, `*Mv`, `*Wh`, `*Sec`, `*KWh`, `*Liters`, `*Celsius`. The unit is expressed exclusively by the channel's `Unit` (e.g. `Unit.DEZIDEGREE_CELSIUS`) and the `Doc`/Config description.

Examples:
- `temperature` — not `tempDeci`
- `capacity` — not `capacityWh`
- `power` — not `powerW`

#### Config parameters: Human-readable SI values, no scaled units

`@ObjectClassDefinition` config parameters are entered by humans in the Felix Web Console / UI. They MUST be expressed in plain SI / human-readable values that require no mental scaling:

- Temperatures in `°C` (e.g. `35.0`) — never deci-°C (`350`)
- Values as the real dimensionless value (e.g. `1.5`) — never deci-Values (`15`)
- Use `float`/`double` config types where fractional values are meaningful

Scaled units (e.g. `Unit.DEZIDEGREE_CELSIUS` channels) are an internal channel representation only; the conversion (e.g. `× 10`) happens inside the component implementation — typically in `activate()` or the cycle logic — never in the user's head. State the unit in the `@AttributeDefinition` name or description (e.g. `"Temperature"` / `"... [°C]"`).

### Edge Execution Cycle

The Edge runs a ~1-second cycle (`Cycle`, default 1000 ms). Each cycle:
1. All component channels' `nextValue` are flushed to `value`.
2. The Scheduler determines execution order of Controllers.
3. Each active Controller's `run()` method is called.
4. Channel values are persisted to Timedata (RRD4J/InfluxDB).

Controllers implement `Controller` and override `run()`. Stateful devices often use `AbstractStateMachine<STATE, CONTEXT>` with `StateHandler` per state.

### Bridges

Bridges abstract communication protocols:
- `io.openems.edge.bridge.modbus` — Modbus TCP/RTU
- `io.openems.edge.bridge.http` — HTTP/REST polling
- `io.openems.edge.bridge.mbus` — M-Bus
- `io.openems.edge.bridge.mqtt` — MQTT

Device drivers reference a bridge and define register/task mappings.

---


## Testing Pattern

Tests use a fluent `ComponentTest` / `ControllerTest` API simulating the OSGi cycle:

```java
new ControllerTest(new MyControllerImpl())
    .addReference("cm", new DummyConfigurationAdmin())
    .addReference("ess", new DummyManagedSymmetricEss("ess0"))
    .activate(MyConfig.create().setId("ctrl0").setEssId("ess0").build())
    .next(new TestCase()
        .input("ess0", SymmetricEss.ChannelId.ACTIVE_POWER, 1000)
        .output("ess0", ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_EQUALS, 500));
```

Dummy implementations live in `*.test` packages of each API bundle (e.g. `io.openems.edge.ess.test`, `io.openems.edge.meter.test`). Each test module's `MyConfig.java` provides a builder for the `@ObjectClassDefinition` config interface.

---


## UI Architecture

The Angular UI (`ui/`) uses Ionic and ngx-translate.

**Theme system:** Each theme lives under `ui/src/themes/<name>/` and provides:
- `environments/` — backend/edge environment files and `theme.ts` / `oem-meta.ts`
- `scss/variables.scss` — styling overrides
- `root/` — static assets (favicon, icons)

**i18n:** Use `translate` attribute in HTML templates and `TranslateService.instant()` in TypeScript. Translations are in `app/shared/translate.ts`.

**RxJS subscriptions** must use the `takeUntil(this.stopOnDestroy)` pattern with a `Subject<void>` destroyed in `ngOnDestroy`.

---

## Application Entry Points

- Edge: `io.openems.edge.application/EdgeApp.bndrun` — lists all bundles included in the Edge fat-jar
- Backend: `io.openems.backend.application/BackendApp.bndrun`
- Backend-Edge (combined): `io.openems.backend.edge.application/BackendEdgeApp.bndrun`

Adding a new bundle to the Edge or Backend requires adding it to the corresponding `.bndrun` file under both `-runrequires` and `-runbundles`.

## Key Configuration Files

- `gradle.properties` — Java version, BND version, Gradle JVM args
- `cnf/build.bnd` — shared BND buildpath (OSGi core, Guava, Gson, etc.) and testpath for all bundles
- `cnf/checkstyle.xml` — Checkstyle rules (0 warnings allowed)
- Each bundle's `bnd.bnd` — bundle name, vendor, version, per-bundle `-buildpath`
