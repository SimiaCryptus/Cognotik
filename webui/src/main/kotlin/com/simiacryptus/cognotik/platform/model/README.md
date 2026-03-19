# Platform Model Interfaces

This package defines the core abstractions and data models for the Cognotik platform. It provides a set of interfaces
for authentication, authorization, storage, cloud integration, and user configuration, allowing for flexible
implementations across different environments.

## Core Components

### Identity and Security

* **`User`**: A data class representing a platform user, identified primarily by their email address. It includes
  optional fields for name, ID, and profile picture.
* **`AuthenticationInterface`**: Manages user sessions and tokens. It provides methods for retrieving users by access
  token, managing session lifecycle (login/logout), and defines the standard `sessionId` cookie.
* **`AuthorizationInterface`**: Defines the access control logic. It uses an `OperationType` enum (Read, Write, Public,
  Share, Execute, Delete, Admin) to determine if a user has permission to perform specific actions within an application
  context.

### Storage and Metadata

* **`StorageInterface`**: The primary interface for managing session data. It handles:
  * Message persistence and retrieval.
  * Session directory management.
  * JSON serialization of session settings.
  * Listing and deleting sessions.
* **`MetadataStorageInterface`**: Specifically handles session-level metadata such as display names, message ID
  sequences, and timestamps, separating these from the raw message content.
* **`ApplicationServicesConfig`**: A global configuration object that defines the `dataStorageRoot` (defaulting to
  `~/.cognotik`) and provides a locking mechanism to prevent runtime configuration changes.

### Cloud and Usage

* **`CloudPlatformInterface`**: Abstraction for cloud-native services, including:
  * **Storage**: Uploading binary or text content to a public/shared URL.
  * **Security**: Encrypting and decrypting data using cloud-managed keys (e.g., KMS).
* **`UsageInterface`**: Tracks AI model consumption. It records token counts (input/output) and monetary costs per user
  and session, providing thread-safe accumulation of usage statistics.

### User Configuration

* **`UserSettingsInterface`**: Manages persistent user-specific preferences.
* **`UserSettings`**: A container for:
  * **`ApiData`**: Configuration for AI providers (OpenAI, Anthropic, etc.), including API keys and base URLs.
  * **`ToolData`**: Custom tools or commands available to the user.
  * **`ApiChatModel`**: Links specific chat models with their required provider configurations.
* **Serialization**: Includes custom Jackson deserializers to maintain backward compatibility with legacy configuration
  formats (e.g., migrating from simple API key maps to structured `ApiData` lists).

## Implementation Details

Most interfaces in this package are designed to be implemented by service managers (e.g., `UserSettingsManager`,
`AuthorizationManager`). The `StorageInterface` implementation typically handles the physical file structure on disk,
while `MetadataStorageInterface` may use a database or sidecar files to track session history.

### Session ID Formats

The platform distinguishes between two types of sessions via ID prefixes:

* `G-`: Global sessions accessible to multiple users.
* `U-`: User-specific sessions restricted to the owner.