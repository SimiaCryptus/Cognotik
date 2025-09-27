package com.simiacryptus.cognotik.embedding

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.util.JsonUtil
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.lang.IllegalStateException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class OpenAIEmbeddingClient(
    apiKey: String = "",
    apiBase: String = "https://api.openai.com/v1",
    workPool: ExecutorService = Executors.newCachedThreadPool(),
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
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
        if (apiKey.isNotBlank()) {
            request.addHeader("Authorization", "Bearer $apiKey")
        } else {
            throw IllegalStateException("OpenAI API key is required")
        }
    }

    override fun createEmbedding(
        request: ApiModel.EmbeddingRequest,
        model: EmbeddingModel
    ): ApiModel.EmbeddingResponse {
        validateEmbeddingRequest(request, model)

        return withReliability {
            withPerformanceLogging {
                // OpenAI embedding request format
                val openAIRequest = mapOf(
                    "model" to (request.model ?: model.modelName),
                    "input" to when {
                        request.input is String -> request.input
                        request.input is List<*> -> request.input
                        else -> listOf(request.input.toString())
                    },
                    "encoding_format" to "float"
                )

                val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(openAIRequest)

                val rawResponse = post("$apiBase/embeddings", json, provider)
                checkError(rawResponse)

                // Parse OpenAI response
                val response = JsonUtil.objectMapper().readValue(rawResponse, ApiModel.EmbeddingResponse::class.java)

                // Validate response
                if (response.data.isEmpty()) {
                    throw IllegalStateException("No embeddings found in response")
                }

                // Update usage with cost calculation
                if (response.usage != null) {
                    onUsage(model, response.usage.copy(cost = model.pricing(response.usage)))
                }

                response
            }
        }
    }

    private fun validateEmbeddingRequest(request: ApiModel.EmbeddingRequest, model: EmbeddingModel) {
        require(request.input.toString().isNotBlank()) { "Embedding request input cannot be blank" }
        require(model.modelName?.isNotBlank() == true) { "Model name cannot be blank" }
        require(apiKey.isNotBlank()) { "OpenAI API key is required" }
    }
}