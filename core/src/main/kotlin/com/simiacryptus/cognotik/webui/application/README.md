# Cognotik Core Utilities

This module provides core supporting utilities used throughout the Cognotik
platform, including plugin infrastructure, HTTP client management, output
interception, and audio transcription client support.

## Components

### `CognotikPlugin`

Interface that plugin JARs must implement to integrate with the Cognotik
platform.

- Plugins must provide a no-arg constructor.
- The `init()` method is called when the plugin is loaded and should be used
  to register any `DynamicEnum` constants (e.g. `TaskType`, `APIProvider`,
  etc.) that the plugin contributes.
- `unload()` is an optional hook for cleanup logic when the plugin is
  unloaded.
- `initializePlugin()` is an optional hook for additional initialization
  logic separate from enum registration.
- `pluginName` provides a human-readable identifier for the plugin, defaulting
  to the implementing class's simple name.

**Usage example:**

```kotlin
class MyPlugin : CognotikPlugin {
    override val pluginName = "MyPlugin"

    override fun init() {
        // Register DynamicEnum constants here
    }

    override fun unload() {
        // Cleanup resources here
    }
}
```

### `HttpClientManager`

Abstract base class providing a shared, connection-pooled Apache HttpClient
(`CloseableHttpClient`) along with performance and diagnostic logging
utilities.

Key features:

- A lazily-initialized, shared `client` configured with:
  - No automatic retries (`maxRetries = 0`) with a 15 second retry interval.
  - A pooling connection manager with a max of 64 total connections and 64
    per route.
  - Socket timeouts and keep-alive configured for long-running requests
    (up to 3000 seconds).
  - A default `Cognotik/1.0` user agent (configurable via
    `createHttpClient(userAgent)`).
- `withPerformanceLogging { ... }` wraps a block of code and logs the time
  taken to execute it.
- `captureCallerStack()` captures the current call stack (excluding internal
  frames) for diagnostic purposes, including any previously captured stack
  for the same thread.
- Logging helpers (`log`, `logFmt`, `logSys`) write formatted, timestamped
  messages to both SLF4J and any configured `BufferedOutputStream` log
  streams.

Subclasses must supply a `workPool` (`ExecutorService`) and `scheduledPool`
(`ListeningScheduledExecutorService`) for asynchronous work, and may override
`logLevel` and `logStreams` to customize logging behavior.

### `OutputInterceptor`

Singleton object that intercepts `System.out` and `System.err` to capture
console output on both a global and per-thread basis.

Key features:

- `setupInterceptor()` installs the interceptor exactly once (subsequent
  calls are no-ops). Original `System.out`/`System.err` streams continue to
  receive output as before.
- `getThreadOutput()` returns all output captured for the calling thread
  since the last reset.
- `clearThreadOutput()` resets the per-thread output buffer.
- `clearGlobalOutput()` resets the global output buffer.
- Internal buffers are capped (8 MB global, 1 MB per-thread) and are reset
  automatically if they grow beyond these limits, preventing unbounded
  memory growth.
- Per-thread buffers are stored in a `WeakHashMap` keyed by `Thread` to avoid
  leaking memory for terminated threads.

This is useful for capturing console output produced by user code or plugin
code for display in logs or UI panels, without losing the original console
behavior.

### `TranscriptionClient`

An `HttpClientManager` subclass that provides audio transcription support via
an OpenAI-compatible `/audio/transcriptions` API endpoint.

Constructor parameters:

- `key`: API key used for `Authorization: Bearer` authentication.
- `apiBase`: Base URL of the API (the client appends `/audio/transcriptions`).
- `logLevel`, `logStreams`: Inherited logging configuration from
  `HttpClientManager` (defaults to `Level.TRACE`).
- `workPool`, `scheduledPool`: Inherited executor configuration.
- `provider`: The `APIProvider` associated with this client instance.

**Usage example:**

```kotlin
val client = TranscriptionClient(
    key = "sk-...",
    apiBase = "https://api.openai.com/v1",
    workPool = myExecutorService,
    scheduledPool = myScheduledExecutorService,
    provider = APIProvider.OpenAI
)

val text = client.transcription(wavAudioBytes, prompt = "", audioModel = AudioModels.Whisper)
```

`transcription()`:

- Sends a multipart POST request with the WAV audio bytes, model ID, desired
  response format (`json`), and optional prompt text.
- Parses the response as `ModelSchema.TranscriptionResult`, falling back to
  reading the raw `text` field from the JSON response if structured parsing
  fails.
- Throws a `RuntimeException` wrapping an `IOException` if the API response
  contains an `error` object.

## Notes

- All HTTP-based clients in this module share a single pooled
  `CloseableHttpClient` instance (`HttpClientManager.client`) to minimize
  connection overhead.
- Logging throughout these components is timestamped relative to JVM/class
  load time (`HttpClientManager.startTime`) for consistent, comparable log
  output across components.