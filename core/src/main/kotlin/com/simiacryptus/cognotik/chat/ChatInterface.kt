package com.simiacryptus.cognotik.chat

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

class ChatInterface(
    logStreams: MutableList<BufferedOutputStream>,
    private val key: SecureString,
    private val base: String,
    private val logLevel: Level,
    val temperature: Double,
    val audio: MutableMap<String, String> = mutableMapOf(),
    val provider: APIProvider,
    val modelType: ChatModel,
    private val workPool: ExecutorService,
    private val scheduledPool: ListeningScheduledExecutorService,
    private val onUsage: (model: LLMModel, tokens: ModelSchema.Usage) -> Unit,
    val session: Session
) {
    val logStreams: MutableList<BufferedOutputStream> = logStreams.toMutableList()
        get() = when {
            !ENABLE_LOGS -> mutableListOf()
            else -> field
        }

    fun chat(chatRequest: ModelSchema.ChatRequest): ModelSchema.ChatResponse = getChatClient().chat(
        chatRequest = chatRequest,
        model = modelType,
        logStreams = logStreams,
        usageHandler = UsageListener.fn(session) { model, usage -> onUsage(model, usage) }
    )

    private fun getChatClient(): ChatClientInterface = provider.getChatClient(
        key = key,
        workPool = workPool,
        logLevel = logLevel,
        logStreams = logStreams,
        scheduledPool = scheduledPool,
        session = session,
    )

    @JsonIgnore
    fun getChildClient(): ChatInterface = ChatInterface(
        logStreams = this.logStreams.toTypedArray().toMutableList(),
        key = this.key,
        base = this.base,
        logLevel = this.logLevel,
        temperature = this.temperature,
        provider = this.provider,
        modelType = this.modelType,
        workPool = this.workPool,
        scheduledPool = this.scheduledPool,
        onUsage = this.onUsage,
        session = this.session,
    )

    companion object {
        val NULL: ChatInterface = ChatInterface(
            logStreams = mutableListOf(),
            key = SecureString(""),
            base = "",
            logLevel = Level.INFO,
            temperature = 0.0,
            provider = APIProvider.NULL,
            modelType = ChatModel(),
            workPool = java.util.concurrent.Executors.newCachedThreadPool(),
            scheduledPool = com.google.common.util.concurrent.MoreExecutors.listeningDecorator(
                java.util.concurrent.Executors.newScheduledThreadPool(
                    4
                )
            ),
            onUsage = { _, _ -> },
            session = Session.newUserID(),
        )
        val log = LoggerFactory.getLogger(ChatInterface::class.java)
        var ENABLE_LOGS = false
    }
}