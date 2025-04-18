package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.skyenet.TabbedDisplay
import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.apps.plan.cognitive.CognitiveMode
import com.simiacryptus.skyenet.apps.plan.cognitive.CognitiveModeStrategy
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.core.util.FixedConcurrencyProcessor
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SocketManager
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * A unified application that can use different cognitive modes based on configuration.
 * This allows for switching between different planning and execution strategies.
 */
open class UnifiedPlanApp(
  applicationName: String = "Unified Planning App",
  path: String = "/unifiedPlan",
  val planSettings: PlanSettings,
  val model: ChatModel,
  val parsingModel: ChatModel,
  val domainName: String = "localhost",
  showMenubar: Boolean = true,
  val api: API? = null,
  val api2: OpenAIClient,
  val cognitiveStrategy: CognitiveModeStrategy,
  val describer: TypeDescriber,
) : ApplicationServer(
  applicationName = applicationName,
  path = path,
  showMenubar = showMenubar,
  root = planSettings.workingDir?.let { File(it) } ?: dataStorageRoot,
) {
  private val log = LoggerFactory.getLogger(UnifiedPlanApp::class.java)
  private val cognitiveModes = ConcurrentHashMap<String, CognitiveMode>()
  private val expansionExpressionPattern = Regex("""\{([^|}{]+(?:\|[^|}{]+)+)}""")
  private val expansionPool = Executors.newFixedThreadPool(4)

  override val stickyInput = true
  override val singleInput = true

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> initSettings(session: Session): T = planSettings as T

  override fun newSession(user: User?, session: Session): SocketManager {
    val socketManager = super.newSession(user, session)
    return socketManager
  }

  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    try {
      log.debug("Received user message: $userMessage")
      // Check if the message contains expansion expressions
      if (expansionExpressionPattern.find(userMessage) != null) {
        processMessageWithExpansions(session, user, userMessage, ui, api)
        return
      }
      
      // Get or create the cognitive mode for this session
      val cognitiveMode = cognitiveModes.computeIfAbsent(session.sessionId) {
        val settings = getSettings(session, user, PlanSettings::class.java) ?: planSettings
        if (api is ChatClient) api.budget = settings.budget

        cognitiveStrategy.getCognitiveMode(
          ui = ui,
          api = api,
          api2 = api2,
          planSettings = settings,
          session = session,
          user = user,
          describer = describer
        ).apply { initialize() }
      }

      // Handle the user message with the appropriate cognitive mode
      cognitiveMode.handleUserMessage(userMessage, ui.newTask(true))

    } catch (e: Throwable) {
      log.error("Error processing user message", e)
      ui.newTask().error(ui, e)
    }
  }
  
  /**
   * Processes a message that contains expansion expressions.
   * This will create multiple tabs for each expansion option and process each variant.
   */
  private fun processMessageWithExpansions(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    val task = ui.newTask()
    val processor = FixedConcurrencyProcessor(expansionPool, 4)
    processMessageRecursive(
      session = session,
      user = user,
      currentMessage = userMessage,
      ui = ui,
      api = api,
      task = task,
      processor = processor
    )
  }
  
  /**
   * Recursively processes a message with expansion expressions.
   * For each level of expansion, it creates tabs for each option.
   */
  private fun processMessageRecursive(
    session: Session,
    user: User?,
    currentMessage: String,
    ui: ApplicationInterface,
    api: API,
    task: com.simiacryptus.skyenet.webui.session.SessionTask,
    processor: FixedConcurrencyProcessor
  ) {
    // Find the first expansion pattern in the message
    val match = expansionExpressionPattern.find(currentMessage)
    if (match == null) {
      // Base case: No more expansions, process the message normally
      val cognitiveMode = cognitiveModes.computeIfAbsent(session.sessionId) {
        val settings = getSettings(session, user, PlanSettings::class.java) ?: planSettings
        if (api is ChatClient) api.budget = settings.budget
        cognitiveStrategy.getCognitiveMode(
          ui = ui,
          api = api,
          api2 = api2,
          planSettings = settings,
          session = session,
          user = user,
          describer = describer
        ).apply { initialize() }
      }
      // Handle the expanded message with the cognitive mode
      cognitiveMode.handleUserMessage(currentMessage, task)
    } else {
      // Recursive case: Process each expansion option
      val expression = match.groupValues[1]
      val options = expression.split('|')
      val tabs = TabbedDisplay(task)
      options.map { option ->
        processor.submit {
          // Create a sub-task for this option
          val subUi = ApplicationInterface(ui.socketManager)
          val subTask = subUi.newTask(false).apply { tabs[option] = placeholder }
          // Replace the first occurrence of the pattern with this option
          val nextMessage = currentMessage.replaceFirst(match.value, option)
          // Recursively process the modified message
          processMessageRecursive(
            session = session,
            user = user,
            currentMessage = nextMessage,
            ui = subUi,
            api = api,
            task = subTask,
            processor = processor
          )
        }
      }.toTypedArray().forEach { it.get() }
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(UnifiedPlanApp::class.java)
  }
}