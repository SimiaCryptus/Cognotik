package com.simiacryptus.cognotik.embedding

import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.CoreProviders

object OllamaEmbeddingModels {
  val NomicEmbedText = EmbeddingModel("nomic-embed-text", 2049, CoreProviders.Ollama, 0.00013)
  val values: Map<String, EmbeddingModel> = mapOf(
    "nomic-embed-text" to NomicEmbedText,
  )
}