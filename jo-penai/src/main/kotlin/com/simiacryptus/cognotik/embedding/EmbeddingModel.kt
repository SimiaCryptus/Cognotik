package com.simiacryptus.cognotik.embedding

import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.util.LoggerFactory

open class EmbeddingModel(
    modelName: String,
    maxTokens: Int,
    private val tokenPricePerK: Double,
    provider: APIProvider = APIProvider.Companion.OpenAI,
) : LLMModel(
    modelName = modelName,
    provider = provider,
    maxTotalTokens = maxTokens
) {
    private val log = LoggerFactory.getLogger(EmbeddingModel::class.java)
    override fun pricing(usage: ApiModel.Usage) = usage.prompt_tokens * tokenPricePerK / 1000.0
        .also { log.info("Calculated pricing for model: $modelName with prompt tokens: ${usage.prompt_tokens}, price: $it") }

    companion object {
        private val log = LoggerFactory.getLogger(EmbeddingModel::class.java)
        fun values() = mapOf(
            "AdaEmbedding" to AdaEmbedding,
            "Small" to Small,
            "Large" to Large,
            "OllamaNomadic" to OllamaNomadic,
        )

        init {
            log.info("Initializing EmbeddingModels with predefined models: AdaEmbedding, Small, Large")
        }

        val AdaEmbedding = EmbeddingModel("text-embedding-ada-002", 2049, 0.0001)
        val Small = EmbeddingModel("text-embedding-3-small", 2049, 0.00002)
        val Large = EmbeddingModel("text-embedding-3-large", 2049, 0.00013)
        val OllamaNomadic = EmbeddingModel("nomic-embed-text", 2049, 0.00013)
    }
}