package com.simiacryptus.cognotik.chat.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.simiacryptus.cognotik.chat.model.Chatter
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.models.ApiModel.ChatMessage
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

open class Chatter(
    val logStreams: MutableList<BufferedOutputStream>,
    private val key: String,
    private val base: String,
    private val logLevel: Level,
    private val temperature: Double,
    val provider: APIProvider,
    val modelType: ChatModel,
    val workPool: ExecutorService,
) {
    init {
        require(key.isNotBlank()) { "API key must be provided" }
        require(base.isNotBlank()) { "Base URL must be provided" }
        require(temperature in 0.0..2.0) { "Temperature must be in range [0.0, 2.0]" }
    }
    open fun chat(
        messages: List<ChatMessage>
    ) = provider.getChatClient(
        key = key,
        base = base,
        workPool = workPool,
        logLevel = logLevel,
        logStreams = logStreams
    ).chat(
        chatRequest = ApiModel.ChatRequest(
            model = modelType.modelName,
            messages = messages,
            temperature = temperature,
        ),
        model = modelType,
        logStreams = this.logStreams
    )

    var budget: Number? // TODO: implement budget tracking
        get() = null
        set(value) {}

    @JsonIgnore
    fun getChildClient(): Chatter = ChildChatter(
        parent = this,
        workPool = this.workPool,
    )

    class ChildChatter(
        val parent: Chatter,
        workPool: ExecutorService,
        logStreams: MutableList<BufferedOutputStream> = parent.logStreams.toTypedArray().toMutableList(),
    ) : Chatter(
        logStreams = logStreams,
        key = parent.key,
        base = parent.base,
        logLevel = parent.logLevel,
        temperature = parent.temperature,
        provider = parent.provider,
        modelType = parent.modelType,
        workPool = workPool,
    ) {
        override fun chat(messages: List<ApiModel.ChatMessage>): ApiModel.ChatResponse =
            this.modelType.instance(
                key = "",
                workPool = workPool,
            ).chat(messages)
    }
}