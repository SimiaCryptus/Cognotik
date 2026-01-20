# HSQL Platform Implementations

This package contains HSQLDB-backed implementations of the core platform interfaces for Cognotik. These components provide a robust, SQL-based persistence layer that supports both persistent file-based storage and transient in-memory operation.

## Components

### [HSQLMetadataStorage](HSQLMetadataStorage.kt)

An implementation of `MetadataStorageInterface` used to manage session-specific metadata.

- **Schema**: Maintains a `metadata` table storing key-value pairs associated with session IDs and user emails.
- **Key Features**:
    - **Session Naming**: Stores and retrieves human-readable names for chat sessions.
    - **Message Tracking**: Persists lists of message IDs associated with a session to maintain conversation structure.
    - **Temporal Metadata**: Tracks session creation and update times.
    - **Session Management**: Provides capabilities to list sessions by path and delete session data.
- **Database Modes**: Automatically switches between `jdbc:hsqldb:file` (if a root directory is provided) and `jdbc:hsqldb:mem` (if no root is provided).

### [HSQLUsageManager](HSQLUsageManager.kt)

An implementation of `UsageInterface` designed for tracking and reporting AI model consumption.

- **Schema**: Utilizes a `usage` table to log individual usage events, including token counts and calculated costs.
- **Key Features**:
    - **Usage Logging**: Records prompt tokens, completion tokens, and cost for every model interaction.
    - **Aggregation**: Provides methods to generate usage summaries grouped by model for specific users or sessions.
    - **Data Lifecycle**: Includes functionality to clear usage logs.
- **Persistence**: Like the metadata storage, it supports both persistent file storage and in-memory operation for testing or ephemeral environments.

## Implementation Details

- **Drivers**: Uses the standard HSQLDB JDBC driver (`org.hsqldb.jdbc.JDBCDriver`).
- **Schema Evolution**: Both classes include `createSchema` logic that executes `CREATE TABLE IF NOT EXISTS` during initialization, ensuring the database is ready for use without manual setup.
- **Concurrency**: Configured with `hsqldb.lock_file=false` and `shutdown=true` in file mode to manage database locks and ensure clean shutdowns within the application lifecycle.