package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.skyenet.apps.plan.cognitive.CognitiveMode
import com.simiacryptus.skyenet.apps.plan.cognitive.CognitiveModeStrategy
import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SocketManager
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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
  val cognitiveStrategy: CognitiveModeStrategy
) : ApplicationServer(
  applicationName = applicationName,
  path = path,
  showMenubar = showMenubar,
  root = planSettings.workingDir?.let { File(it) } ?: dataStorageRoot,
) {
  private val log = LoggerFactory.getLogger(UnifiedPlanApp::class.java)
  private val cognitiveModes = ConcurrentHashMap<String, CognitiveMode>()

  // These can be overridden by specific cognitive modes
  override val stickyInput = true
  override val singleInput = false

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> initSettings(session: Session): T = planSettings.let {
    if (null == root) it.copy(workingDir = root.absolutePath) else
      it
  } as T

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
          user = user
        ).apply { initialize() }
      }

      // Handle the user message with the appropriate cognitive mode
      cognitiveMode.handleUserMessage(userMessage)

    } catch (e: Throwable) {
      log.error("Error processing user message", e)
      ui.newTask().error(ui, e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(UnifiedPlanApp::class.java)
  }
}