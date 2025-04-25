# JOpenAI Core Module

JOpenAI provides a unified, type-safe, and extensible model registry and type system for working with a wide variety of
AI models (text, chat, embedding, image, audio) across multiple providers.

## Features

- **Type-safe referencing** of models by name or provider.
- **Serialization/Deserialization** of model references for API requests and responses.
- **Centralized pricing and quota logic** for each model.
- **Extensibility** for adding new models or providers.
- **Unified API** for working with models across text, chat, embedding, image, and audio domains.

## Main Classes and Objects

- `ApiModel`: Data classes for API requests/responses (chat, completion, image, audio, etc).
- `OpenAIModel`: Base interface for all models.
- `TextModel`: Abstract base for text-completion models.
- `ChatModel`: Abstract base for chat models.
- `EmbeddingModels`, `CompletionModels`, `EditModels`, `ImageModels`, `AudioModels`: Model classes for each domain.
- `APIProvider`: Enum for all supported providers.
- Provider registries: `OpenAIModels`, `AnthropicModels`, `GoogleModels`, `AWSModels`, `PerplexityModels`,
  `MistralModels`, `GroqModels`, `ModelsLabModels`, `DeepSeekModels`.

## Key Concepts

### Model Classes

- **`OpenAIModel`**: Base interface for all models (text, chat, embedding, image, audio). Provides the `modelName`
  property and logging.
- **`TextModel`**: Abstract class for text-completion models, with token limits and pricing.
- **`ChatModel`**: Extends `TextModel` for chat-based LLMs, with pricing for input/output tokens and optional features (
  temperature, reasoning effort).
- **`EmbeddingModels`**: For embedding models, with pricing per token.
- **`CompletionModels`**: For classic OpenAI completion models.
- **`EditModels`**: For edit models (e.g., text-davinci-edit-001).
- **`ImageModels`**: For image generation/editing models, with pricing per image and size.
- **`AudioModels`**: For audio (speech-to-text, text-to-speech) models, with pricing per character or second.

### Model Registries

Each provider (OpenAI, Anthropic, Google, AWS, etc.) has a singleton object (e.g., `OpenAIModels`, `AnthropicModels`,
`GoogleModels`, etc.) containing all known models for that provider as `ChatModel` instances, keyed by a canonical name.

- **Dynamic provider abstraction**:  
  The `APIProvider` dynamic enum class enumerates all supported API providers, with their base URLs.

- **Serialization/Deserialization**:  
  All models support Jackson serialization/deserialization using custom serializers/deserializers. Models can be
  referenced by name in JSON/YAML, and deserialized to the correct class instance.

- **Pricing and usage**:  
  Each model class provides a `pricing()` method to compute cost based on usage (tokens, characters, seconds, etc).
  Token and output limits are encoded per model.

- **Model discovery**:
    - `ChatModel.values()` returns a map of all registered chat models by canonical name.
    - Each registry object provides a `values` map for its models.

## Usage Examples

```kotlin
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.ApiModel

// Get a model by name (case-insensitive, works for all registered models)
val model: ChatModel = ChatModel.values()["GPT4o"]!!

// Access properties
println(model.modelName)           // "gpt-4o"
println(model.maxTotalTokens)      // 128000
println(model.provider.name)       // "OpenAI"

// Compute pricing for a usage record
val usage = ApiModel.Usage(prompt_tokens = 1000, completion_tokens = 500)
val cost = model.pricing(usage)
println("Cost: $cost")
```

### Model Selection by Name

You can convert a string to a `ChatModel` using:

```kotlin
import com.simiacryptus.jopenai.models.chatModel

val model = "gpt-4o".chatModel()
```

### Serialization/Deserialization

All models can be serialized to and from JSON/YAML using Jackson, thanks to custom serializers/deserializers:

```kotlin
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.jopenai.models.ChatModel

val model = ChatModel.values()["GPT4o"]!!
val json = JsonUtil.toJson(model)
val deserialized = JsonUtil.fromJson<ChatModel>(json, ChatModel::class.java)
```

## Adding New Models

To add a new model:

1. Add a new entry to the appropriate registry object (e.g., `OpenAIModels`, `AWSModels`, etc.) as a `ChatModel`,
   `EmbeddingModels`, etc.
2. Update the `values` map in that object.
3. (If needed) Add serialization logic for new model types.

Example:

```kotlin
val MyNewModel = ChatModel(
    name = "MyNewModel",
    modelName = "my-new-model",
    maxTotalTokens = 32768,
    provider = APIProvider.OpenAI,
    inputTokenPricePerK = 0.001,
    outputTokenPricePerK = 0.002
)
val values = mapOf(
    "MyNewModel" to MyNewModel,
    // ... other models ...
)
```

## Extending to New Providers

1. Add a new `APIProvider` entry:
   ```kotlin
   val MyProvider = APIProvider("MyProvider", "https://api.myprovider.com/v1")
   ```
2. Create a new registry object for the provider:
   ```kotlin
   object MyProviderModels {
       val MyModel = ChatModel(/* ... */)
       val values = mapOf("MyModel" to MyModel)
   }
   ```
3. Add models as needed.

## Model Serialization/Deserialization

All models can be serialized to and from JSON/YAML using Jackson, thanks to custom serializers/deserializers.
This allows you to store model references in configuration files, databases, or API requests.

## Testing and Examples

Unit tests are provided in `src/test/kotlin/com/simiacryptus/jopenai/` and cover:

- Model equality, hashCode, and serialization
- Tokenizer and text utilities
- Audio and percentile tools
- Type describers and API function describers
