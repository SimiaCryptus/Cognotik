package com.simiacryptus.cognotik.platform

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.platform.model.mcp.McpToolLoop
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

    /**
     * Sends a chat request. If [ModelSchema.ChatRequest.toolProviders] is non-empty, the request
     * runs through the MCP tool loop (multiple provider rounds); otherwise it is a single round trip.
     */
    fun chat(chatRequest: ModelSchema.ChatRequest): ModelSchema.ChatResponse {
        val streams = logStreams
        // Obtain the client once; the tool loop reuses it for every round.
        val client = provider.getChatClient(
            key = key,
            logLevel = logLevel,
            session = session,
            logStreams = streams,
            workPool = workPool,
            scheduledPool = scheduledPool,
        )
        val usageHandler = UsageListener.fn(session) { model, usage, data ->
            ServiceRouter.incrementUsage(session, user, model, usage, data)
        }
        return if (chatRequest.toolProviders.isNullOrEmpty()) {
            client.chat(
                chatRequest = chatRequest,
                model = model,
                logStreams = streams,
                usageHandler = usageHandler
            )
        } else {
            McpToolLoop().run(
                client = client,
                request = chatRequest,
                model = model,
                session = session,
                user = user,
                logStreams = streams,
                usageHandler = usageHandler,
            )
        }
    }

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