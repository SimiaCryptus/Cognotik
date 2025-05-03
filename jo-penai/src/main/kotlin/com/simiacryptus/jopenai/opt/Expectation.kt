package com.simiacryptus.jopenai.opt

import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ApiModel.ChatResponse
import org.slf4j.LoggerFactory

abstract class Expectation {
    abstract fun matches(api: OpenAIClient, response: ChatResponse): Boolean

    abstract fun score(api: OpenAIClient, response: ChatResponse): Double

}