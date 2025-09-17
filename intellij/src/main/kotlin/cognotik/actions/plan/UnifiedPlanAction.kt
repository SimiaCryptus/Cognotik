package cognotik.actions.plan

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.simiacryptus.cognotik.CognotikAppServer
import com.simiacryptus.cognotik.apps.general.UnifiedPlanApp
import com.simiacryptus.cognotik.apps.graph.GraphOrderedPlanMode
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.instance
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.plan.PlanUtil.isWindows
import com.simiacryptus.cognotik.plan.TaskSettingsBase
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.cognitive.*
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.FileSelectionUtils.filteredWalk
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.io.File
import java.text.SimpleDateFormat

class UnifiedPlanAction : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(e: AnActionEvent) {
        val root: String = e.getRoot()
        val dialog = PlanConfigDialog(
            e.project, object : PlanSettings(
                defaultModel = AppSettingsState.instance.smartModel
                    ?: throw IllegalStateException("Smart model not configured"),
                parsingModel = AppSettingsState.instance.fastModel
                    ?: throw IllegalStateException("Fast model not configured"),
                shellCmd = listOf(
                    if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "bash"
                ),
                temperature = AppSettingsState.instance.temperature.coerceIn(0.0, 1.0),
                env = mapOf(),
                workingDir = root,
            ) {
                override fun instance(model: ApiChatModel) = model.instance()
                    ?: throw IllegalStateException("Model or Provider not set")
            },
            singleTaskMode = false,
        )

        if (dialog.showAndGet()) {
            try {
                val planSettings = dialog.settings

                val selectedCognitiveMode = dialog.cognitiveModeCombo.selectedItem as String

                val cognitiveMode: CognitiveModeStrategy = when (selectedCognitiveMode) {
                    "Plan Ahead" -> object : CognitiveModeStrategy {
                        override val inputCnt = 1

                        override fun getCognitiveMode(
                            ui: ApplicationInterface,
                            planSettings: PlanSettings,
                            session: Session,
                            user: User?,
                            describer: TypeDescriber
                        ) = object : PlanAheadMode(ui, planSettings, session, user,  describer) {
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
                        override val inputCnt = 0
                        override fun getCognitiveMode(
                            ui: ApplicationInterface,
                            planSettings: PlanSettings,
                            session: Session,
                            user: User?,
                            describer: TypeDescriber
                        ) = object : TaskChatMode(ui, planSettings, session, user, describer) {
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
                        override val inputCnt = 1
                        override fun getCognitiveMode(
                            ui: ApplicationInterface,
                            planSettings: PlanSettings,
                            session: Session,
                            user: User?,
                            describer: TypeDescriber
                        ) = object : GraphOrderedPlanMode(
                            ui,
                            planSettings,
                            session,
                            user,
                            graphFile,
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
                        override val inputCnt = 1
                        override fun getCognitiveMode(
                            ui: ApplicationInterface,
                            planSettings: PlanSettings,
                            session: Session,
                            user: User?,
                            describer: TypeDescriber
                        ): CognitiveMode {
                            return object : AutoPlanMode(
                                ui = ui,
                                planSettings = planSettings,
                                session = session,
                                user = user,
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
                    initializeChat(e, progress, planSettings, cognitiveMode)
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
        cognitiveStrategy: CognitiveModeStrategy
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
        val server = CognotikAppServer.getServer(e.project)
        openBrowser(server, session.toString())
    }

    private fun getProjectRoot(e: AnActionEvent): File? {
        val folder = e.getSelectedFolder()
        return folder?.toFile ?: e.getSelectedFile()?.parent?.toFile?.let { file ->
            getModuleRootForFile(file)
        }
    }

    private fun setupChatSession(
        session: Session,
        root: File,
        planSettings: PlanSettings,
        cognitiveStrategy: CognitiveModeStrategy,
        describer: TypeDescriber
    ) {
        DataStorage.sessionPaths[session] = root
        val fastChatModel = (AppSettingsState.instance.fastModel
            ?: throw IllegalStateException("Fast model not configured"))
        SessionProxyServer.chats[session] = object : UnifiedPlanApp(
            applicationName = "Unified Planning",
            path = "/unifiedPlan",
            planSettings = planSettings.copy(
                env = mapOf(),
                workingDir = root.absolutePath,
                language = if (isWindows) "powershell" else "bash",
                command = listOf(
                    if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "bash"
                ),
                parsingModel = fastChatModel,
            ),
            showMenubar = false,
            cognitiveStrategy = cognitiveStrategy,
            describer = describer
        ) {
            override fun instance(model: ApiChatModel) = model.instance()
                ?: throw IllegalStateException("Model or Provider not set")
        }
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Unified Planning",
            inputCnt = 1,
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

    private fun openBrowser(server: CognotikAppServer, session: String) {
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