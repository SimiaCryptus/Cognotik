# HSQL Platform Implementations

This package contains HSQLDB-backed implementations of the core platform interfaces for Cognotik. These components
provide a robust, SQL-based persistence layer that supports both persistent file-based storage and transient in-memory
operation, with the additional capability to connect to a remote HSQL service in CLIENT mode.

## Components

### [HSQLMetadataStorage](HSQLMetadataStorage.kt)

An implementation of `MetadataStorageInterface` used to manage session-specific metadata.

- **Schema**: Maintains a `metadata` table storing key-value pairs associated with session IDs and user emails.
- **Key Features**:
    - **Session Naming**: Stores and retrieves human-readable names for chat sessions.
    - **Message Tracking**: Persists lists of message IDs associated with a session to maintain conversation structure.
    - **Temporal Metadata**: Tracks session creation and update times.
    - **Session Ownership**: Tracks the owner of each session.
    - **Session Management**: Provides capabilities to list sessions by path and delete session data.
- **Database Modes**:
    - **Embedded Server (file-backed)**: When a root directory is provided, HSQL is started as an embedded server
      writing to disk under that directory.
    - **Embedded Server (in-memory)**: When no root directory is provided, the embedded server runs entirely in memory
      (useful for tests or ephemeral deployments).
    - **Remote Client**: When `serviceUrl` (or its system property) is set, the storage skips starting an embedded
      server and instead connects to the specified remote HSQL JDBC URL.

### [HSQLUsageManager](HSQLUsageManager.kt)

An implementation of `UsageInterface` designed for tracking and reporting AI model consumption, including budget
tracking.

- **Schema**: Utilizes multiple tables:
    - `usage` — logs individual usage events (prompt tokens, completion tokens, cost, timestamp).
    - `usage_daily` — pre-aggregated daily totals per (user, day, model).
    - `session_parents` — tracks parent/child relationships between sessions for hierarchical aggregation.
    - `user_credits` — append-only ledger of credit grants/adjustments per user.
    - `user_budget` — cached available budget per user (updated atomically with usage and credits).
- **Key Features**:
    - **Usage Logging**: Records prompt tokens, completion tokens, and cost for every model interaction.
    - **Aggregation**: Provides usage summaries grouped by model for users (over date ranges) or sessions (including
      child sessions).
    - **Daily Reporting**: `getUserDailyUsage` returns per-day usage breakdowns.
    - **Budget Management**: `creditUser` adds credits (with optional comment/metadata) and `getAvailableBudget`
      returns the cached remaining balance.
    - **Session Hierarchy**: `setParentSession` enables roll-up of usage across child sessions.
    - **Data Lifecycle**: `clear()` purges all usage and budget data.
- **Persistence**: Like the metadata storage, supports embedded file-backed, embedded in-memory, and remote-client
  modes.

## Configuration

All important configuration is exposed both as `@JvmStatic` properties on the companion objects and as Java system
properties. System properties are read at class-initialization time. Programmatic assignment to the static fields
overrides the system-property defaults at runtime (but ensure assignment happens before the first connection is
opened, since connections and the embedded server are cached).

### HSQLMetadataStorage configuration

| Property (Kotlin)              | System Property                            | Default       | Description                                                                       |
|--------------------------------|--------------------------------------------|---------------|-----------------------------------------------------------------------------------|
| `serviceUrl`                   | `cognotik.hsql.metadata.serviceUrl`        | `null`        | Optional remote JDBC URL. If set, runs in CLIENT mode (no embedded server).       |
| `serviceUser`                  | `cognotik.hsql.metadata.serviceUser`       | `SA`          | Username used when connecting in CLIENT mode.                                     |
| `servicePassword`              | `cognotik.hsql.metadata.servicePassword`   | (empty)       | Password used when connecting in CLIENT mode.                                     |
| `serverHost`                   | `cognotik.hsql.metadata.serverHost`        | `localhost`   | Bind address for the embedded HSQL server.                                        |
| `serverPort`                   | `cognotik.hsql.metadata.serverPort`        | `9001`        | Port for the embedded HSQL server. `0` lets HSQL pick automatically.              |
| `serverSilent`                 | `cognotik.hsql.metadata.serverSilent`      | `true`        | If `true`, suppresses HSQL server console/log output.                             |
| `dbName`                       | `cognotik.hsql.metadata.dbName`            | `metadata`    | Database name for both in-memory and file modes.                                  |

### HSQLUsageManager configuration

| Property (Kotlin)              | System Property                          | Default       | Description                                                                       |
|--------------------------------|------------------------------------------|---------------|-----------------------------------------------------------------------------------|
| `serviceUrl`                   | `cognotik.hsql.usage.serviceUrl`         | `null`        | Optional remote JDBC URL. If set, runs in CLIENT mode (no embedded server).       |
| `serviceUser`                  | `cognotik.hsql.usage.serviceUser`        | `SA`          | Username used when connecting in CLIENT mode.                                     |
| `servicePassword`              | `cognotik.hsql.usage.servicePassword`    | (empty)       | Password used when connecting in CLIENT mode.                                     |
| `serverHost`                   | `cognotik.hsql.usage.serverHost`         | `localhost`   | Bind address for the embedded HSQL server.                                        |
| `serverPort`                   | `cognotik.hsql.usage.serverPort`         | `9002`        | Port for the embedded HSQL server. `0` lets HSQL pick automatically.              |
| `serverSilent`                 | `cognotik.hsql.usage.serverSilent`       | `true`        | If `true`, suppresses HSQL server console/log output.                             |
| `dbName`                       | `cognotik.hsql.usage.dbName`             | `usage`       | Database name for both in-memory and file modes.                                  |

### Configuration examples

**Via JVM system properties (command line):**

```bash
java \
  -Dcognotik.hsql.metadata.serverPort=19001 \
  -Dcognotik.hsql.usage.serverPort=19002 \
  -Dcognotik.hsql.metadata.serverSilent=false \
  -jar cognotik.jar
```

**Connecting to a remote HSQL service:**

```bash
java \
  -Dcognotik.hsql.metadata.serviceUrl=jdbc:hsqldb:hsql://db-host:9001/metadata \
  -Dcognotik.hsql.metadata.serviceUser=app_user \
  -Dcognotik.hsql.metadata.servicePassword=secret \
  -Dcognotik.hsql.usage.serviceUrl=jdbc:hsqldb:hsql://db-host:9002/usage \
  -Dcognotik.hsql.usage.serviceUser=app_user \
  -Dcognotik.hsql.usage.servicePassword=secret \
  -jar cognotik.jar
```

**Programmatically (Kotlin), before first use:**

```kotlin
HSQLMetadataStorage.serverPort = 19001
HSQLMetadataStorage.serverHost = "0.0.0.0"
HSQLUsageManager.serverPort = 19002
```

> **Note**: The embedded HSQL server and JDBC connections are cached after first use. Set configuration values
> (system properties or static fields) **before** the first call that triggers a connection — otherwise changes
> will not take effect until the JVM is restarted.

## Implementation Details

- **Drivers**: Uses the standard HSQLDB JDBC driver (`org.hsqldb.jdbc.JDBCDriver`), loaded lazily on first use.
- **Schema Evolution**: Both classes execute `CREATE TABLE IF NOT EXISTS` (and `CREATE INDEX IF NOT EXISTS` where
  appropriate) during initialization, ensuring the database is ready for use without manual setup. Schema creation
  is deduplicated per JDBC URL via an internal tracking set.
- **Connection Caching**: A single `Connection` is cached per JDBC URL (via `ConcurrentHashMap`), avoiding
  redundant connection setup and DDL execution.
- **Concurrency**: File-backed mode uses `shutdown=true` to ensure clean shutdown when the last connection closes.
  Both managers register JVM shutdown hooks to gracefully stop the embedded server.
- **Transactions**: `HSQLUsageManager` uses explicit transactions when incrementing usage and applying credit
  deltas, ensuring the `usage`, `usage_daily`, and `user_budget` tables remain consistent.