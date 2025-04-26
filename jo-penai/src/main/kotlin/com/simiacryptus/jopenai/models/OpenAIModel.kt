package com.simiacryptus.jopenai.models

import org.slf4j.LoggerFactory

interface OpenAIModel {

    val modelName: String
    fun logModelName() {
        val log = LoggerFactory.getLogger(OpenAIModel::class.java)
        log.debug("Model name is: $modelName")
    }
}