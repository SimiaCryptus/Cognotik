# SkyeNet WebUI Module Documentation

## Overview

The WebUI module provides a web-based interface for the SkyeNet platform, implementing:

1. Application Server Framework
2. Chat System
3. Session Management
4. WebSocket Communication
5. Servlet Infrastructure

## 1. Application Server Framework

### ApplicationServer
Base class for web applications providing:
- HTTP/WebSocket server setup
- Resource management
- Authentication/authorization integration
- Child application management

```kotlin
abstract class ApplicationServer(
    val applicationName: String,
    val path: String,
    resourceBase: String = "application"
) {
    // Core server configuration
    abstract val dataStorage: StorageInterface?
    val sessions: MutableMap<Session, SocketManager>
    
    // Abstract methods
    abstract fun newSession(user: User?, session: Session): SocketManager
    abstract fun userMessage(session: Session, user: User?, message: String, ui: ApplicationInterface, api: API)
}
```

### ApplicationDirectory
Manages multiple applications and routing:
```kotlin
abstract class ApplicationDirectory(
    val localName: String = "localhost",
    val publicName: String = "localhost",
    val port: Int = 8081
) {
    abstract val childWebApps: List<ChildWebApp>
    
    data class ChildWebApp(
        val path: String,
        val server: ChatServer,
        val thumbnail: String?
    )
}
```

## 2. Chat System

### ChatServer
Base implementation for chat functionality:
```kotlin
abstract class ChatServer(resourceBase: String) {
    abstract val applicationName: String
    abstract fun newSession(user: User?, session: Session): SocketManager
}
```

### ChatSocket
WebSocket implementation for real-time communication:
```kotlin
class ChatSocket(
    private val sessionState: SocketManager
) : WebSocketAdapter() {
    override fun onWebSocketConnect(session: Session)
    override fun onWebSocketText(message: String)
    override fun onWebSocketClose(statusCode: Int, reason: String?)
}
```

### ChatSocketManager
Handles chat message processing and state:
```kotlin
open class ChatSocketManager(
    session: Session,
    val model: ChatModel,
    val userInterfacePrompt: String,
    val systemPrompt: String,
    val api: ChatClient
) : SocketManagerBase {
    protected val messages: MutableList<ApiModel.ChatMessage>
    fun respond(api: ChatClient, messagesCopy: List<ApiModel.ChatMessage>): String
}
```

## 3. Session Management

### SessionTask
Manages individual user interaction sessions:
```kotlin
abstract class SessionTask(
    val messageID: String,
    private var buffer: MutableList<StringBuilder>,
    private val spinner: String
) {
    fun append(htmlToAppend: String, showSpinner: Boolean): StringBuilder?
    fun add(message: String, showSpinner: Boolean = true)
    fun error(ui: ApplicationInterface?, e: Throwable)
    fun complete(message: String = "")
}
```

### SocketManager
Interface for WebSocket session management:
```kotlin
interface SocketManager {
    fun removeSocket(socket: ChatSocket)
    fun addSocket(socket: ChatSocket, session: Session)
    fun getReplay(): List<String>
    fun onWebSocketText(socket: ChatSocket, message: String)
}
```

### SocketManagerBase
Base implementation providing core session functionality:
```kotlin
abstract class SocketManagerBase(
    protected val session: Session,
    protected val dataStorage: StorageInterface?,
    protected val owner: User?,
    private val applicationClass: Class<*>
) : SocketManager {
    private val sockets: MutableMap<ChatSocket, Session>
    private val sendQueues: MutableMap<ChatSocket, Deque<String>>
    
    fun newTask(cancelable: Boolean = false): SessionTask
    fun send(out: String)
}
```

## 4. Servlet Infrastructure

### Core Servlets

1. **AppInfoServlet**: Application information and configuration
2. **SessionServlets**:
   - SessionFileServlet: File management
   - SessionListServlet: Session listing
   - SessionSettingsServlet: Settings management
   - SessionShareServlet: Session sharing
   - SessionThreadsServlet: Thread management
3. **UserServlets**:
   - UserInfoServlet: User information
   - UserSettingsServlet: User settings
   - LogoutServlet: Authentication management

### Authentication/Authorization

```kotlin
class SessionIdFilter(
    val isSecure: (HttpServletRequest) -> Boolean,
    private val loginRedirect: String
) : Filter {
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain)
}
```

### File Management

```kotlin
abstract class FileServlet : HttpServlet() {
    abstract fun getDir(req: HttpServletRequest): File
    protected open fun getFile(dir: File, pathSegments: List<String>, req: HttpServletRequest): File
}
```

## 5. Integration Points

### 1. Creating a New Application

```kotlin
class CustomApp : ApplicationServer(
    applicationName = "CustomApp",
    path = "/custom"
) {
    override fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: API
    ) {
        // Handle user messages
    }
}
```

### 2. WebSocket Integration

```kotlin
val socketManager = object : SocketManagerBase(session, storage, user) {
    override fun onRun(userMessage: String, socket: ChatSocket) {
        // Process incoming messages
    }
}
```

### 3. Session Task Usage

```kotlin
val task = ui.newTask()
task.echo("User input: $userMessage")
try {
    val result = processUserInput(userMessage)
    task.complete("Result: $result")
} catch (e: Exception) {
    task.error(ui, e)
}
```

## Best Practices

1. Session Management:
   - Always clean up resources
   - Handle disconnections gracefully
   - Implement proper error handling
   - Use appropriate timeouts

2. WebSocket Communication:
   - Buffer messages appropriately
   - Handle reconnection scenarios
   - Implement proper message ordering
   - Use appropriate message sizes

3. Security:
   - Validate all user input
   - Implement proper authentication
   - Use appropriate authorization
   - Handle sensitive data carefully

4. Performance:
   - Use appropriate buffer sizes
   - Implement proper caching
   - Handle large files efficiently
   - Manage memory usage

## Configuration

The WebUI module can be configured through various parameters:

```kotlin
ApplicationDirectory(
    localName = "localhost",
    publicName = "example.com",
    port = 8081
).apply {
    childWebApps = listOf(
        ChildWebApp(
            path = "/app1",
            server = CustomApp(),
            thumbnail = "thumbnail.png"
        )
    )
}
```

This documentation provides a comprehensive overview of the WebUI module's capabilities and integration points.