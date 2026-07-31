package com.simiacryptus.cognotik.embedding

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import com.simiacryptus.cognotik.util.encrypt
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class OpenAIEmbeddingClient(
  apiKey: SecureString = "".encrypt,
  apiBase: String = "https://api.openai.com/v1",
  workPool: ExecutorService = Executors.newCachedThreadPool(),
  logLevel: Level = Level.DEBUG,
  logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
  scheduledPool: ListeningScheduledExecutorService = MoreExecutors.listeningDecorator(
    Executors.newScheduledThreadPool(
      1
    )
  )
) : SingleProviderEmbeddingClient(
  provider = APIProvider.valueOf("OpenAI"),
  apiKey = apiKey,
  apiBase = apiBase,
  workPool = workPool,
  logLevel = logLevel,
  logStreams = logStreams,
  scheduledPool = scheduledPool
) {

  override fun authorize(
    request: org.apache.hc.core5.http.HttpRequest,
    apiProvider: APIProvider
  ) {
    request.addHeader("Content-Type", "application/json")
    request.addHeader("Accept", "application/json")
    val apiKey = this.apiKey.decrypt
    if (!apiKey.isNullOrBlank()) {
      request.addHeader("Authorization", "Bearer $apiKey")
    } else {
      throw IllegalStateException("OpenAI API key is required")
    }
  }

  override fun createEmbedding(
    request: ModelSchema.EmbeddingRequest,
    model: EmbeddingModel
  ): ModelSchema.EmbeddingResponse {
    validateEmbeddingRequest(request, model)
    return withPerformanceLogging {
      // OpenAI embedding request format
      val openAIRequest = mapOf(
        "model" to (request.model ?: model.modelId),
        "input" to when {
          request.input is String -> request.input
          else -> listOf(request.input.toString())
        },
        "encoding_format" to "float"
      )

      val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
        .writeValueAsString(openAIRequest)

      val rawResponse = post("$apiBase/embeddings", json, provider)
      checkError(rawResponse)

      // Parse OpenAI response
      val response = JsonUtil.objectMapper().readValue(rawResponse, ModelSchema.EmbeddingResponse::class.java)

      // Validate response
      if (response.data.isEmpty()) {
        throw IllegalStateException("No embeddings found in response")
      }

      // Update usage with cost calculation
      if (response.usage != null) {
        onUsage(model, response.usage!!)
      }

      response
    }

  }

  private fun validateEmbeddingRequest(request: ModelSchema.EmbeddingRequest, model: EmbeddingModel) {
    require(request.input.toString().isNotBlank()) { "Embedding request input cannot be blank" }
    require(model.modelId.isNotBlank()) { "Model name cannot be blank" }
    val apiKey = this.apiKey.decrypt
    require(!apiKey.isNullOrBlank()) { "OpenAI API key is required" }
  }
}