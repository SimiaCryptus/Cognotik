package com.simiacryptus.cognotik.chat

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.ChatModel.Companion.ON_USAGE
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

class ChatInterface(
    val provider: APIProvider,
    val model: ChatModel,
    val session: Session,
    val user : User,
    private val key: SecureString,
    private val base: String,
    private val logLevel: Level,
    private val workPool: ExecutorService,
    private val scheduledPool: ListeningScheduledExecutorService,
    logStreams: MutableList<BufferedOutputStream>,
) {
    val audio: MutableMap<String, String> = mutableMapOf()
    val logStreams: MutableList<BufferedOutputStream> = logStreams.toMutableList()
        get() = when {
            !ENABLE_LOGS -> mutableListOf()
            else -> field
        }

    fun chat(chatRequest: ModelSchema.ChatRequest): ModelSchema.ChatResponse = provider.getChatClient(
        key = key,
        workPool = workPool,
        logLevel = logLevel,
        logStreams = logStreams,
        scheduledPool = scheduledPool,
        session = session,
    ).chat(
        chatRequest = chatRequest,
        model = model,
        logStreams = logStreams,
        usageHandler = UsageListener.fn(session) { model, usage, data ->
            ON_USAGE(model, usage, user, session, data)
        }
    )

    @JsonIgnore
    fun getChildClient(
        session: Session? = null,
    ): ChatInterface = ChatInterface(
        logStreams = this.logStreams.toTypedArray().toMutableList(), // Create a new mutable list to avoid shared state
        key = this.key,
        base = this.base,
        logLevel = this.logLevel,
        provider = this.provider,
        model = this.model,
        workPool = this.workPool,
        scheduledPool = this.scheduledPool,
        session = session ?: this.session,
        user = this.user,
    )

    companion object {
        val NULL: ChatInterface = ChatInterface(
            logStreams = mutableListOf(),
            key = SecureString(""),
            base = "",
            logLevel = Level.INFO,
            provider = APIProvider.NULL,
            model = ChatModel(
                inputModalities = setOf(),
                outputModalities = setOf()
            ),
            workPool = java.util.concurrent.Executors.newCachedThreadPool(),
            scheduledPool = com.google.common.util.concurrent.MoreExecutors.listeningDecorator(
                java.util.concurrent.Executors.newScheduledThreadPool(
                    4
                )
            ),
            session = Session.newUserID(),
            user = User.NULL,
        )
        val log = LoggerFactory.getLogger(ChatInterface::class.java)
        var ENABLE_LOGS = false
    }
}