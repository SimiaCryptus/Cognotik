# Perplexity Models

Perplexity provides an OpenAI-compatible chat completion API, accessible through Cognotik using your own API key.

## Overview

Perplexity is integrated into Cognotik as a chat-capable provider using the OpenAI-compatible protocol
(`OpenAIChatClient`) against the `https://api.perplexity.ai` endpoint. The provider definition in Cognotik does not
enumerate specific model constants, context window sizes, or per-token pricing — these are determined by the
model identifier string you supply at request time and are governed directly by Perplexity's own model catalog and
pricing.

## Available Models

Cognotik does not hard-code a fixed set of Perplexity model identifiers or their context windows/capabilities in
the provider definition. You may specify any model id supported by Perplexity's API (for example, their `sonar`
family of models) when configuring a request.

| Model Name | Context Window | Capabilities | Pricing |
|---|---|---|---|
| — | — | — | — |

> Pricing shown reflects the values defined in Cognotik at the time of writing; verify current rates with the
> provider before relying on them for budgeting. In this case, no pricing or model metadata is defined in Cognotik
> for Perplexity — consult Perplexity's official documentation for current model names, context limits, and rates.

## Usage Example

```kotlin
val client = PerplexityProvider().getChatClient(
    key = SecureString("YOUR_PERPLEXITY_API_KEY"),
    workPool = workPool,
    logLevel = Level.INFO,
    logStreams = mutableListOf(),
    scheduledPool = scheduledPool,
    session = session
)
```

```json
{
  "provider": "Perplexity",
  "model": "sonar"
}
```

## Related Links

* [Perplexity API Documentation](https://docs.perplexity.ai/)
* [Perplexity Pricing](https://docs.perplexity.ai/guides/pricing)