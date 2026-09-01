package com.simiacryptus.cognotik.platform.model

class EmbedderClient(
  private val embeddingClient: EmbeddingClientInterface,
  val model: EmbeddingModel,
  private val onUsage: (LLMModel, ModelSchema.Usage) -> Unit = { _, _ -> }
) : Embedder {
  override fun embed(input: String): DoubleArray {
    val request = ModelSchema.EmbeddingRequest(
      model = model.modelId,
      input = input
    )
    val response = embeddingClient.createEmbedding(request, model)
    if (response.data.isEmpty()) {
      throw IllegalStateException("No embedding data returned")
    }
    response.usage?.let { usage ->
      onUsage(model, usage.copy(cost = model.pricing(usage)))
    }
    EmbeddingModel.log.info("Generated embedding of size ${response.data[0].embedding?.size} for input of length ${input.length}")
    return response.data[0].embedding ?: throw IllegalStateException("Embedding data is null")
  }
}