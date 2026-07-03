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
- **Cascading continuous aggregates** — 1-minute, 15-minute and 1-day summaries
  are pre-computed in the background on both Edge and Backend. Long-range
  queries return in milliseconds.
- **Forever archive for rollup channels** — Fast Lane (`_rollup`) views expire
  on their retention policies (1m = 90 d, 15m = 1 y, 1d = 10 y), while the
  shared (Slow Lane) views are kept forever. A rollup query whose window
  predates the picked `_rollup` tier's retention automatically falls back to
  the shared view, so old rollup history stays reachable at the same or
  next-coarser resolution.
- **Sharding-safe by construction** — UUID v7 primary keys keep insert locality
  while staying globally unique, so two Backends can be merged without ID
  rewrites.
- **Staged asynchronous write pipeline** — `writeBatch()` → bounded 1M-point
  source queue → `DataPointRouter` (channel resolve + routing) → one
  `MergePointsWorker` per value type (batches of up to 10,000 points / 10 s)
  → `CopyWriter` (PostgreSQL binary COPY, on up to `writeWorkers` threads).
  Every queue is bounded and every stage blocks when its successor is full, so
  overload back-pressures the caller instead of dropping data or growing
  unbounded; a background monitor logs backlog and drain rate every 30 s.

---

## Architecture at a glance

Three cooperating bundles:

| Bundle | Role |
|---|---|
| `io.openems.shared.timescaledb` | Core library: schema, channel-ID resolution, batched writes, read queries. Plain Java, no OSGi. |
| `io.openems.edge.timedata.timescaledb` | OSGi DS component wiring the shared library to OpenEMS Edge. |
| `io.openems.backend.timedata.timescaledb` | OSGi DS component wiring the shared library to OpenEMS Backend. |

### Edge vs Backend schema

Both tenancy variants share one schema *except* for the edge dimension.
`SchemaHandler` is a single class holding everything common — the
`channel_def`/`channel` tables, hypertables, aggregates and policies — and
composes the parts that differ dynamically from the `Tenancy` enum:

| | Multi-tenant (`Tenancy.MULTI`, Backend) | Single-tenant (`Tenancy.SINGLE`, Edge) |
|---|---|---|
| `edge` dimension table | yes — one DB holds many edges | none — a single local edge |
| `component` uniqueness | `(edge_id, name)` | `name` |
| `get_or_create_channel_id(...)` | takes an edge name; upserts the edge | no edge argument |

The `Tenancy` passed to the `TimescaleDbHandler` constructor
(`Tenancy.SINGLE` / `Tenancy.MULTI`) is handed to the `SchemaHandler` on
`connect()`. The `ChannelManager` is a single class that switches its lookup /
resolve SQL on the same enum (edge join and edge-name bind only in multi-tenant
mode). The read/write handlers, `DataPoint`, and the rest of the API are
tenancy-agnostic — in single-tenant mode the `edgeName` simply flows through
and is ignored.

Channel registration is a small "conductor" stored procedure
(`get_or_create_channel_id`) that calls modular PL/pgSQL helpers
(`get_or_create_component`, `get_or_create_channel_def`, `get_or_create_channel`,
plus `get_or_create_edge` in the multi-tenant variant). A single server-side call stays atomic
with no extra round trips.

### Fast Lane vs Slow Lane

Every channel carries a boolean `rollup` flag, derived in code from its
`PersistencePriority` (`VERY_HIGH` → `rollup = true`): (OR it can be set accordingly)

- **Fast Lane** (`rollup = true`) — `data_*_rollup_*` views, built
  `WHERE rollup` and cascaded 1m → 15m → 1d on Edge and Backend alike.
- **Slow Lane** (all channels) — `data_15m_*` / `data_1d_*`, no filter, kept
  forever.

`ReadHandler.pickSource` routes each query to the coarsest view whose bucket is
≤ the requested resolution, in the channel's lane — and falls back to the shared
view for rollup windows older than the Fast Lane retention.

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

> **Not a console field:** the tenancy (`Tenancy.SINGLE` /
> `Tenancy.MULTI`) is fixed in code by whichever bundle activates the
> handler (Edge → single-tenant, Backend → multi-tenant) and selects the
> schema/resolver variant.

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

-- Are aggregates refreshing?
SELECT view_name, last_successful_finish
FROM timescaledb_information.job_stats
WHERE proc_name = 'policy_refresh_continuous_aggregate';
```

---

## Public API

Once a `TimescaleDbHandler` is constructed (done for you by the Edge or Backend
bundle's `activate(...)`), the surface is small:

```java
TimescaleDbHandler db = new TimescaleDbHandler(Tenancy.SINGLE, host, username, password)
        .database("data")            // optional — defaults shown in parentheses ("data")
        .port(5432)                  // (5432)
        .poolSize(10)                // (10)
        .writeWorkers(4)             // (4)
        .rawRetentionDays(30)        // (30)
        .rawCompressionDays(7)       // (7)
        .readOnly(false)             // (false) — true discards all writes, e.g. for migrations
        .connect();                  // opens the pool, applies the schema, starts the workers,
                                     // and warms up the channel cache with one bulk query

// Write — returns near-immediately; JDBC happens on worker threads
db.writeBatch(points);

// Read
db.queryLatestValue(edgeName, addr);
db.queryHistoricData(edgeName, from, to, channels, resolution);
db.queryHistoricEnergy(edgeName, from, to, channels);
db.queryHistoricEnergyPerPeriod(edgeName, from, to, channels, resolution);

// Monitoring
db.debugLog();      // "TimescaleDB [queue:123/1000000|written:456789]"
db.debugMetrics();  // hypertable sizes in MB — the Backend exposes both via DebugLoggable

// Shutdown — drains the queue best-effort, then closes the pool
db.deactivate();
```

Energy queries (`queryHistoricEnergy*`) read raw tables and compute
last-minus-first deltas with counter-reset handling — they never use the
averaging aggregates.

---

## Requirements

- Java 17+
- TimescaleDB ≥ 2.8 (timezone-aware `time_bucket`) with the `pg_uuidv7` extension
