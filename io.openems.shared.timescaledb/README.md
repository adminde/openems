# OpenEMS TimescaleDB Integration

High-throughput, long-retention time-series persistence for OpenEMS, built on
[TimescaleDB](https://www.timescale.com/) (a PostgreSQL extension).

---

## Highlights

- **Two deployment modes from one codebase** — write directly from an Edge, or
  aggregate many Edges into a Backend. The same shared library powers both.
- **Native columnar compression** — raw rows shrink substantially after the
  configurable compression delay (`compress_segmentby = channel_id`).
- **Decoupled retention** — separate raw-data retention and compression delays
  for the Edge (e.g. 30 days) vs the Backend (e.g. 90 days) to protect local
  flash storage.
- **Cascading continuous aggregates** — 15-minute and 1-day summaries are
  pre-computed in the background on both Edge and Backend; the Backend
  additionally maintains a 1-minute tier. Long-range queries return in
  milliseconds. The Edge skips the 1-minute aggregate (and its every-60s
  refresh) to spare CPU and flash wear; sub-15-minute Edge queries read raw.
- **Forever archive for core channels** — Fast Lane (`_core`) views expire on
  their retention policies (1m = 90 d, 15m = 1 y, 1d = 10 y), while the shared
  (Slow Lane) views are kept forever. A core query whose window predates the
  picked `_core` tier's retention automatically falls back to the shared view,
  so old core history stays reachable at the same or next-coarser resolution.
- **Sharding-safe by construction** — UUID v7 primary keys keep insert locality
  while staying globally unique, so two Backends can be merged without ID
  rewrites.
- **Asynchronous batched writes** — a bounded 1M-point queue + a configurable
  pool of worker threads (`writeWorkers`, each flushing up to 10,000 rows per
  batch) keep database latency off the OSGi event and websocket I/O threads.
  When the database can't keep up the queue back-pressures the caller (rather
  than dropping data or growing unbounded), and a background monitor logs queue
  depth and drain rate every 30 s.

---

## Architecture at a glance

Three cooperating bundles:

| Bundle | Role |
|---|---|
| `io.openems.shared.timescaledb` | Core library: schema, channel-ID resolution, batched writes, read queries. Plain Java, no OSGi. |
| `io.openems.edge.timedata.timescaledb` | OSGi DS component wiring the shared library to OpenEMS Edge. |
| `io.openems.backend.timedata.timescaledb` | OSGi DS component wiring the shared library to OpenEMS Backend. |

### Edge vs Backend schema

Both deployments share one schema *except* for the edge dimension. `SchemaHandler`
and `ChannelManager` are abstract bases holding everything common — the
`channel_def`/`channel` tables, hypertables, aggregates, policies, and all the
caching/resolution logic — and each deployment supplies only the two parts that
differ:

| | Backend (`Backend*`) | Edge (`Edge*`) |
|---|---|---|
| `edge` dimension table | yes — one DB holds many edges | none — a single local edge |
| `component` uniqueness | `(edge_id, name)` | `name` |
| `get_or_create_channel_id(...)` | takes an edge name; upserts the edge | no edge argument |

`TimescaleDbConfig.deployment` (`Deployment.EDGE` / `Deployment.BACKEND`) selects
the pair, and `TimescaleDbHandler` instantiates the matching `SchemaHandler` +
`ChannelManager` at construction. The read/write handlers, `DataPoint`, and the
rest of the API are deployment-agnostic — on the Edge the `edgeName` simply
flows through and is ignored.

Channel registration is a small "conductor" stored procedure
(`get_or_create_channel_id`) that calls modular PL/pgSQL helpers
(`get_or_create_component`, `get_or_create_channel_def`, `get_or_create_channel`,
plus `get_or_create_edge` on the Backend). A single server-side call stays atomic
with no extra round trips.

### Fast Lane vs Slow Lane

Every channel carries a boolean `core` flag, derived in code from its
`PersistencePriority` (`VERY_HIGH` → `core = true`): (OR it can be set accordingly)

- **Fast Lane** (`core = true`) — `agg_*_core_*` views, built `WHERE core`.
  Backend: 1m → 15m → 1d (cascaded). Edge: 15m (read from raw) → 1d.
- **Slow Lane** (all channels) — `agg_15m_*` / `agg_1d_*`, no filter, kept
  forever.

`ReadHandler.pickSource` routes each query to the coarsest view whose bucket is
≤ the requested resolution, in the channel's lane — and falls back to the shared
view for core windows older than the Fast Lane retention.

---

## Quick start

### 1. Build and run TimescaleDB with pg_uuidv7

The schema uses UUID v7 primary keys (`id UUID DEFAULT uuid_generate_v7()`).
UUID v7 is time-ordered, so new rows append to the right of the B-tree — the same
insert locality as a sequential `BIGINT`, but globally unique, so two Backends
can be merged without rewriting IDs.

`uuid_generate_v7()` is provided by the
[`pg_uuidv7`](https://github.com/fboulnois/pg_uuidv7) extension, which is **not**
bundled in the official TimescaleDB images. Schema apply
(`CREATE EXTENSION IF NOT EXISTS pg_uuidv7`) fails without it, so build a custom
image:

```dockerfile
FROM timescale/timescaledb:latest-pg16

USER root

RUN apk add --no-cache --virtual .build-deps \
    git make gcc musl-dev postgresql16-dev clang19 llvm19

RUN git clone https://github.com/fboulnois/pg_uuidv7.git /tmp/pg_uuidv7 && \
    cd /tmp/pg_uuidv7 && make && make install

RUN apk del .build-deps && rm -rf /tmp/pg_uuidv7

USER postgres
```

```bash
docker build -t openems-timescaledb .
docker run -d --name timescaledb -p 5432:5432 \
  -e POSTGRES_PASSWORD=<your-password> \
  -e POSTGRES_DB=<your-database> \
  openems-timescaledb
```

### 2. Configure

Enable the component matching your deployment mode in the Felix Web Console:

- **Edge-direct** — enable **Timedata TimescaleDB**
  (`io.openems.edge.timedata.timescaledb`) on the Edge.
- **Backend-aggregated** — enable **Timedata TimescaleDB**
  (`io.openems.backend.timedata.timescaledb`) on the Backend, and point each
  Edge's **Controller Api Backend** at the Backend URI.

**Console fields (both modes):**

| Field | Description |
|---|---|
| `host`, `port` | TimescaleDB server address |
| `database` | Target database name |
| `username`, `password` | Database credentials |
| `poolSize` | Hikari connection-pool size. Must cover `writeWorkers` **plus** expected concurrent reads. |
| `writeWorkers` | Background threads draining the write queue (default 4). Keep below `poolSize` so reads still get a connection. |
| `rawRetentionDays` | Days to keep raw rows before deletion (e.g. 30 on an Edge, 90 on a Backend). |
| `rawCompressionDays` | Days before raw rows are columnar-compressed (e.g. 7). |

**Edge-only console fields:**

| Field | Description |
|---|---|
| `edgeName` | Identifier for this Edge in the database, e.g. `edge-site-01`. |
| `noOfCycles` | OpenEMS cycles between each DB flush. |
| `persistencePriority` | Minimum channel priority to store (`HIGH` = critical only … `LOW` = everything). |

> **Not a console field:** the deployment type (`Deployment.EDGE` /
> `Deployment.BACKEND`) is fixed in code by whichever bundle activates the
> handler. It selects the schema/resolver variant **and** whether the 1-minute
> aggregate is built (`true` for the Backend, `false` for the Edge).

### 3. Verify

```sql
-- Is the UUID v7 extension installed? (schema apply fails without it)
SELECT extname FROM pg_extension WHERE extname = 'pg_uuidv7';
SELECT uuid_generate_v7();   -- should return a time-ordered UUID

-- Are the hypertables created?
SELECT hypertable_name FROM timescaledb_information.hypertables;

-- Backend only: the edge dimension exists and is populated.
-- (On the Edge this table is intentionally absent.)
SELECT name FROM edge;

-- Are rows arriving?
SELECT COUNT(*) FROM data_integer;

-- Are aggregates refreshing? (Backend has the 1-minute tier; Edge does not)
SELECT view_name, last_successful_finish
FROM timescaledb_information.job_stats
WHERE proc_name = 'policy_refresh_continuous_aggregate';
```

---

## Public API

Once a `TimescaleDbHandler` is constructed (done for you by the Edge or Backend
bundle's `activate(...)`), the surface is small:

```java
TimescaleDbHandler db = new TimescaleDbHandler(config);

// Write — returns near-immediately; JDBC happens on worker threads
db.writeBatch(points);

// Read
db.queryLatestValue(edgeName, addr);
db.queryHistoricData(edgeName, from, to, channels, bucketSeconds);
db.queryHistoricEnergy(edgeName, from, to, channels);
db.queryHistoricEnergyPerPeriod(edgeName, from, to, channels, bucketSeconds);

// Shutdown — drains the queue best-effort, then closes the pool
db.deactivate();
```

Energy queries (`queryHistoricEnergy*`) read raw tables and compute
last-minus-first deltas with counter-reset handling — they never use the
averaging aggregates.

---

## Source map

```
io.openems.shared.timescaledb/
├── README.md                ← you are here
└── src/io/openems/shared/timescaledb/
    ├── TimescaleDbConfig.java       connection + tuning parameters; nested Deployment enum (EDGE/BACKEND)
    ├── TimescaleDbHandler.java      facade — entry point; picks the variant pair from config.deployment()
    ├── SchemaHandler.java           abstract DDL base: shared tables, hypertables, aggregates, policies, retention constants
    ├── BackendSchemaHandler.java    multi-edge schema: edge dimension + edge_id FK + edge-aware stored functions
    ├── EdgeSchemaHandler.java       single-edge schema: no edge table; component keyed by name
    ├── Priorities.java              PersistencePriority → core (Fast Lane) flag
    ├── ChannelManager.java          abstract names → channel_id UUID resolver, with cache
    ├── BackendChannelManager.java   multi-edge lookup/resolve (joins through edge)
    ├── EdgeChannelManager.java      single-edge lookup/resolve (no edge join; edge name ignored)
    ├── WriteHandler.java            async batched writes + queue monitor
    ├── ReadHandler.java             read queries + resolution/retention routing
    ├── DataPoint.java               one channel sample in transit
    └── ChannelDefinition.java       resolved channel metadata
```

---

## Requirements

- Java 17+
- TimescaleDB 2.x with the `pg_uuidv7` extension
- OpenEMS Edge or Backend (matching the chosen deployment mode)

---

## License

Inherits the OpenEMS project license. See the repository root.
