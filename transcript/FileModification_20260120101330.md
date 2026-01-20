# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/session/BasicChatApp.kt

```
package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.fileApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File

class BasicChatApp(
    root: File,
    val model: ChatModel,
    val parsingModel: ChatModel,
    applicationName: String = "Chat",
    val settings: Settings? = null,
) : ApplicationServer(
    applicationName = applicationName,
    path = root.absolutePath,
    root = root
) {
    override val stickyInput: Boolean
        get() = true
    override val inputCnt get() = 0

    data class Settings(
        val model: ChatModel,
        val parsingModel: ChatModel,
        val temperature: Double = 0.3,
        val budget: Double = 2.0,
    )

    override val settingsClass: Class<*> get() = Settings::class.java

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T? = Settings(
        model = model,
        parsingModel = parsingModel,
    ) as T

    override fun newSession(user: User, session: Session): SocketManager {
        (SessionProxyServer.chats[session]?.takeIf { it != this }?.newSession(user, session)
            ?: SessionProxyServer.agents[session])?.apply {
            return this;
        }
        val user = user ?: throw IllegalArgumentException("User must be provided for chat session")
        val settings = this.settings ?: getSettings(session, user)!!
        fun instance(model: ChatModel): ChatInterface? {
            val api = fileApplicationServices().userSettingsManager.getUserSettings(user).apis
                .firstOrNull { it.provider == model.provider }?.validate()
            val threadPoolManager = ApplicationServices.threadPoolManager
            return api?.let { apiData ->
                model.instance(
                    key = apiData.key ?: return null,
                    base = apiData.baseUrl,
                    workPool = threadPoolManager.getPool(session, user),
                    temperature = settings.temperature,
                    scheduledPool = threadPoolManager.getScheduledPool(session, user),
                    onUsage = { model, usage ->
                        fileApplicationServices().usageManager.incrementUsage(
                            session,
                            user,
                            model,
                            usage
                        )
                    },
                )
            }
        }
        return ChatSocketManager(
            session = session,
            smartModel = instance(settings.model)
                ?: throw RuntimeException("No API key for model ${settings.model.name}"),
            fastModel = instance(settings.parsingModel)
                ?: throw RuntimeException("No API key for model ${settings.parsingModel.name}"),
            systemPrompt = "",
            temperature = settings.temperature,
            applicationClass = this::class.java,
            storage = dataStorage,
            fastTopicParsing = true,
            budget = settings.budget,
        )
    }
}


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/session/ChatServer.kt

```
package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.platform.ApplicationServices.authenticationManager
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.servlet.NewSessionServlet
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest
import org.eclipse.jetty.websocket.server.JettyWebSocketServlet
import org.eclipse.jetty.websocket.server.JettyWebSocketServletFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

abstract class ChatServer(
    private val resourceBase: String = "application",
    val showMenubar: Boolean
) {

    abstract val applicationName: String
    open val description: String = ""
    open val inputCnt = 1
    open val stickyInput = false
    open val dataStorage: StorageInterface? = null
    val sessions: ConcurrentHashMap<Session, SocketManager> = ConcurrentHashMap()

    inner class WebSocketHandler : JettyWebSocketServlet() {
        override fun configure(factory: JettyWebSocketServletFactory) {
            with(factory) {
                isAutoFragment = false
                idleTimeout = Duration.ofMinutes(10)
                outputBufferSize = 1024 * 1024
                inputBufferSize = 1024 * 1024
                maxBinaryMessageSize = 1024 * 1024
                maxFrameSize = 1024 * 1024
                maxTextMessageSize = 1024 * 1024
                this.availableExtensionNames.remove("permessage-deflate")
            }
            trafficLog.debug("Configuring WebSocket factory with settings: autoFragment=false, idleTimeout=10min, bufferSizes=1MB")
            factory.setCreator { req, resp ->
                try {
                    if (req.parameterMap.containsKey("sessionId")) {
                        val session = Session(req.parameterMap["sessionId"]?.first()!!)
                        trafficLog.debug("WebSocket connection request for session: {}", session)
                        val sessionManager = sessions.computeIfAbsent(session) { s ->
                            val user =
                                authenticationManager.getUser(req.getCookie(AuthenticationInterface.AUTH_COOKIE))
                            trafficLog.debug(
                                "Creating new session manager for session: {}, user: {}",
                                s,
                                user?.name ?: "anonymous"
                            )
                            newSession(user, s)
                        }
                        ChatSocket(sessionManager)
                    } else {
                        trafficLog.warn("WebSocket connection rejected: missing sessionId parameter")
                        throw IllegalArgumentException("sessionId is required")
                    }
                } catch (e: Exception) {
                    log.debug("Error configuring websocket", e)
                    trafficLog.error("WebSocket configuration error: ${e.message}", e)
                    resp.sendError(500, e.message)
                    null
                }
            }
        }
    }

    abstract fun newSession(user: User = defaultUser, session: Session): SocketManager

    open val baseResource: Resource?
        get() = javaClass.classLoader.getResource(resourceBase)?.let {
            Resource.newResource(it).apply {
                if (!exists()) {
                    val message = "Resource not found: $it"
                    trafficLog.error("Base resource not found: $it")
                    throw RuntimeException(message)
                }
            }
        }
    private val newSessionServlet by lazy { NewSessionServlet() }
    private val webSocketHandler by lazy { WebSocketHandler() }
    private val defaultServlet by lazy { DefaultServlet() }

    open fun configure(webAppContext: WebAppContext) {
        trafficLog.info("Configuring web app context for ${javaClass.simpleName}")
        webAppContext.addServlet(ServletHolder(javaClass.simpleName + "/default", defaultServlet), "/")
        webAppContext.addServlet(ServletHolder(javaClass.simpleName + "/ws", webSocketHandler), "/ws")
        webAppContext.addServlet(ServletHolder(javaClass.simpleName + "/newSession", newSessionServlet), "/newSession")
        trafficLog.debug("Servlets registered: default(/), ws(/ws), newSession(/newSession)")
    }

    companion object {
        private val log = LoggerFactory.getLogger(ChatServer::class.java)
        private val trafficLog = LoggerFactory.getLogger("TRAFFIC.com.simiacryptus.cognotik.webui.chat")
        fun JettyServerUpgradeRequest.getCookie(name: String) = cookies?.find { it.name == name }?.value
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/session/ChatSocket.kt

```
package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketAdapter

class ChatSocket(
    private val sessionState: SocketManager,
) : WebSocketAdapter() {

    val user get() = SocketManager.getUser(session)

    override fun onWebSocketConnect(session: Session) {
        super.onWebSocketConnect(session)
        try {
            trafficLog.info("WebSocket connected: ${session.remoteAddress}, user: ${SocketManager.getUser(session)?.name ?: "anonymous"}")
            sessionState.addSocket(this, session)
            trafficLog.debug("Socket added to session manager, active connections: ${sessionState.getActiveSockets().size}")

            val firstOrNull = session.upgradeRequest.parameterMap["lastMessageTime"]?.firstOrNull()
            val lastMessageTime =
                when (firstOrNull) {
                    "-Infinity" -> Long.MIN_VALUE
                    "Infinity" -> Long.MAX_VALUE
                    "null" -> 0L
                    "" -> 0L
                    null -> 0L
                    else -> firstOrNull.toLongOrNull()
                } ?: 0L
            trafficLog.debug("Replaying messages since: $lastMessageTime")
            sessionState.getReplay(lastMessageTime).forEach {
                try {
                    trafficLog.trace("Replaying message: ${it.take(100)}${if (it.length > 100) "..." else ""}")
                    remote.sendString(it)
                } catch (e: Exception) {
                    log.warn("Error replaying message to ${session.remoteAddress}", e)
                    trafficLog.error("Failed to replay message to ${session.remoteAddress}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            log.warn("Error during WebSocket connection setup", e)
            trafficLog.error("WebSocket connection error from ${session.remoteAddress}: ${e.message}", e)
            session.close(1011, "WebSocket connection error: ${e.message}")
        }
    }

    override fun onWebSocketError(cause: Throwable?) {
        log.warn("WebSocket error", cause)
        super.onWebSocketError(cause)
    }

    override fun onWebSocketText(message: String) {
        super.onWebSocketText(message)
        trafficLog.debug(
            "Received message from {}: {}{}",
            session.remoteAddress,
            message.take(100),
            if (message.length > 100) "..." else ""
        )
        sessionState.onWebSocketText(this, message)
    }

    override fun onWebSocketClose(statusCode: Int, reason: String?) {
        super.onWebSocketClose(statusCode, reason)
        trafficLog.info("WebSocket closed: ${session?.remoteAddress}, statusCode: $statusCode, reason: $reason")
        sessionState.removeSocket(this)
        trafficLog.debug("Socket removed from session manager, remaining connections: ${sessionState.getActiveSockets().size}")
    }

    companion object {
        private val log = LoggerFactory.getLogger(ChatSocket::class.java)
        private val trafficLog = LoggerFactory.getLogger("TRAFFIC.com.simiacryptus.cognotik.webui.chat")
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/session/ChatSocketManager.kt

```
package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.util.FixedConcurrencyProcessor
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.util.Retryable.Companion.async
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.toContentList
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference


open class ChatSocketManager(
    session: Session,
    var useExpansionSyntax: Boolean = true,
    var smartModel: ChatInterface,
    var fastModel: ChatInterface,
    val userInterfacePrompt: String = (if (!useExpansionSyntax) "" else """
    <div class="expandable-guide">
      <div class="expandable-header">
        <strong>Query Expansion Syntax Guide</strong>
        <span class="expand-icon">▼</span>
      </div>
      <div class="expandable-content">
        <p>You can use the following syntaxes in your messages to automatically expand your queries:</p>

        <h4 class="expandable-section-title">Parallel Expansion</h4>
        <p class="expandable-description">Use <code>@[option1|option2|option3]</code> to run the same prompt with each option in parallel.</p>
        <p class="expandable-example"><em>Example:</em> <code>Tell me a joke about @[cats|dogs|birds]</code></p>

        <h4 class="expandable-section-title">Sequence Expansion</h4>
        <p class="expandable-description">Use <code>@{step1 -> step2 -> step3}</code> to run a sequence of prompts, where the output of each feeds into the next.</p>
        <p class="expandable-example"><em>Example:</em> <code>Summarize this text, then @{translate to French -> translate to German}</code></p>

        <h4 class="expandable-section-title">Range Expansion</h4>
        <p class="expandable-description">Use <code>@(start..end:step)</code> to iterate over a range of numbers.</p>
        <p class="expandable-example"><em>Example:</em> <code>Project an alternate history where Rome never fell. Tell what happened in @(1000..1500:100)</code></p>

        <h4 class="expandable-section-title">Topic Reference Expansion</h4>
        <p class="expandable-description">Use <code>@topicType</code> to refer to previously identified topics.</p>
        <p class="expandable-example"><em>Example:</em> <code>Tell me about @Person</code></p>

        <p class="expandable-footer">You can combine these syntaxes for more complex expansions.</p>
      </div>
    </div>
    """.trimIndent()),
    open val systemPrompt: String,
    var temperature: Double = 0.3,
    applicationClass: Class<out ChatServer>,
    val storage: StorageInterface = ApplicationServices.fileApplicationServices().dataStorageFactory,
    open val fastTopicParsing: Boolean = true,
    val retriable: Boolean = true,
    val budget: Double,
) : SocketManager(session, storage, applicationClass = applicationClass) {

    private val aggregateTopics = ConcurrentHashMap<String, MutableList<String>>()
    private val messagesLock = Any()

    init {
        if (userInterfacePrompt.isNotBlank()) {
            newTask().complete(userInterfacePrompt)
        }
    }

    open val sysMessage: ModelSchema.ChatMessage
        get() = ModelSchema.ChatMessage(ModelSchema.Role.system, systemPrompt.toContentList())

    protected val chatMessages = mutableListOf<ModelSchema.ChatMessage>()

    fun SessionTask.transcript(name: String = this.javaClass.simpleName): FileOutputStream? {
        val relativePath = "transcript/${name}_${SimpleDateFormat("yyyyMMddHHmmss").format(System.currentTimeMillis())}.md"
        val (link, file) = Pair(linkTo(relativePath), resolveUserFile(relativePath))
        val markdownTranscript = file?.outputStream()
        complete(
            "Writing $name to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
                link.removeSuffix(
                    ".md"
                )
            }.pdf' target='_blank'>pdf</a>",
            additionalClasses = "verbose"
        )
        return markdownTranscript
    }

    val markdownTranscript by lazy { newTask().transcript() }

    override fun onRun(userMessage: String, socket: ChatSocket) {

        val expandedUserMessage = expandTopics(userMessage)
        markdownTranscript?.write("## User\n$expandedUserMessage\n\n".toByteArray())
        val task = newTask()
        task.echo(expandedUserMessage.renderMarkdown(true))

        synchronized(messagesLock) {
            chatMessages += ModelSchema.ChatMessage(ModelSchema.Role.user, expandedUserMessage.toContentList())
        }

        try {
            if (!retriable) {
                task.add("")
                val responseString = respond(task, expandedUserMessage, chatMessages(), markdownTranscript)
                synchronized(messagesLock) {
                    if (chatMessages.lastOrNull()?.role == ModelSchema.Role.assistant) {
                        chatMessages.removeAt(chatMessages.size - 1)
                    }
                    chatMessages += ModelSchema.ChatMessage(ModelSchema.Role.assistant, responseString.toContentList())
                }
                task.complete()
            } else {
                Retryable(task, process = { task: SessionTask ->
                        chatMessages.takeLastWhile { it.role == ModelSchema.Role.assistant }
                            .forEach { chatMessages.remove(it) }
                        val currentChatMessages = chatMessages()
                        innerRun(task, expandedUserMessage, currentChatMessages, markdownTranscript)
                    }.async(task.ui, pool))
            }
        } catch (e: Exception) {
            log.info("Error in chat", e)
            task.error(e)
        }
    }

    private fun innerRun(
        task: SessionTask,
        expandedUserMessage: String,
        currentChatMessages: List<ModelSchema.ChatMessage>,
        transcriptStream: OutputStream?
    ) {
        try {
            task.add("")
            val responseString = respond(task, expandedUserMessage, currentChatMessages, transcriptStream)
            synchronized(messagesLock) {
                if (chatMessages.lastOrNull()?.role == ModelSchema.Role.assistant) {
                    chatMessages.removeAt(chatMessages.size - 1)
                }
                chatMessages += ModelSchema.ChatMessage(
                    ModelSchema.Role.assistant,
                    responseString.toContentList()
                )
            }
            task.complete()
        } catch (e: Throwable) {
            log.warn("Exception occurred while processing chat message", e)
        }
    }

    private val idSubPattern =
        """[^|\n,/\\;}\]\[><()@]+""" // Matches any valid identifier character except for special characters used in the expansion syntax
    private val expansionExpressionPattern =
        Regex("""@\[($idSubPattern(?:[|,]$idSubPattern)+)]""") // Matches @[option1|option2|option3]

    private val sequenceExpansionPattern =
        Regex("""@\{([^}]+(?:\s*->\s*[^}]+)+)\}""") // Matches @{item1 -> item2 -> item3}

    private val rangeExpansionPattern =
        Regex("""@\((-?\d+)(?:\.{2,3}| to )(-?\d+)(?:(?::| by )(\d+))?\)""") // Matches @(start..end:step) or @(start to end by step)

    protected open fun respond(
        task: SessionTask,
        userMessage: String,
        currentChatMessages: List<ModelSchema.ChatMessage>,
        transcriptStream: OutputStream? = null
    ): String {
        val model = smartModel.getChildClient(task)
        return buildString {
            runAll(
                processMsgRecursive(
                    userMessage,
                    task,
                    currentChatMessages,
                    transcriptStream,
                    model
                ), this
            )
        }.let { response ->
            // Write assistant response to transcript
            transcriptStream?.write("## Assistant\n$response\n\n".transcriptFilter().toByteArray())
            transcriptStream?.flush()

            try {
                val answer = extractTopics(response, model)
                val topicsText = try {
                    answer.topics.let { topics ->
                        if (topics?.isNotEmpty() == true) {
                            topics.forEach { (topicType, entities) ->
                                val topicList = aggregateTopics.computeIfAbsent(topicType) { mutableListOf() }
                                synchronized(topicList) {
                                    topicList.addAll(entities)
                                }
                            }
                            val joinToString =
                                topics.entries.joinToString("\n") { "* `{${it.key}}` - ${it.value.joinToString(", ") { "`$it`" }}" }
                            task.complete(joinToString.renderMarkdown(), additionalClasses = "topics")
                            "\n\n" + joinToString
                        } else {
                            ""
                        }
                    }
                } catch (e: Exception) {
                    task.error(e)
                    log.error("Error in topic extraction", e)
                    ""
                }
                response + topicsText
            } catch (e: Exception) {
                log.error("Error in topic extraction", e)
                response
            }
        }
    }

    /**
     * Executes a list of functions, each appending to the target StringBuilder, potentially in parallel.
     */
    private fun runAll(function1s: List<(StringBuilder) -> Unit>, target: StringBuilder) {
        val fixedConcurrencyProcessor = FixedConcurrencyProcessor(pool, 4)
        function1s.map { function1 ->
            fixedConcurrencyProcessor.submit {
                function1(target)
            }
        }.forEach { it.get() }
    }

    private fun extractTopics(response: String, model: ChatInterface): Topics {
        val topicsParsedActor = ParsedAgent(
            resultClass = Topics::class.java,
            prompt = "Identify topics (i.e. all named entities grouped by type) in the following text:",
            model = model,
            temperature = temperature,
            name = "Topics",
            parsingChatter = fastModel,
        )
        return if (fastTopicParsing) {
            topicsParsedActor.getParser().apply(response)
        } else {
            topicsParsedActor.answer(listOf(response)).obj
        }
    }

    protected open fun chatMessages(): List<ModelSchema.ChatMessage> = synchronized(messagesLock) {
        if (chatMessages.isEmpty() || chatMessages.first().role != ModelSchema.Role.system) {
            listOf(sysMessage) + chatMessages
        } else {
            chatMessages
        }
    }

    data class Topics(
        val topics: Map<String, List<String>>? = emptyMap()
    )

    protected open fun expandTopics(userMessage: String): String {
        // Matches both @TopicType and @{Topic Type With Spaces}
        val topicReferencePattern =
            Regex("""@\{([A-Z][a-zA-Z0-9_ ]+)\}|@([A-Z][a-zA-Z0-9_]*)""")
        return topicReferencePattern.replace(userMessage) { matchResult -> // Read access needs synchronization
            // Group 1 is for delimited format @{Topic Type}, Group 2 is for simple format @TopicType
            val topicType = matchResult.groupValues[1].ifEmpty { matchResult.groupValues[2] }
            val topicList = aggregateTopics[topicType]
            val entities = synchronized(topicList ?: Any()) { // Synchronize on the list if it exists, or a dummy object
                topicList?.toList() // Create copy while holding lock
            }
            if (!entities.isNullOrEmpty()) { // Check if the copied list is not null or empty
                "@[${entities.joinToString("|")}]" // Use the copied list
            } else {
                matchResult.value
            }
        }
    }

    private fun processMsgRecursive(
        currentMessage: String,
        task: SessionTask,
        baseMessages: List<ModelSchema.ChatMessage>,
        transcriptStream: OutputStream? = null,
        model: ChatInterface
    ): List<(StringBuilder) -> Unit> {

        if (useExpansionSyntax) {
            val rangeMatch = rangeExpansionPattern.find(currentMessage)
            if (rangeMatch != null) {
                return expandRange(currentMessage, task, baseMessages, rangeMatch, transcriptStream)
            }

            val sequenceMatch = sequenceExpansionPattern.find(currentMessage)
            if (sequenceMatch != null) {
                return listOf { finalAggregate: StringBuilder ->
                    expandSequence(
                        task,
                        baseMessages,
                        sequenceMatch.groupValues[1].split(Regex("""\s*->\s*""")),
                        currentMessage,
                        sequenceMatch.value,
                        transcriptStream
                    )
                }
            }

            val match = expansionExpressionPattern.find(currentMessage)
            if (match != null && match.groupValues[1].split('|', ',').size > 1) {
                return expandAlternatives(
                    currentMessage,
                    task,
                    baseMessages,
                    match,
                    transcriptStream
                ) { msg, tsk, msgs ->
                    processMsgRecursive(msg, tsk, msgs, transcriptStream, this@ChatSocketManager.smartModel)
                }
            }
        }

        return listOf { aggregateResponse: StringBuilder ->
            task.add("")

            val finalMessages =
                baseMessages + ModelSchema.ChatMessage(ModelSchema.Role.user, currentMessage.toContentList())
            val responseRef = AtomicReference<String>()
            try {
                val chatResponse = model.chat(finalMessages)
                val choices = chatResponse.choices
                var responseText = choices.firstOrNull()?.message?.content.orEmpty()
                choices.forEach { choice ->
                    choice.message?.image_data?.let {
                        val imageMimeType = choice.message?.image_mime_type ?: "image/png"
                        val (link, file) = task.createFile(
                            UUID.randomUUID().toString() + when (imageMimeType) {
                                "image/png" -> ".png"
                                "image/jpeg", "image/jpg" -> ".jpg"
                                "image/gif" -> ".gif"
                                else -> ".img"
                            }
                        )
                        file?.writeBytes(it)
                        val imageLink =
                            """<a href='$link' target='_blank'><img src="$link" alt="Image" style="max-width: 300px;" ></a>"""
                        responseText += "\n\n" + imageLink
                    }
                }
                responseRef.set(responseText)
            } catch (e: Exception) {
                log.error("Error in API call", e)
                responseRef.set("Error: ${e.message}")
            }

            val response = responseRef.get() ?: "No response received"
            task.complete(renderResponse(response, task))
            aggregateResponse.append(response).append("\n\n")
            // Write intermediate responses to transcript if in expansion mode
            if (useExpansionSyntax && transcriptStream != null) {
                transcriptStream.write("### Expansion Result\n$response\n\n".transcriptFilter().toByteArray())
                transcriptStream.flush()
            }

        }
    }

    /**
     * Expands range expressions in the format [start...end:step]
     * Creates a sequence of numbers from start to end with the given step (default 1)
     */
    private fun expandRange(
        currentMessage: String,
        task: SessionTask,
        baseMessages: List<ModelSchema.ChatMessage>,
        rangeMatch: MatchResult,
        transcriptStream: OutputStream? = null
    ): List<(StringBuilder) -> Unit> = listOf { finalAggregate: StringBuilder ->
        val start = rangeMatch.groupValues[1].toInt()
        val end = rangeMatch.groupValues[2].toInt()
        val step = rangeMatch.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 1
        expandSequence(
            task,
            baseMessages,
            generateSequence(start) { it + step }
                .takeWhile { if (step > 0) it <= end else it >= end }
                .toList()
                .map { it.toString() },
            currentMessage,
            rangeMatch.value,
            transcriptStream
        )
    }

    /**
     * Expands alternative expressions in the format {option1|option2|option3}
     * Each option is processed in parallel
     */
    private fun expandAlternatives(
        currentMessage: String,
        task: SessionTask,
        baseMessages: List<ModelSchema.ChatMessage>,
        match: MatchResult,
        transcriptStream: OutputStream? = null,
        recursiveFn: (String, SessionTask, List<ModelSchema.ChatMessage>) -> List<(StringBuilder) -> Unit>
    ): List<(StringBuilder) -> Unit> {
        val tabs = TabbedDisplay(task, closable = useExpansionSyntax)
        return match.groupValues[1].split('|', ',').flatMap { option ->
            recursiveFn(
                currentMessage.replaceFirst(match.value, option),
                this.newTask(cancelable = false, root = false).apply { tabs[option] = placeholder },
                baseMessages.filter { it.content?.any { it.text?.contains(match.value) == true } != true }
            )
        }.apply {
            tabs.update()
        }
    }

    private fun expandSequence(
        task: SessionTask,
        baseMessages: List<ModelSchema.ChatMessage>,
        items: List<String>,
        currentMessage: String,
        expression: String,
        transcriptStream: OutputStream? = null
    ) {
        val aggregatedResponse = StringBuilder()
        val tabs = TabbedDisplay(task, closable = useExpansionSyntax)
        val messages = baseMessages.dropLast(1).toMutableList()
        for (item in items) {
            val newMessage = currentMessage.replaceFirst(expression, item)
            val subTaskFunctions = processMsgRecursive(
                currentMessage = newMessage,
                task = this.newTask(cancelable = false, root = false).apply { tabs[item] = placeholder },
                baseMessages = messages.filter { it.content?.any { it.text?.contains(expression) == true } != true },
                transcriptStream = transcriptStream,
                model = this@ChatSocketManager.smartModel
            )
            val subAggregate = StringBuilder()
            runAll(subTaskFunctions, subAggregate)
            aggregatedResponse.append("[").append(item).append("]\n").append(subAggregate.toString()).append("\n")
            messages.add(ModelSchema.ChatMessage(ModelSchema.Role.user, newMessage.toContentList()))
            messages.add(ModelSchema.ChatMessage(ModelSchema.Role.assistant, subAggregate.toString().toContentList()))
        }
        tabs.update()
    }

    open fun renderResponse(response: String, task: SessionTask) = """<div>${response.renderMarkdown(true)}</div>"""

    companion object {
        private val log = LoggerFactory.getLogger(ChatSocketManager::class.java)
    }
}

fun String.transcriptFilter() = this.let {
    Regex("""(href=|src=['"])?fileIndex/[A-Za-z0-9\-_]+/""").replace(it) { matchResult ->
        matchResult.groupValues[1]
    }
}

```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/session/SessionTask.kt

```
package com.simiacryptus.cognotik.webui.session


import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SocketManager.Companion.randomID
import java.awt.image.BufferedImage
import java.io.BufferedOutputStream
import java.util.*
import java.util.function.Consumer


open class SessionTask(
    val messageID: String = Session.long64(),
    private var buffer: MutableList<StringBuilder> = mutableListOf(),
    private val spinner: String = SessionTask.spinner,
    val ui: SocketManager
) {

    val placeholder: String get() = "<div message-id=\"$messageID\"></div>"

    private val currentText: String
        get() = buffer.toTypedArray().filter { it.isNotBlank() }.joinToString("")

    fun append(
        htmlToAppend: String,
        showSpinner: Boolean = true
    ): StringBuilder? {
        val stringBuilder: StringBuilder?
        if (htmlToAppend.isNotBlank()) {
            stringBuilder = StringBuilder("<div>$htmlToAppend</div>")
            buffer += stringBuilder
        } else {
            stringBuilder = null
        }
        send(currentText + if (showSpinner) "<div>$spinner</div>" else "")
        return stringBuilder
    }
    fun newLogStream(name: String = """API log"""): BufferedOutputStream {
        val relativePath = ".logs/api-${UUID.randomUUID()}.md"
        val (file, createFile) = Pair(
            linkTo(relativePath),
            resolveSystemFile(relativePath)
        )
        val buffered = createFile?.outputStream()?.buffered()
            ?: throw RuntimeException("Failed to create log file at path: $relativePath")
        buffered.write("API Logging Started\n".toByteArray())
        buffered.write("<details><summary>Stack Trace</summary>\n\n```text\n".toByteArray())
        Thread.currentThread().stackTrace.forEach { element ->
            buffered.write("  ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})\n".toByteArray())
        }
        buffered.write("```\n</details>\n".toByteArray())
        verbose("""<a href='${file.removeSuffix(".md")}.html' target='_blank'>$name</a>: <input type="text" value="${createFile.absolutePath}" id="file-path-${messageID}"/>""".trimMargin())
        return buffered
    }

    protected open fun send(
        html: String = currentText
    ) = ui.send(html)

    @Description("Saves the given data to a file and returns the url of the file.")
    open fun saveFile(
        @Description("The name of the file to save")
        relativePath: String,
        @Description("The data to save")
        data: ByteArray
    ): String {
        require(relativePath.isNotBlank()) { "File path cannot be blank" }
        require(!relativePath.contains("..")) { "Invalid file path: path traversal not allowed" }

        if (data.isEmpty()) {
            log.warn("Saving empty file at path: {}", relativePath)
        }

        log.debug("Saving file at path: {}", relativePath)

        ui.dataStorage?.getSessionDir(ui.owner, ui.sessionId)?.let { dir ->
            if (!dir.exists() && !dir.mkdirs()) {
                throw RuntimeException("Failed to create session directory: ${dir.absolutePath}")
            }
            val resolve = dir.resolve(relativePath)
            resolve.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    throw RuntimeException("Failed to create parent directory: ${parent.absolutePath}")
                }
            }
            resolve.writeBytes(data)
            log.info("Successfully saved file: {} ({} bytes)", relativePath, data.size)
        }
        return linkTo(relativePath)
    }

    @Description("Adds a message to the task output.")
    fun add(
        @Description("The message to add")
        message: String,
        @Description("Whether to show the spinner for the task (default: true)")
        showSpinner: Boolean = true,
        @Description("The html tag to wrap the message in (default: div)")
        tag: String = "div",
        @Description("Additional css class(es) to apply to the message")
        additionalClasses: String = "",
        @Description("Whether to render the message as markdown (default: false)")
        markdown: Boolean = false
    ) = append(
        """<$tag class="${
            (additionalClasses.split(" ").toSet() + setOf("response-message")).joinToString(" ")
        }">${if (markdown) message.renderMarkdown() else message}</$tag>""", showSpinner
    )

    @Description("Adds a hideable message to the task output.")
    fun hideable(
        @Description("The message to add")
        message: String,
        @Description("Whether to show the spinner for the task (default: true)")
        showSpinner: Boolean = true,
        @Description("The html tag to wrap the message in (default: div)")
        tag: String = "div",
        @Description("Additional css class(es) to apply to the message")
        additionalClasses: String = "",
        @Description("Whether to render the message as markdown (default: false)")
        markdown: Boolean = false
    ): StringBuilder? {
        var windowBuffer: StringBuilder? = null
        val closeButton = """<span class="close">${
            ui.hrefLink(
                "&times;",
                "close-button href-link",
                null,
                oneAtATime { it: Unit ->
                    windowBuffer?.clear()
                    send()
                })
        }</span>"""
        windowBuffer = append(
            """<$tag class="${
                (additionalClasses.split(" ").toSet() + setOf("response-message", "hideable-message")).joinToString(" ")
            }">$closeButton${if (markdown) message.renderMarkdown() else message}</$tag>""",
            showSpinner
        )
        return windowBuffer
    }

    @Description("Echos a user message to the task output.")
    fun echo(
        @Description("The message to echo")
        message: String,
        @Description("Whether to show the spinner for the task (default: true)")
        showSpinner: Boolean = false,
        @Description("The html tag to wrap the message in (default: div)")
        tag: String = "div"
    ) = add(message, showSpinner, tag, "user-message", markdown = true)

    @Description("Adds a header to the task output.")
    fun header(
        @Description("The message to add")
        message: String,
        level: Int = 0,
        @Description("Whether to show the spinner for the task (default: true)")
        showSpinner: Boolean = true,
        additionalClasses: String = ""
    ) = add(
        message = message,
        showSpinner = showSpinner,
        tag = when {
            level <= 0 -> "div"
            level == 1 -> "h1"
            level == 2 -> "h2"
            level == 3 -> "h3"
            level == 4 -> "h4"
            level == 5 -> "h5"
            level == 6 -> "h6"
            else -> "div"
        },
        additionalClasses = additionalClasses.split(" ").toSet().plus("response-header").joinToString(" "),
        markdown = true
    )

    @Description("Adds an expandable/collapsible section to the task output.")
    fun expandable(
        @Description("The title displayed in the header")
        title: String,
        @Description("The content within the expandable section")
        content: String,
        @Description("Whether to show the spinner after adding (default: false)")
        showSpinner: Boolean = false,
        @Description("The html tag for the main container (default: div)")
        tag: String = "div",
        @Description("Additional css class(es) to apply to the main container")
        additionalClasses: String = "",
        @Description("Whether to render the content as markdown (default: true)")
        markdown: Boolean = true
    ) = renderExpandable(title, content, showSpinner, tag, additionalClasses, false, markdown)

    @Description("Adds an expandable/collapsible section to the task output.")
    fun expanded(
        @Description("The title displayed in the header")
        title: String,
        @Description("The content within the expandable section")
        content: String,
        @Description("Whether to show the spinner after adding (default: false)")
        showSpinner: Boolean = false,
        @Description("The html tag for the main container (default: div)")
        tag: String = "div",
        @Description("Additional css class(es) to apply to the main container")
        additionalClasses: String = "",
        @Description("Whether to render the content as markdown (default: true)")
        markdown: Boolean = true
    ) = renderExpandable(title, content, showSpinner, tag, additionalClasses, true, markdown)

    private fun renderExpandable(
        title: String,
        content: String,
        showSpinner: Boolean,
        tag: String,
        additionalClasses: String,
        isExpanded: Boolean,
        markdown: Boolean
    ): StringBuilder? {
        val combinedClasses =
            (additionalClasses.split(" ").toSet() + setOf("expandable-guide")).filter { it.isNotBlank() }
                .joinToString(" ")
        val renderedContent = if (markdown) content.renderMarkdown() else content
        val html = """
            <$tag class="$combinedClasses">
              <div class="expandable-header">
                <strong>$title</strong>
                <span class="expand-icon">▼</span>
              </div>
              <div class="expandable-content${if (isExpanded) " expanded" else ""}">${renderedContent}</div>
            </$tag>
        """.trimIndent()
        return append(html, showSpinner)
    }

    @Description("Adds a verbose message to the task output; verbose messages are hidden by default.")
    fun verbose(
        @Description("The message to add")
        message: String,
        @Description("Whether to show the spinner for the task (default: true)")
        showSpinner: Boolean = true,
        @Description("The html tag to wrap the message in (default: pre)")
        tag: String = "pre"
    ) = add(message, showSpinner, tag, "verbose")

    @Description("Displays an error in the task output.")
    fun error(
        @Description("The error to display")
        e: Throwable,
        @Description("Whether to show the spinner for the task (default: false)")
        showSpinner: Boolean = false,
        @Description("The html tag to wrap the message in (default: div)")
        tag: String = "div"
    ) = hideable(
        when {
            e is ValidatedObject.ValidationError -> """

**Data Validation Error**

""" + e.message + """

Stack Trace:

```text
""" + e.stackTraceTxt + """
```

"""

            e is FailedToImplementException -> "**Failed to Implement** \n\n${e.message}\n\nPrefix:\n```${e.language?.lowercase() ?: ""}\n${e.prefix}\n```\n\nImplementation Attempt:\n```${e.language?.lowercase() ?: ""}\n${e.code}\n```\n\n"

            else -> "**Error `${e.javaClass.name}`**\n\n```text\n${e.stackTraceToString()}\n```\n"

        }, showSpinner, tag, "error", markdown = true
    )

    @Description("Displays a final message in the task output. This will hide the spinner.")
    fun complete(
        @Description("The message to display")
        message: String = "",
        @Description("The html tag to wrap the message in (default: div)")
        tag: String = "div",
        @Description("Additional css class(es) to apply to the message")
        additionalClasses: String = ""
    ) = add(
        message = message,
        showSpinner = false,
        tag = tag,
        additionalClasses = (additionalClasses.split(" ").toSet() + setOf("completion-message")).joinToString(" "),
        markdown = true
    )

    @Description("Displays an image to the task output.")
    fun image(
        @Description("The image to display")
        image: BufferedImage
    ) = add("""<img src="${saveFile("images/${Session.long64()}.png", image.toPng())}" />""")

    fun newSession(session: Session = Session.newGlobalID(), appname: String = session.toString()): SocketManager {
        val linkedManager = ui.createLinkedManager(session)
        SessionProxyServer.agents[session] = linkedManager
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = appname,
            inputCnt = 1,
            stickyInput = false,
            loadImages = true,
            showMenubar = false,
        )
        return linkedManager
    }

    fun linkedTask(
        label: String,
        renderFn: (String) -> String = { """Processing ${it}...<br/>""" },
    ): SessionTask {
        val task = newSession(appname = label).newTask()
        add(renderFn(task.ui.linkToSession(label)))!!
        return task
    }

    companion object {
        val log = LoggerFactory.getLogger(SessionTask::class.java)

        const val spinner =
            """<div class="spinner-border" role="status"><span class="sr-only">Loading...</span></div>"""

        fun BufferedImage.toPng(): ByteArray {
            java.io.ByteArrayOutputStream().use { os ->
                javax.imageio.ImageIO.write(this, "png", os)
                return os.toByteArray()
            }
        }
    }

    fun createFile(relativePath: String) = Pair(linkTo(relativePath), resolveSystemFile(relativePath))

    fun linkTo(relativePath: String): String {
        require(relativePath.isNotBlank()) { "File path cannot be blank" }
        return "fileIndex/${ui.sessionId}/$relativePath"
    }

    fun resolveSystemFile(relativePath: String) = this.ui.resolveSystemFile(relativePath)

    fun resolveUserFile(relativePath: String) = this.ui.resolveUserFile(relativePath)

    fun update() = send()

    open fun hrefLink(
        linkText: String,
        classname: String = "href-link",
        id: String? = null,
        handler: Consumer<Unit>
    ): String {
        log.debug("Creating href link with text: {}", linkText)
        val operationID = randomID()
        ui.linkTriggers[operationID] = handler
        return """<a class="$classname" data-id="$operationID"${
            when {
                id != null -> """ id="$id""""
                else -> ""
            }
        }>$linkText</a>"""
    }

    fun newTask(showSpinner: Boolean = true): SessionTask {
        val newTask = ui.newTask(false)
        add(newTask.placeholder, showSpinner = showSpinner)
        return newTask
    }
}

val Throwable.stackTraceTxt: String
    get() {
        val sw = java.io.StringWriter()
        val pw = java.io.PrintWriter(sw)
        printStackTrace(pw)
        return sw.toString()
    }

fun ChatInterface.getChildClient(task: SessionTask): ChatInterface {
    val childClient = this.getChildClient()
    childClient.logStreams += task.newLogStream()
    return childClient
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/session/SmartChatSocketManager.kt

```
package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.toContentList
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.OutputStream

/**
 * A ChatSocketManager that features:
 * 1. History auto-summarization when conversation gets too long
 * 2. Query elevation from fast model to smart model for complex queries
 */
open class SmartChatSocketManager(
    session: Session,
    useExpansionSyntax: Boolean = true,
    smartModel: ChatInterface,
    fastModel: ChatInterface,
    userInterfacePrompt: String = "",
    override val systemPrompt: String,
    temperature: Double = 0.3,
    applicationClass: Class<out ChatServer>,
    storage: StorageInterface = ApplicationServices.fileApplicationServices().dataStorageFactory,
    override val fastTopicParsing: Boolean = true,
    retriable: Boolean = true,
    budget: Double,
    /**
     * Maximum number of tokens in conversation history before summarization is triggered
     */
    private val maxHistoryTokens: Int = 4000,
    /**
     * Target number of tokens after summarization
     */
    private val targetSummaryTokens: Int = 1000,
    /**
     * Number of recent messages to preserve without summarization
     */
    private val preserveRecentMessages: Int = 4,
) : ChatSocketManager(
    session = session,
    useExpansionSyntax = useExpansionSyntax,
    smartModel = smartModel,
    fastModel = fastModel,
    userInterfacePrompt = userInterfacePrompt,
    systemPrompt = systemPrompt,
    temperature = temperature,
    applicationClass = applicationClass,
    storage = storage,
    fastTopicParsing = fastTopicParsing,
    retriable = retriable,
    budget = budget,
) {

    private var conversationSummary: String? = null
    private val summaryLock = Any()

    data class QueryElevationDecision(
        @Description("Indicates whether the query should be elevated to the smart model, or left on the fast model")
        val shouldElevate: Boolean = false,
        @Description("Reason for the elevation decision")
        val reason: String = ""
    )

    override fun respond(
        task: SessionTask,
        userMessage: String,
        currentChatMessages: List<ModelSchema.ChatMessage>,
        transcriptStream: OutputStream?
    ): String {
        val fast = fastModel.getChildClient(task)
        val smart = smartModel.getChildClient(task)
        // Check if we need to summarize history
        val messagesForChat = maybeCompactHistory(currentChatMessages, task)

        // First, use fast model to determine if we should elevate to smart model
        val shouldElevate = checkQueryElevation(
            userMessage,
            messagesForChat,
            fast
        )

        val modelToUse = if (shouldElevate) {
            log.info("Elevating query to smart model: $userMessage")
            task.add("<div class='elevation-notice'>🧠 <em>Using advanced model for this query</em></div>")
            smart
        } else {
            fast
        }

        return buildString {
            val finalMessages = messagesForChat + ModelSchema.ChatMessage(
                ModelSchema.Role.user,
                userMessage.toContentList()
            )

            task.add("")
            try {
                val chatResponse = modelToUse.chat(finalMessages)
                val choices = chatResponse.choices
                var responseText = choices.firstOrNull()?.message?.content.orEmpty()

                choices.forEach { choice ->
                    choice.message?.image_data?.let {
                        val imageMimeType = choice.message?.image_mime_type ?: "image/png"
                        val (link, file) = task.createFile(
                          java.util.UUID.randomUUID().toString() + when (imageMimeType) {
                              "image/png" -> ".png"
                              "image/jpeg", "image/jpg" -> ".jpg"
                              "image/gif" -> ".gif"
                              else -> ".img"
                          }
                        )
                        file?.writeBytes(it)
                        val imageLink =
                            """<a href='$link' target='_blank'><img src="$link" alt="Image" style="max-width: 300px;" ></a>"""
                        responseText += "\n\n" + imageLink
                    }
                }

                append(responseText)
                task.complete(renderResponse(responseText, task))

                // Write to transcript
                transcriptStream?.write("## Assistant\n$responseText\n\n".transcriptFilter().toByteArray())
                transcriptStream?.flush()

            } catch (e: Exception) {
                log.error("Error in API call", e)
                val errorMsg = "Error: ${e.message}"
                append(errorMsg)
                task.error(e)
            }
        }.let { response ->
            // Extract topics as in parent class
            try {
                val answer = extractTopicsInternal(response, smart)
                val topicsText = try {
                    answer.topics?.let { topics ->
                        if (topics.isNotEmpty()) {
                            val joinToString = topics.entries.joinToString("\n") {
                                "* `{${it.key}}` - ${it.value.joinToString(", ") { "`$it`" }}"
                            }
                            task.complete(joinToString.renderMarkdown(), additionalClasses = "topics")
                            "\n\n$joinToString"
                        } else ""
                    } ?: ""
                } catch (e: Exception) {
                    task.error(e)
                    log.error("Error in topic extraction", e)
                    ""
                }
                response + topicsText
            } catch (e: Exception) {
                log.error("Error in topic extraction", e)
                response
            }
        }
    }

    private fun extractTopicsInternal(response: String, model: ChatInterface): Topics {
        val topicsParsedActor = ParsedAgent(
            resultClass = Topics::class.java,
            prompt = "Identify topics (i.e. all named entities grouped by type) in the following text:",
            model = model,
            temperature = temperature,
            name = "Topics",
            parsingChatter = fastModel,
        )
        return if (fastTopicParsing) {
            topicsParsedActor.getParser().apply(response)
        } else {
            topicsParsedActor.answer(listOf(response)).obj
        }
    }

    /**
     * Checks if the query should be elevated to the smart model
     */
    private fun checkQueryElevation(
        userMessage: String,
        currentMessages: List<ModelSchema.ChatMessage>,
        model: ChatInterface
    ) = try {
        val decision = ParsedAgent(
            resultClass = QueryElevationDecision::class.java,
            /* Note: this prompt is ignored due to getParser().apply */
            prompt = """
                Analyze the following user query and conversation context to determine if it requires advanced reasoning capabilities.
                
                Criteria for elevation to advanced model:
                - Complex multi-step reasoning or analysis
                - Code generation or debugging
                - Mathematical or scientific calculations
                - Creative writing requiring nuanced understanding
                - Questions requiring synthesis of multiple concepts
                - Ambiguous queries requiring careful interpretation
                
                Simple queries that DON'T need elevation:
                - Factual lookups
                - Simple clarifications
                - Greetings or casual conversation
                - Direct, straightforward questions
                
                User Query: $userMessage
                
                Recent conversation context (last few messages):
                ${
                currentMessages.takeLast(4)
                    .joinToString("\n") { "${it.role}: ${it.content?.firstOrNull()?.text?.take(200) ?: ""}" }
            }
            """.trimIndent(),
            model = model,
            temperature = 0.1,
            name = "QueryElevation",
            parsingChatter = model,
        ).getParser().apply(userMessage)
        log.debug("Elevation decision: shouldElevate=${decision.shouldElevate}, reason=${decision.reason}")
        decision.shouldElevate
    } catch (e: Exception) {
        log.warn("Error checking query elevation, defaulting to fast model", e)
        false
    }

    /**
     * Compacts conversation history by summarizing older messages if needed
     */
    private fun maybeCompactHistory(
        messages: List<ModelSchema.ChatMessage>,
        task: SessionTask
    ): List<ModelSchema.ChatMessage> {
        val estimatedTokens = estimateTokenCount(messages)

        if (estimatedTokens <= maxHistoryTokens) {
            return messages
        }

        log.info("Compacting history: estimated $estimatedTokens tokens exceeds max $maxHistoryTokens")

        synchronized(summaryLock) {
            // Separate system message from conversation
            val systemMessage = messages.firstOrNull { it.role == ModelSchema.Role.system }
            val conversationMessages = messages.filter { it.role != ModelSchema.Role.system }

            // Preserve recent messages
            val recentMessages = conversationMessages.takeLast(preserveRecentMessages)
            val messagesToSummarize = conversationMessages.dropLast(preserveRecentMessages)

            if (messagesToSummarize.isEmpty()) {
                return messages
            }

            // Generate summary of older messages
            val newSummary = generateSummary(messagesToSummarize, task)
            conversationSummary = if (conversationSummary != null) {
                // Combine with existing summary
                "$conversationSummary\n\nAdditional context:\n$newSummary"
            } else {
                newSummary
            }

            // Build new message list with summary
            val summaryMessage = ModelSchema.ChatMessage(
                ModelSchema.Role.system,
                """
                    ${systemMessage?.content?.firstOrNull()?.text ?: systemPrompt}
                    
                    Previous conversation summary:
                    $conversationSummary
                """.trimIndent().toContentList()
            )

            task.add("<div class='summary-notice'>📝 <em>Conversation history summarized</em></div>")

            return listOf(summaryMessage) + recentMessages
        }
    }

    /**
     * Generates a summary of the given messages
     */
    private fun generateSummary(
        messages: List<ModelSchema.ChatMessage>,
        task: SessionTask
    ): String {
        val conversationText = messages.joinToString("\n\n") { msg ->
            "${msg.role}: ${msg.content?.firstOrNull()?.text ?: ""}"
        }

        val summaryPrompt = """
            Summarize the following conversation, preserving:
            - Key topics discussed
            - Important decisions or conclusions
            - Any specific facts, numbers, or code mentioned
            - User preferences or requirements stated
            
            Keep the summary concise but comprehensive (target: ~$targetSummaryTokens tokens).
            
            Conversation:
            $conversationText
        """.trimIndent()

        return try {
            val summaryMessages = listOf(
                ModelSchema.ChatMessage(
                    ModelSchema.Role.system,
                    "You are a helpful assistant that creates concise conversation summaries.".toContentList()
                ),
                ModelSchema.ChatMessage(ModelSchema.Role.user, summaryPrompt.toContentList())
            )

            val response = fastModel.getChildClient(task).chat(summaryMessages)
            response.choices.firstOrNull()?.message?.content ?: "Unable to generate summary"
        } catch (e: Exception) {
            log.error("Error generating summary", e)
            "Previous conversation covered: ${messages.size} messages"
        }
    }

    /**
     * Estimates token count for messages (rough approximation: ~4 chars per token)
     */
    private fun estimateTokenCount(messages: List<ModelSchema.ChatMessage>): Int {
        return messages.sumOf { msg ->
            (msg.content?.sumOf { it.text?.length ?: 0 } ?: 0) / 4
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SmartChatSocketManager::class.java)
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/session/SocketManager.kt

```
package com.simiacryptus.cognotik.webui.session

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.threadPoolManager
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.chat.ChatSocket
import java.io.File
import java.io.OutputStream
import java.net.URLDecoder
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

abstract class SocketManager(
    val sessionId: Session,
    val dataStorage: StorageInterface = ApplicationServices.fileApplicationServices().dataStorageFactory,
    val owner: User = defaultUser,
    private val applicationClass: Class<*>,
) {
    private val messageStates = Collections.synchronizedMap(
        try {
            dataStorage?.getMessages(owner, sessionId) ?: LinkedHashMap()
        } catch (e: Exception) {
            log.error("Failed to load messages from storage for session: {}, using empty map", sessionId, e)
            LinkedHashMap()
        }
    )
    fun resolveSystemFile(relativePath: String): File? {
        require(relativePath.isNotBlank()) { "File path cannot be blank" }
        require(!relativePath.contains("..")) { "Invalid file path: path traversal not allowed" }
        return dataStorage?.getDataDir(
            owner,
            sessionId
        )?.let { dir ->
            if (!dir.exists() && !dir.mkdirs()) {
                throw RuntimeException("Failed to create session directory: ${dir.absolutePath}")
            }
            val resolve = dir.resolve(relativePath)
            resolve.parentFile?.let { parent ->
                if (!parent.exists()) {
                    if (!parent.mkdirs()) SessionTask.log.warn("Failed to create parent directory: {}", parent.absolutePath)
                }
            }
            SessionTask.log.debug("Successfully created file path: {}", resolve.absolutePath)
            resolve
        }
    }
    fun resolveUserFile(relativePath: String): File? {
        require(relativePath.isNotBlank()) { "File path cannot be blank" }
        require(!relativePath.contains("..")) { "Invalid file path: path traversal not allowed" }
        return dataStorage?.getSessionDir(
            owner,
            sessionId
        )?.let { dir ->
            val resolve = if (dir.exists()) {
                dir.resolve(relativePath).apply { parentFile.mkdirs() }
            } else {
                if (!dir.mkdirs()) {
                    throw RuntimeException("Failed to create directory: ${dir.absolutePath}")
                }
                val resolve = dir.resolve(relativePath)
                resolve.parentFile?.let { parent ->
                    if (!parent.exists()) {
                        if (!parent.mkdirs()) SessionTask.log.warn(
                            "Failed to create parent directory: {}",
                            parent.absolutePath
                        )
                    }
                }
                SessionTask.log.debug("Successfully created file path: {}", resolve.absolutePath)
                resolve
            }
            resolve
        }
    }
    @Suppress("unused")
    private val createdBy = Thread.currentThread().stackTrace
    private val messageTimestamps = HashMap<String, Long>()
    private val sockets: MutableMap<ChatSocket, org.eclipse.jetty.websocket.api.Session> = ConcurrentHashMap()
    private val sendQueues: MutableMap<ChatSocket, Deque<String>> = ConcurrentHashMap()
    private val queueProcessing: MutableSet<ChatSocket> = ConcurrentHashMap.newKeySet()
    private val messageVersions = ConcurrentHashMap<String, AtomicInteger>()
    val pool get() = threadPoolManager.getPool(sessionId, owner)
    val scheduledThreadPoolExecutor get() = threadPoolManager.getScheduledPool(sessionId, owner)

    fun removeSocket(socket: ChatSocket) {
        log.debug("Removing socket: {} (id: {})", socket, System.identityHashCode(socket))
        trafficLog.info(
            "Removing socket: {} (id: {}), user: {}",
            socket,
            System.identityHashCode(socket),
            socket.user?.name ?: "anonymous"
        )

        try {

            sendQueues.remove(socket) // Clean up the send queue
            sockets.remove(socket)
            queueProcessing.remove(socket)
        } catch (e: Exception) {
            log.error("Error during socket cleanup for socket: {}", socket, e)
            trafficLog.error("Error during socket cleanup: {}", e.message)
        }
        trafficLog.debug("Socket removed, remaining connections: {}", sockets.size)
    }

    fun addSocket(socket: ChatSocket, session: org.eclipse.jetty.websocket.api.Session) {

        val user = getUser(session)
        log.debug("Adding socket: {} (id: {}) for user: {}", socket, System.identityHashCode(socket), user)
        trafficLog.info(
            "Adding socket: {} (id: {}), user: {}, remote: {}",
            socket,
            System.identityHashCode(socket),
            user?.name ?: "anonymous",
            session.remoteAddress
        )

        if (!ApplicationServices.authorizationManager.isAuthorized(
                applicationClass = applicationClass,
                user = user,
                operationType = OperationType.Read
            )
        ) {
            log.warn(
                "Unauthorized access attempt from user: {}, remote: {}",
                user?.name ?: "anonymous",
                session.remoteAddress
            )
            throw IllegalArgumentException("Unauthorized")
        }

        try {
            sockets[socket] = session
            sendQueues[socket] = ConcurrentLinkedDeque()
        } catch (e: Exception) {
            log.error("Error adding socket for user: {}", user?.name ?: "anonymous", e)
            throw e
        }
        trafficLog.debug("Socket added, active connections: {}", sockets.size)
    }

    fun newTask(
        root: Boolean = true,
        cancelable: Boolean = false,
    ): SessionTask {
        try {
            val operationID = randomID(root)
            val responseContents = divInitializer(operationID, cancelable)
//            log.debug(
//                "Creating new task with operationID: {}\n\t{}",
//                operationID, Thread.currentThread().stackTrace.joinToString("\n\t")
//            )
            trafficLog.debug("Creating new task with operationID: {}", operationID)
            send(responseContents)
            return SessionTask(
                messageID = operationID,
                buffer = mutableListOf(StringBuilder(responseContents)),
                spinner = SessionTask.spinner,
                ui = this,
            ).apply {
                add("")
            }
        } catch (e: Exception) {
            log.error("Failed to create new task", e)
            trafficLog.error("Failed to create new task: {}", e.message)
            throw e
        }
    }

    open fun send(out: String) {
        if (out.isBlank()) {
            log.warn("Attempted to send an empty message")
            return
        }

        try {
            log.debug("Processing send message ({} bytes)", out.length)
            trafficLog.trace(
                "Processing send message ({} bytes): {}...",
                out.length,
                out.take(100) + (if (out.length > 100) "..." else "")
            )

            val split = out.split(',', ignoreCase = false, limit = 2)
            if (split.size < 2) {
                log.warn(
                    "Invalid message format ({} bytes), expected 'id,content' but got: {}",
                    out.length,
                    out.take(100)
                )
                trafficLog.warn("Invalid message format received, length: {} bytes", out.length)
                return
            }
            val messageID = split[0]
            if (messageID.isBlank()) {
                log.warn("Message ID cannot be blank")
                return
            }
            var newValue = split[1]
            if (newValue == "null") newValue = ""

            log.debug("Setting message - Key: {}, Content size: {} bytes", messageID, newValue.length)
            val version = setMessage(messageID, newValue)
            if (version < 0) {
                log.debug("Skipping duplicate message - Key: {}, Content size: {} bytes", messageID, newValue.length)
                return
            }
            if (newValue.isEmpty()) {
                log.debug("Skipping empty message - Key: {}, Content size: {} bytes", messageID, newValue.length)
                return
            }

            val (ver, v) = synchronized(stateLock) {
                val version = messageVersions[messageID]?.get()
                val value = messageStates[messageID]
                Pair(version, value)
            }

            trafficLog.debug(
                "Sending message - Key: {}, Version: {}, Content size: {} bytes",
                messageID, ver, v?.length ?: 0
            )

            sockets.keys.forEach { chatSocket ->
                try {
                    val deque = sendQueues.computeIfAbsent(chatSocket) { ConcurrentLinkedDeque() }
                    val queueMessage = "$messageID,$ver,$v"
                    deque.add(queueMessage)

                    log.trace(
                        "Queuing message for socket {} (id: {}): Key: {}, Queue message size: {} bytes",
                        chatSocket, System.identityHashCode(chatSocket), messageID, queueMessage.length
                    )

                    if (queueProcessing.add(chatSocket)) {
                        try {
                            ioPool.submit { processQueue(chatSocket) }
                        } catch (e: Exception) {
                            log.error(
                                "Failed to submit queue processing task for socket: {} (id: {})",
                                chatSocket, System.identityHashCode(chatSocket), e
                            )
                            queueProcessing.remove(chatSocket)
                        }
                    }
                } catch (e: Exception) {
                    log.error(
                        "Error preparing message for socket: {} (id: {})",
                        chatSocket, System.identityHashCode(chatSocket), e
                    )
                }
            }
        } catch (e: Exception) {
            log.error("Error in send method for session: {}", sessionId, e)
            trafficLog.error("Error in send method for session: {}, error: {}", sessionId, e.message)
        }
    }

    private fun processQueue(chatSocket: ChatSocket) {
        try {
            val deque = sendQueues[chatSocket] ?: return
            var msg: String?
            while (deque.poll().also { msg = it } != null) {
                val message = msg!!
                if (message.isBlank()) {
                    continue
                }

                val messageID = message.substringBefore(',')
                var processedMsg = message

                val maxMessageSize = 100000
                if (message.length > maxMessageSize) {
                    log.warn(
                        "Message too long - Key: {}, Content size: {} bytes, truncating to {} bytes",
                        messageID, message.length, maxMessageSize
                    )
                    processedMsg = message.substring(0, maxMessageSize)
                }

                try {
                    synchronized(chatSocket) {
                        if (!sockets.containsKey(chatSocket)) {
                            return
                        }
                        chatSocket.remote.sendString(processedMsg)
                    }
                } catch (e: Exception) {
                    log.error(
                        "Error sending message to socket: {} (id: {})",
                        chatSocket, System.identityHashCode(chatSocket), e
                    )
                    removeSocket(chatSocket)
                    return
                }
            }

            try {
                chatSocket.remote.flush()
            } catch (e: Exception) {
                log.error("Error flushing socket: {} (id: {})", chatSocket, System.identityHashCode(chatSocket), e)
                removeSocket(chatSocket)
            }
        } finally {
            queueProcessing.remove(chatSocket)
        }
    }

    private val stateLock = Any()


    fun getReplay(since: Long): List<String> {
        log.debug("Getting replay messages since: {}", since)
        trafficLog.debug("Getting replay messages since: {}", since)
        return synchronized(stateLock) {
            messageStates.entries
                .filter { (messageTimestamps[it.key] ?: 0L) > since }
                .map {
                    val version = messageVersions[it.key]?.get() ?: 1
                    "${it.key},$version,${it.value}"
                }
        }.also {
            val totalSize = it.sumOf { msg -> msg.length }
            trafficLog.debug("Returning {} replay messages, total size: {} bytes", it.size, totalSize)
        }
    }

    private fun setMessage(key: String, value: String) = synchronized(stateLock) {
        val existingValue = messageStates[key] ?: ""
        if (existingValue == value) {
            log.debug("Skipping update for key: {}, content is identical ({} bytes)", key, value.length)
            return@synchronized -1 // Message content is identical, do not update version or timestamp
        }
        if (existingValue.length == value.length) {
            log.debug("Odd update for key: {}, content length unchanged ({} bytes)", key, value.length)
        }
        try {
            log.debug("Updating message - Key: {}, Content size: {} bytes", key, value.length)
            dataStorage?.updateMessage(owner, sessionId, key, value)
            messageStates[key] = value // Using [] syntax for put
            messageTimestamps[key] = System.currentTimeMillis()
            messageVersions.getOrPut(key) { AtomicInteger(0) }.incrementAndGet()
        } catch (e: Exception) {
            log.error("Error updating message state for key: $key", e)
            trafficLog.error("Error updating message state for key: {}, error: {}", key, e.message)
            -1 // Return error code
        }
    }

    fun onWebSocketText(socket: ChatSocket, message: String) {

//        log.debug(
//            "Received WebSocket message ({} bytes): {} from socket: {} (id: {})",
//            message.length,
//            message,
//            socket,
//            System.identityHashCode(socket)
//        )

        val maxMessageLength = 1000000
        if (message.length > maxMessageLength) {
            log.warn(
                "Message too long from socket: {}, length: {} bytes, limit: {} bytes",
                socket, message.length, maxMessageLength
            )
            send("""${randomID()},<div class="error">Message too long (${message.length} bytes). Maximum allowed: $maxMessageLength bytes.</div>""")
            return
        }


        val trimmed = message.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            if (trimmed.contains("\"type\":\"pong\"")) {
                log.debug("Received heartbeat pong - updating heartbeat timestamp.")

                return
            }
            if (trimmed.contains("\"type\":\"ping\"") || trimmed.contains("\"type\":\"heartbeat\"")) {
                //log.debug("Received heartbeat ping - sending pong response.")
                try {
                    socket.remote.sendString("{\"type\":\"pong\"}")
                } catch (e: Exception) {
                    log.error(
                        "Error sending pong response to socket: {} (id: {})",
                        socket, System.identityHashCode(socket), e
                    )
                    removeSocket(socket)
                }
                return
            }
        }

        if (!canWrite(socket.user)) {
            log.warn(
                "Unauthorized message from socket: {} (id: {}), user: {}",
                socket, System.identityHashCode(socket), socket.user?.name ?: "anonymous"
            )
            send("""${randomID()},<div class="error">Unauthorized message</div>""")
            return
        }

        try {
            pool.submit { processUserMessage(message, socket) }
        } catch (e: Exception) {
            log.error(
                "Failed to submit message processing task for socket: {} (id: {})",
                socket, System.identityHashCode(socket), e
            )
            send("""${randomID()},<div class="error">Failed to process message: ${e.message}</div>""")
        }
    }

    private fun processUserMessage(message: String, socket: ChatSocket) {
        try {
            log.debug(
                "Processing user message from socket: {} (id: {}), size: {} bytes",
                socket,
                System.identityHashCode(socket),
                message.length
            )
            val opCmdPattern = """![a-z]{3,7},.*""".toRegex()
            if (opCmdPattern.matches(message)) {
                val commaIndex = message.indexOf(",")
                if (commaIndex == -1 || commaIndex == message.length - 1) {
                    log.warn("Invalid command format from socket: {} (id: {})", socket, System.identityHashCode(socket))
                    return
                }
                val id = message.substring(1, commaIndex)
                val code = message.substring(commaIndex + 1)
                if (id.isBlank()) {
                    log.warn("Empty command ID from socket: {} (id: {})", socket, System.identityHashCode(socket))
                    return
                }
                trafficLog.debug("Processing command - ID: {}, Code size: {} bytes, Code: {}", id, code.length, code)
                onCmd(id, code)
            } else {
                trafficLog.debug(
                    "Processing user message from socket: {} (id: {}), size: {} bytes",
                    socket,
                    System.identityHashCode(socket),
                    message.length
                )
                onRun(message, socket)
            }
        } catch (e: Throwable) {
            log.error(
                "Error processing message from socket: {} (id: {}), message: {}",
                socket,
                System.identityHashCode(socket),
                message.take(100),
                e
            )
            trafficLog.error(
                "Error processing message from socket: {} (id: {}), error: {}",
                socket,
                System.identityHashCode(socket),
                e.message
            )
            try {
                send("""${randomID()},<div class="error">${e.message ?: "Unknown error".renderMarkdown()}</div>""")
            } catch (sendError: Exception) {
                log.error("Failed to send error message", sendError)
            }
        }
    }

    open fun canWrite(user: User?) = ApplicationServices.authorizationManager.isAuthorized(
        applicationClass = applicationClass,
        user = user,
        operationType = OperationType.Write
    )

    val linkTriggers = mutableMapOf<String, Consumer<Unit>>()
    private val txtTriggers = mutableMapOf<String, Consumer<String>>()

    private fun onCmd(id: String, code: String) {
        require(id.isNotBlank()) { "Command ID cannot be blank" }
        require(code.isNotBlank()) { "Command code cannot be blank" }

        log.debug("Processing command - ID: {}, Code size: {} bytes, Code: {}", id, code.length, code)

        when {
            code == "link" -> {
                val consumer = linkTriggers.remove(id)
                    ?: throw IllegalArgumentException("No link handler found for ID: $id")
                trafficLog.debug("Executing link handler for ID: {}", id)
                consumer.accept(Unit)
            }

            code.startsWith("userTxt,") -> {
                val consumer = txtTriggers.remove(id)
                    ?: throw IllegalArgumentException("No input handler found for ID: $id")
                val text = code.substringAfter("userTxt,")
                val unencoded = try {
                    URLDecoder.decode(text, "UTF-8")
                } catch (e: Exception) {
                    log.error("Failed to decode user text for ID: {}", id, e)
                    text
                }
                trafficLog.debug(
                    "Executing text input handler for ID: {}, text size: {} bytes",
                    id, unencoded.length
                )
                consumer.accept(unencoded)
            }

            else -> {
                log.warn("Unknown command received: {} for ID: {}", code, id)
                throw IllegalArgumentException("Unknown command: $code")
            }
        }
    }

    fun hrefLink(
        linkText: String,
        classname: String = "href-link",
        id: String? = null,
        handler: Consumer<Unit>
    ): String {
        log.debug("Creating href link with text: {}", linkText)
        trafficLog.trace("Creating href link with text: {}", linkText)
        val operationID = randomID()
        linkTriggers[operationID] = handler
        return """<a class="$classname" data-id="$operationID"${
            when {
                id != null -> """ id="$id""""
                else -> ""
            }
        }>$linkText</a>"""
    }

    fun textInput(handler: Consumer<String>): String {

        log.debug("Creating text input")
        trafficLog.trace("Creating text input field")
        val operationID = randomID()
        txtTriggers[operationID] = handler

        return """<div class="reply-form">
                   <textarea class="reply-input" data-id="$operationID" rows="3" placeholder="Type a message"></textarea>
                   <button class="text-submit-button" data-id="$operationID">Send</button>
               </div>""".trimIndent()
    }

    /**
     * Creates a linked SocketManager for a new session that shares the same configuration
     * but operates independently with its own message state and socket connections.
     */
    open fun createLinkedManager(newSession: Session): SocketManager {
        log.debug("Creating linked manager for session: {}", newSession)
        trafficLog.info("Creating linked manager for session: {}, owner: {}", newSession, owner?.name ?: "anonymous")
        return ReadonlySocketManager(newSession, dataStorage, owner, applicationClass)
    }


    protected abstract fun onRun(
        userMessage: String,
        socket: ChatSocket,
    )

    fun getActiveSockets(): List<ChatSocket> {
        log.debug("Getting active sockets, count: {}", sockets.size)
        trafficLog.debug("Getting active sockets, count: {}", sockets.size)
        return try {
            sockets.keys.toList()
        } catch (e: Exception) {
            log.error("Error getting active sockets", e)
            emptyList()
        }
    }


    fun linkToSession(label: String): String =
        """<a href="#${sessionId}" target="_blank" class="linked-task-link">${label}</a>"""

    companion object {
        private val log = LoggerFactory.getLogger(SocketManager::class.java)
        private val trafficLog = LoggerFactory.getLogger("TRAFFIC.com.simiacryptus.cognotik.webui.session")
        private val ioPool = Executors.newCachedThreadPool()
        private val range1 = ('a'..'y').toList().toTypedArray()
        private val range2 = range1 + 'z'

        fun randomID(root: Boolean = true): String {
            val random = Random()
            val joinToString = (if (root) range1[random.nextInt(range1.size)] else "z").toString() +
                    (0..4).map { range2[random.nextInt(range2.size)] }.joinToString("")
            return joinToString
        }

        fun divInitializer(operationID: String = randomID(), cancelable: Boolean): String =
            if (!cancelable) """$operationID,""" else
                """$operationID,<button class="cancel-button" data-id="$operationID">&times;</button>"""

        fun getUser(session: org.eclipse.jetty.websocket.api.Session): User {
            log.debug("Getting user from session: {}", session)
            trafficLog.trace("Getting user from session: {}", session.remoteAddress)
            return try {
                ApplicationServices.authenticationManager.getUser(
                    session.upgradeRequest?.cookies
                        ?.find { it.name == AuthenticationInterface.AUTH_COOKIE }
                        ?.value)
            } catch (e: Exception) {
                log.error("Error getting user from session", e)
                defaultUser
            }
        }
    }
}


class ReadonlySocketManager(
    newSession: Session,
    storageInterface: StorageInterface = ApplicationServices.fileApplicationServices().dataStorageFactory,
    owner: User = defaultUser,
    clazz: Class<*>
) : SocketManager(
    sessionId = newSession,
    dataStorage = storageInterface,
    owner = owner,
    applicationClass = clazz
) {
    override fun onRun(userMessage: String, socket: ChatSocket) {
        throw UnsupportedOperationException("onRun not implemented in linked manager")
    }

    override fun canWrite(user: User?): Boolean {
        return false
    }

}
class ServerlessSocketManager(
    session: Session,
    val messageEvents: OutputStream? = null,
    storageInterface: StorageInterface = ApplicationServices.fileApplicationServices().dataStorageFactory,
    owner: User = defaultUser,
    clazz: Class<*>
) : SocketManager(
    sessionId = session,
    dataStorage = storageInterface,
    owner = owner,
    applicationClass = clazz
) {
    override fun onRun(userMessage: String, socket: ChatSocket) {
        // No-op for serverless execution
    }

    override fun send(out: String) {
        super.send(out)
        messageEvents?.write((out + "\n").toByteArray())
        messageEvents?.flush()
    }

    override fun createLinkedManager(newSession: Session): SocketManager {
        return ServerlessSocketManager(newSession, messageEvents, dataStorage, owner, javaClass)
    }
}
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

### webui/src/main/kotlin/com/simiacryptus/cognotik/webui/session/README.md
```markdown
# Web UI Chat and Session Management

This package provides the core infrastructure for building interactive, LLM-powered chat applications with a web-based user interface. It handles WebSocket communication, session persistence, complex task management, and advanced chat features like query expansion and history summarization.

## Key Components

### Server Infrastructure
- **`ChatServer`**: An abstract base class for Jetty-based web servers. It configures the necessary servlets (WebSocket, session management, and static resources) and manages the lifecycle of user sessions.
- **`ChatSocket`**: A WebSocket adapter that handles real-time communication between the client and the server. It supports message replay upon reconnection to ensure a seamless user experience.

### Session and Task Management
- **`SocketManager`**: The central coordinator for a single user session. It manages multiple WebSocket connections, maintains message state with versioning, and handles command processing (e.g., button clicks, text input).
- **`SessionTask`**: Represents a specific unit of work or a message block within a session. It provides a rich API for updating the UI, including:
    - Markdown rendering
    - Expandable/collapsible sections
    - File uploads and downloads
    - Error reporting with stack traces
    - Progress indicators (spinners)
    - Interactive elements like links and text inputs

### Chat Logic
- **`ChatSocketManager`**: Specializes `SocketManager` for LLM interactions. It manages the conversation history, performs topic extraction, and implements a powerful **Query Expansion Syntax**.
- **`SmartChatSocketManager`**: An advanced version of the chat manager that adds:
    - **History Summarization**: Automatically compacts long conversations when token limits are approached to maintain context without exceeding model windows.
    - **Query Elevation**: Uses a fast, inexpensive model to analyze queries and decide if they require the advanced reasoning capabilities of a "smart" model.
- **`BasicChatApp`**: A concrete implementation of a chat application that integrates models, settings, and session management into a deployable server.

## Advanced Features

### Query Expansion Syntax
The `ChatSocketManager` supports a unique syntax for generating complex, multi-part prompts:
- **Parallel Expansion**: `@[option1|option2]` runs the prompt for each option in parallel.
- **Sequence Expansion**: `@{step1 -> step2}` feeds the output of one step into the next.
- **Range Expansion**: `@(1..10:2)` iterates over a numeric range.
- **Topic Reference**: `@TopicType` automatically inserts previously identified entities (e.g., `@Person`).

### Topic Extraction
As the conversation progresses, the system uses LLMs to identify and categorize named entities (topics). These topics are aggregated and can be referenced in subsequent prompts using the expansion syntax, enabling context-aware interactions.

### Persistence and Replay
All messages and UI updates are persisted via a `StorageInterface`. When a client reconnects or refreshes, the `SocketManager` uses message timestamps and versions to replay the session state, ensuring no data is lost.

## Implementation Details

- **Concurrency**: Uses dedicated thread pools for API calls and UI updates to keep the interface responsive.
- **Extensibility**: The architecture is highly modular, allowing for custom `SocketManager` implementations or specialized `SessionTask` behaviors.
- **Logging**: Includes detailed traffic logging for debugging WebSocket communication and API interactions.
```

The `README.md` has been created to document the classes and functionality within the `com.simiacryptus.cognotik.webui.chat` (and `session`) package. It highlights the server infrastructure, session management, and advanced chat features like query expansion and smart history management. No follow-up actions are required as this is a documentation update.
</details>

                - <a href='fileIndex/G-20260120-6kpA/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/session/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/webui/session/README.md</a> Updated

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-6kpA/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/session/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/webui/session/README.md</a> Updated
