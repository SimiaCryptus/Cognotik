package com.simiacryptus.jopenai.models
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.simiacryptus.jopenai.models.ApiModel.Usage

open class CompletionModels(
    modelName: String,
    maxTokens: Int,
    private val tokenPricePerK: Double,
) : TextModel(modelName, maxTokens) {
    private val log: Logger = LoggerFactory.getLogger(CompletionModels::class.java)
    init {
        log.info("Initialized CompletionModels with modelName: $modelName, maxTokens: $maxTokens, tokenPricePerK: $tokenPricePerK")
    }

    override fun pricing(usage: Usage) = usage.prompt_tokens * tokenPricePerK / 1000.0

    companion object {
        fun values() = mapOf("DaVinci" to DaVinci)

        private val DaVinci = CompletionModels("text-davinci-003", 2049, 0.002)
        init {
            LoggerFactory.getLogger(CompletionModels::class.java).info("Initialized DaVinci model with modelName: text-davinci-003, maxTokens: 2049, tokenPricePerK: 0.002")
        }
    }
}