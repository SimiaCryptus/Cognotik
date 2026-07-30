# Cognotik Chat Client Module

This module provides the core chat client abstractions used to communicate
with various LLM providers (OpenAI, Anthropic, etc.) in a consistent,
provider-agnostic way. It handles HTTP communication, authentication,
logging, and usage tracking for chat interactions.

## Overview

The module is composed of three primary components:

### `ChatClientInterface`

Defines the contract that all chat clients must implement. It exposes:

- `getModels()` — returns the list of `ChatModel`s supported by the client.
- `chat(chatRequest, model, logStreams, usageHandler)` — sends a chat
  request to the underlying provider and returns a `ChatResponse`.
  This method is deprecated in favor of using pre-authenticated chat
  models with a `messages` parameter directly, but remains available
  for backward compatibility.
- `session` — the `Session` associated with the client.
- `logStreams` / `workPool` — shared logging and execution resources.

Also defines `UsageListener`, a callback interface for reporting model
usage (token counts, costs, etc.) back to the caller. A convenience
factory method `UsageListener.fn(sessionId, fn)` allows constructing a
listener from a lambda.

### `ChatClientBase`

An abstract base class implementing `ChatClientInterface` on top of
`HttpClientManager`. It provides:

- **HTTP GET/POST helpers** (`get`, `post`) with built-in logging of
  requests and responses (in collapsible `<details>` blocks for
  readability), including request IDs for correlation.
- **Authorization hook** (`authorize(request)`) that subclasses override
  to inject provider-specific authentication headers (e.g., API keys,
  bearer tokens).
- **Validation** of POST requests to ensure URLs and payloads are
  well-formed before sending.
- **Error handling** that logs failures with full context (URL, request
  ID, and request body) before rethrowing.
- An overridable `innerPost` method for subclasses that need custom
  handling of the raw HTTP response (e.g., streaming responses).

Subclasses are expected to supply:

- `provider` — the `APIProvider` this client targets.
- `apiKey` — a `SecureString` credential.
- `apiBase` — the base URL for the provider's API.
- Thread pools (`workPool`, `scheduledPool`) for async work and
  scheduled tasks.
- `session` — the current `Session`.

### `ChatInterface`

A high-level, per-session/per-model façade over `ChatClientInterface`
implementations. It is constructed with a specific `provider`, `model`,
`session`, `user`, and API credentials, and exposes a simple `chat()`
method that:

1. Resolves the appropriate chat client via
   `provider.getChatClient(...)`.
2. Delegates the chat request to that client.
3. Wires up a `UsageListener` that reports usage back through
   `ChatModel.ON_USAGE`, associating it with the current `user` and
   `session`.

`ChatInterface` also supports spawning child interfaces via
`getChildClient(session)`, which clones the current configuration
(optionally with a new `Session`) while giving each child its own
independent `logStreams` list to avoid shared mutable state across
sessions.

A `NULL` singleton is provided for use as a safe default/placeholder
when no real chat interface is configured.

Logging of chat interactions can be toggled globally via the
`ChatInterface.ENABLE_LOGS` companion flag; when disabled, `logStreams`
returns an empty list regardless of what was configured, effectively
silencing log output without needing to change call sites.

## Usage Example

```kotlin
val chatInterface = ChatInterface(
    provider = APIProvider.OpenAI,
    model = someChatModel,
    session = currentSession,
    user = currentUser,
    key = SecureString("sk-..."),
    base = APIProvider.OpenAI.base,
    logLevel = Level.DEBUG,
    workPool = myExecutorService,
    scheduledPool = myScheduledExecutorService,
    logStreams = mutableListOf(),
)

val response = chatInterface.chat(
    ModelSchema.ChatRequest(
        messages = listOf(/* ... */)
    )
)
```

To create an isolated child interface for a sub-session (e.g., a
nested agent or tool call) without sharing log buffers:

```kotlin
val childInterface = chatInterface.getChildClient(session = childSession)
```

## Extending with a New Provider

To add support for a new LLM provider:

1. Create a subclass of `ChatClientBase`, providing the appropriate
   `provider`, `apiKey`, and `apiBase`.
2. Override `authorize(request: HttpRequest)` to add the necessary
   authentication headers for that provider.
3. Implement `ChatClientInterface.chat(...)` to translate the generic
   `ChatRequest`/`ChatResponse` model schema to/from the provider's
   native API format, using the inherited `post`/`get` helpers for
   HTTP communication.
4. Optionally override `innerPost` if the provider requires special
   response handling (e.g., streaming or non-standard content types).

## Notes on Logging

All HTTP requests and responses are logged at `DEBUG` level by default
using collapsible HTML `<details>` blocks, making it easier to inspect
verbose payloads in rendered log viewers without cluttering plain-text
logs. Each request/response pair is tagged with a shared `requestID`
for correlation. Errors during POST requests are logged at `ERROR`
level with full request context to aid debugging.