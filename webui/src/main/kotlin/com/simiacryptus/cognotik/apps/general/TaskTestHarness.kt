package com.simiacryptus.cognotik.apps.general

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.models.ToolProvider
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.CognotikAppServer
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

open class TaskTestHarness<T : TaskExecutionConfig, U : TaskTypeConfig>(
    val taskType: TaskType<T, U>,
    val typeConfig: U,
    val executionConfig: T,
    val modelInstanceFn: (ApiChatModel) -> ChatInterface = { model ->
        val api = model.findApi()
        val model =
            model.model ?: throw IllegalArgumentException("No model found for provider: ${model.provider?.name}")
        model.instance(
            key = api?.key ?: throw IllegalArgumentException("No API key found for provider: ${model.provider?.name}"),
            base = api.baseUrl,
        )
    },
    val port: Int = 8082,
    val openBrowser: Boolean = false,
    val timeoutMinutes: Long = 30,
    val fastModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    val smartModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    val imageModel: ChatModel = GeminiModels.GeminiPro_30_Image_Preview,
) {
    private val log = LoggerFactory.getLogger(TaskTestHarness::class.java)
    val workspace = createTempDirectory()

    fun run() {
        log.info("Running task in ephemeral workspace: ${workspace.absolutePath}")

        val completionLatch = CountDownLatch(1)
        var error: Throwable? = null
        val session = Session.newGlobalID()
        DataStorage.sessionPaths[session] = workspace

        val singleTaskApp = object : SingleTaskApp(
            path = "/test",
            taskType = taskType,
            taskConfig = executionConfig,
            instanceFn = modelInstanceFn
        ) {
            override fun instance(model: ApiChatModel) = modelInstanceFn(model)

            override fun onTaskComplete(result: String) {
                log.info("Task completed successfully")
                completionLatch.countDown()
            }

            override fun onTaskError(e: Throwable) {
                log.error("Task failed", e)
                error = e
                completionLatch.countDown()
            }

            override fun <T : Any> initSettings(session: Session): T {
                val orchestrationConfig = newConfig(session, workspace)
                val settingsFile = getSettingsFile(session, defaultUser)
                val json = orchestrationConfig.toJson()
                settingsFile.writeText(json)
                @Suppress("UNCHECKED_CAST")
                return orchestrationConfig as T
            }
        }
        SessionProxyServer.chats[session] = singleTaskApp
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Single Task App",
            inputCnt = 0,
            stickyInput = false,
            showMenubar = false
        )

        val server = CognotikAppServer(
            localName = "localhost",
            port = port
        )
        val jettyServer = server.start()

        try {
            val url = "http://localhost:$port/#$session"
            log.info("Server started at $url")

            singleTaskApp.initSettings<Any>(session)
            singleTaskApp.newSession(User(email = "test@example.com"), session)

            if (openBrowser) {
                try {
                    Desktop.getDesktop().browse(URI(url))
                } catch (e: Exception) {
                    log.warn("Failed to open browser", e)
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
            if(openBrowser) {
                javax.swing.JOptionPane.showMessageDialog(
                    null,
                    "Task completed. Click OK to shut down the server and exit.",
                    "Task Test Harness",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE
                )
            }
            jettyServer.stop()
        }
    }

    private fun createTempDirectory(): File {
        val name = this.taskType.name
        val time = SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())
        return File(".").resolve("workspaces/$name/test-$time").apply {
            mkdirs()
            log.debug("Created temp directory: ${this.absolutePath}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    open fun newConfig(session: Session, tempDir: File) = OrchestrationConfig(
        sessionId = session.sessionId,
        workingDir = tempDir.absolutePath,
        taskSettings = mutableMapOf(
            typeConfig.name!! to typeConfig
        ),
        defaultFastModel = fastModel.asApiChatModel(),
        defaultSmartModel = smartModel.asApiChatModel(),
        defaultImageModel = imageModel.asApiChatModel(),
        autoFix = !openBrowser,
    )

    companion object {
        fun configurePlatform() {
            require(TaskType.values().isNotEmpty())
            require(ToolProvider.values().isNotEmpty())
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