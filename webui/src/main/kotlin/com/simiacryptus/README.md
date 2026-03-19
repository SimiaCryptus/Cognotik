# Cognotik Utilities

The `CognotikUtils` object provides a centralized set of utility functions for managing application services, user
settings, and LLM API configurations within the Cognotik platform.

## Key Features

### Application Services & User Management

- **`fileApplicationServices()`**: Provides access to the `FileApplicationServices` using the configured data storage
  root.
- **`user()`**: Retrieves the default user instance.
- **`userSettings()`**: Accesses the `UserSettings` for the default user, allowing for retrieval of API keys and
  preferences.

### API Configuration

- **Environment Variable Support**: The `configureEnvironmentalKeys()` method allows for automatic configuration of API
  providers using system environment variables:
    - `GOOGLE_API_KEY` (Gemini)
    - `OPENAI_API_KEY` (OpenAI)
    - `ANTHROPIC_API_KEY` (Anthropic)
    - `GROQ_API_KEY` (Groq)
- **Dynamic Provider Management**: Methods to get and set API provider data (`ApiData`), including base URLs and secure
  API keys.

### Chat Interface Initialization

- **`getInterface(model, session)`**: A factory method that instantiates a `ChatInterface` for a given `ApiChatModel`.
  It handles:
    - API key retrieval.
    - Thread pool management for execution and scheduling.
    - Usage tracking via the `UsageManager`.

### Path Utilities

- **`relativize(root, file)`**: A helper to calculate relative paths, useful for file-based storage and session
  management.

## Usage Example

### Initializing API Keys from Environment

```kotlin
CognotikUtils.configureEnvironmentalKeys()
```

### Getting a Chat Interface

```kotlin
val session = Session(...)
val apiChatModel = CognotikUtils.getChatModel(chatModel)
val chatInterface = CognotikUtils.getInterface(apiChatModel, session)
```

## Implementation Details

- **Usage Tracking**: The utility automatically hooks into the `UsageManager` when creating chat interfaces to ensure
  token usage is recorded per session and user.
- **Security**: API keys are handled using `SecureString` to prevent accidental exposure in logs or memory dumps.
- **Concurrency**: Uses cached and scheduled thread pools for handling asynchronous LLM interactions.