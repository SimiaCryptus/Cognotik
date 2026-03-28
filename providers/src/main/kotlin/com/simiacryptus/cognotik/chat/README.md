# Chat Client Implementations

This package provides a comprehensive set of chat client implementations for various Large Language Model (LLM)
providers. It includes a robust base infrastructure for handling authentication, request/response mapping, usage
tracking, and reliability.

## Core Infrastructure

The chat clients are built upon a hierarchical structure that ensures consistency and reduces code duplication:

* **`ChatClientInterface`**: The primary interface defining the contract for all chat clients. It includes methods for
  sending chat requests (`chat`), retrieving available models (`getModels`), and performing content moderation (
  `moderate`).
* **`ChatClientBase`**: An abstract base class that integrates with `HttpClientManager`. it provides:
  * **Usage Tracking**: Automatically records token usage and calculates costs.
  * **Budget Management**: Monitors and enforces session or user-level budgets.
  * **Logging**: Detailed logging of requests and responses, including formatted JSON and caller stack traces.
  * **Reliability**: Hooks for performance logging and reliability wrappers.
* **`SingleProviderChatClient`**: A specialized base class for providers that follow standard HTTP patterns, simplifying
  the implementation of `GET` and `POST` operations with provider-specific authorization.

## Provider Implementations

The following provider-specific clients are implemented:

| Client                | Provider      | Description                                                                                                                                               |
|:----------------------|:--------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `AnthropicChatClient` | Anthropic     | Supports Claude models via the Anthropic Messages API. Handles message consolidation and system prompt mapping.                                           |
| `AwsChatClient`       | AWS Bedrock   | Integrates with AWS Bedrock using the AWS SDK. Supports a wide range of models including Anthropic Claude, Meta Llama, Mistral, Amazon Titan, and Cohere. |
| `DeepSeekChatClient`  | DeepSeek      | Implementation for the DeepSeek API, supporting their high-performance reasoning and chat models.                                                         |
| `GeminiChatClient`    | Google Gemini | REST-based implementation for Google's Gemini API.                                                                                                        |
| `GeminiSdkChatClient` | Google Gemini | Implementation using the official Google Gen AI Java SDK, supporting advanced features like image input and Vertex AI integration.                        |
| `GroqChatClient`      | Groq          | High-speed inference client for models hosted on Groq's LPU platform.                                                                                     |
| `MistralChatClient`   | Mistral AI    | Client for Mistral's native API, supporting models like Mistral Large and Mixtral.                                                                        |
| `ModelsLabChatClient` | ModelsLab     | Supports various open-source models via the ModelsLab (formerly Stable Diffusion API) infrastructure, including long-polling for queued responses.        |
| `OllamaChatClient`    | Ollama        | Enables interaction with locally hosted models running via Ollama.                                                                                        |
| `OpenAIChatClient`    | OpenAI        | Standard implementation for OpenAI's GPT-4, GPT-4o, and o1/o3 series models.                                                                              |

## Key Features

### Reliability and Performance

Clients utilize `withReliability` and `withPerformanceLogging` blocks to ensure robust execution and provide insights
into API latency and success rates.

### Model Discovery

Most clients implement `getModels()`, which dynamically fetches available models from the provider's API and maps them
to internal `ChatModel` definitions, often including pricing and context window metadata.

### Message Mapping

The clients handle the complexities of mapping the internal `ModelSchema.ChatRequest` format to provider-specific
formats. This includes:

* Consolidating consecutive messages with the same role.
* Handling system prompts (either as a separate field or a specific message role).
* Converting multi-modal content (like images) for supported providers (e.g., Gemini).

### Usage and Budgeting

Every successful chat completion triggers `onUsage`, which:

1. Updates token counts (prompt, completion, total).
2. Calculates cost based on the specific model's pricing.
3. Deducts from the available budget if configured.
4. Notifies registered listeners for downstream tracking or billing.