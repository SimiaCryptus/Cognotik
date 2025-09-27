package com.simiacryptus.cognotik.chat

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.models.LLMModel
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

interface ChatClientInterface {
    var budget: Number?
    val logStreams: MutableList<BufferedOutputStream>
    val workPool : ExecutorService
    val onUsageListeners: MutableList<(model: LLMModel, tokens: ApiModel.Usage) -> Unit>

    /**
     * Sends a chat request to the configured model and returns the response
     * @param chatRequest The chat request containing messages and parameters
     * @param model The text model to use for the chat
     * @return The chat response from the model
     * @throws IllegalArgumentException if the request is invalid
     * @throws RuntimeException if the API call fails
     */
    @Deprecated("Use chat with messages parameter instead via preauthenticated chat models")
    fun chat(
        chatRequest: ApiModel.ChatRequest,
        model: ChatModel,
        logStreams: MutableList<BufferedOutputStream> = this.logStreams
    ): ApiModel.ChatResponse

    /**
     * Moderates the given text for policy violations
     * @param text The text to moderate
     * @throws ModerationException if the text violates policies
     */
    fun moderate(text: String) {}

}