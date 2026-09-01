# File-Based Platform Implementation

This package provides filesystem-based implementations for the core platform interfaces of the Cognotik system. These
components handle authentication, authorization, session data storage, and user settings persistence using the local
file system and classpath resources.

## Components

### [AuthenticationManager](AuthenticationManager.kt)

A simple implementation of `AuthenticationInterface` that manages user sessions in memory.

- Maps access tokens to `User` objects.
- Provides a `defaultUser` (typically `user@localhost`) when no access token is provided or found.
- Supports basic login (`putUser`) and `logout` operations.

### [AuthorizationManager](AuthorizationManager.kt)

An implementation of `AuthorizationInterface` that uses text files located in the classpath to define permissions.

- **Permission Resolution**: Checks for permissions at global paths (e.g., `/permissions/read.txt`) and
  application-specific paths (e.g., `/permissions/com/package/name/read.txt`).
- **Matching Logic**:
    - `email@example.com`: Exact match for a specific user.
    - `@example.com`: Matches any user within a specific domain.
    - `.`: Matches any authenticated user.
    - `*`: Matches any user, including anonymous/unauthenticated users.

### [DataStorage](DataStorage.kt)

Handles the storage and retrieval of session data and messages on the filesystem.

- **Session Hierarchy**: Organizes data into `global` and `user-sessions` directories.
- **Session IDs**:
    - `G-YYYY-MM-DD-ID`: Global sessions accessible to everyone.
    - `U-YYYY-MM-DD-ID`: User-specific private sessions.
- **Message Management**: Stores individual messages as JSON files within session directories.
- **Metadata Integration**: Works alongside a `MetadataStorageInterface` to manage session listings and properties.

### [UserSettingsManager](UserSettingsManager.kt)

Manages persistent user preferences and settings.

- Stores settings as JSON files named after the user (e.g., `user@example.com.json`) in a configured root directory.
- Provides thread-safe access to `UserSettings` objects.
- Automatically creates default settings if no configuration file exists for a user.

## Configuration and Usage

### Permissions

To configure permissions, place `.txt` files in your resources folder under `/permissions/`. For example, to allow all
users in `example.com` to perform `read` operations in the `com.simiacryptus.app` package:
File: `src/main/resources/permissions/com/simiacryptus/app/read.txt`
Content:

```text
@example.com
```

### Data Directory

The `DataStorage` and `UserSettingsManager` require a root directory on the filesystem. This is typically configured
during application startup via `ApplicationServicesImpl`.

## Implementation Details

- **JSON Serialization**: Uses Jackson (via `JsonUtil`) for persisting settings and messages.
- **Logging**: Comprehensive logging is implemented across all managers to track authorization decisions and filesystem
  operations.
- **Thread Safety**: `DataStorage` uses synchronization for message ID updates, and `UserSettingsManager` uses a
  `HashMap` with lazy loading for settings.