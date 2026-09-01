package com.simiacryptus.cognotik.embedding

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.platform.model.EmbeddingModel

object OllamaEmbeddingModels {
  val NomicEmbedText = EmbeddingModel("nomic-embed-text", 2049, CoreProviders.Ollama, 0.00013)
  val values: Map<String, EmbeddingModel> = mapOf(
    "nomic-embed-text" to NomicEmbedText,
  )
}