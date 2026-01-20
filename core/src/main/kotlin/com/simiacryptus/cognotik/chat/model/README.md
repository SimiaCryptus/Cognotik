# Chat Models

The `com.simiacryptus.cognotik.chat.model` package provides a unified abstraction for interacting with various Large Language Model (LLM) providers. It includes core classes for model definition and execution, along with a comprehensive library of predefined model configurations for major AI providers.

## Core Components

### [ChatModel](ChatModel.kt)
The base class for all chat models. It extends `LLMModel` and encapsulates:
- **Metadata**: Model name, provider, and token limits (`maxTotalTokens`, `maxOutTokens`).
- **Pricing**: Logic for calculating costs based on input and output token usage.
- **Serialization**: Custom Jackson serializers/deserializers for persisting model configurations.
- **Instantiation**: The `instance()` method creates a `ChatInterface` for active interaction.

### [ChatInterface](ChatInterface.kt)
Represents an active session with a specific model. It handles:
- **Configuration**: Manages API keys, base URLs, temperature, and logging.
- **Execution**: Provides the `chat()` method to send messages and receive responses via the provider's client.
- **Usage Tracking**: Reports token usage and costs via callbacks.

## Supported Providers and Models

The package includes predefined configurations for a wide array of models across multiple providers:

| Provider | Description | Key Models |
| :--- | :--- | :--- |
| **[AWS](AWSModels.kt)** | Models hosted on AWS Bedrock. | Llama 3.1 (8b to 405b), Mistral Large, Claude 3/3.5/3.7, Amazon Nova, Titan. |
| **[Anthropic](AnthropicModels.kt)** | Native Anthropic Claude models. | Claude 3.5 Haiku, Claude 4/4.5 (Sonnet, Opus, Haiku). |
| **[DeepSeek](DeepSeekModels.kt)** | DeepSeek's specialized models. | DeepSeek Chat, Coder, and Reasoner. |
| **[Gemini](GeminiModels.kt)** | Google's Gemini family. | Gemini 1.5/2.0/2.5/3.0 (Pro, Flash, Flash-Lite). |
| **[Groq](GroqModels.kt)** | High-performance inference models. | Llama 3.3, Qwen 2.5, DeepSeek R1 Distill, Vision models. |
| **[Mistral](MistralModels.kt)** | Mistral AI's native models. | Mistral Large/Medium/Small, Mixtral 8x7B/8x22B, Codestral. |
| **[OpenAI](OpenAIModels.kt)** | OpenAI's flagship models. | GPT-4o, GPT-4.5, O1/O3/O4 series (including Mini and Preview). |
| **[Perplexity](PerplexityModels.kt)** | Search-optimized models. | Sonar Small/Large (Chat and Online variants). |
| **[ModelsLab](ModelsLabModels.kt)** | Open-source models via ModelsLab. | Zephyr, MistralLite, OpenHermes, Dolphin. |

## Usage Example

To use a model, select a predefined instance and create a `ChatInterface`:

```kotlin
val model = OpenAIModels.GPT4o
val chatInterface = model.instance(
    key = SecureString("your-api-key"),
    temperature = 0.7
)

val response = chatInterface.chat(listOf(
    ChatMessage(Role.system, "You are a helpful assistant."),
    ChatMessage(Role.user, "Hello!")
))
```

## Data Models

- **[ModelsLabDataModel](ModelsLabDataModel.kt)**: Contains specific request/response structures for the ModelsLab API.