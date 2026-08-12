# Ollama Models

    Run local, self-hosted models through Ollama with Cognotik using your own compute — no per-token API fees.

    ## Overview

    Ollama is a local model runtime that lets you download and serve open-weight models (e.g. Llama, Mistral,
    Qwen, and others) on your own machine. Cognotik integrates with Ollama for both **chat** and **embedding**
    workloads via `OllamaChatClient` and `OllamaEmbeddingClient`. Because Ollama runs locally, the specific models
    available depend entirely on what you have pulled into your local Ollama installation — Cognotik does not
    hardcode a fixed model catalog for this provider.

    ## Available Models

    Ollama does not expose a static, predefined list of models within Cognotik's provider definition. Instead,
    the set of usable models is determined dynamically by whatever models are installed in your local Ollama
    instance (default endpoint: `http://localhost:11434`).

    | Model Name | Context Window | Capabilities | Pricing |
    |---|---|---|---|
    | *(depends on your local Ollama install)* | — | — | — |

    For embeddings, Cognotik surfaces whatever models are defined in `OllamaEmbeddingModels`, which similarly
    reflects locally available embedding models rather than a fixed provider catalog.

    ## Pricing

    Ollama models run on your own hardware, so there is **no per-token pricing** from Cognotik or Ollama itself.
    Your cost is limited to the compute/hardware you use to run the models locally.

    > **Note:** Pricing shown reflects the values defined in Cognotik at the time of writing; verify current rates
    > with the provider before relying on them for budgeting. In Ollama's case, this means there is no metered
    > cost — confirm this remains accurate for your deployment.

    ## Usage Example

    ```kotlin
    val model = "llama3" // or any model name you have pulled via `ollama pull <model>`
    val client = OllamaProvider().getChatClient(
        key = apiKey,
        workPool = workPool,
        logLevel = logLevel,
        logStreams = logStreams,
        scheduledPool = scheduledPool,
        session = session
    )
    ```

    ```json
    {
      "provider": "Ollama",
      "model": "llama3",
      "baseUrl": "http://localhost:11434"
    }
    ```

    ## Related Links

    - [Ollama Official Site](https://ollama.com)
    - [Ollama Model Library](https://ollama.com/library)