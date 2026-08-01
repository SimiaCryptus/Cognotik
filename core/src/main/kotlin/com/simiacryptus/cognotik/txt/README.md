# Cognotik Core Utilities

This module provides core infrastructure classes used throughout the Cognotik platform, including plugin support, HTTP client management, output interception, and audio transcription client integration.

## Contents

- [`CognotikPlugin`](#cognotikplugin)
- [`HttpClientManager`](#httpclientmanager)
- [`OutputInterceptor`](#outputinterceptor)
- [`TranscriptionClient`](#transcriptionclient)

---

## CognotikPlugin

`com.simiacryptus.cognotik.CognotikPlugin`

Interface that all plugin JARs must implement in order to be loaded by the Cognotik plugin system.

### Requirements

- Implementations **must** provide a no-arg constructor so they can be instantiated via reflection.
- The `init()` method must be implemented and is responsible for registering any `DynamicEnum` constants (e.g. `TaskType`, `APIProvider`) that the plugin contributes.
- `unload()` and `initializePlugin()` have default no-op implementations and are optional to override.
- `pluginName` defaults to the implementing class's simple name but can be overridden to provide a friendlier display name.

### Example

```kotlin
class MyPlugin : CognotikPlugin {
    override fun init() {
        // Register custom TaskType / APIProvider constants here
    }

    override val pluginName = "My Custom Plugin"
}
```

---

## HttpClientManager

`com.simiacryptus.cognotik.HttpClientManager`

Abstract base class providing a shared, pooled Apache HttpClient5 instance along with structured logging and performance instrumentation helpers.

### Key Features

- **Shared HTTP Client**: A lazily-initialized, connection-pooled `CloseableHttpClient` (`HttpClientManager.client`) with:
  - No automatic retries (`maxRetries = 0`)
  - 30s connect timeout, 3000s socket timeout, keep-alive enabled
  - Up to 64 connections total / per route
  - Configurable `User-Agent` header (default: `Cognotik/1.0`)
- **Performance Logging**: `withPerformanceLogging { ... }` wraps a block of code and logs its execution duration.
- **Structured Logging**: `log(...)` overloads write formatted, timestamped messages to both SLF4J and any configured `BufferedOutputStream` log sinks.
- **Caller Stack Capture**: `captureCallerStack()` records the calling stack trace (excluding internal frames) for diagnostic purposes, chaining with any previously captured stack for the same thread.

### Constructor Parameters

| Parameter       | Description                                            |
|-----------------|--------------------------------------------------------|
| `logLevel`      | Default SLF4J log level for this manager instance      |
| `logStreams`    | Mutable list of output streams to mirror log messages  |
| `workPool`      | `ExecutorService` used for background work             |
| `scheduledPool` | `ListeningScheduledExecutorService` for scheduled tasks |

### Usage

```kotlin
class MyClient(
    workPool: ExecutorService,
    scheduledPool: ListeningScheduledExecutorService
) : HttpClientManager(workPool = workPool, scheduledPool = scheduledPool) {

    fun doWork() = withPerformanceLogging {
        // Use HttpClientManager.client to make requests
    }
}
```

---

## OutputInterceptor

`com.simiacryptus.cognotik.OutputInterceptor`

Singleton object that transparently intercepts `System.out` and `System.err`, capturing output both globally and per-thread, while still forwarding it to the original streams.

### Key Features

- **Idempotent Setup**: `setupInterceptor()` is safe to call multiple times; interception is installed only once via an `AtomicBoolean` guard.
- **Global Buffer**: Captures up to 8 MB of combined output across all threads (auto-resets when exceeded).
- **Per-Thread Buffers**: Captures up to 1 MB of output per thread (auto-resets when exceeded), tracked via a `WeakHashMap` keyed by `Thread` to avoid memory leaks.
- **Non-destructive**: All output is still written to the original `System.out` / `System.err` streams.

### API

| Method                  | Description                                              |
|--------------------------|-----------------------------------------------------------|
| `setupInterceptor()`      | Installs the interceptor on `System.out` / `System.err`  |
| `getThreadOutput()`       | Returns captured output for the current thread as a `String` |
| `clearThreadOutput()`     | Clears the current thread's captured output buffer       |
| `clearGlobalOutput()`     | Clears the global captured output buffer                 |

### Usage

```kotlin
OutputInterceptor.setupInterceptor()

println("Hello, world!")

val threadOutput = OutputInterceptor.getThreadOutput()
OutputInterceptor.clearThreadOutput()
```

---

## TranscriptionClient

`com.simiacryptus.cognotik.TranscriptionClient`

An `HttpClientManager` subclass that provides audio transcription capabilities against an OpenAI-compatible `/audio/transcriptions` API endpoint.

### Constructor Parameters

| Parameter       | Description                                             |
|-----------------|----------------------------------------------------------|
| `key`           | API key used for `Authorization: Bearer` header          |
| `apiBase`       | Base URL of the API (e.g. `https://api.openai.com/v1`)   |
| `logLevel`      | Logging level (default `Level.TRACE`)                    |
| `logStreams`    | Output streams for log mirroring                          |
| `workPool`      | `ExecutorService` for background work                     |
| `scheduledPool` | `ListeningScheduledExecutorService` for scheduled tasks    |
| `provider`      | `APIProvider` associated with this client                 |

### Methods

#### `transcription(wavAudio: ByteArray, prompt: String = "", audioModel: AudioModels): String`

Sends a multipart POST request containing WAV audio bytes to the transcription endpoint and returns the transcribed text.

- Builds a multipart request with:
  - `file`: the raw WAV audio bytes (`audio/x-wav`)
  - `model`: the model ID from the supplied `AudioModels`
  - `response_format`: `json`
  - `prompt`: optional context prompt, included only if non-empty
- Parses the JSON response:
  - Throws a `RuntimeException` wrapping an `IOException` if the response contains an `error` object.
  - Attempts to deserialize into `ModelSchema.TranscriptionResult` and returns its `text` field.
  - Falls back to reading the raw `text` field from the JSON object if deserialization fails.
- Wrapped in `withPerformanceLogging` to log request duration.

### Usage

```kotlin
val client = TranscriptionClient(
    key = "sk-...",
    apiBase = "https://api.openai.com/v1",
    workPool = workPool,
    scheduledPool = scheduledPool,
    provider = APIProvider.OpenAI
)

val text = client.transcription(wavBytes, prompt = "", audioModel = AudioModels.Whisper1)
```

---

## Notes / Follow-up

- These classes rely on Apache HttpClient5, Guava (`ListeningScheduledExecutorService`), Gson, and SLF4J — ensure these dependencies remain on the classpath.
- `HttpClientManager.client` is a shared, process-wide instance; callers should not attempt to close it individually.
- Consumers of `CognotikPlugin` should ensure `init()` is idempotent, since plugin reload scenarios may invoke it more than once.