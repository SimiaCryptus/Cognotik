# Platform Services

The `com.simiacryptus.cognotik.platform` package provides the core infrastructure and service management for the Cognotik application. It handles service orchestration, cloud integration, session management, and concurrency.

## Key Components

### [ApplicationServices](./ApplicationServices.kt)
The central registry for application-wide services. It manages the lifecycle and access to:
- **Authentication & Authorization**: Pluggable managers for user identity and permissions.
- **Cloud Platform**: Integration with cloud providers (defaults to AWS).
- **Thread Management**: Centralized thread pool management via `ThreadPoolManager`.
- **Storage Services**: Access to `FileApplicationServices` which provides:
    - `DataStorage`: File-based data persistence.
    - `HSQLMetadataStorage`: Database for metadata.
    - `HSQLUsageManager`: Usage tracking and analytics.
    - `UserSettingsManager`: Management of user-specific configurations.

### [AwsPlatform](./AwsPlatform.kt)
An implementation of `CloudPlatformInterface` that leverages Amazon Web Services.
- **S3 Integration**: Handles file uploads to a configured S3 bucket for sharing.
- **KMS Integration**: Provides encryption and decryption services using AWS Key Management Service.
- **Configuration**: Supports configuration via system properties (`share_bucket`, `share_base`, `aws.profile`) and uses standard AWS credential provider chains (Instance Profile and Profile providers).

### [Session](./Session.kt)
A value object representing a unique session within the system.
- **Session Types**: Distinguishes between Global (`G-`) and User (`U-`) sessions.
- **ID Generation**: Generates time-stamped, cryptographically random identifiers.
- **Validation**: Enforces strict format validation for session strings using regex patterns.

### [ThreadPoolManager](./ThreadPoolManager.kt)
Manages execution contexts scoped to specific sessions and users.
- **Scoped Executors**: Provides `ImmediateExecutorService` and `ListeningScheduledExecutorService` instances cached by session and user.
- **Observability**: Uses `RecordingThreadFactory` to ensure threads are properly tagged with session and user metadata for logging and debugging.

## Configuration

Many services in this package are configured via `ApplicationServicesConfig`. The `ApplicationServices` object supports a locking mechanism (`isLocked`) to prevent modification of core services after the application has initialized.

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
```