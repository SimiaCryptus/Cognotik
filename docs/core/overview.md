# SkyeNet Core Module Documentation

## Overview

The core module provides the fundamental building blocks for the SkyeNet platform, including:

1. Actor System for AI-driven interactions
2. Platform Services for system configuration and management
3. Authentication and Authorization
4. Storage and Data Management
5. Utility Classes

## 1. Actor System

The actor system provides a framework for AI-driven interactions through several specialized actors:

### BaseActor
Abstract base class for all actors providing:
- Model configuration
- Temperature control
- Response generation
- Chat message handling

### CodingActor
Specialized actor for code generation and execution:
- Language-specific interpreter support
- Code validation and execution
- Error handling and correction
- Iterative improvement of generated code

### ImageActor
Handles image generation tasks:
- Integration with DALL-E API
- Image prompt optimization
- Size and format control
- Response handling

### LargeOutputActor
Manages generation of large text outputs:
- Recursive content expansion
- Section-based refinement
- Quality improvement iterations
- Content validation

### ParsedActor
Handles structured data parsing:
- Type-safe response parsing
- Schema validation
- Error recovery
- Mapping capabilities

### SimpleActor
Basic implementation for simple text interactions:
- Direct prompt-response handling
- Temperature control
- Model configuration

### TextToSpeechActor
Manages text-to-speech conversion:
- Voice selection
- Speed control
- Audio format handling
- Multiple model support

## 2. Platform Services

### ApplicationServices
Central configuration and service management:
```kotlin
object ApplicationServices {
    var authorizationManager: AuthorizationInterface
    var userSettingsManager: UserSettingsInterface
    var authenticationManager: AuthenticationInterface
    var dataStorageFactory: (File) -> StorageInterface
    var metadataStorageFactory: (File) -> MetadataStorageInterface
    var clientManager: ClientManager
    var cloud: CloudPlatformInterface?
    var usageManager: UsageInterface
}
```

### ClientManager
Manages API clients and thread pools:
- OpenAI client management
- Thread pool management
- Session-based client caching
- Usage tracking

### Cloud Platform Integration
AWS integration providing:
- S3 storage
- KMS encryption
- Credential management
- File upload capabilities

## 3. Authentication/Authorization

### Authentication System
```kotlin
interface AuthenticationInterface {
    fun getUser(accessToken: String?): User?
    fun putUser(accessToken: String, user: User): User
    fun logout(accessToken: String, user: User)
}
```

### Authorization System
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

### User Management
```kotlin
data class User(
    val email: String,
    val name: String?,
    val id: String?,
    val picture: String?,
    val credential: Any?
)
```

## 4. Storage Interfaces

### Data Storage
```kotlin
interface StorageInterface {
    fun getMessages(user: User?, session: Session): LinkedHashMap<String, String>
    fun getSessionDir(user: User?, session: Session): File
    fun getDataDir(user: User?, session: Session): File
    fun listSessions(user: User?, path: String): List<Session>
    fun updateMessage(user: User?, session: Session, messageId: String, value: String)
    // ... additional methods
}
```

### Metadata Storage
```kotlin
interface MetadataStorageInterface {
    fun getSessionName(user: User?, session: Session): String
    fun setSessionName(user: User?, session: Session, name: String)
    fun getMessageIds(user: User?, session: Session): List<String>
    fun setMessageIds(user: User?, session: Session, ids: List<String>)
    // ... additional methods
}
```

### Usage Tracking
```kotlin
interface UsageInterface {
    fun incrementUsage(session: Session, apiKey: String?, model: OpenAIModel, tokens: ApiModel.Usage)
    fun getUserUsageSummary(apiKey: String): Map<OpenAIModel, ApiModel.Usage>
    fun getSessionUsageSummary(session: Session): Map<OpenAIModel, ApiModel.Usage>
}
```

## 5. Utility Classes

### File Validation
- `FileValidationUtils`: Code validation and file filtering
- `CommonRoot`: Path manipulation utilities
- `GetModuleRootForFile`: Project structure utilities

### Text Processing
- `IterativePatchUtil`: Advanced diff and patch handling
- `SimpleDiffApplier`: Code modification utilities
- `StringSplitter`: Text segmentation tools

### Logging
- `LoggingInterceptor`: Logging capture and management
- `OutputInterceptor`: Output stream management

### Function Handling
- `FunctionWrapper`: Function interception utilities
- `MultiException`: Error aggregation
- `RuleTreeBuilder`: Rule-based decision trees

## Integration Points

1. Actor System Integration:
```kotlin
val actor = CodingActor(
    interpreterClass = KotlinInterpreter::class,
    symbols = mapOf("util" to utilityObject),
    model = OpenAIModels.GPT4
)
val result = actor.respond(input, api)
```

2. Authentication Usage:
```kotlin
if (ApplicationServices.authorizationManager.isAuthorized(
    applicationClass = this::class.java,
    user = currentUser,
    operationType = OperationType.Execute
)) {
    // Perform authorized operation
}
```

3. Storage Implementation:
```kotlin
val storage = ApplicationServices.dataStorageFactory(rootDir)
storage.updateMessage(user, session, messageId, content)
```

## Best Practices

1. Actor Implementation:
   - Extend BaseActor for consistent behavior
   - Implement proper error handling
   - Use appropriate temperature settings
   - Handle model fallbacks

2. Storage:
   - Use appropriate storage interfaces
   - Implement proper error handling
   - Maintain data consistency
   - Handle concurrent access

3. Security:
   - Always check authorization
   - Validate user input
   - Handle sensitive data appropriately
   - Implement proper session management

4. Error Handling:
   - Use MultiException for aggregating errors
   - Implement proper logging
   - Provide meaningful error messages
   - Handle recovery scenarios

## Configuration

The core module can be configured through ApplicationServices:

```kotlin
ApplicationServices.apply {
    dataStorageRoot = File("/path/to/data")
    authenticationManager = CustomAuthManager()
    authorizationManager = CustomAuthManager()
    userSettingsManager = CustomSettingsManager()
    clientManager = CustomClientManager()
}
```

This documentation provides a comprehensive overview of the core module's capabilities and integration points.