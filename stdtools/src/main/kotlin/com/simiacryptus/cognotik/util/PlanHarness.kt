package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ToolProvider
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApiData
import com.simiacryptus.cognotik.platform.model.User
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.concurrent.CountDownLatch
import kotlin.random.Random

open class PlanHarness(
  val prompt: String,
  val cognitiveSettings: CognitiveModeConfig,
  val modelInstanceFn: (ApiChatModel, Session, User) -> ChatInterface = { model, session, user ->
    val api = model.findApi(user)
    val model =
      model.model ?: throw IllegalArgumentException("No model found for provider: ${model.provider?.name}")
    model.instance(
      key = api?.key ?: throw IllegalArgumentException("No API key found for provider: ${model.provider?.name}"),
      base = api.apiBase ?: throw IllegalArgumentException("No API base found for provider: ${model.provider?.name}"),
      onUsage = { model, usage ->
        ApplicationServices.fileApplicationServices().usageManager.incrementUsage(
          session = session,
          user,
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
  val fastModel: ChatModel,
  var smartModel: ChatModel,
  val imageModel: ChatModel,
  val workspace: File? = null,
  val user: User,
) {

  private val harness = object : UnifiedHarness(
    port = port,
    serverless = serverless,
    openBrowser = openBrowser,
    modelInstanceFn = modelInstanceFn,
    fastModel = fastModel,
    smartModel = smartModel,
    imageModel = imageModel,
    showMenubar = true,
    user = user
  ) {
    override fun createTempDirectory(prefix: String) = createTempDirectory()
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
    } catch (e: Exception) {
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
    fastModel = fastModel.modelId,
    smartModel = smartModel.modelId,
    imageModel = imageModel.modelId,
    autoFix = !openBrowser,
    cognitiveSettings = cognitiveSettings,
    user = user,
  )

  open fun createTempDirectory(): File = File(".").resolve("workspaces/${cognitiveSettings.type!!.name}/test-${now()}")
    .apply {
      mkdirs()
      log.debug("Created temp directory: ${this.absolutePath}")
    }

  companion object {
    fun configurePlatform(session: Session, user: User) {
      OrchestrationConfig.instanceFn = instanceFn(session)
      UnifiedHarness.configurePlatform(user)
    }

    fun instanceFn(session: Session): (ApiChatModel, User) -> ChatInterface = { model, user ->
      val api = model.findApi(user)
      val model =
        model.model ?: throw IllegalArgumentException("No model found for provider: ${model.provider?.name}")
      model.instance(
        key = api?.key
          ?: throw IllegalArgumentException("No API key found for provider: ${model.provider?.name}"),
        base = api.apiBase,
        onUsage = { model, usage ->
          ApplicationServices.fileApplicationServices().usageManager.incrementUsage(
            session = session,
            user,
            model,
            usage
          )
        },
      )
    }

    @JvmStatic
    fun initDynamicEnums() {
      require(APIProvider.values().isNotEmpty())
      require(TaskType.values().isNotEmpty())
      require(ToolProvider.values().isNotEmpty())
      require(CognitiveModeType.values().isNotEmpty())
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
    var fix: (Exception) -> Unit = { e ->
      log.error("Error during task execution", e)
    }
  }
}
