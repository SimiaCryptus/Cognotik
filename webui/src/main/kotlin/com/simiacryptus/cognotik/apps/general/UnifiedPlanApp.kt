package com.simiacryptus.cognotik.apps.general

import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.cognitive.CognitiveMode
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeStrategy
import com.simiacryptus.cognotik.plan.tools.CommandAutoFixTask
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.FixedConcurrencyProcessor
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.jopenai.models.ChatModel
import org.slf4j.LoggerFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * A unified application that can use different cognitive modes based on configuration.
 * This allows for switching between different planning and execution strategies.
 */
open class UnifiedPlanApp(
    path: String,
    applicationName: String = "Unified Planning App",
    val planSettings: PlanSettings,
    val model: ChatModel,
    val parsingModel: ChatModel,
    showMenubar: Boolean = true,
    val api: API? = null,
    val api2: OpenAIClient,
    val cognitiveStrategy: CognitiveModeStrategy,
    val describer: TypeDescriber,
) : ApplicationServer(
    applicationName = applicationName,
    path = path,
    showMenubar = showMenubar,
    root = planSettings.absoluteWorkingDir?.let { File(it) } ?: dataStorageRoot,
) {
    private val log = LoggerFactory.getLogger(UnifiedPlanApp::class.java)
    private val cognitiveModes = ConcurrentHashMap<String, CognitiveMode>()
    private val expansionExpressionPattern = Regex("""\{([^|}{]+(?:\|[^|}{]+)+)}""")
    private val expansionPool = Executors.newFixedThreadPool(4)
    override val stickyInput = true
    override val singleInput = cognitiveStrategy.singleInput

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T = planSettings as T

    override fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: API
    ) {
        try {
            val settings = getSettings(session, user, PlanSettings::class.java) ?: planSettings
            ui.newTask(true).expandable("Session Info", """
                Session ID: `${session.sessionId}`
                
                Start Time: `${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}`
                
                Enabled Tasks: `${settings.taskSettings.filter { it.value.enabled }.keys.joinToString(", ")}`
                
                Root: `${settings.absoluteWorkingDir}`
                
                Location: `${dataStorage.getDataDir(user, session).absolutePath}`
            """.trimIndent().renderMarkdown())
            log.debug("Received user message: $userMessage")

            if (expansionExpressionPattern.find(userMessage) != null) {
                processMessageWithExpansions(session, user, userMessage, ui, api)
                return
            }

            val cognitiveMode = cognitiveModes.computeIfAbsent(session.sessionId) {
                user?.let { ApplicationServices.userSettingsManager.getUserSettings(it) }?.apply {
                    (settings.taskSettings[TaskType.CommandAutoFixTask.name] as? CommandAutoFixTask.CommandAutoFixTaskSettings)
                        ?.commandAutoFixCommands?.addAll(this.localTools)
                }
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
        task: com.simiacryptus.cognotik.webui.session.SessionTask,
        processor: FixedConcurrencyProcessor
    ) {

        val match = expansionExpressionPattern.find(currentMessage)
        if (match == null) {

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

            cognitiveMode.handleUserMessage(currentMessage, task)
        } else {

            val expression = match.groupValues[1]
            val options = expression.split('|')
            val tabs = TabbedDisplay(task)
            options.map { option ->
                processor.submit {

                    val subUi = ApplicationInterface(ui.socketManager)
                    val subTask = subUi.newTask(false).apply { tabs[option] = placeholder }

                    val nextMessage = currentMessage.replaceFirst(match.value, option)

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
    }
}

