# Platform Services

    The `com.simiacryptus.cognotik.platform` package provides the core infrastructure and service management for the
    Cognotik application. It handles service orchestration, cloud integration, session management, and concurrency.

    The *ports* (interfaces, domain model, exception hierarchy) live in `lwcore`; this module contains the concrete
    implementations that back them.

    ## Key Components

    ### [ApplicationServices](./ApplicationServices.kt)

    The central registry for application-wide services. It manages the lifecycle and access to:

    - **Authentication & Authorization**: Pluggable managers for user identity and permissions.
    - **Cloud Platform**: Integration with cloud providers (defaults to AWS).
    - **Thread Management**: Centralized thread pool management via `ThreadPoolManager`.
    - **Storage Services**: Access to `FileApplicationServices` which provides:
        - `DataStorage`: File-based data persistence (`StorageInterface`).
        - `MetadataStorageDB`: Database for session metadata.
        - `UsageDB`: Usage tracking and analytics.
        - `UserSettingsDB`: Management of user-specific configurations.
        - `GiftedCreditsDB`: Promotional credit gifts and claims.

    ### [AwsPlatform](./AwsPlatform.kt)

    An implementation of `CloudPlatformInterface` that leverages Amazon Web Services.

    - **S3 Integration**: Handles file uploads to a configured S3 bucket for sharing.
    - **KMS Integration**: Provides encryption and decryption services using AWS Key Management Service.
    - **Configuration**: Supports configuration via system properties (`share_bucket`, `share_base`, `aws.profile`) and uses
      standard AWS credential provider chains (Instance Profile and Profile providers).

    ### [Session](./Session.kt)

    A value object representing a unique session within the system.

    - **Session Types**: Distinguishes between Global (`G-`) and User (`U-`) sessions.
    - **ID Generation**: Generates time-stamped, cryptographically random identifiers.
    - **Validation**: Enforces strict format validation for session strings using regex patterns.
    - **Null sentinel**: `Session.NULL` is rejected by `DataStorage`; a valid session id is always required.

    ### [ThreadPoolManager](./ThreadPoolManager.kt)

    Manages execution contexts scoped to specific sessions and users.

    - **Scoped Executors**: Provides `ImmediateExecutorService` and `ListeningScheduledExecutorService` instances cached by
      session and user (`user` is optional).
    - **Observability**: Uses `RecordingThreadFactory` to ensure threads are properly tagged with session and user metadata
      for logging and debugging. Factories for *both* immediate and scheduled pools are tracked, so `isAlive` sees them all.
    - **Lifecycle**: `shutdown(session, user)` evicts and stops the pools for a scope; terminated threads are pruned from
      the tracker so long-lived sessions do not retain every thread ever created.

    ## Port conformance notes

    Several defaults on the `lwcore` interfaces are self-recursive migration stubs and must be overridden. The
    implementations here do so:

    | Port | Overridden here |
    |---|---|
    | `StorageInterface.listSessionsForUser` | `DataStorage.listSessionsForUser` |
    | `MessageStore.getMessageMap` | `DataStorage.getMessageMap` |
    | `JsonStore.getJson` | `DataStorage.getJson` |
    | `SessionFileStore.userRootFor` | `DataStorage.userRootFor` |
    | `MetadataStorageInterface.getSessionTimestamp` | `MetadataStorageDB.getSessionTimestamp` |
    | `MetadataStorageInterface.listSessionsByPath` / `listSessionsForUser` | `MetadataStorageDB` |
    | `MetadataStorageInterface.getSessionPath` / `setSessionPath` / `exists` | `MetadataStorageDB` |
    | `AuthenticationInterface.logoutIfMatching` | `AuthenticationManager` |
    | `AuthorizationInterface.isAuthorized(ResourceRef, Principal, OperationType)` | `AuthorizationManager` |

    ### Authentication

    `AuthenticationManager` stores only a SHA-256 hash of each bearer token. Consequently `getAccessToken(user)` is
    deprecated and always returns `null`; use `listTokens(user)` and `TokenMetadata.tokenId` to build session-management
    UIs, and `revokeAll(user)` to invalidate every session.

    ### Authorization

    `AuthorizationManager` is now keyed on `(ResourceRef?, Principal, OperationType)` and consults
    `OperationType.implies`, so a grant in `admin.txt` satisfies a `Read` check. `Principal.System` is always allowed;
    all errors fail secure (deny).

    ## Configuration

    Many services in this package are configured via `ApplicationServicesConfig`. Call
    `ApplicationServicesConfig.lock()` exactly once from bootstrap to prevent modification of core services after the
    application has initialized.

    ### AWS System Properties

    - `share_bucket`: The S3 bucket name for uploads (default: `share.simiacrypt.us`).
    - `share_base`: The base URL for shared resources.
    - `aws.profile`: The AWS CLI profile to use for credentials.

    ## Usage Example

    ```kotlin
    // Accessing the default data storage
    val storage = ApplicationServices.fileApplicationServices().dataStorageFactory

    // Generating a new user session
    val session = Session.newUserID()

    // Getting a thread pool for a specific session
    val pool = ApplicationServices.threadPoolManager.getPool(session)

    // Reading/writing session content without leaking java.io.File
    storage.openWrite(user, session, "notes/summary.md").use { it.write(text.toByteArray()) }
    val paths = storage.list(user, session, prefix = "notes/")

    // Clearing a metadata field (impossible with snapshot writes)
    ApplicationServices.fileApplicationServices().metadataDB.updateSessionMetadata(
      user, session, SessionMetadataPatch(workerId = Patch.Set(null))
    )

    // Releasing a session's executors
    ApplicationServices.threadPoolManager.shutdown(session)
    ```