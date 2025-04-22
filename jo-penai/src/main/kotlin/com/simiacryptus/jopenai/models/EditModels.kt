package com.simiacryptus.jopenai.models
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.simiacryptus.jopenai.models.ApiModel.Usage

open class EditModels(
    modelName: String,
    maxTokens: Int,
    private val tokenPricePerK: Double,
) : TextModel(modelName, maxTokens) {
    private val log: Logger = LoggerFactory.getLogger(EditModels::class.java)
    init {
        log.info("Initialized EditModels with modelName: $modelName, maxTokens: $maxTokens, tokenPricePerK: $tokenPricePerK")
    }

    override fun pricing(usage: Usage) = usage.prompt_tokens * tokenPricePerK / 1000.0

    companion object {
        fun values() = mapOf("DaVinciEdit" to DaVinciEdit)

        private val DaVinciEdit = EditModels("text-davinci-edit-001", 2049, 0.002)
        init {
            LoggerFactory.getLogger(EditModels::class.java).info("Initialized DaVinciEdit model with maxTokens: 2049 and tokenPricePerK: 0.002")
        }
    }
}