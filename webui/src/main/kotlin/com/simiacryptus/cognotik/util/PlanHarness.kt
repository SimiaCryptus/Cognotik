package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.models.ToolProvider
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.plan.cognitive.CognitiveSchemaStrategy
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.*
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.concurrent.CountDownLatch

open class PlanHarness(
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
    val serverless: Boolean = true,
    val openBrowser: Boolean = false,
    val timeoutMinutes: Long = 30,
    val fastModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    val smartModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    val imageModel: ChatModel = GeminiModels.GeminiPro_30_Image_Preview,
) {
    val workspace = createTempDirectory()
    private val harness = UnifiedHarness(
        port = port,
        openBrowser = openBrowser,
        serverless = serverless,
        modelInstanceFn = modelInstanceFn,
        fastModel = fastModel,
        smartModel = smartModel,
        imageModel = imageModel
    )

    fun run() {
        harness.start()
        try {
            harness.runPlan(
                prompt = prompt,
                cognitiveSettings = cognitiveSettings,
                timeoutMinutes = timeoutMinutes,
                autoFix = !openBrowser,
                workspace = workspace,
                config = { session: Session, finalWorkspace: File ->
                    newConfig(session, finalWorkspace)
                }
            )
        } finally {
            harness.stop()
        }
    }

    open fun newConfig(
        session: Session,
        finalWorkspace: File
    ): OrchestrationConfig = OrchestrationConfig(
        sessionId = session.sessionId,
        workingDir = finalWorkspace.absolutePath,
        defaultFastModel = fastModel.asApiChatModel(),
        defaultSmartModel = smartModel.asApiChatModel(),
        defaultImageModel = imageModel.asApiChatModel(),
        autoFix = !openBrowser,
        cognitiveSettings = cognitiveSettings,
    )

    private fun createTempDirectory(): File {
        val time = SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())
        return File(".").resolve("workspaces/${cognitiveSettings.type!!.name}/test-$time").apply {
            mkdirs()
            log.debug("Created temp directory: ${this.absolutePath}")
        }
    }

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

        fun initDynamicEnums() {
            require(TaskType.values().isNotEmpty())
            require(ToolProvider.values().isNotEmpty())
            require(CognitiveModeType.values().isNotEmpty())
            require(CognitiveSchemaStrategy.values().isNotEmpty())
            require(CodeRuntimes.values().isNotEmpty())
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

        private val log = LoggerFactory.getLogger(PlanHarness::class.java)
    }
}