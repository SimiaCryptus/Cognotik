package com.simiacryptus.cognotik.apps.general

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.FixedConcurrencyProcessor
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * A unified application that can use different cognitive modes based on configuration.
 * This allows for switching between different planning and execution strategies.
 */
abstract class UnifiedPlanApp(
    path: String,
    applicationName: String = "Unified Planning App",
    showMenubar: Boolean = true,
    val useExpansionSyntax: Boolean = true,
) : ApplicationServer(
    applicationName = applicationName,
    path = path,
    showMenubar = showMenubar,
    root = dataStorageRoot,
) {
    private val log = LoggerFactory.getLogger(UnifiedPlanApp::class.java)

    // Updated expansion patterns to match ChatSocketManager
    private val idSubPattern = """[^|\n,/\\;}\]\[><()@]+"""
    private val expansionExpressionPattern = Regex("""@\[($idSubPattern(?:[|,]$idSubPattern)+)]""")
    private val sequenceExpansionPattern = Regex("""@\{([^}]+(?:\s*->\s*[^}]+)+)\}""")
    private val rangeExpansionPattern = Regex("""@\((-?\d+)(?:\.{2,3}| to )(-?\d+)(?:(?::| by )(\d+))?\)""")
    private val topicReferencePattern = Regex("""@([A-Z][a-zA-Z0-9_]*)""")

    private val expansionPool = Executors.newFixedThreadPool(4)
    private val aggregateTopics = ConcurrentHashMap<String, MutableList<String>>()
    override val stickyInput = true
    override val inputCnt = 4
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T = OrchestrationConfig() as T

    abstract fun instance(model: ApiChatModel): ChatInterface

    override fun newSession(
        user: User?,
        session: Session
    ): SocketManager {
        val socketManager = super.newSession(user, session)
        val settings = getSettings(session, user, OrchestrationConfig::class.java)
        if (useExpansionSyntax) {
            socketManager.newTask(cancelable = false, root = true).expandable(
                "Query Expansion Syntax Guide", """
                <div class="expandable-guide">
                  <p>You can use the following syntaxes in your messages to automatically expand your queries:</p>
                  <h4 class="expandable-section-title">Parallel Expansion</h4>
                  <p class="expandable-description">Use <code>@[option1|option2|option3]</code> to run the same prompt with each option in parallel.</p>
                  <p class="expandable-example"><em>Example:</em> <code>Analyze the performance of @[React|Vue|Angular] frameworks</code></p>
                  <h4 class="expandable-section-title">Sequence Expansion</h4>
                  <p class="expandable-description">Use <code>@{step1 -> step2 -> step3}</code> to run a sequence of prompts, where the output of each feeds into the next.</p>
                  <p class="expandable-example"><em>Example:</em> <code>Create a plan, then @{implement the first step -> test the implementation -> document the results}</code></p>
                  <h4 class="expandable-section-title">Range Expansion</h4>
                  <p class="expandable-description">Use <code>@(start..end:step)</code> to iterate over a range of numbers.</p>
                  <p class="expandable-example"><em>Example:</em> <code>Generate test cases for input values @(1..10:2)</code></p>
                  <h4 class="expandable-section-title">Topic Reference Expansion</h4>
                  <p class="expandable-description">Use <code>@topicType</code> to refer to previously identified topics.</p>
                  <p class="expandable-example"><em>Example:</em> <code>Create documentation for @Function</code></p>
                  <p class="expandable-footer">You can combine these syntaxes for more complex expansions.</p>
                </div>
                """.trimIndent()
            )
        }

        socketManager.newTask(cancelable = false, root = true).expandable(
            "Session Info", """
                Session ID: `${session}`
                Start Time: `${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}`
                Root: `${settings?.absoluteWorkingDir}`
                Session Location: `${dataStorage.getSessionDir(user, session).absolutePath}`
                Data Location: `${dataStorage.getDataDir(user, session).absolutePath}`
                Expansion Syntax: `${if (useExpansionSyntax) "Enabled" else "Disabled"}`
            """.trimIndent().renderMarkdown()
        )
        return socketManager
    }

    override fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        ui: SocketManager
    ) {
        try {
            val settings: OrchestrationConfig = try {
                getSettings(session, user, OrchestrationConfig::class.java) ?: OrchestrationConfig()
            } catch (e: Exception) {
                log.error("Error retrieving orchestration config, using default", e)
                OrchestrationConfig()
            }

            settings.absoluteWorkingDir?.let { DataStorage.sessionPaths[session] = File(it) }
            log.debug("Received user message: $userMessage")

            val expandedMessage = if (useExpansionSyntax) expandTopics(userMessage) else userMessage

            if (useExpansionSyntax && hasExpansionSyntax(expandedMessage)) {
                processMessageWithExpansions(
                    session,
                    user,
                    expandedMessage,
                    ui,
                    settings
                )
                return
            }

            val cognitiveMode = (settings.cognitiveMode ?: throw IllegalStateException("Cognitive mode not configured")).getCognitiveMode(
                ui = ui,
                orchestrationConfig = settings,
                session = session,
                user = user
            )
            cognitiveMode.apply { initialize() }.handleUserMessage(expandedMessage, ui.newTask(true))

        } catch (e: Throwable) {
            log.error("Error processing user message", e)
            ui.newTask().error(e)
        }
    }

    /**
     * Expands topic references in the message using previously identified topics
     */
    private fun expandTopics(userMessage: String): String {
        return topicReferencePattern.replace(userMessage) { matchResult ->
            val topicType = matchResult.groupValues[1]
            val topicList = aggregateTopics[topicType]
            val entities = synchronized(topicList ?: Any()) {
                topicList?.toList()
            }
            if (!entities.isNullOrEmpty()) {
                "@[${entities.joinToString("|")}]"
            } else {
                matchResult.value
            }
        }
    }

    /**
     * Checks if the message contains any expansion syntax
     */
    private fun hasExpansionSyntax(message: String): Boolean {
        return expansionExpressionPattern.find(message) != null ||
                sequenceExpansionPattern.find(message) != null ||
                rangeExpansionPattern.find(message) != null
    }

    /**
     * Processes a message that contains expansion expressions.
     * This will create multiple tabs for each expansion option and process each variant.
     */
    private fun processMessageWithExpansions(
        session: Session,
        user: User?,
        userMessage: String,
        ui: SocketManager,
        orchestrationConfig: OrchestrationConfig
    ) {
        val task = ui.newTask()
        val processor = FixedConcurrencyProcessor(expansionPool, 4)
        processMessageRecursive(
            session = session,
            user = user,
            currentMessage = userMessage,
            ui = ui,
            task = task,
            processor = processor,
            orchestrationConfig = orchestrationConfig
        )
    }

    /**
     * Recursively processes a message with expansion expressions.
     * Handles parallel, sequence, and range expansions similar to ChatSocketManager.
     */
    private fun processMessageRecursive(
        session: Session,
        user: User?,
        currentMessage: String,
        ui: SocketManager,
        task: SessionTask,
        processor: FixedConcurrencyProcessor,
        orchestrationConfig: OrchestrationConfig
    ) {

        // Check for range expansion first
        val rangeMatch = rangeExpansionPattern.find(currentMessage)
        if (rangeMatch != null) {
            expandRange(session, user, currentMessage, ui, task, processor, rangeMatch)
            return
        }

        // Check for sequence expansion
        val sequenceMatch = sequenceExpansionPattern.find(currentMessage)
        if (sequenceMatch != null) {
            expandSequence(session, user, currentMessage, ui, task, processor, sequenceMatch)
            return
        }

        // Check for parallel expansion
        val parallelMatch = expansionExpressionPattern.find(currentMessage)
        if (parallelMatch != null && parallelMatch.groupValues[1].split('|', ',').size > 1) {
            expandParallel(session, user, currentMessage, ui, task, processor, parallelMatch, orchestrationConfig)
            return
        }
        val cognitiveMode = orchestrationConfig.cognitiveMode?.getCognitiveMode(
            ui = ui,
            orchestrationConfig = orchestrationConfig,
            session = session,
            user = user
        )?.apply { initialize() } ?: throw IllegalStateException("Cognitive mode not configured")
        cognitiveMode.handleUserMessage(currentMessage, task)
    }

    /**
     * Expands range expressions in the format @(start..end:step)
     */
    private fun expandRange(
        session: Session,
        user: User?,
        currentMessage: String,
        ui: SocketManager,
        task: SessionTask,
        processor: FixedConcurrencyProcessor,
        rangeMatch: MatchResult
    ) {
        val start = rangeMatch.groupValues[1].toInt()
        val end = rangeMatch.groupValues[2].toInt()
        val step = rangeMatch.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 1

        val items = generateSequence(start) { it + step }
            .takeWhile { if (step > 0) it <= end else it >= end }
            .toList()
            .map { it.toString() }

        expandSequenceItems(
            session,
            user,
            currentMessage,
            ui,
            task,
            processor,
            rangeMatch.value,
            items,
            this@UnifiedPlanApp.getSettings(session, user, OrchestrationConfig::class.java)!!
        )
    }

    /**
     * Expands sequence expressions in the format @{step1 -> step2 -> step3}
     */
    private fun expandSequence(
        session: Session,
        user: User?,
        currentMessage: String,
        ui: SocketManager,
        task: SessionTask,
        processor: FixedConcurrencyProcessor,
        sequenceMatch: MatchResult
    ) {
        val items = sequenceMatch.groupValues[1].split(Regex("""\s*->\s*"""))
        expandSequenceItems(
            session,
            user,
            currentMessage,
            ui,
            task,
            processor,
            sequenceMatch.value,
            items,
            this@UnifiedPlanApp.getSettings(session, user, OrchestrationConfig::class.java)!!
        )
    }

    /**
     * Expands parallel expressions in the format @[option1|option2|option3]
     */
    private fun expandParallel(
        session: Session,
        user: User?,
        currentMessage: String,
        ui: SocketManager,
        task: SessionTask,
        processor: FixedConcurrencyProcessor,
        parallelMatch: MatchResult,
        orchestrationConfig: OrchestrationConfig
    ) {
        val options = parallelMatch.groupValues[1].split('|', ',')
        val tabs = TabbedDisplay(task, closable = useExpansionSyntax)

        options.map { option ->
            processor.submit {
                val subTask = ui.newTask(false).apply { tabs[option] = placeholder }
                val nextMessage = currentMessage.replaceFirst(parallelMatch.value, option)

                processMessageRecursive(
                    session = session,
                    user = user,
                    currentMessage = nextMessage,
                    ui = ui,
                    task = subTask,
                    processor = processor,
                    orchestrationConfig = orchestrationConfig
                )
            }
        }.forEach { it.get() }

        tabs.update()
    }

    /**
     * Expands sequence items (used by both range and sequence expansions)
     */
    private fun expandSequenceItems(
        session: Session,
        user: User?,
        currentMessage: String,
        ui: SocketManager,
        task: SessionTask,
        processor: FixedConcurrencyProcessor,
        expression: String,
        items: List<String>,
        orchestrationConfig: OrchestrationConfig
    ) {
        val tabs = TabbedDisplay(task, closable = useExpansionSyntax)

        for (item in items) {
            val subTask = ui.newTask(false).apply { tabs[item] = placeholder }
            val nextMessage = currentMessage.replaceFirst(expression, item)

            processMessageRecursive(
                session = session,
                user = user,
                currentMessage = nextMessage,
                ui = ui,
                task = subTask,
                processor = processor,
                orchestrationConfig = orchestrationConfig
            )
        }

        tabs.update()
    }

    companion object {
    }
}