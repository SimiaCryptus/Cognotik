package com.simiacryptus.cognotik.platform.model

interface EmbeddingClientInterface {

  fun createEmbedding(
    request: ModelSchema.EmbeddingRequest,
    model: EmbeddingModel
  ): ModelSchema.EmbeddingResponse

}