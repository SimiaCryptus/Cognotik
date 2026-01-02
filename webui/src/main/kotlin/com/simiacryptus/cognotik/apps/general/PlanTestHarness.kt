package com.simiacryptus.cognotik.apps.general

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.models.ToolProvider
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.cognitive.CognitiveMode
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.plan.cognitive.CognitiveSchemaStrategy
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
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.awt.AWTException
import java.awt.Color
import java.awt.Desktop
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

open class PlanTestHarness(
    val prompt: String,
    val cognitiveSettings: CognitiveModeConfig,
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
    val workspace = createTempDirectory()

    fun run() {
        log.info("Running plan in ephemeral workspace: ${workspace.absolutePath}")

        val completionLatch = CountDownLatch(1)
        val session = Session.newGlobalID()
        DataStorage.sessionPaths[session] = workspace

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
                val orchestrationConfig = newConfig(session, workspace)
                val settingsFile = getSettingsFile(session, defaultUser)
                val json = orchestrationConfig.toJson()
                settingsFile.writeText(json)
                @Suppress("UNCHECKED_CAST")
                return orchestrationConfig as T
            }

            override fun newSession(user: User, session: Session): SocketManager {
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
        SessionProxyServer.chats[session] = planApp
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Plan Test App",
            inputCnt = 4,
            stickyInput = true,
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

            planApp.initSettings<Any>(session)
            SessionProxyServer.agents[session] = planApp.newSession(defaultUser, session)

            if (openBrowser) {
                try {
                    Desktop.getDesktop().browse(URI(url))
                } catch (e: Exception) {
                    log.warn("Failed to open browser", e)
                }
            }

            log.info("Waiting for plan completion (or timeout)...")
            completionLatch.await(timeoutMinutes, TimeUnit.MINUTES)

        } finally {
            if (openBrowser) {
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
            jettyServer.stop()
        }
    }

    private fun createTempDirectory(): File {
        val time = SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())
        return File(".").resolve("workspaces/${cognitiveSettings.type!!.name}/test-$time").apply {
            mkdirs()
            log.debug("Created temp directory: ${this.absolutePath}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    open fun newConfig(session: Session, tempDir: File) = OrchestrationConfig(
        sessionId = session.sessionId,
        workingDir = tempDir.absolutePath,
        defaultFastModel = fastModel.asApiChatModel(),
        defaultSmartModel = smartModel.asApiChatModel(),
        defaultImageModel = imageModel.asApiChatModel(),
        autoFix = !openBrowser,
        cognitiveSettings = cognitiveSettings,
    )

    companion object {
        fun configurePlatform() {
            OrchestrationConfig.instanceFn = { model ->
                val api = model.findApi()
                val model =
                    model.model ?: throw IllegalArgumentException("No model found for provider: ${model.provider?.name}")
                model.instance(
                    key = api?.key ?: throw IllegalArgumentException("No API key found for provider: ${model.provider?.name}"),
                    base = api.baseUrl,
                )
            }
            require(TaskType.values().isNotEmpty())
            require(ToolProvider.values().isNotEmpty())
            require(CognitiveModeType.values().isNotEmpty())
            require(CognitiveSchemaStrategy.values().isNotEmpty())
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
        fun trayIcon(): Pair<CountDownLatch, TrayIcon?> {
            val shutdownLatch = CountDownLatch(1)
            var trayIcon: TrayIcon? = null
            if (SystemTray.isSupported()) {
                val tray = SystemTray.getSystemTray()
                val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
                val g = image.createGraphics()
                g.color = Color.GREEN
                g.fillRect(0, 0, 16, 16)
                g.dispose()

                val popup = PopupMenu()
                val exitItem = MenuItem("Exit")
                exitItem.addActionListener { shutdownLatch.countDown() }
                popup.add(exitItem)

                trayIcon = TrayIcon(image, "Plan Test Harness", popup)
                trayIcon.isImageAutoSize = true
                try {
                    tray.add(trayIcon)
                } catch (e: AWTException) {
                    log.warn("TrayIcon could not be added.")
                }
            }

            val inputThread = Thread {
                try {
                    log.info("Press Enter to shut down...")
                    System.`in`.read()
                } catch (e: Exception) {
                    // ignore
                } finally {
                    shutdownLatch.countDown()
                }
            }
            inputThread.isDaemon = true
            inputThread.start()
            return Pair(shutdownLatch, trayIcon)
        }

        private val log = LoggerFactory.getLogger(PlanTestHarness::class.java)
    }
}