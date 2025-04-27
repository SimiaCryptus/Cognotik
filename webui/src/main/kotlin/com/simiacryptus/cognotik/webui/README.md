# Cognotik Web UI Module Documentation

## Overview

The Cognotik Web UI module provides a comprehensive framework for building interactive web applications powered by AI.
It offers a WebSocket-based communication system, session management, and a variety of UI components for creating
chat-based interfaces and other interactive applications.

## Core Components

### Chat System

The chat system provides the foundation for real-time communication between clients and the server.

#### `ChatServer`

An abstract base class that sets up WebSocket communication and session management.

- Manages WebSocket connections and session state
- Handles client connections and message routing
- Provides session persistence

#### `ChatSocket`

Handles individual WebSocket connections and message passing.

- Manages connection lifecycle (connect, disconnect)
- Handles message sending and receiving
- Maintains user context

#### `ChatSocketManager`

Manages the chat interaction logic and message processing.

- Processes user messages and generates responses
- Supports advanced query expansion syntax
- Extracts and manages topics from conversations
- Handles message rendering and formatting

#### `BasicChatApp`

A simple implementation of a chat application using the framework.

- Configurable with different language models
- Provides basic chat functionality

### Session Management

The session system handles user sessions, state persistence, and task management.

#### `SocketManager` and `SocketManagerBase`

Interface and base implementation for managing WebSocket connections and session state.

- Handles multiple concurrent connections to the same session
- Manages message state and replay
- Provides task creation and management

#### `SessionTask`

Represents a unit of work that can display progress and results to the user.

- Supports incremental updates
- Handles error reporting
- Provides file saving capabilities
- Supports various output formats (text, HTML, images)

### Application Framework

The application framework provides a structure for building web applications.

#### `ApplicationServer`

Base class for web applications that extends `ChatServer`.

- Configures servlets and routes
- Manages settings and session state
- Handles user authentication and authorization

#### `ApplicationDirectory`

Manages a collection of applications and provides a unified interface.

- Configures the web server
- Manages application routing
- Handles authentication and common services

#### `ApplicationInterface`

Provides UI components and interaction patterns.

- Creates interactive links and buttons
- Manages text input forms
- Creates and manages tasks

#### `ApplicationSocketManager`

Extends `SocketManagerBase` to provide application-specific functionality.

- Processes user messages in an application context
- Provides access to the application interface

### Servlets

The module includes various servlets for handling HTTP requests.

#### File Management

- `FileServlet`: Base class for serving files
- `SessionFileServlet`: Serves files from a session directory
- `ZipServlet`: Creates and serves ZIP archives of session files

#### Session Management

- `NewSessionServlet`: Creates new sessions
- `SessionListServlet`: Lists available sessions
- `SessionSettingsServlet`: Manages session settings
- `SessionShareServlet`: Shares sessions with other users
- `SessionThreadsServlet`: Shows active threads in a session
- `DeleteSessionServlet`: Deletes sessions
- `CancelThreadsServlet`: Cancels running threads in a session

#### Authentication and User Management

- `OAuthBase` and `OAuthGoogle`: Handles OAuth authentication
- `UserInfoServlet`: Provides user information
- `UserSettingsServlet`: Manages user settings
- `LogoutServlet`: Handles user logout
- `ApiKeyServlet`: Manages API keys

#### Other Utilities

- `AppInfoServlet`: Provides application information
- `UsageServlet`: Shows API usage statistics
- `ProxyHttpServlet`: Proxies requests to external APIs
- `CorsFilter`: Handles CORS headers
- `WelcomeServlet`: Serves the welcome page

### Test Applications

The module includes several test applications that demonstrate various capabilities.

- `SimpleActorTestApp`: Tests simple AI actors
- `ParsedActorTestApp`: Tests actors that parse structured data
- `CodingActorTestApp`: Tests actors that generate code
- `ImageActorTestApp`: Tests actors that generate images
- `FilePatchTestApp`: Tests file patching capabilities

## Key Features

### Query Expansion Syntax

The chat system supports advanced query expansion syntax:

- **Parallel Expansion**: `{option1|option2|option3}` - Runs the same prompt with each option in parallel
- **Sequence Expansion**: `<step1;step2;step3>` - Runs a sequence of prompts, with each output feeding into the next
- **Range Expansion**: `[[start..end:step]]` - Iterates over a range of numbers
- **Topic Reference Expansion**: `{topicType}` - Refers to previously identified topics

### Topic Extraction

The system automatically extracts named entities and topics from conversations, making them available for reference in
future queries.

### Interactive UI Components

- **Links**: Create clickable links that trigger server-side actions
- **Text Inputs**: Create forms for user input
- **Tasks**: Display progress and results of long-running operations
- **File Handling**: Save and serve files generated during sessions

### Session Sharing and Management

- Share sessions with other users
- Export sessions as static HTML
- Manage session settings
- Monitor and control session threads

## Usage Examples

### Creating a Basic Chat Application

```kotlin
val app = BasicChatApp(
    root = File("/path/to/data"),
    model = ChatModel.GPT4,
    parsingModel = ChatModel.GPT35Turbo,
    applicationName = "My Chat App"
)
```

### Creating a Custom Application

```kotlin
class MyCustomApp(root: File) : ApplicationServer(
  applicationName = "My Custom App",
  path = "/myapp",
  root = root
) {
  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    val task = ui.newTask()
    task.echo(userMessage)

    val response = "This is a response to: $userMessage"

    task.complete(response)
  }
}
```

### Using the Session Task API

```kotlin
fun processUserQuery(query: String, ui: ApplicationInterface) {
  val task = ui.newTask()
  task.echo(query)

  task.add("Processing your request...")

  try {

    val result = doSomeWork(query)

    task.complete("Here's your result: $result")
  } catch (e: Exception) {
    task.error(ui, e)
  }
}
```

### Creating Interactive UI Elements

```kotlin
fun createInteractiveUI(ui: ApplicationInterface) {
  val task = ui.newTask()

  val link = ui.hrefLink("Click me", "my-link") {
    task.add("You clicked the link!")
  }

  val input = ui.textInput { text ->
    task.add("You entered: $text")
  }

  task.add("Here are some interactive elements: $link $input")
}
```

## Best Practices

1. **Session Management**: Use the session system to maintain state between user interactions.
2. **Task-Based UI**: Use the task system to show progress and results of operations.
3. **Error Handling**: Use the error reporting capabilities to provide useful feedback to users.
4. **Topic Extraction**: Leverage the topic extraction system to make conversations more contextual.
5. **Query Expansion**: Use the query expansion syntax to create more dynamic interactions.

## Architecture Considerations

- The system is designed to be stateless on the server side, with state maintained in the session.
- WebSockets are used for real-time communication, with fallback to HTTP for file serving and other operations.
- The system is designed to be scalable, with multiple users able to connect to the same session.
- Authentication and authorization are handled at the application level, with support for OAuth.
