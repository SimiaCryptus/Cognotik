package com.simiacryptus.cognotik.embedding

import com.simiacryptus.cognotik.models.APIProvider

object OllamaEmbeddingModels {
    val NomicEmbedText = EmbeddingModel("nomic-embed-text", 2049, APIProvider.Companion.Ollama, 0.00013)
    val values: Map<String, EmbeddingModel> = mapOf(
        "nomic-embed-text" to NomicEmbedText,
    )
}