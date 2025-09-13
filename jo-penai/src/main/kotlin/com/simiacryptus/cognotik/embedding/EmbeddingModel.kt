package com.simiacryptus.cognotik.embedding

import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.util.LoggerFactory

interface Embedder {
    fun embed(input: String): DoubleArray
}

open class EmbeddingModel(
    modelName: String? = null,
    maxTokens: Int = 0,
    provider: APIProvider? = null,
    private val tokenPricePerK: Double = 0.0,
) : LLMModel(
    modelName = modelName,
    provider = provider,
    maxTotalTokens = maxTokens
) {
    private val log = LoggerFactory.getLogger(EmbeddingModel::class.java)
    override fun pricing(usage: ApiModel.Usage) = usage.prompt_tokens * tokenPricePerK / 1000.0
        .also { log.info("Calculated pricing for model: $modelName with prompt tokens: ${usage.prompt_tokens}, price: $it") }
    fun instance() = EmbedderClient(
        when (provider) {
        APIProvider.Ollama -> OllamaEmbeddingClient()
        APIProvider.OpenAI -> OpenAIEmbeddingClient()
        else -> throw IllegalArgumentException("Unsupported provider: $provider")
    }, this)
    fun instance(key: String, base: String): EmbedderClient {
        val client = when (provider) {
            APIProvider.Ollama -> OllamaEmbeddingClient()
            APIProvider.OpenAI -> OpenAIEmbeddingClient()
            else -> throw IllegalArgumentException("Unsupported provider: $provider")
        }
        // Configure client with provided key and base URL
        return EmbedderClient(client, this)
    }

    companion object {
        val log = LoggerFactory.getLogger(EmbeddingModel::class.java)
        fun values() = mapOf(
            "OllamaNomadic" to OllamaNomadic,
            "AdaEmbedding" to AdaEmbedding,
            "Small" to Small,
            "Large" to Large,
        )

        init {
            log.info("Initializing EmbeddingModels with predefined models: AdaEmbedding, Small, Large")
        }

        val OllamaNomadic = EmbeddingModel("nomic-embed-text", 2049, APIProvider.Ollama, 0.00013)
        val AdaEmbedding = EmbeddingModel("text-embedding-ada-002", 2049, APIProvider.OpenAI, 0.0001)
        val Small = EmbeddingModel("text-embedding-3-small", 2049, APIProvider.OpenAI, 0.00002)
        val Large = EmbeddingModel("text-embedding-3-large", 2049, APIProvider.OpenAI, 0.00013)
    }
}

class EmbedderClient(private val embeddingClient: EmbeddingClientInterface, val model : EmbeddingModel) : Embedder {
    override fun embed(input: String): DoubleArray {
        val request = ApiModel.EmbeddingRequest(
            model = model.modelName,
            input = input
        )
        val response = embeddingClient.createEmbedding(request, model)
        if (response.data.isEmpty()) {
            throw IllegalStateException("No embedding data returned")
        }
        EmbeddingModel.log.info("Generated embedding of size ${response.data[0].embedding?.size} for input of length ${input.length}")
        return response.data[0].embedding ?: throw IllegalStateException("Embedding data is null")
    }
}