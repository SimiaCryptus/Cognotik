# HSQL Storage Backend

This package provides [HyperSQL (HSQLDB)](http://hsqldb.org/) backed implementations of the Cognotik platform storage
interfaces. It is the default persistent storage layer used by the web UI for metadata, usage tracking, and user
settings.

## Overview

The HSQL backend is organized around the concept of **facets**. Each facet is a logically separate database with its own
schema, connection pool, and (optionally)
embedded server. Facets share a common implementation, [`HSQLFacet`](./HSQLFacet.kt), which centralizes:

- JDBC driver loading
- Embedded server lifecycle (start, shutdown hook)
- Connection caching by JDBC URL
- Schema initialization (DDL is executed once per URL)
- Transaction and connection-locking helpers

Three facets are provided out of the box:

| Facet           | Class                     | Purpose                                            |
|-----------------|---------------------------|----------------------------------------------------|
| `metadata`      | `HSQLMetadataStorage`     | Session metadata (names, message IDs, owners, ...) |
| `usage`         | `HSQLUsageManager`        | Token/cost usage tracking, budgets, credits        |
| `user_settings` | `HSQLUserSettingsManager` | Per-user settings stored as JSON blobs             |

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Application Code                     │
└──────────────┬──────────────┬───────────────┬───────────┘
│              │               │
┌────────▼─────┐ ┌──────▼──────┐ ┌──────▼────────────┐
│  Metadata    │ │   Usage     │ │  UserSettings     │
│  Storage     │ │   Manager   │ │  Manager          │
└────────┬─────┘ └──────┬──────┘ └──────┬────────────┘
│              │               │
┌────────▼──────────────▼───────────────▼────────────┐
│                    HSQLFacet                       │
│  - Driver loading                                  │
│  - Embedded server lifecycle                       │
│  - Connection caching                              │
│  - Schema initialization                           │
│  - Transaction / locking helpers                   │
└────────┬───────────────────────────────────────────┘
│
┌────────▼───────────────┐    ┌────────────────────┐
│  Embedded HSQL Server  │ OR │ External HSQL URL  │
│  (mem: or file:)       │    │ (jdbc:hsqldb:hsql) │
└────────────────────────┘    └────────────────────┘
```

Each facet either:

- **Embeds** an `org.hsqldb.server.Server` instance bound to a configurable host/port, with storage in memory (`mem:`)
  or on disk (`file:`), depending on whether a root directory is provided; or
- **Connects** to an external HSQL server when `cognotik.db.serviceUrl` is set.

## Configuration

All facets honor the following JVM system properties:

| Property                      | Default        | Description                                             |
|-------------------------------|----------------|---------------------------------------------------------|
| `cognotik.db.serviceUrl`      | _(unset)_      | If set, use this external JDBC URL instead of embedded. |
| `cognotik.db.serviceUser`     | `SA`           | Username for external HSQL service.                     |
| `cognotik.db.servicePassword` | _(empty)_      | Password for external HSQL service.                     |
| `cognotik.db.serverHost`      | `localhost`    | Bind address for embedded server.                       |
| `cognotik.db.serverPort`      | `9010`         | Bind port for embedded server.                          |
| `cognotik.db.serverSilent`    | `true`         | Suppress HSQL server log output.                        |
| `cognotik.db.dbName`          | _(facet name)_ | Override the database name (rarely needed).             |

### Embedded vs. External

- **Embedded (default):** Set no `serviceUrl`. The first call to
  `getConnection()` or `getLocalServiceUrl()` starts an embedded `Server`. If a
  `root: File` is passed, the database is persisted at
  `<root>/<dbName>;shutdown=true`; otherwise it lives in memory (`mem:<dbName>`).
- **External:** Set `-Dcognotik.db.serviceUrl=jdbc:hsqldb:hsql://host:port/dbname`
  to connect to an externally managed HSQL server. Credentials come from
  `serviceUser` / `servicePassword`.

## Concurrency Model

`HSQLFacet` caches **one shared `Connection` per JDBC URL** across the JVM. Because a JDBC `Connection` is not
thread-safe for arbitrary multi-statement use (especially when toggling `autoCommit`), the facet exposes two locking
helpers:

- `withConnection(root) { conn -> ... }` — serializes a single block of work on the shared connection. Use this for
  single-statement operations that must not race with transactions.
- `withTransaction(root) { conn -> ... }` — same locking as `withConnection`, but additionally sets
  `autoCommit = false`, commits on success, rolls back on exception, and restores the prior `autoCommit` state. Always
  prefer these helpers over calling `getConnection()` directly when issuing multiple statements that must be atomic or
  isolated from other threads.

## Schemas

Schemas are declared in each manager's `companion object` via the `schemaSql`
parameter passed to `HSQLFacet`. All DDL uses `CREATE TABLE IF NOT EXISTS` /
`CREATE INDEX IF NOT EXISTS` so initialization is idempotent. Schemas are applied at most once per JDBC URL per JVM.

### `metadata` facet

```sql
CREATE TABLE metadata (
session_id VARCHAR(255),
user_email VARCHAR(255),
key        VARCHAR(255),
value      LONGVARCHAR,
timestamp  TIMESTAMP,
PRIMARY KEY (session_id, user_email, key)
);
```

A simple key/value store keyed by `(session_id, user_email, key)`. Used for session names, message ID lists, session
timestamps, owners, and arbitrary path tags.

### `usage` facet

Tables:

- `usage` — append-only log of per-call usage rows (`session_id`, `user_id`, `model`, `prompt_tokens`,
  `completion_tokens`,
  `cost`, `datetime`).
- `usage_daily` — rolled-up per-day totals keyed by
  `(user_id, day, model)` for fast summary queries.
- `session_parents` — child→parent session links used by
  `getSessionUsageSummary` to aggregate across session trees.
- `user_credits` — append-only ledger of credit grants (`user_id`, `amount`, `comment`, `metadata`, `datetime`).
- `user_budget` — current available budget per user (updated by `incrementUsage` and `creditUser`). Indexes are created
  on commonly filtered columns (`session_id`, `(user_id, datetime)`, `parent_session_id`, `(user_id, day)`).

### `user_settings` facet

```sql
CREATE TABLE user_settings (
user_key      VARCHAR(255) PRIMARY KEY,
settings_json LONGVARCHAR,
timestamp     TIMESTAMP
);
```

Stores each user's `UserSettings` as a JSON blob. `user_key` is derived from the user's email (falling back to
`user.toString()` if email is unavailable). A small in-process cache avoids repeated reads.

## Usage

### Reading metadata

```kotlin
val storage = HSQLMetadataStorage(File("/var/lib/cognotik/metadata"))
val name = storage.getSessionName(user, session)
storage.setSessionName(user, session, "My Session")
```

### Tracking usage

```kotlin
val usage = HSQLUsageManager(File("/var/lib/cognotik/usage"))
usage.incrementUsage(
  session, user, model, ModelSchema.Usage(
    prompt_tokens = 100,
    completion_tokens = 50,
    cost = 0.0012
  )
)
val summary = usage.getUserUsageSummary(user, LocalDate.now().minusDays(7), LocalDate.now())
val budget = usage.getAvailableBudget(user)
```

Note that `getUserUsageSummary` and `getUserDailyUsage` treat the `to` date as **exclusive**.

### User settings

```kotlin
val settings = HSQLUserSettingsManager(File("/var/lib/cognotik/user_settings"))
val current = settings.getUserSettings(user)
settings.updateUserSettings(user, current.copy(/* ... */))
```

`updateUserSettings` merges with previously-stored settings so that a blank
`passwordHash` does not clobber an existing one.

### Direct JDBC access

For ad-hoc queries or migrations, each manager exposes a static `getConn(root)`
that returns the shared `Connection` for its facet:

```kotlin
val conn = HSQLMetadataStorage.getConn(rootDir)
```

Remember to use `facet.withConnection { ... }` / `facet.withTransaction { ... }`
if you need to serialize work against other callers.

## Lifecycle and Shutdown

Each embedded server registers a JVM shutdown hook that calls `Server.shutdown()`. When using `file:` storage, the JDBC
URL also includes `;shutdown=true`, which instructs HSQL to flush and close the database cleanly on the last connection
close. In practice, both mechanisms together ensure data is persisted on normal JVM exit. There is no explicit `close()`
API on the managers — connections are cached for the lifetime of the JVM and reused across calls.

## Adding a New Facet

To add a new HSQL-backed storage area:

1. Define your schema as a `List<String>` of DDL statements (each ending without a trailing semicolon, since HSQL
   `executeUpdate` runs one statement at a time).
2. Create a singleton `HSQLFacet`:

```kotlin
internal val facet = HSQLFacet(
  name = "my_feature",
  schemaSql = listOf(
    """CREATE TABLE IF NOT EXISTS my_table (...)"""
  )
)
```

3. Use `facet.withConnection { ... }` or `facet.withTransaction { ... }` for all database access.
4. Optionally expose `getConn(root)` and/or `getLocalServiceUrl(root)` helpers on your companion object for external
   tooling.

## Limitations and Caveats

- **Single shared connection per URL.** All concurrency must go through the provided locking helpers. Long-running
  queries will block other callers on the same facet.
- **No connection pooling.** If you need true parallel query execution, run an external HSQL server and front it with a
  proper pool (e.g. HikariCP) in a custom implementation.
- **MERGE syntax.** The SQL uses HSQL's `MERGE INTO ... USING (VALUES(...))`
  syntax, which is not portable to other databases without modification.
- **In-memory mode loses data.** When no `root` directory is supplied, the database lives in `mem:` and is discarded on
  JVM exit.