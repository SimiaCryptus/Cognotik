package com.simiacryptus.cognotik.chat

import com.simiacryptus.cognotik.chat.ChatClientInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.models.LLMModel
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

interface ChatClientInterface {
    var budget: Number?
    val logStreams: MutableList<BufferedOutputStream>
    val workPool : ExecutorService

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
    ) = model.instance(
            key = key(model.provider),
            base = apiBase(model.provider),
            logStreams = logStreams,
            workPool = workPool,
            temperature = chatRequest.temperature
        ).chat(chatRequest.messages)

    fun apiBase(provider: APIProvider): String

    fun key(provider: APIProvider): String

    /**
     * Moderates the given text for policy violations
     * @param text The text to moderate
     * @throws ModerationException if the text violates policies
     */
    fun moderate(text: String) {}

    /**
     * Creates a child client that inherits configuration from this client
     * @return A new ChatClientInterface instance
     */
    fun getChildClient(): ChatClientInterface

    /**
     * Called when API usage occurs to track tokens and costs
     * @param model The model that was used
     * @param tokens Usage information including token counts and cost
     */
    fun onUsage(
        model: LLMModel,
        tokens: ApiModel.Usage,
        logStreams: MutableList<BufferedOutputStream> = this.logStreams.toTypedArray().toMutableList(),
    )
}