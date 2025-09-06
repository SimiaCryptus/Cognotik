package com.simiacryptus.cognotik.opt

import com.simiacryptus.cognotik.OpenAIClient
import com.simiacryptus.cognotik.models.ApiModel.ChatResponse

abstract class Expectation {
    abstract fun matches(api: OpenAIClient, response: ChatResponse): Boolean

    abstract fun score(api: OpenAIClient, response: ChatResponse): Double

}