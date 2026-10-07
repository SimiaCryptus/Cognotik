package com.simiacryptus.cognotik.platform

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.platform.model.ChatModel.Companion.ON_USAGE
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
        logLevel = logLevel,
        session = session,
        logStreams = logStreams,
        workPool = workPool,
        scheduledPool = scheduledPool,
    ).chat(
        chatRequest = chatRequest,
        model = model,
        logStreams = logStreams,
        usageHandler = UsageListener.fn(session) { model, usage, data ->
            ON_USAGE(model, usage, user, session, data)
        }
    )
    fun getChildClient(task: ISessionTask) = getChildClient(task.sessionId).apply { logStreams += task.newLogStream() }

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
            workPool = ThreadPoolManager.newCachedThreadPool(Session.NULL),
            scheduledPool = ThreadPoolManager.newScheduledThreadPool(4, Session.NULL),
            session = Session.newUserID(),
            user = User.NULL,
        )
        val log = LoggerFactory.getLogger(ChatInterface::class.java)
        var ENABLE_LOGS = false
    }
}