package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.actors.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.util.FixedConcurrencyProcessor
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.Retryable.Companion.retryable
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.toContentList
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference


open class ChatSocketManager(
    session: Session,
    var useExpansionSyntax: Boolean = true,
    var model: ChatInterface,
    var parsingModel: ChatInterface,
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
    val storage: StorageInterface?,
    open val fastTopicParsing: Boolean = true,
    val retriable: Boolean = true,
    val budget: Double,
) : SocketManager(session, storage, owner = null, applicationClass = applicationClass) {

    private val aggregateTopics = ConcurrentHashMap<String, MutableList<String>>()
    private val messagesLock = Any()

    init {
        if (userInterfacePrompt.isNotBlank()) {
            newTask().complete(userInterfacePrompt)
        }
    }

    val sysMessage: ModelSchema.ChatMessage
        get() {
            return ModelSchema.ChatMessage(ModelSchema.Role.system, systemPrompt.toContentList())
        }
    protected val chatMessages = mutableListOf<ModelSchema.ChatMessage>()

    val markdownTranscript by lazy { transcript() }

    override fun onRun(userMessage: String, socket: ChatSocket) {

        val expandedUserMessage = expandTopics(userMessage)
        markdownTranscript?.write("## User\n$expandedUserMessage\n\n".toByteArray())
        val task = newTask()
        task.echo(renderResponse(expandedUserMessage, task))

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
                retryable(task.manager, pool, task) { task ->
                    chatMessages.takeLastWhile { it.role == ModelSchema.Role.assistant }
                        .forEach { chatMessages.remove(it) }
                    val currentChatMessages = chatMessages()
                    innerRun(task, expandedUserMessage, currentChatMessages, markdownTranscript)
                }
            }
        } catch (e: Exception) {
            log.info("Error in chat", e)
            task.error(e)
        }
    }

    private fun transcript(): FileOutputStream? {
        val task = newTask()
        val (link, file) = task.createFile("transcript.md")
        val markdownTranscript = file?.outputStream()
        task.complete(
            "Writing transcript to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
                link.removeSuffix(
                    ".md"
                )
            }.pdf' target='_blank'>pdf</a>"
        )
        return markdownTranscript
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
        val model = model.getChildClient(task)
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
            transcriptStream?.write("## Assistant\n$response\n\n".toByteArray())
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
            parsingModel = parsingModel,
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
        val topicReferencePattern =
            Regex("""@([A-Z][a-zA-Z0-9_]*)""") // Matches @TopicType (must start with capital letter)
        return topicReferencePattern.replace(userMessage) { matchResult -> // Read access needs synchronization
            val topicType = matchResult.groupValues[1] // Synchronize read access to aggregateTopics
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
                    processMsgRecursive(msg, tsk, msgs, transcriptStream, this@ChatSocketManager.model)
                }
            }
        }

        return listOf { aggregateResponse: StringBuilder ->
            task.add("")

            val finalMessages = baseMessages + ModelSchema.ChatMessage(ModelSchema.Role.user, currentMessage.toContentList())
            val responseRef = AtomicReference<String>()
            try {
                val chatResponse = model.chat(finalMessages)
                val newValue = chatResponse.choices.firstOrNull()?.message?.content.orEmpty()
                responseRef.set(newValue)
            } catch (e: Exception) {
                log.error("Error in API call", e)
                responseRef.set("Error: ${e.message}")
            }

            val response = responseRef.get() ?: "No response received"
            task.complete(renderResponse(response, task))
            aggregateResponse.append(response).append("\n\n")
            // Write intermediate responses to transcript if in expansion mode
            if (useExpansionSyntax && transcriptStream != null) {
                transcriptStream.write("### Expansion Result\n$response\n\n".toByteArray())
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
                model = this@ChatSocketManager.model
            )
            val subAggregate = StringBuilder()
            runAll(subTaskFunctions, subAggregate)
            aggregatedResponse.append("[").append(item).append("]\n").append(subAggregate.toString()).append("\n")
            messages.add(ModelSchema.ChatMessage(ModelSchema.Role.user, newMessage.toContentList()))
            messages.add(ModelSchema.ChatMessage(ModelSchema.Role.assistant, subAggregate.toString().toContentList()))
        }
        tabs.update()
    }

    open fun renderResponse(response: String, task: SessionTask) =
        """<div>${response.renderMarkdown()}</div>"""

    companion object {
        private val log = LoggerFactory.getLogger(ChatSocketManager::class.java)
    }
}