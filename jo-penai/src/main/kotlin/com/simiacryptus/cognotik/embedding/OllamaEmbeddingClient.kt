package com.simiacryptus.cognotik.embedding

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class OllamaEmbeddingClient(
    apiKey: String = "",
    apiBase: String = "http://localhost:11434",
    workPool: ExecutorService = Executors.newCachedThreadPool(),
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService = MoreExecutors.listeningDecorator(
        Executors.newScheduledThreadPool(
            1
        )
    ),
) : SingleProviderEmbeddingClient(
    provider = APIProvider.valueOf("Ollama"),
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
        // Ollama typically doesn't require authorization for local instances
        if (apiKey.isNotBlank()) {
            request.addHeader("Authorization", "Bearer $apiKey")
        }
    }

    override fun createEmbedding(
        request: ModelSchema.EmbeddingRequest,
        model: EmbeddingModel
    ): ModelSchema.EmbeddingResponse {
        validateEmbeddingRequest(request, model)

        return withReliability {
            withPerformanceLogging {
                // Convert OpenAI-style request to Ollama format
                val ollamaRequest = mapOf(
                    "model" to (request.model ?: model.modelName),
                    "prompt" to when {
                        request.input is String -> request.input
                        else -> request.input.toString()
                    }
                )

                val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(ollamaRequest)

                val rawResponse = post("$apiBase/api/embeddings", json, provider)
                checkError(rawResponse)

                // Parse Ollama response and convert to OpenAI format
                val ollamaResponse = JsonUtil.objectMapper().readValue(rawResponse, Map::class.java)
                val embeddings = ollamaResponse["embedding"] as? List<Double>
                    ?: throw IllegalStateException("No embeddings found in response")

                val response = ModelSchema.EmbeddingResponse(
                    data = listOf(
                        ModelSchema.EmbeddingData(
                            embedding = embeddings.toDoubleArray(),
                            index = 0,
                            `object` = "embedding"
                        )
                    ),
                    model = request.model ?: model.modelName,
                    `object` = "list",
                    usage = ModelSchema.Usage(
                        prompt_tokens = estimateTokens(request.input.toString()).toLong(),
                        total_tokens = estimateTokens(request.input.toString()).toLong(),
                        completion_tokens = 0
                    )
                )

                if (response.usage != null) {
                    onUsage(model, response.usage?.copy(cost = model.pricing(response.usage!!))!!)
                }

                response
            }
        }
    }

    private fun validateEmbeddingRequest(request: ModelSchema.EmbeddingRequest, model: EmbeddingModel) {
        require(request.input.toString().isNotBlank()) { "Embedding request input cannot be blank" }
        require(model.modelName?.isNotBlank() == true) { "Model name cannot be blank" }
    }

    private fun estimateTokens(text: String): Int {
        // Simple token estimation - roughly 4 characters per token
        return (text.length / 4).coerceAtLeast(1)
    }
}