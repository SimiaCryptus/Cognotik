# Web UI Chat and Session Management

This package provides the core infrastructure for building interactive, LLM-powered chat applications with a web-based user interface. It handles WebSocket communication, session persistence, complex task management, and advanced chat features like query expansion and history summarization.

## Key Components

### Server Infrastructure
- **`ChatServer`**: An abstract base class for Jetty-based web servers. It configures the necessary servlets (WebSocket, session management, and static resources) and manages the lifecycle of user sessions.
- **`ChatSocket`**: A WebSocket adapter that handles real-time communication between the client and the server. It supports message replay upon reconnection to ensure a seamless user experience.

### Session and Task Management
- **`SocketManager`**: The central coordinator for a single user session. It manages multiple WebSocket connections, maintains message state with versioning, and handles command processing (e.g., button clicks, text input).
- **`SessionTask`**: Represents a specific unit of work or a message block within a session. It provides a rich API for updating the UI, including:
    - Markdown rendering
    - Expandable/collapsible sections
    - File uploads and downloads
    - Error reporting with stack traces
    - Progress indicators (spinners)
    - Interactive elements like links and text inputs

### Chat Logic
- **`ChatSocketManager`**: Specializes `SocketManager` for LLM interactions. It manages the conversation history, performs topic extraction, and implements a powerful **Query Expansion Syntax**.
- **`SmartChatSocketManager`**: An advanced version of the chat manager that adds:
    - **History Summarization**: Automatically compacts long conversations when token limits are approached to maintain context without exceeding model windows.
    - **Query Elevation**: Uses a fast, inexpensive model to analyze queries and decide if they require the advanced reasoning capabilities of a "smart" model.
- **`BasicChatApp`**: A concrete implementation of a chat application that integrates models, settings, and session management into a deployable server.

## Advanced Features

### Query Expansion Syntax
The `ChatSocketManager` supports a unique syntax for generating complex, multi-part prompts:
- **Parallel Expansion**: `@[option1|option2]` runs the prompt for each option in parallel.
- **Sequence Expansion**: `@{step1 -> step2}` feeds the output of one step into the next.
- **Range Expansion**: `@(1..10:2)` iterates over a numeric range.
- **Topic Reference**: `@TopicType` automatically inserts previously identified entities (e.g., `@Person`).

### Topic Extraction
As the conversation progresses, the system uses LLMs to identify and categorize named entities (topics). These topics are aggregated and can be referenced in subsequent prompts using the expansion syntax, enabling context-aware interactions.

### Persistence and Replay
All messages and UI updates are persisted via a `StorageInterface`. When a client reconnects or refreshes, the `SocketManager` uses message timestamps and versions to replay the session state, ensuring no data is lost.

## Implementation Details

- **Concurrency**: Uses dedicated thread pools for API calls and UI updates to keep the interface responsive.
- **Extensibility**: The architecture is highly modular, allowing for custom `SocketManager` implementations or specialized `SessionTask` behaviors.
- **Logging**: Includes detailed traffic logging for debugging WebSocket communication and API interactions.