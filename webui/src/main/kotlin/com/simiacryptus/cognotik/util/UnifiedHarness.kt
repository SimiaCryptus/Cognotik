package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.apps.SingleTaskApp
import com.simiacryptus.cognotik.apps.UnifiedPlanApp
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveMode
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApiData
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.asApiChatModel
import com.simiacryptus.cognotik.util.PlanHarness.Companion.initDynamicEnums
import com.simiacryptus.cognotik.util.PlanHarness.Companion.trayIcon
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
import java.io.OutputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

open class UnifiedHarness(
    val port: Int = Random.nextInt(1024, 65535),
    val serverless: Boolean = false,
    val openBrowser: Boolean = false,
    val captureMessages: Boolean = serverless,
    val redirectData: Boolean = serverless,
    val modelInstanceFn: (ApiChatModel, Session) -> ChatInterface = { model,session ->
        val api = model.findApi()
        val model =
            model.model ?: throw IllegalArgumentException("No model found for provider: ${model.provider?.name}")
        model.instance(
            key = api?.key ?: throw IllegalArgumentException("No API key found for provider: ${model.provider?.name}"),
            base = api.baseUrl,
            onUsage = { model, usage ->
                ApplicationServices.fileApplicationServices().usageManager.incrementUsage(
                    session = session,
                    UserSettingsManager.defaultUser,
                    model,
                    usage
                )
            },
        )
    },
    val fastModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    val smartModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    val imageModel: ChatModel = GeminiModels.GeminiPro_30_Image_Preview,
    val temperature: Double = 0.0,
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

    var session = Session.newGlobalID()
        private set

    fun resetSession() {
        session = Session.newGlobalID()
    }

    open fun runPlan(
        prompt: String,
        cognitiveSettings: CognitiveModeConfig,
        timeoutMinutes: Long = 30,
        autoFix: Boolean = !openBrowser,
        workspace: File? = null,
        config: (Session, File) -> OrchestrationConfig = { session: Session, finalWorkspace: File ->
            initSettings(
                session,
                finalWorkspace,
                autoFix,
                cognitiveSettings
            )
        }
    ) {
        val completionLatch = CountDownLatch(1)
        val session = this.session
        val planApp = object : UnifiedPlanApp(
            path = "/test",
            applicationName = "Plan Test App",
            showMenubar = false,
            useExpansionSyntax = true
        ) {
            override fun instance(model: ApiChatModel) = modelInstanceFn(model,session)

            override fun onComplete(mode: CognitiveMode<*>, task: SessionTask) {
                task.resolveUserFile("results.md")?.writeText(mode.contextData().joinToString("\n\n"))
                val usageManager = ApplicationServices.fileApplicationServices().usageManager
                task.resolveUserFile("usage.json")?.writeText(usageManager.getSessionUsageSummary(session).toJson())
                super.onComplete(mode, task)
            }

            override fun <T : Any> initSettings(session: Session): T {
                val orchestrationConfig = config(session, getRoot(workspace, session, cognitiveSettings.type?.name ?: "plan"))
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
                        messageEvents = getMessageLog(workspace),
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
            handleBrowserShutdown(session)
        }
    }


    open fun <T : TaskExecutionConfig, U : TaskTypeConfig> runTask(
        taskType: TaskType<T, U>,
        typeConfig: U,
        executionConfig: T,
        timeoutMinutes: Long = 30,
        autoFix: Boolean = !openBrowser,
        workspace: File? = null,
        initSettings : (Session) -> OrchestrationConfig = { session ->
            initSettings(session, workspace, autoFix, taskType, typeConfig)
        }
    ) {
        val completionLatch = CountDownLatch(1)
        var error: Throwable? = null
        val session = this.session

        val singleTaskApp = object : SingleTaskApp(
            path = "/test",
            taskType = taskType,
            taskConfig = executionConfig,
            instanceFn = { model -> modelInstanceFn(model,session) },
        ) {
            override fun instance(model: ApiChatModel) = modelInstanceFn(model,session)

            override fun onTaskComplete(result: String, task: SessionTask) {
                log.info("Task completed successfully")
                task.resolveUserFile("result.md")?.writeText(result)
                val usageManager = ApplicationServices.fileApplicationServices().usageManager
                task.resolveUserFile("usage.json")?.writeText(usageManager.getSessionUsageSummary(session).toJson())
                completionLatch.countDown()
            }

            override fun onTaskError(e: Throwable) {
                log.error("Task failed", e)
                error = e
                completionLatch.countDown()
            }

            override fun <T : Any> initSettings(session: Session): T {
                val orchestrationConfig = initSettings(session)
                val json = orchestrationConfig.toJson()
                getSettingsFile(session, defaultUser).writeText(json)
                @Suppress("UNCHECKED_CAST")
                return orchestrationConfig as T
            }

            override fun newSession(user: User, session: Session): SocketManager {
                if (serverless) {
                    val socketManager = ServerlessSocketManager(
                        session = session,
                        messageEvents = getMessageLog(workspace),
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
            handleBrowserShutdown(session)
        }
    }
    open fun initSettings(
        session: Session,
        finalWorkspace: File,
        autoFix: Boolean,
        cognitiveSettings: CognitiveModeConfig
    ): OrchestrationConfig = OrchestrationConfig(
        sessionId = session.sessionId,
        workingDir = finalWorkspace.absolutePath,
        defaultFastModel = fastModel.asApiChatModel(),
        defaultSmartModel = smartModel.asApiChatModel(),
        defaultImageModel = imageModel.asApiChatModel(),
        autoFix = autoFix,
        temperature = temperature,
        cognitiveSettings = cognitiveSettings,
    )

    open fun <T : TaskExecutionConfig, U : TaskTypeConfig> initSettings(
        session: Session,
        workspace: File?,
        autoFix: Boolean,
        taskType: TaskType<T, U>,
        typeConfig: U
    ): OrchestrationConfig = OrchestrationConfig(
        sessionId = session.sessionId,
        workingDir = getRoot(workspace, session, taskType.name).absolutePath,
        taskSettings = mutableMapOf(
            typeConfig.name!! to typeConfig
        ),
        defaultFastModel = fastModel.asApiChatModel(),
        defaultSmartModel = smartModel.asApiChatModel(),
        defaultImageModel = imageModel.asApiChatModel(),
        autoFix = autoFix,
        temperature = temperature,
    )

    open fun getRoot(
        workspace: File?,
        session: Session,
        name: String
    ): File {
        val tempDirectory = createTempDirectory(name)
        log.info("Running task in workspace: ${tempDirectory.absolutePath}")
        DataStorage.sessionPaths[session] = tempDirectory
        if (redirectData) DataStorage.dataPaths[session] = tempDirectory
        return workspace ?: tempDirectory
    }

    private fun getMessageLog(workspace: File?): OutputStream? =
        if (captureMessages) workspace?.resolve(".logs/messageEvents_${time()}.log")?.apply {
            parentFile?.mkdirs()
        }?.outputStream()?.buffered() else null

    protected open fun handleBrowserShutdown(session: Session) {

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
        fun time(): String {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss")
            return sdf.format(System.currentTimeMillis())
        }


        @JvmStatic
        fun configurePlatform() {
            initDynamicEnums()
            ApplicationServices.authenticationManager = object : AuthenticationInterface {
                override fun getUser(accessToken: String?) = defaultUser
                override fun putUser(accessToken: String, user: User) = throw UnsupportedOperationException()
                override fun logout(accessToken: String, user: User) {}
            }
            ApplicationServices.authorizationManager = object : AuthorizationManager() {
                override fun isAuthorized(
                    applicationClass: Class<*>?,
                    user: User?,
                    operationType: AuthorizationInterface.OperationType
                ): Boolean = true
            }
        }
    }
}

fun ApiChatModel.findApi(): ApiData? {
    val userSettings = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings()
    return (userSettings.apis.find { api -> api.provider?.name == provider?.name })
}