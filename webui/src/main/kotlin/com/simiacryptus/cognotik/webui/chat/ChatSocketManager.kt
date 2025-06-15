package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.util.FixedConcurrencyProcessor
import com.simiacryptus.cognotik.util.Retryable.Companion.retryable
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManagerBase
import com.simiacryptus.cognotik.webui.session.getChildClient
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

open class ChatSocketManager(
    session: Session,
    var model: ChatModel,
    var parsingModel: ChatModel,
    val userInterfacePrompt: String = """
    <div class="expandable-guide">
      <div class="expandable-header">
        <strong>Query Expansion Syntax Guide</strong>
        <span class="expand-icon">▼</span>
      </div>
      <div class="expandable-content">
        <p>You can use the following syntaxes in your messages to automatically expand your queries:</p>

        <h4 class="expandable-section-title">Parallel Expansion</h4>
        <p class="expandable-description">Use <code>{option1|option2|option3}</code> to run the same prompt with each option in parallel.</p>
        <p class="expandable-example"><em>Example:</em> <code>Tell me a joke about {cats|dogs|birds}</code></p>

        <h4 class="expandable-section-title">Sequence Expansion</h4>
        <p class="expandable-description">Use <code>&lt;step1;step2;step3&gt;</code> to run a sequence of prompts, where the output of each feeds into the next.</p>
        <p class="expandable-example"><em>Example:</em> <code>Summarize this text, then &lt;translate to French;translate to German&gt;</code></p>

        <h4 class="expandable-section-title">Range Expansion</h4>
        <p class="expandable-description">Use <code>[[start..end:step]]</code> to iterate over a range of numbers.</p>
        <p class="expandable-example"><em>Example:</em> <code>Project an alternate history where Rome never fell. Tell what happened in [[1000..1500:100]]</code></p>

        <h4 class="expandable-section-title">Topic Reference Expansion</h4>
        <p class="expandable-description">Use <code>{topicType}</code> to refer to previously identified topics.</p>
        <p class="expandable-example"><em>Example:</em> <code>Tell me about {Person}</code></p>

        <p class="expandable-footer">You can combine these syntaxes for more complex expansions.</p>
      </div>
    </div>
  """.trimIndent(),
    open val initialAssistantPrompt: String = "",
    open val systemPrompt: String,
    var api: ChatClient,
    var temperature: Double = 0.3,
    applicationClass: Class<out ChatServer>,
    val storage: StorageInterface?,
    open val fastTopicParsing: Boolean = true,
    val retriable: Boolean = true,
    val budget: Double,
) : SocketManagerBase(session, storage, owner = null, applicationClass = applicationClass) {

    private val aggregateTopics = ConcurrentHashMap<String, MutableList<String>>()
    private val messagesLock = Any()

    init {
        if (userInterfacePrompt.isNotBlank()) {
            val task = newTask(false, true)
            task.complete(userInterfacePrompt)
        }
    }

    val sysMessage: ApiModel.ChatMessage
        get() = ApiModel.ChatMessage(ApiModel.Role.system, systemPrompt.toContentList())
    protected val chatMessages = mutableListOf<ApiModel.ChatMessage>()
    protected val messages: List<ApiModel.ChatMessage> get() = synchronized(messagesLock) {
        // Ensure the system message is always first
        if (chatMessages.isEmpty() || chatMessages.first().role != ApiModel.Role.system) {
            listOf(sysMessage) + chatMessages
        } else {
            chatMessages
        }
    }
    val ui = ApplicationInterface(this)

    override fun onRun(userMessage: String, socket: ChatSocket) {

        val expandedUserMessage = expandTopics(userMessage)

        val task = newTask()
        val api = api.getChildClient(task)
        task.echo(renderResponse(expandedUserMessage, task))

        synchronized(messagesLock) {
            chatMessages += ApiModel.ChatMessage(ApiModel.Role.user, expandedUserMessage.toContentList())
        }

        try {
            if (!retriable) {
                task.add("")
                val responseString = respond(api, task, expandedUserMessage, chatMessages())
                synchronized(messagesLock) {
                    if (messages.lastOrNull()?.role == ApiModel.Role.assistant) {
                        chatMessages.removeAt(messages.size - 1)
                    }
                    chatMessages += ApiModel.ChatMessage(ApiModel.Role.assistant, responseString.toContentList())
                }
                task.complete()
            } else {
                val currentChatMessages = chatMessages()
                retryable(ui, pool, task) { task ->
                    subquery(task, api, expandedUserMessage, currentChatMessages)
                }
            }
        } catch (e: Exception) {
            log.info("Error in chat", e)
            task.error(ApplicationInterface(this), e)
        }
    }

    private fun subquery(
        task: SessionTask,
        api: ChatClient,
        expandedUserMessage: String,
        currentChatMessages: List<ApiModel.ChatMessage>
    ) {
        try {
            task.add("")
            val responseString = respond(api, task, expandedUserMessage, currentChatMessages)
            synchronized(messagesLock) {
                if (messages.lastOrNull()?.role == ApiModel.Role.assistant) {
                    chatMessages.removeAt(messages.size - 1)
                }
                chatMessages += ApiModel.ChatMessage(
                    ApiModel.Role.assistant,
                    responseString.toContentList()
                )
            }
            task.complete()
        } catch (e: Throwable) {
            log.warn("Exception occurred while processing chat message", e)
        }
    }

    private val idSubPattern = """[^|\n,/\\;}{\]\[><]+""" // Matches any valid identifier character except for special characters used in the expansion syntax
    private val expansionExpressionPattern = Regex("""\{($idSubPattern(?:[|,]$idSubPattern)*)}""") // Matches

    private val sequenceExpansionPattern = Regex("""<($idSubPattern(?:$idSubPattern)+)>""") // Matches <item1;item2;item3>

    private val rangeExpansionPattern = Regex("""\[\[(\d+)(?:\.{2,3}| to )(\d+)(?:(?::| by )(\d+))?]]""") // Matches [[start..end:step]] or [[start to end by step]]

    protected open fun respond(
        api: ChatClient, task: SessionTask, userMessage: String, currentChatMessages: List<ApiModel.ChatMessage>
    ) = buildString {
        val function1List = processMsgRecursive(api, userMessage, task, currentChatMessages)
        runAll(function1List, this)
    }.let { response ->
        try {
            val answer = extractTopics(api, response)
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
                log.error("Error in topic extraction", e)
                ""
            }
            response + topicsText
        } catch (e: Exception) {
            task.error(null, e)
            log.error("Error in topic extraction", e)
            response
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

    private fun extractTopics(api: ChatClient, response: String): Topics {
        val topicsParsedActor = ParsedActor(
            resultClass = Topics::class.java,
            prompt = "Identify topics (i.e. all named entities grouped by type) in the following text:",
            model = model,
            temperature = temperature,
            name = "Topics",
            parsingModel = parsingModel,
        )
        return if (fastTopicParsing) {
            topicsParsedActor.getParser(api).apply(response)
        } else {
            topicsParsedActor.answer(listOf(response), api).obj
        }
    }

    protected open fun chatMessages(): List<ApiModel.ChatMessage> = synchronized(messagesLock) {
        // Return a snapshot of messages, ensuring the system prompt is first
        listOf(messages.first()) + messages.drop(1)
    }

    data class Topics(
        val topics: Map<String, List<String>>? = emptyMap()
    )

    protected open fun expandTopics(userMessage: String): String {
        val topicReferencePattern = Regex("""\{([^}|]+)}""")
        return topicReferencePattern.replace(userMessage) { matchResult -> // Read access needs synchronization
            val topicType = matchResult.groupValues[1] // Synchronize read access to aggregateTopics
            val topicList = aggregateTopics[topicType]
            val entities = synchronized(topicList ?: Any()) { // Synchronize on the list if it exists, or a dummy object
                topicList?.toList() // Create copy while holding lock
            }
            if (!entities.isNullOrEmpty()) { // Check if the copied list is not null or empty
                "{${entities.joinToString("|")}}" // Use the copied list
            } else {
                matchResult.value
            }
        }
    }

    private fun processMsgRecursive(
        api: ChatClient,
        currentMessage: String,
        task: SessionTask,
        baseMessages: List<ApiModel.ChatMessage>,
    ): List<(StringBuilder) -> Unit> {

        val rangeMatch = rangeExpansionPattern.find(currentMessage)
        if (rangeMatch != null) {
            return expandRange(api, currentMessage, task, baseMessages, rangeMatch)
        }
        val sequenceMatch = sequenceExpansionPattern.find(currentMessage)
        if (sequenceMatch != null) {
            return expandSequences(api, currentMessage, task, baseMessages, sequenceMatch)
        }

        val match = expansionExpressionPattern.find(currentMessage)
        if (match != null) {
            return expandAlternatives(api, currentMessage, task, baseMessages, match, this::processMsgRecursive)
        }

        return listOf { aggregateResponse: StringBuilder ->
            task.add("")

            val finalMessages = baseMessages + ApiModel.ChatMessage(ApiModel.Role.user, currentMessage.toContentList())
            val responseRef = AtomicReference<String>()
            try {
                val chatResponse = api.chat(
                    ApiModel.ChatRequest(
                        messages = finalMessages,
                        temperature = temperature,
                        model = model.modelName,
                    ), model
                )
                val newValue = chatResponse.choices.firstOrNull()?.message?.content.orEmpty()
                responseRef.set(newValue)
            } catch (e: Exception) {
                log.error("Error in API call", e)
                responseRef.set("Error: ${e.message}")
            }

            val response = responseRef.get() ?: "No response received"
            task.complete(renderResponse(response, task))
            aggregateResponse.append(response).append("\n\n")

        }
    }

    /**
     * Expands range expressions in the format [start...end:step]
     * Creates a sequence of numbers from start to end with the given step (default 1)
     */
    private fun expandRange(
        api: ChatClient,
        currentMessage: String,
        task: SessionTask,
        baseMessages: List<ApiModel.ChatMessage>,
        rangeMatch: MatchResult
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
            api
        )
    }

    /**
     * Expands alternative expressions in the format {option1|option2|option3}
     * Each option is processed in parallel
     */
    private fun expandAlternatives(
        api: ChatClient,
        currentMessage: String,
        task: SessionTask,
        baseMessages: List<ApiModel.ChatMessage>,
        match: MatchResult,
        recursiveFn: (ChatClient, String, SessionTask, List<ApiModel.ChatMessage>) -> List<(StringBuilder) -> Unit>
    ): List<(StringBuilder) -> Unit> {
        val tabs = TabbedDisplay(task, closable = false)
        return match.groupValues[1].split('|', ',').flatMap { option ->
            val newConversation = currentMessage.replaceFirst(match.value, option)
            val task = ui.newTask(false).apply { tabs[option] = placeholder }
            val filteredMessages = baseMessages.filter { it.content?.any { it.text?.contains(match.value) == true } != true }
            recursiveFn(api, newConversation, task, filteredMessages)
        }.apply {
            tabs.update()
        }
    }

    /**
     * Expands sequence expressions in the format <item1;item2;item3>
     * Each item is processed sequentially, with the output of each step feeding into the next.
     * Returns a single function that encapsulates the entire sequential process.
     */
    private fun expandSequences(
        api: ChatClient,
        currentMessage: String,
        task: SessionTask,
        baseMessages: List<ApiModel.ChatMessage>,
        sequenceMatch: MatchResult
    ) = listOf { finalAggregate: StringBuilder ->
        expandSequence(
            task,
            baseMessages,
            sequenceMatch.groupValues[1].split(';'),
            currentMessage,
            sequenceMatch.value,
            api
        )
    }

    private fun expandSequence(
        task: SessionTask,
        baseMessages: List<ApiModel.ChatMessage>,
        items: List<String>,
        currentMessage: String,
        expression: String,
        api: ChatClient
    ) {
        val aggregatedResponse = StringBuilder()
        val tabs = TabbedDisplay(task, closable = false)
        val messages = baseMessages.dropLast(1).toMutableList()
        for (item in items) {
            val newMessage = currentMessage.replaceFirst(expression, item)
            val subTaskFunctions = processMsgRecursive(
                api = api,
                currentMessage = newMessage,
                task = ui.newTask(false).apply { tabs[item] = placeholder },
                baseMessages = messages.filter { it.content?.any { it.text?.contains(expression) == true } != true }
            )
            val subAggregate = StringBuilder()
            runAll(subTaskFunctions, subAggregate)
            aggregatedResponse.append("[").append(item).append("]\n").append(subAggregate.toString()).append("\n")
            messages.add(ApiModel.ChatMessage(ApiModel.Role.user, newMessage.toContentList()))
            messages.add(ApiModel.ChatMessage(ApiModel.Role.assistant, subAggregate.toString().toContentList()))
        }
        tabs.update()
    }

    open fun renderResponse(response: String, task: SessionTask) =
        """<div>${response.renderMarkdown()}</div>"""

    companion object {
        private val log = LoggerFactory.getLogger(ChatSocketManager::class.java)
    }
}
