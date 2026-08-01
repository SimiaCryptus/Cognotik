# Embedding Module

The `com.simiacryptus.cognotik.embedding` package provides a robust framework for generating and working with text
embeddings. It supports multiple providers, distance metrics, and advanced prompt optimization techniques.

## Key Components

### Core Interfaces and Classes

* **`Embedder`**: A simple interface for converting text into vector representations (`DoubleArray`).
* **`EmbeddingModel`**: A base class representing specific embedding models (e.g., OpenAI's `text-embedding-3-small`).
  It handles model metadata, pricing calculations, and serialization.
* **`DistanceType`**: An enum providing implementations for common vector distance metrics:
  * **Euclidean**: Standard straight-line distance.
  * **Manhattan**: Sum of absolute differences.
  * **Cosine**: Measures the cosine of the angle between vectors (useful for semantic similarity).
* **`EmbedderClient`**: The primary implementation of the `Embedder` interface, coordinating between a specific model
  and an API client.

### API Clients

The module uses a provider-based architecture for API interactions:

* **`EmbeddingClientBase`**: Provides shared logic for HTTP communication, logging, and usage tracking.
* **`OpenAIEmbeddingClient`**: Implementation for the OpenAI Embeddings API.
* **`OllamaEmbeddingClient`**: Implementation for local Ollama instances, allowing for private, local embedding
  generation.

### Prompt Optimization

The **`PromptOptimization`** class implements a genetic algorithm to evolve and improve system prompts. It uses:

* **Mutation**: Applies one of several directives (weighted at random), including Rephrase, Randomize, Summarize,
  Expand, Reorder, and Remove Duplicate.
* **Recombination**: Combining successful prompts to create better descendants.
* **Evaluation**: Scoring prompts based on test cases and `Expectation` implementations, which use the configured
  `EmbeddingModel` and `EmbeddingClientInterface` to judge response quality.

## Supported Models

### OpenAI

Predefined models available in `OpenAIEmbeddingModels`:

* `text-embedding-ada-002`
* `text-embedding-3-small`
* `text-embedding-3-large`

### Ollama

Predefined models available in `OllamaEmbeddingModels`:

* `nomic-embed-text`

## Usage Example

### Generating Embeddings

```kotlin
val model = OpenAIEmbeddingModels.TextEmbedding3Small
val embedder = model.instance(apiKey = "your_api_key".encrypt)

val vector = embedder.embed("Hello, world!")
```

### Calculating Similarity

```kotlin
val vec1 = embedder.embed("The cat sat on the mat.")
val vec2 = embedder.embed("A feline rested on the rug.")

val distance = DistanceType.Cosine.distance(vec1, vec2)
println("Cosine Distance: $distance")
```

## Implementation Details

* **Serialization**: `EmbeddingModel` includes custom Jackson serializers/deserializers to allow models to be easily
  used in configuration files or API requests.
* **Reliability**: Clients are designed to work with `HttpClientManager` to provide performance logging and reliability
  features.
* **Cost Tracking**: The `onUsage` callback allows applications to track token consumption and costs in real-time.
* **Provider Abstraction**: `EmbeddingModel.instance(...)` resolves the appropriate `EmbeddingClientInterface`
  implementation via `APIProvider.getEmbeddingClient(...)`, decoupling model selection from transport details.