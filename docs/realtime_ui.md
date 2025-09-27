# Real-time UI System Developer Documentation

## Overview

The real-time UI system provides a WebSocket-based framework for building interactive web applications with live
updates. It enables bidirectional communication between the server and client, allowing for dynamic content updates,
user interactions, and session management.

## Architecture

### Core Components

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   ChatServer    │────│ SocketManager    │────│   SessionTask   │
│   (Abstract)    │    │   (Interface)    │    │   (Abstract)    │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │                       │
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│  BasicChatApp   │    │SocketManager │    │SessionTaskImpl  │
│  (Concrete)     │    │   (Abstract)     │    │   (Concrete)    │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │
         │                       │
         ▼                       ▼
┌─────────────────┐    ┌──────────────────┐
│   ChatSocket    │    │ChatSocketManager │
│   (WebSocket)   │    │   (Concrete)     │
└─────────────────┘    └──────────────────┘
```

## Core Classes

### 1. ChatServer (Abstract Base)

The foundation class for creating chat-based applications.

```kotlin

```

**Key Responsibilities:**

- WebSocket connection management
- Session lifecycle management
- Servlet configuration
- Resource serving

**Implementation Example:**

```kotlin

```

### 2. SocketManager Interface

Defines the contract for managing WebSocket connections and message handling.

```kotlin

```

### 3. SocketManager (Abstract Implementation)

Provides core functionality for socket management with message queuing and processing.

**Key Features:**

- **Message State Management**: Maintains message history with versioning
- **Queue Processing**: Handles message delivery with concurrent processing
- **Authorization**: Integrates with authentication/authorization system
- **File Operations**: Supports file creation and storage

**Message Format:**

```
messageID,version,content
```

**Critical Methods:**

#### `newTask(cancelable: Boolean, root: Boolean): SessionTask`

Creates a new task for UI operations:

```kotlin
val task = newTask(cancelable = true, root = true)
task.add("Hello, World!")
task.complete()
```

#### `send(out: String)`

Sends messages to all connected clients:

```kotlin
// Format: "messageID,content"
send("task123,<div>Updated content</div>")
```

#### Message Processing Pipeline:

1. **Validation**: Check message format and size limits
2. **State Update**: Update message state with versioning
3. **Queue Distribution**: Add to each socket's send queue
4. **Async Processing**: Process queues concurrently
5. **Error Handling**: Remove failed sockets

### 4. SessionTask (Abstract)

Provides a high-level API for building interactive UI components.

**Core Methods:**

#### Content Addition

```kotlin
// Basic message
task.add("Simple message")

// With custom styling
task.add("Styled message", tag = "p", additionalClasses = "highlight")

// Echo user input
task.echo("User said: Hello")

// Headers
task.header("Section Title", level = 2)
```

#### Interactive Elements

```kotlin
// Expandable sections
task.expandable("Click to expand", "Hidden content")

// File operations
val url = task.saveFile("data.json", jsonBytes)
val (url, file) = task.createFile("output.txt")

// Links with handlers
val link = task.hrefLink("Click me") {
    task.add("Link clicked!")
}
```

#### Error Handling

```kotlin
try {
    // Risky operation
} catch (e: Exception) {
    task.error(ui, e)
}
```

### 5. ChatSocketManager

Concrete implementation providing chat functionality with advanced features.

**Key Features:**

#### Message Expansion Syntax

- **Parallel**: `{option1|option2|option3}` - Process options in parallel
- **Sequential**: `<step1;step2;step3>` - Chain operations
- **Range**: `[[1..10:2]]` - Iterate over numeric ranges
- **Topic Reference**: `{Person}` - Reference extracted topics

#### Topic Extraction

Automatically identifies and categorizes named entities:

```kotlin

```

#### Implementation Example:

```kotlin
val chatManager = ChatSocketManager(
    session = session,
    model = ChatModel.GPT4o,
    parsingModel = ChatModel.GPT4oMini,
    systemPrompt = "You are a helpful assistant",
    api = chatClient,
    temperature = 0.3,
    applicationClass = MyApp::class.java,
    storage = dataStorage,
    budget = 10.0
)
```

## Message Flow

### 1. Client to Server

```
WebSocket Message → ChatSocket.onWebSocketText() → SocketManager.onWebSocketText() → processUserMessage()
```

### 2. Server to Client

```
SessionTask.add() → SocketManager.send() → Message Queuing → WebSocket Delivery
```

### 3. Message Processing Pipeline

```kotlin
// 1. Receive user message
override fun onRun(userMessage: String, socket: ChatSocket) {
    // 2. Expand topics and syntax
    val expandedMessage = expandTopics(userMessage)

    // 3. Create task for response
    val task = newTask()

    // 4. Process with AI
    val response = respond(api, task, expandedMessage, chatMessages)

    // 5. Extract topics
    val topics = extractTopics(api, response)

    // 6. Complete task
    task.complete()
}
```

## WebSocket Protocol

### Connection Establishment

```javascript
const ws = new WebSocket(`ws://localhost:8080/ws?sessionId=${sessionId}`);
```

### Message Types

#### 1. Content Updates

```
Format: messageID,version,content
Example: "abc123,1,<div>Hello World</div>"
```

#### 2. Commands

```
Format: !commandType,parameters
Examples:
- "!link,abc123" - Trigger link handler
- "!userTxt,abc123,Hello%20World" - Submit text input
```

#### 3. Heartbeat

```json
// Ping
{"type": "ping"}

// Pong
{"type": "pong"}
```

## Security Model

### Authentication

- Cookie-based authentication via `AuthenticationInterface.AUTH_COOKIE`
- User context available throughout the session

### Authorization

```kotlin
// Check read access
ApplicationServices.authorizationManager.isAuthorized(
    applicationClass = MyApp::class.java,
    user = user,
    operationType = OperationType.Read
)


```

### Input Validation

- Message size limits (default: 1MB)
- Path traversal protection for file operations
- Command validation and sanitization

## Advanced Features

### 1. Message Expansion

#### Parallel Processing

```kotlin
// Input: "Tell me about {cats|dogs|birds}"
// Creates 3 parallel tasks, each processing one animal
```

#### Sequential Processing

```kotlin
// Input: "Analyze this data, then <summarize;translate to French;create chart>"
// Chains operations where each step uses the previous output
```

#### Range Expansion

```kotlin
// Input: "Generate report for year [[2020..2024:1]]"
// Creates reports for 2020, 2021, 2022, 2023, 2024
```

### 2. Topic Management

```kotlin
// Topics are automatically extracted and stored
private val aggregateTopics = ConcurrentHashMap<String, MutableList<String>>()

// Reference topics in future messages
// Input: "Tell me more about {Person}"
// Expands to: "Tell me more about {John Doe|Jane Smith|...}"
```

### 3. File Management

```kotlin
// Save files to session directory
val url = task.saveFile("report.pdf", pdfBytes)

// Create files for writing
val (url, file) = task.createFile("output.log")
file?.writeText("Log entry")
```

### 4. UI Components

#### Tabbed Display

```kotlin
val tabs = TabbedDisplay(task, closable = false)
tabs["Tab 1"] = task1.placeholder
tabs["Tab 2"] = task2.placeholder
tabs.update()
```

#### Interactive Forms

```kotlin
val textInput = textInput { userInput ->
    task.add("You entered: $userInput")
}
```

## Error Handling

### Connection Errors

```kotlin
try {
    socket.remote.sendString(message)
} catch (e: Exception) {
    log.error("Error sending message", e)
    removeSocket(socket)
}
```

### Processing Errors

```kotlin
try {
    // Process user message
} catch (e: Throwable) {
    task.error(ui, e)
    log.error("Processing error", e)
}
```

### Validation Errors

```kotlin
// File path validation
require(!relativePath.contains("..")) {
    "Invalid file path: path traversal not allowed"
}

// Message size validation
if (message.length > maxMessageLength) {
    send("${randomID()},<div class=\"error\">Message too long</div>")
    return
}
```

## Performance Considerations

### 1. Message Queuing

- Concurrent processing of send queues
- Automatic socket cleanup on failures
- Message deduplication based on content

### 2. Memory Management

```kotlin
// Synchronized access to shared state
private val stateLock = Any()
synchronized(stateLock) {
    messageStates[key] = value
    messageTimestamps[key] = System.currentTimeMillis()
}
```

### 3. Thread Pool Management

```kotlin
// Separate pools for different operations
val pool = clientManager.getPool(session, owner)
val scheduledPool = clientManager.getScheduledPool(session, owner, dataStorage)
```

## Best Practices

### 1. Task Management

```kotlin
// Always complete tasks
val task = newTask()
try {
    task.add("Processing...")
    // Do work
    task.add("Result: $result")
} finally {
    task.complete()
}
```

### 2. Error Handling

```kotlin
// Provide user-friendly error messages
try {
    riskyOperation()
} catch (e: Exception) {
    task.error(ui, e)
    log.error("Operation failed", e)
}
```

### 3. Resource Management

```kotlin
// Clean up resources
override fun removeSocket(socket: ChatSocket) {
    try {
        sendQueues.remove(socket)
        sockets.remove(socket)
        queueProcessing.remove(socket)
    } catch (e: Exception) {
        log.error("Cleanup error", e)
    }
}
```

### 4. Security

```kotlin
// Always validate user input
require(relativePath.isNotBlank()) { "File path cannot be blank" }
require(!relativePath.contains("..")) { "Path traversal not allowed" }

// Check permissions
if (!canWrite(socket.user)) {
    send("${randomID()},<div class=\"error\">Unauthorized</div>")
    return
}
```

## Example Implementation

```kotlin

```
