package com.simiacryptus.cognotik.embedding

import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.CoreProviders

object OpenAIEmbeddingModels {
  val TextEmbeddingAda002 = EmbeddingModel("text-embedding-ada-002", 8191, CoreProviders.OpenAI, 0.0001)
  val TextEmbedding3Small = EmbeddingModel("text-embedding-3-small", 8191, CoreProviders.OpenAI, 0.00002)
  val TextEmbedding3Large = EmbeddingModel("text-embedding-3-large", 8191, CoreProviders.OpenAI, 0.00013)

  val values: Map<String, EmbeddingModel> = mapOf(
    "text-embedding-ada-002" to TextEmbeddingAda002,
    "text-embedding-3-small" to TextEmbedding3Small,
    "text-embedding-3-large" to TextEmbedding3Large,
  )
}