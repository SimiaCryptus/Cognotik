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
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.concurrent.CountDownLatch
import kotlin.random.Random

open class PlanHarness(
    val prompt: String,
    val cognitiveSettings: CognitiveModeConfig,
    val modelInstanceFn: (ApiChatModel, Session) -> ChatInterface = { model, session ->
        val api = model.findApi()
        val model =
            model.model ?: throw IllegalArgumentException("No model found for provider: ${model.provider?.name}")
        model.instance(
            key = api?.key ?: throw IllegalArgumentException("No API key found for provider: ${model.provider?.name}"),
            base = api.baseUrl,
            onUsage = { model, usage ->
                ApplicationServices.fileApplicationServices().usageManager.incrementUsage(
                    session = session,
                    defaultUser,
                    model,
                    usage
                )
            },
        )
    },
    val port: Int = Random.nextInt(1024, 65535),
    val serverless: Boolean = true,
    val openBrowser: Boolean = false,
    val timeoutMinutes: Long = 30,
    val fastModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    var smartModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
//    val imageModel: ChatModel = GeminiModels.GeminiPro_30_Image_Preview,
    val imageModel: ChatModel = GeminiModels.GeminiFlash_25_Image_Generation,
    val workspace: File? = null,
) {

    private val harness = object : UnifiedHarness(
        port = port,
        openBrowser = openBrowser,
        serverless = serverless,
        modelInstanceFn = modelInstanceFn,
        fastModel = fastModel,
        smartModel = smartModel,
        imageModel = imageModel
    ) {
        override fun createTempDirectory(prefix: String) = createWorkspace()
    }

    fun run() {
        try {
            harness.start()
            try {
                harness.runPlan(
                    prompt = prompt,
                    cognitiveSettings = cognitiveSettings,
                    timeoutMinutes = timeoutMinutes,
                    autoFix = !openBrowser,
                    workspace = workspace,
                    config = { session: Session, finalWorkspace: File ->
                        OrchestrationConfig.instanceFn = instanceFn(session)
                        newConfig(session, finalWorkspace)
                    }
                )
            } finally {
                harness.stop()
            }
        } catch (e: Exception){
            fix(e)
            throw RuntimeException(e)
        }
    }

    open fun newConfig(
        session: Session,
        finalWorkspace: File
    ): OrchestrationConfig = OrchestrationConfig(
        sessionId = session.sessionId,
        workingDir = workspace?.absolutePath ?: finalWorkspace.absolutePath,
        defaultFastModel = fastModel.asApiChatModel(),
        defaultSmartModel = smartModel.asApiChatModel(),
        defaultImageModel = imageModel.asApiChatModel(),
        autoFix = !openBrowser,
        cognitiveSettings = cognitiveSettings,
    )

    open fun createWorkspace(): File = File(".").resolve("workspaces/${cognitiveSettings.type!!.name}/test-${now()}")
        .apply {
            mkdirs()
            log.debug("Created temp directory: ${this.absolutePath}")
    }

    companion object {
        fun configurePlatform(session: Session) {
            OrchestrationConfig.instanceFn = instanceFn(session)
            configurePlatform()
        }

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

        fun instanceFn(session: Session): (ApiChatModel) -> ChatInterface = { model ->
            val api = model.findApi()
            val model =
                model.model ?: throw IllegalArgumentException("No model found for provider: ${model.provider?.name}")
            model.instance(
                key = api?.key
                    ?: throw IllegalArgumentException("No API key found for provider: ${model.provider?.name}"),
                base = api.baseUrl,
                onUsage = { model, usage ->
                    ApplicationServices.fileApplicationServices().usageManager.incrementUsage(
                        session = session,
                        defaultUser,
                        model,
                        usage
                    )
                },
            )
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
        fun now(): String? = SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())
        var fix : (Exception) -> Unit = { e ->
            log.error("Error during task execution", e)
        }
    }
}