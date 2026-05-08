package com.simiacryptus.cognotik.apps

import com.simiacryptus.cognotik.CoreTasks
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveMode
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.FixedConcurrencyProcessor
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.slf4j.LoggerFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * A unified application that can use different cognitive modes based on configuration.
 * This allows for switching between different planning and execution strategies.
 */
open class SinglePlanApp(
  path: String,
  applicationName: String = "Unified Planning App",
  showMenubar: Boolean = true,
  var useExpansionSyntax: Boolean = true
) : ApplicationServer(
  applicationName = applicationName,
  path = path,
  showMenubar = showMenubar,
  root = dataStorageRoot,
) {
  private val log = LoggerFactory.getLogger(SinglePlanApp::class.java)

  // Updated expansion patterns to match ChatSocketManager
  private val idSubPattern = """[^|\n,/\\;}\]\[><()@]+"""
  private val expansionExpressionPattern = Regex("""@\[($idSubPattern(?:[|,]$idSubPattern)+)]""")
  private val sequenceExpansionPattern = Regex("""@\{([^}]+(?:\s*->\s*[^}]+)+)\}""")
  private val rangeExpansionPattern = Regex("""@\((-?\d+)(?:\.{2,3}| to )(-?\d+)(?:(?::| by )(\d+))?\)""")
  private val topicReferencePattern = Regex("""@([A-Z][a-zA-Z0-9_]*)""")

  private val aggregateTopics = ConcurrentHashMap<String, MutableList<String>>()
  override val stickyInput = true
  override val inputCnt: Int = 4

  override fun appInfo(session: Session, user: User): Map<String, Any> {
    val settings = getSettings(session, user, OrchestrationConfig::class.java)
    return AppInfoData(
      applicationName = applicationName,
      inputCnt = when {
        settings?.let { it.cognitiveSettings?.type?.name } == "Chat" -> 0
        else -> 4
      },
      stickyInput = stickyInput,
      loadImages = false,
      showMenubar = showMenubar,
    ).toMap()
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> initSettings(session: Session,
                                      user: User,
  ): T =
    OrchestrationConfig(sessionId = session.sessionId, null, user = user) as T


  override fun newSession(
    user: User,
    session: Session
  ): SocketManager {
    val socketManager = super.newSession(user, session)!!
    val settings = getSettings(session, user, OrchestrationConfig::class.java)
    useExpansionSyntax = when (settings?.let { it.cognitiveSettings?.type?.name }) {
      "Chat" -> true
      else -> false
    }
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

Session Location: `${dataStorage.getUserDir(user, session).absolutePath}`

Data Location: `${dataStorage.getSystemDir(user, session).absolutePath}`

Expansion Syntax: `${if (useExpansionSyntax) "Enabled" else "Disabled"}`

```json
${settings?.toJson()}
```
            """.renderMarkdown()
    )
    return socketManager
  }

  override fun userMessage(
    session: Session,
    user: User,
    userMessage: String,
    ui: SocketManager
  ) {
    try {
      val settings = try {
        getSettings(session, user, OrchestrationConfig::class.java)
      } catch (e: Exception) {
        log.error("Error retrieving orchestration config, using default", e)
        null
      }?.apply {
        if (null == DataStorage.userPaths[session]) absoluteWorkingDir?.let {
          DataStorage.userPaths[session] = File(it)
        }
      } ?: throw IllegalStateException("OrchestrationConfig not found in session settings")

      val cognitiveMode = (settings.cognitiveSettings?.type ?: CoreTasks.Chat).getImpl(
        orchestrationConfig = settings,
        session = session,
        user = user
      )

      log.debug("Received user message: $userMessage")

      val expandedMessage = if (useExpansionSyntax) expandTopics(userMessage) else userMessage

//            if (useExpansionSyntax && hasExpansionSyntax(expandedMessage)) {
//                processMessageWithExpansions(
//                    session,
//                    user,
//                    expandedMessage,
//                    ui,
//                    settings
//                )
//                return
//            }

      val task = ui.newTask(true)
      val mode = cognitiveMode.apply { initialize(task) }
      mode.handleUserMessage(expandedMessage, task)
      onComplete(mode, task)
    } catch (e: Throwable) {
      log.error("Error processing user message", e)
      ui.newTask().error(e)
    }
  }

  open fun onComplete(mode: CognitiveMode<*>, task: SessionTask) {
    // No-op by default
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
   * Recursively processes a message with expansion expressions.
   * Handles parallel, sequence, and range expansions similar to ChatSocketManager.
   */
  private fun processMessageRecursive(
    session: Session,
    currentMessage: String,
    ui: SocketManager,
    task: SessionTask,
    processor: FixedConcurrencyProcessor,
    orchestrationConfig: OrchestrationConfig,
    user: User,
  ) {

    // Check for range expansion first
    val rangeMatch = rangeExpansionPattern.find(currentMessage)
    if (rangeMatch != null) {
      expandRange(session, currentMessage, ui, task, processor, rangeMatch, user)
      return
    }

    // Check for sequence expansion
    val sequenceMatch = sequenceExpansionPattern.find(currentMessage)
    if (sequenceMatch != null) {
      expandSequence(session, currentMessage, ui, task, processor, sequenceMatch, user)
      return
    }

    // Check for parallel expansion
    val parallelMatch = expansionExpressionPattern.find(currentMessage)
    if (parallelMatch != null && parallelMatch.groupValues[1].split('|', ',').size > 1) {
      expandParallel(
        session,
        currentMessage,
        ui,
        task,
        processor,
        parallelMatch,
        orchestrationConfig,
        user
      )
      return
    }
    val cognitiveMode = orchestrationConfig.cognitiveSettings?.type?.getImpl(
      orchestrationConfig = orchestrationConfig,
      session = session,
      user = user
    )?.apply { initialize(task) } ?: throw IllegalStateException("Cognitive mode not configured")
    cognitiveMode.handleUserMessage(currentMessage, task)
  }

  /**
   * Expands range expressions in the format @(start..end:step)
   */
  private fun expandRange(
    session: Session,
    currentMessage: String,
    ui: SocketManager,
    task: SessionTask,
    processor: FixedConcurrencyProcessor,
    rangeMatch: MatchResult,
    user: User
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
      currentMessage,
      ui,
      task,
      processor,
      rangeMatch.value,
      items,
      this@SinglePlanApp.getSettings(session, user, OrchestrationConfig::class.java)!!,
      user
    )
  }

  /**
   * Expands sequence expressions in the format @{step1 -> step2 -> step3}
   */
  private fun expandSequence(
    session: Session,
    currentMessage: String,
    ui: SocketManager,
    task: SessionTask,
    processor: FixedConcurrencyProcessor,
    sequenceMatch: MatchResult,
    user: User
  ) {
    val items = sequenceMatch.groupValues[1].split(Regex("""\s*->\s*"""))
    expandSequenceItems(
      session,
      currentMessage,
      ui,
      task,
      processor,
      sequenceMatch.value,
      items,
      this@SinglePlanApp.getSettings(session, user, OrchestrationConfig::class.java)!!,
      user
    )
  }

  /**
   * Expands parallel expressions in the format @[option1|option2|option3]
   */
  private fun expandParallel(
    session: Session,
    currentMessage: String,
    ui: SocketManager,
    task: SessionTask,
    processor: FixedConcurrencyProcessor,
    parallelMatch: MatchResult,
    orchestrationConfig: OrchestrationConfig,
    user: User
  ) {
    val options = parallelMatch.groupValues[1].split('|', ',')
    val tabs = TabbedDisplay(task, closable = useExpansionSyntax)

    options.map { option ->
      processor.submit {
        val subTask = ui.newTask(false).apply { tabs[option] = placeholder }
        val nextMessage = currentMessage.replaceFirst(parallelMatch.value, option)

        processMessageRecursive(
          session = session,
          currentMessage = nextMessage,
          ui = ui,
          task = subTask,
          processor = processor,
          orchestrationConfig = orchestrationConfig,
          user = user,
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
    currentMessage: String,
    ui: SocketManager,
    task: SessionTask,
    processor: FixedConcurrencyProcessor,
    expression: String,
    items: List<String>,
    orchestrationConfig: OrchestrationConfig,
    user: User
  ) {
    val tabs = TabbedDisplay(task, closable = useExpansionSyntax)

    for (item in items) {
      val subTask = ui.newTask(false).apply { tabs[item] = placeholder }
      val nextMessage = currentMessage.replaceFirst(expression, item)

      processMessageRecursive(
        session = session,
        currentMessage = nextMessage,
        ui = ui,
        task = subTask,
        processor = processor,
        orchestrationConfig = orchestrationConfig,
        user = user,
      )
    }

    tabs.update()
  }

  companion object {
  }
}