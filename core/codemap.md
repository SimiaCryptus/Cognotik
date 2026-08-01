# codemap.md

## Cognotik Module Overview

This module implements a comprehensive framework for interacting with AI/LLM services (chat, embedding, image
generation, audio transcription/generation), along with supporting infrastructure for HTTP communication, plugin
extensibility, type description/reflection, code execution agents, and platform domain models.

### Top-Level Infrastructure (`com.simiacryptus.cognotik`)

- **CognotikPlugin**: Interface for plugin JARs; implementations register `DynamicEnum` constants (e.g.
  `TaskType`, `APIProvider`) via `init()`.
- **HttpClientManager**: Abstract base providing a shared, pooled `CloseableHttpClient`, performance logging,
  and structured log output to SLF4J and configurable `BufferedOutputStream`s.
- **OutputInterceptor**: Singleton that transparently intercepts `System.out`/`System.err`, capturing output
  globally and per-thread while still forwarding to original streams.
- **TranscriptionClient**: `HttpClientManager` subclass for audio transcription against OpenAI-compatible
  `/audio/transcriptions` endpoints.

### Agents (`agents`)

High-level abstractions for LLM-driven agents built atop `BaseAgent<I, R>`:

- **ChatAgent**: Simple text-to-text conversational agent.
- **AudioProcessingAgent** / **AudioAndText**: Multi-modal text/audio agent with two-phase script generation,
  multi-voice support (`[voice:Name]`, `[silence:Seconds]` directives), parallel segment rendering with retry/backoff,
  and configurable text scrubbing.
- **CodeAgent**: Generates and executes code via `CodeRuntime`, with self-correction on execution failure and
  automatic API documentation generation from symbol maps.
- **ImageGenerationAgent** / **ImageProcessingAgent** / **ImageAndText**: Text-to-image and image+text agents.
- **ParsedAgent** / **ParsedImageAgent** / **ParsedResponse**: Agents that return structured/validated data
  parsed from JSON LLM responses.
- **ProxyAgent**: Dynamic proxy implementation of an interface where method calls become LLM prompts and JSON
  responses are mapped back to return types; supports few-shot examples.

### Audio (`audio`)

DSP, capture, VAD, and transcription pipeline:

- **AudioPacket**: Core audio unit with RMS, spectral entropy/centroid/flatness, A-weighting, FFT utilities.
- **AudioRecorder**: Captures from microphone lines into packetized buffers.
- **SilenceDiscriminator** / **TrainedSilenceDiscriminator**: State-machine and adaptive statistical VAD.
- **PercentileTool**: Statistical tool for percentile tracking, KL-divergence, entropy thresholding.
- **AudioModels**: Model configuration for `Transcription`/`TextToSpeech`.
- **TranscriptionProcessor**: Orchestrates transcription calls from audio packet queues.
- **DictationManager**: Ties recorder, VAD, and transcription processor into a full dictation pipeline.

### Chat (`chat`, `chat/model`)

- **ChatClientInterface** / **ChatClientBase**: Provider-agnostic contract and HTTP-based base implementation
  (logging, auth hooks, request/response handling) for chat clients.
- **ChatInterface**: Per-session/per-model façade wiring provider, model, credentials, and usage tracking;
  supports child interfaces via `getChildClient`.
- **ChatModel** / **ChatMessageModality**: Describes a specific chat-capable model (pricing per token type,
  modality support, reasoning levels) and provides `instance(...)` to construct a `ChatInterface`.

### Describe (`describe`)

Reflection-based type description framework for surfacing Kotlin/Java types to LLMs:

- **TypeDescriber** (abstract base), **JsonDescriber**, **YamlDescriber**, **TypeScriptDescriber**: Generate
  JSON/YAML/TypeScript representations of types, methods, and enums.
- **AbbrevWhitelistYamlDescriber** / **AbbrevWhitelistTSDescriber**: Whitelist-based describers that avoid
  abbreviating specified package prefixes.
- **Description**: Annotation for attaching human-readable docs to classes/properties/methods.
- **DescriptorUtil**: Reflection helpers (generic type resolution, annotation lookup, array component types).
- **MethodTypeDescriber**: Interface for dynamic per-call method parameter type overrides.

### Embedding (`embedding`)

- **Embedder** / **EmbedderClient**: Simple interface and implementation for text-to-vector embedding.
- **EmbeddingModel**: Model metadata, pricing, and `instance(...)` factory.
- **EmbeddingClientBase** / **SingleProviderEmbeddingClient**: HTTP-based embedding client infrastructure.
- **DistanceType**: Euclidean/Manhattan/Cosine distance metrics.
- **PromptOptimization** / **Expectation**: Genetic-algorithm-based system prompt optimization (mutation,
  recombination, evaluation against test cases).

### Exceptions (`exceptions`)

Structured exception hierarchy for AI service errors:

- **AIServiceException** (base, with `isFatal` flag), **ErrorUtil** (pattern-based error message parsing).
- Fatal: **InvalidModelException**, **InvalidValueException**, **ModelMaxException**, **QuotaException**.
- Transient/behavioral: **RateLimitException**, **RequestOverloadException**, **SafetyException**,
  **ModerationException**.
- Other: **FailedToImplementException**, **MultiExeption**, **NonRetryableException** / **BudgetException**.

### Image (`image`)

- **ImageClientInterface** / **ImageModel**: Provider-agnostic image generation contract and model metadata
  with pricing functions.

### Interpreter (`interpreter`)

- **CodeRuntime**: Interface for executing code snippets in a given `language`, with symbol table injection,
  validation, and execution-wrapping hooks (used by `CodeAgent`).

### Models (`models`)

Core AI model/provider abstractions:

- **AIModel** / **LLMModel**: Base interfaces/classes for model identity, provider association, and pricing.
- **APIProvider**: `DynamicEnum`-based registry of service providers, each supplying chat/embedding/image/
  transcription clients and model lists.
- **ServiceProviders**: Additional non-chat providers (SearchAPI, Google, Github).
- **ModelSchema**: Comprehensive request/response data classes (Chat, Embedding, Image, Transcription, Usage).
- **AudioSegment**: Base64-encoded audio payload with format metadata; supports conversion (WAV/PCM/ffmpeg),
  concatenation (`+`), silence generation, and file I/O.
- **ToolProvider** / **ToolData**: `DynamicEnum`-based registry of external CLI tools (git, python, docker,
  provers, etc.) with PATH discovery and version validation.

### Platform Model (`platform/model`)

- **Session**: Validated global/user session identifiers (`G-`/`U-` prefixed) with ID generation and parsing.
- **User**: Domain user identified primarily by email, with a `NULL` sentinel and a `defaultUser`.

### Text Utilities (`txt` / `util`)

- **TextBlock** / **TextBlockFactory** / **IndentedText** / **LineComment** / **BlockComment**: Utilities for
  parsing and reconstructing indented/comment-wrapped text blocks.
- **util.kt**: `String.toContentList()` / `String.toChatMessage(role)` extensions for quickly building chat
  message content from plain strings.

### Web UI Application (`webui/application`)

- **AppEntry**: `DynamicEnum`-based registry entry describing a registered application/tool card (name, icon,
  description, category, tags, resource path, etc.) for platform UI listings.

## Design Patterns

- **DynamicEnum**: Used pervasively (`APIProvider`, `ToolProvider`, `AppEntry`) to allow runtime-extensible,
  plugin-registrable enum-like constants.
- **Provider Abstraction**: Model instantiation (`ChatModel.instance`, `EmbeddingModel.instance`) is decoupled
  from transport via `APIProvider.getChatClient/getEmbeddingClient/getImageClient`.
- **Agent Composition**: All agents extend `BaseAgent<I, R>`, standardizing prompt construction and response
  handling across chat, code, image, audio, and structured-data use cases.
- **HTTP Client Reuse**: All HTTP-based clients share pooled connections via `HttpClientManager.client`.

## Testing

Tests cover type describers (`JsonDescriberTest`, `YamlDescriberTest`), audio statistics (`PercentileToolTest`),
distance metrics (`DistanceTypeTest`), JSON utilities (`JsonUtilTest`), template engine behavior
(`ErbTemplateEngineTest`), rule tree building, and threaded output interception (`OutputInterceptorThreadedTest`).