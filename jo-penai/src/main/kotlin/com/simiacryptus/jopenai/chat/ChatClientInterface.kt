package com.simiacryptus.jopenai.chat

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.chat.LLMModel
import java.io.BufferedOutputStream

interface ChatClientInterface : API {
    var budget: Number?
    val logStreams: MutableList<BufferedOutputStream>

    /**
     * Sends a chat request to the configured model and returns the response
     * @param chatRequest The chat request containing messages and parameters
     * @param model The text model to use for the chat
     * @return The chat response from the model
     * @throws IllegalArgumentException if the request is invalid
     * @throws RuntimeException if the API call fails
     */
    fun chat(chatRequest: ApiModel.ChatRequest, model: LLMModel): ApiModel.ChatResponse

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
    fun onUsage(model: LLMModel, tokens: ApiModel.Usage)
}