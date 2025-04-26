# Cognotik API Documentation

## Overview

The Cognotik platform provides a comprehensive set of APIs for building AI-powered applications. The API architecture
consists of:

- RESTful HTTP endpoints for system management
- WebSocket-based real-time communication
- Authentication and authorization controls
- Storage and session management interfaces
- Platform service integrations

## Authentication & Authorization

### Authentication

Authentication is handled through the `AuthenticationInterface`:

```kotlin
interface AuthenticationInterface {
  fun getUser(accessToken: String?): User?
  fun putUser(accessToken: String, user: User): User
  fun logout(accessToken: String, user: User)
  
  companion object {
    const val AUTH_COOKIE = "sessionId"
  }
}
```

Key features:

- Cookie-based session management
- User identity verification
- Session persistence
- Logout capabilities

### Authorization

Authorization is managed through the `AuthorizationInterface`:

```kotlin
interface AuthorizationInterface {
  enum class OperationType {
    Read,
    Write, 
    Public,
    Share,
    Execute,
    Delete,
    Admin,
    GlobalKey
  }

  fun isAuthorized(
    applicationClass: Class<*>?,
    user: User?,
    operationType: OperationType
  ): Boolean
}
```

Features:

- Fine-grained operation control
- Role-based access control
- Application-specific permissions
- Public/private resource management

## Data Models

### User Model

```kotlin
data class User(
  val email: String,
  val name: String?,
  val id: String?,
  val picture: String?,
  val credential: Any?
)
```

### Application Settings

```kotlin
data class AppInfoData(
  val applicationName: String,
  val singleInput: Boolean,
  val stickyInput: Boolean,
  val loadImages: Boolean,
  val showMenubar: Boolean
)
```

### User Settings

```kotlin
data class UserSettings(
  val apiKeys: Map<APIProvider, String>,
  val apiBase: Map<APIProvider, String>
)
```

## REST Endpoints

### Application Management

| Endpoint    | Method   | Description                        |
|-------------|----------|------------------------------------|
| `/appInfo`  | GET      | Retrieve application configuration |
| `/userInfo` | GET      | Get current user information       |
| `/usage`    | GET      | Get API usage statistics           |
| `/settings` | GET/POST | Manage application settings        |

### Session Management

| Endpoint       | Method | Description               |
|----------------|--------|---------------------------|
| `/sessions`    | GET    | List available sessions   |
| `/fileIndex/*` | GET    | Access session files      |
| `/fileZip`     | GET    | Download session archives |
| `/delete`      | POST   | Delete sessions           |
| `/cancel`      | POST   | Cancel running operations |

### User Management

| Endpoint        | Method   | Description             |
|-----------------|----------|-------------------------|
| `/logout`       | POST     | End user session        |
| `/userSettings` | GET/POST | Manage user preferences |
| `/apiKeys`      | GET/POST | Manage API credentials  |

## WebSocket Protocol

### Connection Establishment

1. Connect to `/ws/{sessionId}`
2. Send authentication token
3. Receive session confirmation

### Message Format

```typescript
interface WebSocketMessage {
  type: 'user' | 'system' | 'error' | 'status';
  content: string;
  messageId?: string;
  timestamp: number;
}
```

### Communication Flow

1. Client sends user message
2. Server acknowledges receipt
3. Server processes message
4. Server sends response(s)
5. Client acknowledges receipt

## Storage Interface

The `StorageInterface` provides data persistence capabilities:

```kotlin
interface StorageInterface {
  fun getMessages(user: User?, session: Session): LinkedHashMap<String, String>
  fun updateMessage(user: User?, session: Session, messageId: String, value: String)
  fun getSessionDir(user: User?, session: Session): File
  fun getDataDir(user: User?, session: Session): File
  fun listSessions(user: User?, path: String): List<Session>
  fun deleteSession(user: User?, session: Session)
}
```

Features:

- Message persistence
- File storage
- Session management
- Data organization

## Cloud Platform Integration

The `CloudPlatformInterface` enables cloud service integration:

```kotlin
interface CloudPlatformInterface {
  val shareBase: String
  fun upload(path: String, contentType: String, bytes: ByteArray): String
  fun upload(path: String, contentType: String, request: String): String
  fun encrypt(fileBytes: ByteArray, keyId: String): String?
  fun decrypt(encryptedData: ByteArray): String
}
```

Features:

- File upload/download
- Content sharing
- Encryption/decryption
- Cloud storage management

## Integration Guidelines

### Application Implementation

1. Extend `ApplicationServer`:

```kotlin
class CustomApplication : ApplicationServer(
  applicationName = "Custom App",
  path = "/custom",
  resourceBase = "application"
) {
  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    // Implementation
  }
}
```

2. Configure WebSocket handling:

```kotlin
override fun newSession(user: User?, session: Session): SocketManager {
  return object : ApplicationSocketManager(
    session = session,
    owner = user,
    dataStorage = dataStorage,
    applicationClass = this::class.java
  ) {
    override fun userMessage(/*...*/) {
      // Implementation
    }
  }
}
```

### Security Best Practices

1. Authentication:
    - Always verify user tokens
    - Implement session timeouts
    - Use secure cookie settings

2. Authorization:
    - Check permissions for all operations
    - Implement least privilege access
    - Validate resource ownership

3. Data Security:
    - Encrypt sensitive data
    - Sanitize user input
    - Implement rate limiting

### Error Handling

1. Use standard error responses:

```kotlin
data class ErrorResponse(
  val error: String,
  val code: Int,
  val details: Map<String, Any>? = null
)
```

2. Implement proper error status codes:

- 400: Bad Request
- 401: Unauthorized
- 403: Forbidden
- 404: Not Found
- 500: Internal Server Error

3. Log errors appropriately:

```kotlin
try {
  // Operation
} catch (e: Exception) {
  log.error("Operation failed", e)
  throw ApiException(500, "Internal error", e)
}
```

### Performance Considerations

1. Message Processing:
    - Implement message queuing
    - Use async processing for long operations
    - Implement timeout handling

2. Resource Management:
    - Implement connection pooling
    - Cache frequently accessed data
    - Clean up unused resources

3. Scaling:
    - Design for horizontal scaling
    - Implement proper session management
    - Use distributed storage when needed

## Testing Guidelines

1. Unit Tests:

```kotlin
@Test
fun testAuthentication() {
  val auth = MockAuthenticationManager()
  val token = auth.createToken(testUser)
  val user = auth.getUser(token)
  assertEquals(testUser, user)
}
```

2. Integration Tests:

```kotlin
@Test
fun testWebSocket() {
  val client = WebSocketClient()
  client.connect("ws://localhost:8081/ws/test")
  client.send("Test message")
  val response = client.receiveMessage()
  assertNotNull(response)
}
```

3. Load Tests:

```kotlin
@Test
fun testConcurrentConnections() {
  val clients = List(100) { WebSocketClient() }
  clients.forEach { it.connect() }
  // Test concurrent operations
}
```