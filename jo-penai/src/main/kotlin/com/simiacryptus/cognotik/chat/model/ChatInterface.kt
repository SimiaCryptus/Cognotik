package com.simiacryptus.cognotik.chat.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.ChatMessage
import com.simiacryptus.cognotik.models.LLMModel
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

open class ChatInterface(
    val logStreams: MutableList<BufferedOutputStream>,
    private val key: String,
    private val base: String,
    private val logLevel: Level,
    private val temperature: Double,
    val provider: APIProvider,
    val modelType: ChatModel,
    val workPool: ExecutorService,
    val scheduledPool: ListeningScheduledExecutorService,
    val onUsage: (model: LLMModel, tokens: ModelSchema.Usage) -> Unit,
) {
    init {
        //require(key != null) { "API key must be provided" }
        require(base.isNotBlank()) { "Base URL must be provided" }
        require(temperature in 0.0..2.0) { "Temperature must be in range [0.0, 2.0]" }
    }
    open fun chat(
        messages: List<ChatMessage>,
        streams: MutableList<BufferedOutputStream> = logStreams
    ) = provider.getChatClient(
        key = key,
        base = base,
        workPool = workPool,
        logLevel = logLevel,
        logStreams = streams,
        scheduledPool = scheduledPool,
    ).apply {
        onUsageListeners.add { model, usage -> onUsage(model, usage) }
    }.chat(
        chatRequest = ModelSchema.ChatRequest(
            model = modelType.modelName,
            messages = messages,
            temperature = temperature,
        ),
        model = modelType,
        logStreams = streams
    )

    var budget: Number? // TODO: implement budget tracking
        get() = null
        set(value) {}

    @JsonIgnore
    fun getChildClient(): ChatInterface = ChildChatInterface(
        parent = this,
    )

    class ChildChatInterface(
        val parent: ChatInterface,
        logStreams: MutableList<BufferedOutputStream> = parent.logStreams.toTypedArray().toMutableList(),
    ) : ChatInterface(
        logStreams = logStreams,
        key = parent.key,
        base = parent.base,
        logLevel = parent.logLevel,
        temperature = parent.temperature,
        provider = parent.provider,
        modelType = parent.modelType,
        workPool = parent.workPool,
        scheduledPool = parent.scheduledPool,
        onUsage = parent.onUsage,
    )
}