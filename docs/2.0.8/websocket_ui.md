# Cognotik Web UI Library Documentation

## Overview

The Cognotik WebUI Library provides a comprehensive framework for building AI-powered chat applications with web
interfaces. It offers real-time WebSocket communication, session management, file handling, and advanced chat features.

### Key Features

- **Real-time Communication**: WebSocket-based bidirectional messaging
- **Session Management**: Persistent sessions with message history
- **File Handling**: Upload, download, and process files within chat sessions
- **UI Components**: Rich set of UI elements including tabs, expandable sections, and interactive elements
- **Security**: Built-in authentication and authorization
- **Scalability**: Thread pool management and efficient message queuing

## Getting Started

### Prerequisites

- Java 8 or higher
- Kotlin support
- Jetty WebSocket server
- Maven or Gradle for dependency management

### Installation

Add the following dependency to your project:

```xml
<!-- Maven -->
<dependency>
    <groupId>com.simiacryptus</groupId>
    <artifactId>cognotik-webui</artifactId>
    <version>2.0.8</version>
</dependency>
```

```gradle
// Gradle
implementation 'com.simiacryptus:cognotik-webui:2.0.8'
```

## Architecture

### Component Overview

```
┌─────────────────────────────────────────┐
│           Client Browser                 │
│  ┌─────────────────────────────────┐    │
│  │    JavaScript WebSocket Client   │    │
│  └──────────────┬──────────────────┘    │
└─────────────────┼───────────────────────┘
                  │ WebSocket
┌─────────────────┼───────────────────────┐
│                 ▼                        │
│  ┌──────────────────────────────────┐   │
│  │        ChatSocket                │   │
│  └──────────────┬───────────────────┘   │
│                 │                        │
│  ┌──────────────▼───────────────────┐   │
│  │      SocketManager               │   │
│  │  ┌────────────────────────────┐  │   │
│  │  │   Message Queue & State    │  │   │
│  │  └────────────────────────────┘  │   │
│  └──────────────┬───────────────────┘   │
│                 │                        │
│  ┌──────────────▼───────────────────┐   │
│  │       SessionTask                │   │
│  │  ┌────────────────────────────┐  │   │
│  │  │   Task Operations          │  │   │
│  │  └────────────────────────────┘  │   │
│  └──────────────────────────────────┘   │
│           Cognotik Server                │
└──────────────────────────────────────────┘
```

## Core Components

### 1. SocketManager

Manages WebSocket connections and message routing for a session.

```kotlin
abstract class SocketManager(
    val sessionId: Session,
    val dataStorage: StorageInterface? = null,
    val owner: User? = null,
    private val applicationClass: Class<*>
)
```

**Key Responsibilities:**

- WebSocket connection lifecycle management
- Message queuing and delivery
- Session state persistence
- Authorization checks

**Key Methods:**

- `addSocket()` - Register a new WebSocket connection
- `removeSocket()` - Clean up disconnected socket
- `send()` - Broadcast message to all connected clients
- `getReplay()` - Retrieve message history for reconnection

### 2. SessionTask

Handles individual operations within a chat session.

 ```kotlin
 class SessionTask(
    val messageID: String,
    private var buffer: MutableList<StringBuilder> = mutableListOf(),
    private val spinner: String = SessionTask.spinner,
    val manager: SocketManager
)
```

**Key Responsibilities:**

- Content buffering and rendering
- File management
- UI element generation
- Task lifecycle management

**Key Methods:**

- `add()` - Add content to the task output
- `complete()` - Mark task as finished
- `error()` - Display error messages
- `saveFile()` - Save files and return URLs

## Basic Usage

### Real-time UI Updates with Tabbed Displays

```kotlin
// Create tabbed interface for complex operations
val tabbedDisplay = TabbedDisplay(task)

// Add tabs dynamically
val planningTab = ui.newTask(false).apply {
    tabbedDisplay["Planning"] = placeholder
}

val executionTab = ui.newTask(false).apply {
    tabbedDisplay["Execution"] = placeholder
}

val resultsTab = ui.newTask(false).apply {
    tabbedDisplay["Results"] = placeholder
}

// Update tabs as work progresses
planningTab.complete("Planning phase completed")
executionTab.add("Executing task 1 of 3...")
```

### File Handling

```kotlin
// In a SessionTask
val task = ui.newTask()

// Save a file
val fileUrl = task.saveFile("report.pdf", pdfData)
task.add("Report saved: <a href='$fileUrl'>Download</a>")

// Create a file for writing
val (url, file) = task.createFile("output.txt")
file?.writeText("Hello, World!")
```

### Custom UI Elements

```kotlin
// Add expandable sections
task.expandable(
    title = "Advanced Options",
    content = "This content can be collapsed/expanded"
)

// Add images
task.image(bufferedImage)

// Create interactive links
val link = task.hrefLink("Click me") {
    task.add("Link was clicked!")
}
task.add("Interactive element: $link")
// Add headers with different levels
task.header("Main Section", level = 1)
task.header("Subsection", level = 2)
```

## Advanced Features

### Message Versioning and Replay

The SocketManager maintains message versions to handle reconnections and ensure message consistency:

```kotlin
// Get replay messages since a timestamp
val replayMessages = socketManager.getReplay(since = lastTimestamp)
// Messages are automatically versioned
// Format: "messageId,version,content"
```

### Thread Pool Management

```kotlin
// Access managed thread pools
val pool = socketManager.pool  // For general tasks
val scheduledPool = socketManager.scheduledThreadPoolExecutor  // For scheduled tasks
// Submit async tasks
pool.submit {
    // Long-running operation
    val result = performComplexCalculation()
    task.add("Result: $result")
    task.complete()
}
```

### Custom Authentication and Authorization

```kotlin
class CustomAuthManager : AuthenticationInterface {
    override fun getUser(authToken: String?): User? {
        // Implement custom authentication logic
        return validateToken(authToken)
    }
}
class CustomAuthzManager : AuthorizationInterface {
    override fun isAuthorized(
        applicationClass: Class<*>,
        user: User?,
        operationType: OperationType
    ): Boolean {
        // Implement custom authorization logic
        return checkPermissions(user, operationType)
    }
}
// Configure in ApplicationServices
ApplicationServices.authenticationManager = CustomAuthManager()
ApplicationServices.authorizationManager = CustomAuthzManager()
```

### Error Handling

```kotlin
override fun onRun(userMessage: String, socket: ChatSocket) {
    val task = newTask()
    try {
        // Process message
        val result = processMessage(userMessage)
        task.add(result)
        task.complete()
    } catch (e: ValidationError) {
        task.error(e, showSpinner = false)
    } catch (e: Exception) {
        log.error("Processing failed", e)
        task.error(e)
    }
// Create linked tasks for complex workflows
    val mainTask = ui.newTask(true)
    val subTask = mainTask.linkedTask("Subtask Name")
    subTask.add("This is a linked subtask")
// Add verbose information that can be toggled
    task.verbose("Detailed debug information here")
}
```

## Configuration

### WebSocket Configuration

```kotlin
// In ChatServer.WebSocketHandler
override fun configure(factory: JettyWebSocketServletFactory) {
    with(factory) {
        isAutoFragment = false
        idleTimeout = Duration.ofMinutes(10)
        outputBufferSize = 1024 * 1024
        inputBufferSize = 1024 * 1024
        maxBinaryMessageSize = 1024 * 1024
        maxFrameSize = 1024 * 1024
        maxTextMessageSize = 1024 * 1024
    }
}
```

### Storage Configuration

```kotlin
// Custom storage implementation
class CustomStorage : StorageInterface {
    override fun getSessionDir(user: User?, session: Session): File? {
        // Your custom storage logic
        return File("/path/to/sessions/$session")
    }

    override fun getMessages(user: User?, session: Session): LinkedHashMap<String, String> {
        // Load existing messages
    }

    override fun updateMessage(user: User?, session: Session, messageId: String, message: String) {
        // Save message updates
    }
}
```

## WebSocket Protocol

### Message Format

Messages between client and server follow specific formats:

**Server to Client:**

```
messageId,version,htmlContent
```

**Client to Server:**

```
// User message
plainTextMessage
// Command
!cmdType,messageId,data
// Heartbeat
{"type":"ping"}
```

### Command Types

| Command   | Format                    | Description          |
|-----------|---------------------------|----------------------|
| `link`    | `!link,id`                | Trigger link handler |
| `userTxt` | `!userTxt,id,encodedText` | Submit text input    |

## Performance Optimization

### Message Queuing

The SocketManager implements efficient message queuing:

```kotlin
// Messages are queued per socket
private val sendQueues: MutableMap<ChatSocket, Deque<String>> = ConcurrentHashMap()

// Processing happens asynchronously
private fun processQueue(chatSocket: ChatSocket) {
    val deque = sendQueues[chatSocket] ?: return
    while (deque.poll()?.let { msg ->
            chatSocket.remote.sendString(msg)
            true
        } == true) {
        // Continue processing
    }
    chatSocket.remote.flush()
}
```

### Resource Management

```kotlin
// Automatic cleanup on socket disconnect
override fun onWebSocketClose(statusCode: Int, reason: String?) {
    socketManager.removeSocket(this)
    // Resources are automatically cleaned up
}

// Session cleanup
override fun close() {
    sockets.keys.forEach { removeSocket(it) }
    pool.shutdown()
    scheduledThreadPoolExecutor.shutdown()
}
```

## API Reference

### SocketManager Methods

| Method                                                 | Description                           |
|--------------------------------------------------------|---------------------------------------|
| `newTask(root: Boolean, cancelable: Boolean)`          | Create new session task               |
| `send(message: String)`                                | Send message to all connected clients |
| `addSocket(socket: ChatSocket, session: Session)`      | Register new WebSocket connection     |
| `removeSocket(socket: ChatSocket)`                     | Remove WebSocket connection           |
| `getReplay(since: Long)`                               | Get messages since timestamp          |
| `onWebSocketText(socket: ChatSocket, message: String)` | Handle incoming WebSocket message     |
| `canWrite(user: User?)`                                | Check write permissions               |
| `createLinkedManager(newSession: Session)`             | Create linked session manager         |

### SessionTask Methods

| Method                                                                                                                  | Description                      |
|-------------------------------------------------------------------------------------------------------------------------|----------------------------------|
| `add(message: String, showSpinner: Boolean, tag: String, additionalClasses: String)`                                    | Add content to task output       |
| `echo(message: String, showSpinner: Boolean, tag: String)`                                                              | Echo user message                |
| `complete(message: String, tag: String, additionalClasses: String)`                                                     | Complete task with final message |
| `error(e: Throwable)`                                                                                                   | Display error message            |
| `saveFile(path: String, data: ByteArray)`                                                                               | Save file and return URL         |
| `image(image: BufferedImage)`                                                                                           | Display image                    |
| `expandable(title: String, content: String, showSpinner: Boolean, tag: String, additionalClasses: String)`              | Create expandable section        |
| `expanded(title: String, content: String, showSpinner: Boolean, tag: String, additionalClasses: String)`                | Create expanded section          |
| `hideable(message: String, showSpinner: Boolean, tag: String, additionalClasses: String, socketManager: SocketManager)` | Create hideable message          |
| `header(message: String, level: Int, showSpinner: Boolean, additionalClasses: String)`                                  | Add header                       |
| `verbose(message: String, showSpinner: Boolean, tag: String)`                                                           | Add verbose output               |
| `hrefLink(text: String, handler: Consumer<Unit>)`                                                                       | Create interactive link          |

## Troubleshooting

| Method | Description |
|--------|-------------|

### Common Issues

1. **WebSocket Connection Drops**
    - Check idle timeout configuration
    - Implement heartbeat mechanism
    - Review proxy/firewall settings

2. **Message Not Displaying**
    - Verify message format (messageId,version,content)
    - Check browser console for JavaScript errors
    - Ensure proper HTML escaping

### 3. ChatSocket

WebSocket endpoint handling individual client connections.

```kotlin
class ChatSocket(
    val sessionId: Session,
    val socketManager: SocketManager,
    val user: User?
)
```

### Creating a Simple Chat Application

```kotlin
class SimpleChatApp : ApplicationServer {
    override fun newSession(user: User?, session: Session): SocketManager {
        return object : SocketManager(session, dataStorage, user, SimpleChatApp::class.java) {
            override fun onRun(userMessage: String, socket: ChatSocket) {
                val task = newTask()
                task.echo(userMessage)
                // Process the message
                val response = processMessage(userMessage)
                task.add(response)
                task.complete()
            }
        }
    }
}
```