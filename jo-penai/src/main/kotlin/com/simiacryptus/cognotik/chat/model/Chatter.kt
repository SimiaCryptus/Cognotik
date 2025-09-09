package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.ApiModel
import java.util.concurrent.ExecutorService

interface Chatter {
    fun chat(
        messages: List<ApiModel.ChatMessage> = listOf()
    ): ApiModel.ChatResponse
    val modelType: ChatModel
    val workPool: ExecutorService
}