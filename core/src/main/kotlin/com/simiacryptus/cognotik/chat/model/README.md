# Cognotik Chat Model

This module defines the core data model used for representing chat-capable LLM
(Large Language Model) configurations within Cognotik, including modality
support, pricing, and reasoning capabilities.

## Overview

The chat model package (`com.simiacryptus.cognotik.chat.model`) provides two
primary components:

- **`ChatMessageModality`** — an enum describing the types of content a chat
  message can contain.
- **`ChatModel`** — a concrete implementation of `LLMModel` that describes a
  specific chat-capable model, its token pricing, supported modalities, and
  reasoning capabilities. It also provides a factory method for creating a
  `ChatInterface` instance bound to this model.

## `ChatMessageModality`

```kotlin
enum class ChatMessageModality {
  TEXT, IMAGE, AUDIO, VIDEO
}
```

Represents the kinds of content supported as input or output by a model:

| Value   | Description                    |
|---------|---------------------------------|
| `TEXT`  | Plain text content              |
| `IMAGE` | Image content                   |
| `AUDIO` | Audio content                   |
| `VIDEO` | Video content                   |

Used by `ChatModel.inputModalities` and `ChatModel.outputModalities` to
declare which modalities a given model can accept or produce.

## `ChatModel`

`ChatModel` extends `LLMModel` and adds chat-specific configuration:

### Constructor Parameters

| Parameter               | Type                              | Default          | Description |
|--------------------------|-----------------------------------|-------------------|-------------|
| `name`                   | `String`                          | `""`              | Human-readable model name |
| `modelId`                | `String`                          | `name`            | Identifier used with the provider's API |
| `maxTotalTokens`         | `Int`                             | `-1`              | Maximum total tokens (context window) |
| `maxOutTokens`           | `Int`                             | `maxTotalTokens`  | Maximum output tokens |
| `provider`               | `APIProvider?`                    | `null`            | API provider for this model |
| `tokenPricingPerK`       | `Map<TokenTypes, Double>`         | `emptyMap()`      | Pricing per 1,000 tokens, keyed by token type |
| `supportsTemperature`    | `Boolean`                         | `true`            | Whether the model supports a temperature parameter |
| `supportsReasoning`      | `Boolean`                         | `false`           | Whether the model supports explicit reasoning |
| `deprecated`             | `Boolean`                         | `false`           | Whether the model is deprecated |
| `inputModalities`        | `Set<ChatMessageModality>`        | *(required)*      | Modalities the model accepts as input |
| `outputModalities`       | `Set<ChatMessageModality>`        | *(required)*      | Modalities the model can produce as output |
| `reasoningLevel`         | `ReasoningLevel?`                 | `null`            | Reasoning effort level, if supported |

A backwards-compatible secondary constructor is also provided, accepting
`inputTokenPricePerK` and `outputTokenPricePerK` directly instead of a full
`tokenPricingPerK` map. This constructor internally builds the map using
`TokenTypes.Prompt` and `TokenTypes.Completion` keys.

### `ReasoningLevel`

```kotlin
enum class ReasoningLevel {
    Low, Medium, High, X
}
```

Describes the level of reasoning effort a model can be configured to use,
when `supportsReasoning` is `true`.

### Pricing

`ChatModel` computes costs based on the `tokenPricingPerK` map, which
associates each `TokenTypes` value with a price per 1,000 tokens.

- `priceStructure()` resolves a price for every `TokenTypes` value by walking
  up the type's `parent` chain when a direct price is not specified,
  defaulting to `0.0` if no price is found anywhere in the chain.
- `pricing(usage: Usage)` calculates the total cost for a given `Usage`
  object:
  1. Costs are computed for token types with a known price.
  2. Any tokens not covered by a known type are estimated using the
     average price across all known token type prices.
  3. The total cost (in dollars) is returned, computed by dividing the
     summed cost (accounted + estimated) by 1000.
  4. Detailed cost breakdowns are logged via SLF4J at `INFO` level.

For backwards compatibility, `inputTokenPricePerK` and `outputTokenPricePerK`
properties expose the `Prompt` and `Completion` entries from
`tokenPricingPerK`, defaulting to `0.0` when absent.

### Creating a `ChatInterface`

The `instance(...)` method constructs a `ChatInterface` bound to this model:

```kotlin
fun instance(
    key: SecureString,
    base: String = provider?.base!!,
    logLevel: Level = Level.DEBUG,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    workPool: ExecutorService = Executors.newFixedThreadPool(4),
    temperature: Double = 0.1,
    scheduledPool: ListeningScheduledExecutorService = ...,
    session: Session,
    user: User,
): ChatInterface
```

This wires up the model, provider, API key, logging, and thread pools needed
to actually communicate with the underlying chat API.

### Usage Tracking

`ChatModel` exposes a companion-level callback hook:

```kotlin
companion object {
    var ON_USAGE: (LLMModel, Usage, User, Session, ModelSchema.UsageData?) -> Unit
}
```

By default, this logs prompt/completion/total token counts and the
associated user ID at `INFO` level. Consumers may override `ON_USAGE` to
integrate with custom usage tracking or billing systems.

### `NULL` Model

A sentinel `ChatModel.NULL` instance is provided for cases requiring a
placeholder or no-op model:

```kotlin
val NULL: ChatModel = ChatModel(
    name = "NULL",
    modelId = "NULL",
    tokenPricingPerK = mapOf(
        TokenTypes.Prompt to 0.0,
        TokenTypes.Completion to 0.0,
    ),
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
)
```

## Example

```kotlin
val gpt4 = ChatModel(
    name = "GPT-4",
    modelId = "gpt-4",
    maxTotalTokens = 8192,
    provider = APIProvider.OpenAI,
    tokenPricingPerK = mapOf(
        TokenTypes.Prompt to 0.03,
        TokenTypes.Completion to 0.06,
    ),
    supportsTemperature = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT),
)

val chatInterface = gpt4.instance(
    key = mySecureApiKey,
    session = currentSession,
    user = currentUser,
)
```

## Notes

- `tokenPricingPerK` supports hierarchical fallback via `TokenTypes.parent`,
  allowing coarse-grained pricing (e.g., a general "Prompt" price) to apply
  to more specific sub-types unless explicitly overridden.
- The `pricing` calculation logs detailed breakdowns to aid in auditing and
  debugging billing discrepancies; consider lowering the log level to
  `DEBUG` once cost estimation accuracy is validated in production.