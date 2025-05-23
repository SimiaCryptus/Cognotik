package cognotik.actions.plan

import cognotik.actions.BaseAction
import cognotik.actions.SessionProxyServer
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.simiacryptus.cognotik.AppServer
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.UITools
import com.simiacryptus.cognotik.apps.general.UnifiedPlanApp
import com.simiacryptus.cognotik.apps.graph.GraphOrderedPlanMode
import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.plan.PlanUtil.isWindows
import com.simiacryptus.cognotik.plan.TaskSettingsBase
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.cognitive.*
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.FileSelectionUtils.Companion.filteredWalk
import com.simiacryptus.cognotik.util.getModuleRootForFile
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.jopenai.models.chatModel
import java.io.File
import java.text.SimpleDateFormat

class UnifiedPlanAction : BaseAction() {
    private companion object {
        private const val DEFAULT_API_BUDGET = 10.0
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(e: AnActionEvent) {
        val root: String = UITools.getRoot(e)
        val dialog = PlanConfigDialog(
            e.project, PlanSettings(
                defaultModel = AppSettingsState.instance.smartModel.chatModel(),
                parsingModel = AppSettingsState.instance.fastModel.chatModel(),
                shellCmd = listOf(
                    if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "bash"
                ),
                temperature = AppSettingsState.instance.temperature.coerceIn(0.0, 1.0),
                env = mapOf(),
                workingDir = root,
            ),
            singleTaskMode = false,

            apiBudget = DEFAULT_API_BUDGET

        )

        if (dialog.showAndGet()) {
            try {
                val planSettings = dialog.settings

                val selectedCognitiveMode = dialog.cognitiveModeCombo.selectedItem as String

                val cognitiveMode: CognitiveModeStrategy = when (selectedCognitiveMode) {
                    "Plan Ahead" -> object : CognitiveModeStrategy {
                        override val singleInput: Boolean = true

                        override fun getCognitiveMode(
                            ui: ApplicationInterface,
                            api: API,
                            api2: OpenAIClient,
                            planSettings: PlanSettings,
                            session: Session,
                            user: User?,
                            describer: TypeDescriber
                        ) = object : PlanAheadMode(ui, api, planSettings, session, user, api2, describer) {
                            override fun contextData(): List<String> {
                                return listOf(
                                    buildString {

                                        append("Selected Files:\n")
                                        append(filteredWalk(File(root)).joinToString("\n") {
                                            "* ${
                                                it.toRelativeString(
                                                    File(root)
                                                )
                                            }"
                                        })
                                    }
                                )
                            }
                        }
                    }

                    "Single Task" -> object : CognitiveModeStrategy {
                        override val singleInput: Boolean = false
                        override fun getCognitiveMode(
                            ui: ApplicationInterface,
                            api: API,
                            api2: OpenAIClient,
                            planSettings: PlanSettings,
                            session: Session,
                            user: User?,
                            describer: TypeDescriber
                        ) = object : TaskChatMode(ui, api, planSettings, session, user, api2, describer) {
                            override fun contextData(): List<String> {
                                return listOf(
                                    buildString {

                                        append("Selected Files:\n")
                                        append(filteredWalk(File(root)).joinToString("\n") {
                                            "* ${
                                                it.toRelativeString(
                                                    File(root)
                                                )
                                            }"
                                        })
                                    }
                                )
                            }
                        }
                    }

                    "Graph" -> object : CognitiveModeStrategy {
                        override val singleInput: Boolean = true
                        override fun getCognitiveMode(
                            ui: ApplicationInterface,
                            api: API,
                            api2: OpenAIClient,
                            planSettings: PlanSettings,
                            session: Session,
                            user: User?,
                            describer: TypeDescriber
                        ) = object : GraphOrderedPlanMode(
                            ui,
                            api,
                            planSettings,
                            session,
                            user,
                            api2,
                            GraphOrderedPlanMode.graphFile,
                            describer
                        ) {
                            override fun contextData(): List<String> {
                                return listOf(
                                    buildString {

                                        append("Selected Files:\n")
                                        append(filteredWalk(File(root)).joinToString("\n") {
                                            "* ${
                                                it.toRelativeString(
                                                    File(root)
                                                )
                                            }"
                                        })
                                    }
                                )
                            }
                        }
                    }

                    "Auto Plan" -> object : CognitiveModeStrategy {
                        override val singleInput: Boolean = true
                        override fun getCognitiveMode(
                            ui: ApplicationInterface,
                            api: API,
                            api2: OpenAIClient,
                            planSettings: PlanSettings,
                            session: Session,
                            user: User?,
                            describer: TypeDescriber
                        ): CognitiveMode {
                            return object : AutoPlanMode(
                                ui = ui,
                                api = api,
                                planSettings = planSettings,
                                session = session,
                                user = user,
                                api2 = api2,
                                maxTaskHistoryChars = dialog.settings.maxTaskHistoryChars,
                                maxTasksPerIteration = dialog.settings.maxTasksPerIteration,
                                maxIterations = dialog.settings.maxIterations,
                                describer
                            ) {
                                override fun contextData(): List<String> {
                                    return listOf(
                                        buildString {

                                            append("Selected Files:\n")
                                            append(filteredWalk(File(root)).joinToString("\n") {
                                                "* ${
                                                    it.toRelativeString(
                                                        File(root)
                                                    )
                                                }"
                                            })
                                        }
                                    )
                                }
                            }
                        }
                    }

                    else -> throw RuntimeException("Unknown plan mode: $selectedCognitiveMode")
                }

                val isSingleTaskMode = selectedCognitiveMode == "Single Task"

                if (isSingleTaskMode) {
                    val enabledTask = TaskType.values().find { planSettings.getTaskSettings(it).enabled }
                    if (enabledTask != null) {

                        TaskType.values().forEach { taskType ->
                            if (taskType != enabledTask) {
                                planSettings.setTaskSettings(
                                    taskType,
                                    TaskSettingsBase(taskType.name, false, planSettings.getTaskSettings(taskType).model)
                                )
                            }
                        }
                    }
                }

                UITools.runAsync(e.project, "Initializing Unified Plan", true) { progress ->
                    initializeChat(e, progress, planSettings, cognitiveMode, dialog.apiBudget)
                }
            } catch (ex: Exception) {
                log.error("Failed to initialize unified plan", ex)
                UITools.showError(e.project, "Failed to initialize unified plan: ${ex.message}")
            }
        }
    }

    private fun initializeChat(
        e: AnActionEvent,
        progress: ProgressIndicator,
        planSettings: PlanSettings,
        cognitiveStrategy: CognitiveModeStrategy,
        apiBudget: Double
    ) {
        progress.text = "Setting up session..."
        val session = Session.newGlobalID()
        val root = getProjectRoot(e) ?: throw RuntimeException("Could not determine project root")
        progress.text = "Processing files..."
        setupChatSession(
            session,
            root,
            planSettings,
            cognitiveStrategy,
            apiBudget,
            object : AbbrevWhitelistYamlDescriber(
                "com.simiacryptus", "cognotik.actions"
            ) {
                override val includeMethods: Boolean get() = false

                override fun getEnumValues(clazz: Class<*>): List<String> {
                    return if (clazz == TaskType::class.java) {
                        planSettings.taskSettings.filter { it.value.enabled }.map { it.key }
                    } else {
                        super.getEnumValues(clazz)
                    }
                }
            })
        progress.text = "Starting server..."
        val server = AppServer.getServer(e.project)
        openBrowser(server, session.toString())
    }

    private fun getProjectRoot(e: AnActionEvent): File? {
        val folder = UITools.getSelectedFolder(e)
        return folder?.toFile ?: UITools.getSelectedFile(e)?.parent?.toFile?.let { file ->
            getModuleRootForFile(file)
        }
    }

    private fun setupChatSession(
        session: Session,
        root: File,
        planSettings: PlanSettings,
        cognitiveStrategy: CognitiveModeStrategy,
        apiBudget: Double,
        describer: TypeDescriber
    ) {
        DataStorage.sessionPaths[session] = root
        SessionProxyServer.chats[session] = UnifiedPlanApp(
            applicationName = "Unified Planning",
            path = "/unifiedPlan",
            planSettings = planSettings.copy(
                env = mapOf(),
                workingDir = root.absolutePath,
                language = if (isWindows) "powershell" else "bash",
                command = listOf(
                    if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "bash"
                ),
                parsingModel = AppSettingsState.instance.fastModel.chatModel(),
            ),
            model = AppSettingsState.instance.smartModel.chatModel(),
            parsingModel = AppSettingsState.instance.fastModel.chatModel(),
            showMenubar = false,
            api = api.getChildClient().apply {
                budget = apiBudget

            },
            api2 = api2,
            cognitiveStrategy = cognitiveStrategy,
            describer = describer
        )
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Unified Planning",
            singleInput = true,
            stickyInput = true,
            loadImages = false,
            showMenubar = false
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null,
            session,
            "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
    }

    private fun openBrowser(server: AppServer, session: String) {
        Thread {
            Thread.sleep(500)
            try {
                val uri = server.server.uri.resolve("/#$session")
                log.info("Opening browser to $uri")
                browse(uri)
            } catch (e: Throwable) {
                log.warn("Error opening browser", e)
            }
        }.start()
    }
}