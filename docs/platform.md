---
documents:
    - webui/src/main/kotlin/com/simiacryptus/cognotik/platform/README.md
    - webui/src/main/kotlin/com/simiacryptus/cognotik/platform/**/README.md
specifies: ../site/cognotik.com/platform.html
---

# Cognotik Platform Developer Documentation

## Overview

The Cognotik Platform is a comprehensive application framework designed for building AI-powered applications with
session management, user authentication, data storage, and cloud integration capabilities. The platform provides a
modular architecture with pluggable components for different storage backends, authentication mechanisms, and cloud
providers.

## Architecture

The platform follows a layered architecture with clear separation of concerns:

```
┌─────────────────────────────────────────┐
│           Application Layer             │
├─────────────────────────────────────────┤
│           Platform Services             │
│  ┌─────────────┐ ┌──────────┐ ┌────────┐│
│  │ Thread Pool │ │ Usage    │ │ User   ││
│  │ Manager     │ │ Manager  │ │Settings││
│  └─────────────┘ └──────────┘ └────────┘│
├─────────────────────────────────────────┤
│           Interface Layer               │
│  ┌─────────┐ ┌─────────┐ ┌─────────────┐│
│  │ Storage │ │ Auth    │ │ Cloud       ││
│  │Interface│ │Interface│ │ Interface   ││
│  └─────────┘ └─────────┘ └─────────────┘│
├─────────────────────────────────────────┤
│         Implementation Layer            │
│  ┌─────────┐ ┌─────────┐ ┌─────────────┐│
│  │ File    │ │ HSQL    │ │ AWS         ││
│  │ Storage │ │ Storage │ │ Platform    ││
│  └─────────┘ └─────────┘ └─────────────┘│
└─────────────────────────────────────────┘
```

## Core Components

### 1. Session Management

The `Session` class provides unique session identification and validation:

```kotlin
// Create new sessions
val globalSession = Session.newGlobalID()  // Format: G-YYYYMMDD-XXXX
val userSession = Session.newUserID()      // Format: U-YYYYMMDD-XXXX

// Parse existing session ID
val session = Session.parseSessionID("G-20231215-AbC1")

// Check session type
if (session.isGlobal()) {
    // Handle global session
}
```

**Session ID Format:**

- Global sessions: `G-YYYY-MM-DD-XXXX` (accessible to all users)
- User sessions: `U-YYYY-MM-DD-XXXX` (user-specific to the owner)
- Legacy format: `YYYY-MM-DD-XXXX` (treated as global)

### 2. User Management

The `User` data class represents authenticated users:

```kotlin
val user = User(
    email = "user@example.com",
    name = "John Doe",
    id = "user123",
    picture = "https://example.com/avatar.jpg",
    credential = oauthCredential // Optional credential object
)
```

### 3. Application Services

`ApplicationServices` is the central registry for all platform services:

```kotlin
// Access services
val authManager = ApplicationServices.authenticationManager
val authzManager = ApplicationServices.authorizationManager
val storage = ApplicationServices.fileApplicationServices().dataStorageFactory
val cloud = ApplicationServices.cloud
val usageManager = ApplicationServices.fileApplicationServices().usageManager
val threadPoolManager = ApplicationServices.threadPoolManager
```

### 4. Thread Pool Management

The `ThreadPoolManager` provides execution contexts scoped to specific sessions and users, ensuring proper resource isolation and logging:

```kotlin
// Get a standard thread pool for a session
val pool = ApplicationServices.threadPoolManager.getPool(session, user)

// Get a scheduled executor
val scheduledPool = ApplicationServices.threadPoolManager.getScheduledPool(session, user)
```

## Storage System

### Data Storage Interface

The `StorageInterface` handles the physical persistence of session data:

- **Message Persistence**: Storing and retrieving individual chat messages.
- **Session Management**: Listing, deleting, and organizing session directories.
- **JSON Data**: Generic storage for session-specific configuration and state.

```kotlin

```

### File Storage Implementation

The `DataStorage` class provides file-based storage:

```kotlin
val dataStorage = DataStorage(File("/path/to/data"))

// Store JSON data
dataStorage.setJson(user, session, "config.json", myConfig)

// Update messages
dataStorage.updateMessage(user, session, "msg-123", "Hello World")

// Get session directory
val sessionDir = dataStorage.getSessionDir(user, session)
```

**Directory Structure:**

```
data/
├── global/                    # Global sessions
│   └── 2023-12-15/
│       └── AbC1/
│           ├── messages/
│           └── config.json
├── user-sessions/             # User sessions
│   └── user@example.com/
│       └── 2023-12-15/
│           └── XyZ2/
└── users/                     # User settings
    └── user@example.com.json
```

### Metadata Storage

The `MetadataStorageInterface` manages high-level session information separately from raw content:

- **Session Naming**: Human-readable titles for sessions.
- **Message Sequences**: Maintaining the order and presence of message IDs.
- **Timestamps**: Tracking creation and last-update times.

```kotlin

```

### HSQL Implementation

The `HSQLMetadataStorage` provides in-memory SQL-based metadata storage:

```kotlin
val metadataStorage = HSQLMetadataStorage()

// Set session metadata
metadataStorage.setSessionName(user, session, "My Chat Session")
metadataStorage.setMessageIds(user, session, listOf("msg-1", "msg-2"))

// Query metadata
val sessionName = metadataStorage.getSessionName(user, session)
val messageIds = metadataStorage.getMessageIds(user, session)
```

## Authentication & Authorization
### Authentication
The `AuthenticationManager` handles user identity:
- Maps access tokens to `User` objects.
- Manages the `sessionId` cookie.
- Provides a `defaultUser` (e.g., `user@localhost`) for local development.


### File-based Authorization

The `AuthorizationManager` uses resource files (e.g., `/permissions/read.txt`) to define access. It supports several operation types: `Read`, `Write`, `Public`, `Share`, `Execute`, `Delete`, and `Admin`.

**Permission Files:**

- `/permissions/read.txt` - Global read permissions
- `/permissions/write.txt` - Global write permissions
- `/permissions/com/example/app/read.txt` - App-specific permissions

**Permission Syntax:**

```
user@example.com          # Specific user
@company.com              # Domain-based
.                         # Any authenticated user
*                         # Any user (including anonymous)
```

## User Settings Management

### Usage Example

The `UserSettings` object contains structured configuration for AI providers and tools:

```kotlin
val userSettings = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(user)

// Update API configurations (ApiData includes key, base URL, etc.)
val updatedSettings = userSettings.copy(
    apiKeys = userSettings.apiKeys + ApiData(provider = APIProvider.OpenAI, key = "sk-...")
)

```

## Usage Tracking

### HSQL Usage Manager

```kotlin
val usageManager = HSQLUsageManager()

// Track usage
usageManager.incrementUsage(session, apiKey, ChatModel.GPT35Turbo, usage)

// Get summaries
val userUsage = usageManager.getUserUsageSummary(apiKey)
val sessionUsage = usageManager.getSessionUsageSummary(session)
```

## Cloud Integration

### AWS Platform

The `AwsPlatform` provides S3-based sharing and KMS-based encryption:

```kotlin
val awsPlatform = AwsPlatform(
    bucket = "my-bucket",
    shareBase = "https://my-bucket.s3.amazonaws.com",
    region = Region.US_EAST_1,
    profileName = "my-profile"
)

// Upload file
val url = awsPlatform.upload("path/to/file.txt", "text/plain", fileBytes)

// Encrypt/decrypt with KMS
val encrypted = awsPlatform.encrypt(data, "arn:aws:kms:us-east-1:123456789012:key/12345678-1234-1234-1234-123456789012")
val decrypted = awsPlatform.decrypt(encryptedData)
```
**Configuration Properties:**
- `share_bucket`: S3 bucket for uploads.
- `share_base`: Base URL for shared links.
- `aws.profile`: AWS CLI profile for credentials.


## Configuration

### Application Services Configuration

```kotlin
// Configure before locking
ApplicationServices.dataStorageRoot = File("/custom/data/path")

// Lock configuration to prevent changes
ApplicationServicesConfig.isLocked = true
```

### Custom Service Implementation

```kotlin
// Custom storage implementation
class CustomStorage(dataDir: File) : DataStorage(dataDir) {
    override fun getMessages(user: User?, session: Session): LinkedHashMap<String, String> {
        // Custom implementation
        return super.getMessages(user, session)
    }
}

// Register custom services
ApplicationServices.fileApplicationServices().dataStorageFactory = { file -> CustomStorage(file) }
ApplicationServices.authenticationManager = CustomAuthenticationManager()
ApplicationServices.authorizationManager = CustomAuthorizationManager()
```

## Best Practices

### 1. Session Management

- Always validate session IDs using `Session.validateSessionId()`
- Use global sessions for public content, user sessions for private content
- Clean up expired sessions regularly

### 2. Error Handling

```kotlin
try {
    val session = Session.parseSessionID(sessionId)
    // Use session
} catch (e: IllegalArgumentException) {
    // Handle invalid session ID
    log.error("Invalid session ID: $sessionId", e)
}
```

### 3. Resource Management

```kotlin
// Always ensure directories exist
val sessionDir = dataStorage.getSessionDir(user, session).apply { mkdirs() }

// Use try-with-resources for streams
sessionDir.resolve("data.json").outputStream().use { output ->
    // Write data
}
```

### 4. Security

- Validate user permissions before operations
- Use encrypted storage for sensitive data
- Implement proper session timeout mechanisms

### 5. Performance

- Cache frequently accessed data
- Use appropriate thread pools for concurrent operations
- Monitor usage patterns and optimize accordingly