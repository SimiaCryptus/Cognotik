package com.simiacryptus.cognotik.apps.general

import com.simiacryptus.cognotik.apps.general.PlanHarness.Companion.trayIcon
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveMode
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApiData
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.asApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.CognotikAppServer
import com.simiacryptus.cognotik.webui.session.ServerlessSocketManager
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.eclipse.jetty.server.Server
import java.awt.Desktop
import java.awt.SystemTray
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

open class UnifiedHarness(
    val port: Int = 8082,
    val serverless: Boolean = false,
    val openBrowser: Boolean = false,
    val modelInstanceFn: (ApiChatModel) -> ChatInterface = { model ->
        val api = model.findApi()
        val model =
            model.model ?: throw IllegalArgumentException("No model found for provider: ${model.provider?.name}")
        model.instance(
            key = api?.key ?: throw IllegalArgumentException("No API key found for provider: ${model.provider?.name}"),
            base = api.baseUrl,
        )
    },
    val fastModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    val smartModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    val imageModel: ChatModel = GeminiModels.GeminiPro_30_Image_Preview,
) {
    private var jettyServer: Any? = null
    private var appServer: CognotikAppServer? = null

    open fun start() {
        if (serverless) {
            log.info("Starting in serverless mode - skipping Jetty startup")
            return
        }
        if (jettyServer == null) {
            appServer = CognotikAppServer(
                localName = "localhost",
                port = port
            )
            jettyServer = appServer?.start()
            log.info("Server started on port $port")
        }
    }

    open fun stop() {
        if (serverless) return
        try {
            (jettyServer as? Server)?.stop()
            jettyServer = null
            appServer = null
            log.info("Server stopped")
        } catch (e: Exception) {
            log.warn("Error stopping server", e)
        }
    }

    open fun runPlan(
        prompt: String,
        cognitiveSettings: CognitiveModeConfig,
        timeoutMinutes: Long = 30,
        autoFix: Boolean = !openBrowser,
        workspace: File? = null,
        config: (Session, File) -> OrchestrationConfig = { session: Session, finalWorkspace: File -> OrchestrationConfig(
            sessionId = session.sessionId,
            workingDir = finalWorkspace.absolutePath,
            defaultFastModel = fastModel.asApiChatModel(),
            defaultSmartModel = smartModel.asApiChatModel(),
            defaultImageModel = imageModel.asApiChatModel(),
            autoFix = autoFix,
            cognitiveSettings = cognitiveSettings,
        ) }
    ) {
        val finalWorkspace: File = workspace ?: createTempDirectory(cognitiveSettings.type?.name ?: "plan")
        log.info("Running plan in workspace: ${finalWorkspace.absolutePath}")

        val completionLatch = CountDownLatch(1)
        val session = Session.newGlobalID()
        DataStorage.sessionPaths[session] = finalWorkspace

        val planApp = object : UnifiedPlanApp(
            path = "/test",
            applicationName = "Plan Test App",
            showMenubar = false,
            useExpansionSyntax = true
        ) {
            override fun instance(model: ApiChatModel) = modelInstanceFn(model)

            override fun onComplete(mode: CognitiveMode<*>, task: SessionTask) {
                task.resolveUserFile("results.md")?.writeText(mode.contextData().joinToString("\n\n"))
                super.onComplete(mode, task)
            }

            override fun <T : Any> initSettings(session: Session): T {
                val orchestrationConfig = config(session, finalWorkspace)
                val settingsFile = getSettingsFile(session, defaultUser)
                val json = orchestrationConfig.toJson()
                settingsFile.writeText(json)
                @Suppress("UNCHECKED_CAST")
                return orchestrationConfig as T
            }

            override fun newSession(user: User, session: Session): SocketManager {
                if (serverless) {
                    val socketManager = ServerlessSocketManager(
                        session = session,
                        owner = user,
                        clazz = this.javaClass
                    )
                    // Manually trigger execution since we don't have a UI to send the first message
                    // We use a thread to simulate async execution
                    Thread {
                        try {
                            userMessage(session, user, prompt, socketManager)
                            completionLatch.countDown()
                        } catch (e: Throwable) {
                            log.error("Error running plan", e)
                            completionLatch.countDown() // Ensure we don't hang on error
                        }
                    }.start()
                    return socketManager
                } else {
                    val socketManager = super.newSession(user, session)
                    socketManager.pool.submit {
                        try {
                            Thread.sleep(1000)
                            userMessage(session, user, prompt, socketManager)
                            completionLatch.countDown()
                        } catch (e: Throwable) {
                            log.error("Error running plan", e)
                        }
                    }
                    return socketManager
                }
            }
        }

        if (!serverless) {
            SessionProxyServer.chats[session] = planApp
            ApplicationServer.appInfoMap[session] = AppInfoData(
                applicationName = "Plan Test App",
                inputCnt = 4,
                stickyInput = true,
                showMenubar = false
            )
        }

        try {
            planApp.initSettings<Any>(session)
            val socketManager = planApp.newSession(defaultUser, session)
            if (!serverless) {
                SessionProxyServer.agents[session] = socketManager
                val url = "http://localhost:$port/#$session"
                log.info("Plan available at $url")

                if (openBrowser) {
                    try {
                        Desktop.getDesktop().browse(URI(url))
                    } catch (e: Exception) {
                        log.warn("Failed to open browser", e)
                    }
                }
            }

            log.info("Waiting for plan completion (or timeout)...")
            if (!completionLatch.await(timeoutMinutes, TimeUnit.MINUTES)) {
                log.warn("Plan timed out")
            }

        } finally {
            handleBrowserShutdown()
        }
    }

    open fun <T : TaskExecutionConfig, U : TaskTypeConfig> runTask(
        taskType: TaskType<T, U>,
        typeConfig: U,
        executionConfig: T,
        timeoutMinutes: Long = 30,
        autoFix: Boolean = !openBrowser,
        workspace: File? = null
    ) {
        val finalWorkspace = workspace ?: createTempDirectory(taskType.name)
        log.info("Running task in workspace: ${finalWorkspace.absolutePath}")

        val completionLatch = CountDownLatch(1)
        var error: Throwable? = null
        val session = Session.newGlobalID()
        DataStorage.sessionPaths[session] = finalWorkspace

        val singleTaskApp = object : SingleTaskApp(
            path = "/test",
            taskType = taskType,
            taskConfig = executionConfig,
            instanceFn = modelInstanceFn
        ) {
            override fun instance(model: ApiChatModel) = modelInstanceFn(model)

            override fun onTaskComplete(result: String, task: SessionTask) {
                log.info("Task completed successfully")
                task.resolveUserFile("result.md")?.writeText(result)
                completionLatch.countDown()
            }

            override fun onTaskError(e: Throwable) {
                log.error("Task failed", e)
                error = e
                completionLatch.countDown()
            }

            override fun <T : Any> initSettings(session: Session): T {
                val orchestrationConfig = OrchestrationConfig(
                    sessionId = session.sessionId,
                    workingDir = finalWorkspace.absolutePath,
                    taskSettings = mutableMapOf(
                        typeConfig.name!! to typeConfig
                    ),
                    defaultFastModel = fastModel.asApiChatModel(),
                    defaultSmartModel = smartModel.asApiChatModel(),
                    defaultImageModel = imageModel.asApiChatModel(),
                    autoFix = autoFix,
                )
                val json = orchestrationConfig.toJson()
                getSettingsFile(session, defaultUser).writeText(json)
                @Suppress("UNCHECKED_CAST")
                return orchestrationConfig as T
            }

            override fun newSession(user: User, session: Session): SocketManager {
                if (serverless) {
                    val socketManager = ServerlessSocketManager(
                        session = session,
                        owner = user,
                        clazz = this.javaClass
                    )
                    startSession(session, user, socketManager)
                    return socketManager
                } else {
                    return super.newSession(user, session)
                }
            }
        }

        if (!serverless) {
            SessionProxyServer.chats[session] = singleTaskApp
            ApplicationServer.appInfoMap[session] = AppInfoData(
                applicationName = "Single Task App",
                inputCnt = 0,
                stickyInput = false,
                showMenubar = false
            )
        }

        try {
            singleTaskApp.initSettings<Any>(session)
            val socketManager = singleTaskApp.newSession(defaultUser, session)
            
            if (!serverless) {
                SessionProxyServer.agents[session] = socketManager
                val url = "http://localhost:$port/#$session"
                log.info("Task available at $url")

                if (openBrowser) {
                    try {
                        Desktop.getDesktop().browse(URI(url))
                    } catch (e: Exception) {
                        log.warn("Failed to open browser", e)
                    }
                }
            }

            log.info("Waiting for task completion...")
            if (!completionLatch.await(timeoutMinutes, TimeUnit.MINUTES)) {
                throw RuntimeException("Task timed out after $timeoutMinutes minutes")
            }

            if (error != null) {
                throw RuntimeException("Task failed", error)
            }

        } finally {
            handleBrowserShutdown()
        }
    }

    protected open fun handleBrowserShutdown() {
        if (openBrowser && !serverless) {
            val pair = trayIcon()
            val shutdownLatch = pair.first
            val trayIcon = pair.second

            try {
                shutdownLatch.await()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }

            if (trayIcon != null && SystemTray.isSupported()) {
                SystemTray.getSystemTray().remove(trayIcon)
            }
        }
    }

    protected open fun createTempDirectory(prefix: String): File {
        val time = SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())
        return File(".").resolve("workspaces/$prefix/test-$time").apply {
            mkdirs()
            log.debug("Created temp directory: ${this.absolutePath}")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(UnifiedHarness::class.java)
    }
}

fun ApiChatModel.findApi(): ApiData? {
    val userSettings = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings()
    return (userSettings.apis.find { api -> api.provider?.name == provider?.name })
}