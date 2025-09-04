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
│           Service Layer                 │
│  ┌─────────────┐ ┌─────────────────────┐│
│  │ Client      │ │ Application         ││
│  │ Manager     │ │ Services            ││
│  └─────────────┘ └─────────────────────┘│
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

- Global sessions: `G-YYYYMMDD-XXXX` (accessible to all users)
- User sessions: `U-YYYYMMDD-XXXX` (user-specific)
- Legacy format: `YYYYMMDD-XXXX` (treated as global)

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
val storage = ApplicationServices.dataStorageFactory(dataDir)
val clientManager = ApplicationServices.clientManager
val cloud = ApplicationServices.cloud
val usageManager = ApplicationServices.usageManager
```

## Storage System

### Data Storage Interface

The `StorageInterface` provides methods for managing session data:

```kotlin
interface StorageInterface {
    // Message management
    fun getMessages(user: User?, session: Session): LinkedHashMap<String, String>
    fun updateMessage(user: User?, session: Session, messageId: String, value: String)

    // Directory management
    fun getSessionDir(user: User?, session: Session): File
    fun getDataDir(user: User?, session: Session): File
    fun userRoot(user: User?): File

    // Session management
    fun listSessions(user: User?, path: String): List<Session>
    fun deleteSession(user: User?, session: Session)

    // JSON storage
    fun <T : Any> setJson(user: User?, session: Session, filename: String, settings: T): T
}
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
│   └── 20231215/
│       └── AbC1/
│           ├── messages/
│           └── config.json
├── user-sessions/             # User sessions
│   └── user@example.com/
│       └── 20231215/
│           └── XyZ2/
└── users/                     # User settings
    └── user@example.com.json
```

### Metadata Storage

The `MetadataStorageInterface` handles session metadata:

```kotlin
interface MetadataStorageInterface {
    fun getSessionName(user: User?, session: Session): String
    fun setSessionName(user: User?, session: Session, name: String)
    fun getMessageIds(user: User?, session: Session): List<String>
    fun setMessageIds(user: User?, session: Session, ids: List<String>)
    fun getSessionTime(user: User?, session: Session): Date?
    fun setSessionTime(user: User?, session: Session, time: Date)
    fun listSessions(path: String): List<String>
    fun deleteSession(user: User?, session: Session)
}
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

### Authentication Interface

```kotlin
interface AuthenticationInterface {
    fun getUser(accessToken: String?): User?
    fun putUser(accessToken: String, user: User): User
    fun logout(accessToken: String, user: User)
}
```

### Authorization Interface

```kotlin
interface AuthorizationInterface {
    enum class OperationType {
        Read, Write, Public, Share, Execute, Delete, Admin, GlobalKey
    }

    fun isAuthorized(
        applicationClass: Class<*>?,
        user: User?,
        operationType: OperationType
    ): Boolean
}
```

### File-based Authorization

The `AuthorizationManager` uses resource files for permission management:

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

### User Settings Interface

```kotlin
interface UserSettingsInterface {
    data class UserSettings(
        val apiKeys: Map<APIProvider, String> = mapOf(),
        val apiBase: Map<APIProvider, String> = mapOf(),
        val localTools: List<String> = emptyList()
    )

    fun getUserSettings(user: User): UserSettings
    fun updateUserSettings(user: User, settings: UserSettings)
}
```

### Usage Example

```kotlin
val userSettings = ApplicationServices.userSettingsManager.getUserSettings(user)

// Update API keys
val updatedSettings = userSettings.copy(
    apiKeys = userSettings.apiKeys + (APIProvider.OpenAI to "sk-...")
)

ApplicationServices.userSettingsManager.updateUserSettings(user, updatedSettings)
```

## Usage Tracking

### Usage Interface

```kotlin
interface UsageInterface {
    fun incrementUsage(session: Session, apiKey: String?, model: OpenAIModel, tokens: ApiModel.Usage)
    fun getUserUsageSummary(apiKey: String): Map<OpenAIModel, ApiModel.Usage>
    fun getSessionUsageSummary(session: Session): Map<OpenAIModel, ApiModel.Usage>
    fun clear()
}
```

### HSQL Usage Manager

```kotlin
val usageManager = HSQLUsageManager()

// Track usage
usageManager.incrementUsage(session, apiKey, ChatModel.GPT35Turbo, usage)

// Get summaries
val userUsage = usageManager.getUserUsageSummary(apiKey)
val sessionUsage = usageManager.getSessionUsageSummary(session)
```

## Client Management

### Client Manager

The `ClientManager` provides OpenAI client instances with automatic usage tracking:

```kotlin
val clientManager = ApplicationServices.clientManager

// Get chat client for session
val chatClient = clientManager.getChatClient(session, user)

// Get thread pools
val pool = clientManager.getPool(session, user)
val scheduledPool = clientManager.getScheduledPool(session, user, dataStorage)
```

### Custom Client Creation

```kotlin
class CustomClientManager : ClientManager() {
    override fun createChatClient(session: Session, user: User?): ChatClient? {
        // Custom client creation logic
        return CustomChatClient(session, user)
    }
}

// Use custom client manager
ApplicationServices.clientManager = CustomClientManager()
```

## Cloud Platform Integration

### Cloud Platform Interface

```kotlin
interface CloudPlatformInterface {
    val shareBase: String

    fun upload(path: String, contentType: String, bytes: ByteArray): String
    fun upload(path: String, contentType: String, request: String): String
    fun encrypt(fileBytes: ByteArray, keyId: String): String?
    fun decrypt(encryptedData: ByteArray): String
}
```

### AWS Platform Implementation

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

## Configuration

### Application Services Configuration

```kotlin
// Configure before locking
ApplicationServicesConfig.dataStorageRoot = File("/custom/data/path")

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
ApplicationServices.dataStorageFactory = { file -> CustomStorage(file) }
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

## Testing

### Unit Testing Example

```kotlin
class DataStorageTest {
    private lateinit var tempDir: File
    private lateinit var dataStorage: DataStorage

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("test").toFile()
        dataStorage = DataStorage(tempDir)
    }

    @Test
    fun testMessageStorage() {
        val user = User("test@example.com")
        val session = Session.newUserID()
        val messageId = "test-message"
        val content = "Hello, World!"

        dataStorage.updateMessage(user, session, messageId, content)
        val messages = dataStorage.getMessages(user, session)

        assertEquals(content, messages[messageId])
    }

    @AfterEach
    fun cleanup() {
        tempDir.deleteRecursively()
    }
}
```

## Migration Guide

### Upgrading from Legacy Sessions

```kotlin
// Handle legacy session IDs
fun migrateSession(legacySessionId: String): Session {
    return if (legacySessionId.matches("""\d{8}-[\w+.\-]{4}""".toRegex())) {
        Session("G-$legacySessionId") // Convert to global session
    } else {
        Session.parseSessionID(legacySessionId)
    }
}
```

### Database Migration

```kotlin
// Migrate from file-based to database storage
class MigrationService {
    fun migrateToDatabase(fileStorage: DataStorage, dbStorage: MetadataStorageInterface) {
        // Migration logic
        val sessions = fileStorage.listSessions(null, "/")
        sessions.forEach { session ->
            // Migrate session data
            val messages = fileStorage.getMessages(null, session)
            // Store in database
        }
    }
}
```
