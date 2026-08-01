# AI Models and Providers

This package contains the core abstractions and data structures for interacting with various AI models and external
tools. It provides a unified interface for chat, embedding, image generation, and transcription services across multiple
providers.

## Core Abstractions

### [AIModel](AIModel.kt)

The base interface for all AI models. It defines the basic properties:

- `provider`: The `APIProvider` that hosts the model.

### [APIProvider](APIProvider.kt)

An abstract base class and registry for AI service providers. It handles:

- **Client Creation**: Methods to instantiate chat, embedding, and image clients.
- **Model Discovery**: Methods to list available models for different modalities.
- **Authorization**: Custom header injection for different API requirements (e.g., Bearer tokens vs. custom headers).

**Supported Providers:**

- **OpenAI**: Full support for Chat, Embeddings, Images, and Transcription (Whisper).
- **Gemini**: Google's generative models (Chat and Images).
- **Anthropic**: Claude series chat models.
- **Ollama**: Local model execution for Chat and Embeddings.
- **Groq**: High-performance inference for Chat and Transcription.
- **Mistral / DeepSeek / Perplexity**: Specialized LLM providers.
- **AWS**: Integration with Amazon Bedrock/SageMaker endpoints.
- **SearchAPI / Google / Github**: Specialized providers for search and repository metadata.

Provider registration follows the `DynamicEnum` pattern: each provider (e.g. in
[ServiceProviders](ServiceProviders.kt)) is instantiated as an anonymous object and registered via
`DynamicEnum.register(APIProvider::class.java, instance)`, allowing lookups through
`APIProvider.valueOf(name)` and `APIProvider.values()`.

### [LLMModel](LLMModel.kt)

A base class for Large Language Models that includes:

- **Token Limits**: Tracking of `maxTotalTokens` and `maxOutTokens`.
- **Pricing**: A `pricing(Usage)` method to calculate costs based on token consumption.
- **Serialization**: Custom Jackson serializers to handle model references by name.

### [ModelSchema](ModelSchema.kt)

A comprehensive collection of data classes representing the JSON structures used by various AI APIs. This includes:

- **Chat**: `ChatRequest`, `ChatResponse`, `ChatMessage`, `ContentPart` (supporting text and images).
- **Embeddings**: `EmbeddingRequest`, `EmbeddingResponse`.
- **Images**: `ImageGenerationRequest`, `ImageGenerationResponse`.
- **Transcription**: `TranscriptionResult`, `TranscriptionPacket`.
- **Usage**: Tracking token counts and costs.

## Tool Integration

### [ToolProvider](ToolProvider.kt)

Manages external executable tools and environments. It allows the system to discover, validate, and resolve paths for
various development and utility tools.

**Supported Tools include:**

- **Languages/Runtimes**: Python, Jdk, Node, Rust, Go, Ruby, PHP, Julia.
- **Build Tools**: Gradle, Maven, Ant, Make, Cmake.
- **Infrastructure**: Docker, Terraform, Kubectl, AWS/Gcloud CLI.
- **Version Control**: Git.
- **Scientific/Math**: Octave, Gnuplot, Maxima, Sage, Gap.
- **Formal Verification**: Z3, CVC5, Lean, Coq, Isabelle, Agda.
- **Utilities**: Bash, Zsh, Powershell, SSH, Ffmpeg, Pandoc, Dot (Graphviz).

## Usage Example

To get a chat client for a specific provider:

```kotlin
val provider = APIProvider.OpenAI
val client = provider.getChatClient(
    key = SecureString("your-api-key"),
    workPool = executorService,
    scheduledPool = scheduledExecutorService
)

val models = provider.getChatModels(SecureString("your-api-key"), provider.base)
```

To discover installed tools on the system:

```kotlin
val installedTools = ToolProvider.discoverAllToolsFromPath()
installedTools.forEach { tool ->
    println("Found ${tool.provider?.name} at ${tool.path}")
}
```
To generate silent audio and concatenate it with another segment:
```kotlin
val silence = AudioSegment.silence(durationSeconds = 1.0, format = "wav")
val combined = silence + otherAudioSegment
combined.writeAudio(Paths.get("output.wav"))
```
- `modelId`: The identifier for the model.
- `pricing(usage)`: Computes the cost for a given `ModelSchema.Usage` (defaults to `0.0`).
### [AudioSegment](AudioSegment.kt)
A data class representing an audio payload (Base64-encoded) with associated format metadata
(`format`, `sampleRate`, `channels`, `bitsPerSample`). It supports:
- **Format Conversion**: `convert(targetFormat)` handles WAV/PCM (`L16`) conversions natively and
   falls back to `ffmpeg` for other formats (e.g., mp3).
- **Concatenation**: The `+` operator merges two segments of matching sample rate, channel count,
   and bit depth, handling WAV, MP3 (via `ffmpeg` concat), and raw PCM concatenation.
- **File I/O**: `writeAudio(path)` writes the segment to disk, auto-converting to match the target
   file extension.
- **Utilities**: Static helpers for generating `silence(...)`, stripping/adding WAV headers
   (`stripWavHeader`, `pcmToWav`).