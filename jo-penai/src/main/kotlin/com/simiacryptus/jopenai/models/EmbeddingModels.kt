package com.simiacryptus.jopenai.models
import org.slf4j.LoggerFactory

import com.simiacryptus.jopenai.models.ApiModel.Usage

open class EmbeddingModels(
    modelName: String,
    maxTokens: Int,
    private val tokenPricePerK: Double,
) : TextModel(modelName, maxTokens) {
    private val log = LoggerFactory.getLogger(EmbeddingModels::class.java)
    override fun pricing(usage: Usage) = usage.prompt_tokens * tokenPricePerK / 1000.0
        .also { log.info("Calculated pricing for model: $modelName with prompt tokens: ${usage.prompt_tokens}, price: $it") }

    companion object {
        private val log = LoggerFactory.getLogger(EmbeddingModels::class.java)
        fun values() = mapOf(
            "AdaEmbedding" to AdaEmbedding,
            "Small" to Small,
            "Large" to Large
        )
        init {
            log.info("Initializing EmbeddingModels with predefined models: AdaEmbedding, Small, Large")
        }

        val AdaEmbedding = EmbeddingModels("text-embedding-ada-002", 2049, 0.0001)
        val Small = EmbeddingModels("text-embedding-3-small", 2049, 0.00002)
        val Large = EmbeddingModels("text-embedding-3-large", 2049, 0.00013)
    }
}