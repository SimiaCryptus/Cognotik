package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.apps.SinglePlanApp
import com.simiacryptus.cognotik.apps.SingleTaskApp
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveMode
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.CognotikAppServer
import com.simiacryptus.cognotik.webui.session.ServerlessSocketManager
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.eclipse.jetty.server.Server
import org.slf4j.LoggerFactory.getLogger
import java.awt.Desktop
import java.io.File
import java.io.OutputStream
import java.lang.AutoCloseable
import java.net.URI
import java.text.SimpleDateFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

open class UnifiedHarness(
  val port: Int = Random.nextInt(1024, 65535),
  val serverless: Boolean = true,
  val openBrowser: Boolean = false,
  val captureMessages: Boolean = serverless,
  val redirectData: Boolean = serverless,
  val modelInstanceFn: (ApiChatModel, Session, User) -> ChatInterface = { model, session, user ->
    val api = model.provider
    if (api == null) {
      throw IllegalArgumentException("No API found for model: ${model.toJson()}")
    }
    val model = model.model ?: throw IllegalArgumentException("No model found for provider: ${model.provider?.name}")
    model.instance(
      key = (if (api.key != null) api.key else {
        throw IllegalArgumentException("No API key found for provider: ${model.provider?.name}")
      })!!,
      base = api.apiBase,
      session = session,
      user = user,
    )
  },
  val smartModel: ChatModel,
  val fastModel: ChatModel = smartModel,
  val imageModel: ChatModel = fastModel,
  val audioModel: ChatModel = imageModel,
  val temperature: Double = 0.0,
  var processor: PatchProcessor = PatchProcessors.Fuzzy,
  val showMenubar: Boolean,
  val name: String = "Cognotik",
  val user: User
) : AutoCloseable {
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

  var session = Session.newUserID()
    private set

  fun resetSession() {
    session = Session.newUserID()
  }

  open fun runPlan(
    prompt: String,
    cognitiveSettings: CognitiveModeConfig,
    timeoutMinutes: Long = 30,
    autoFix: Boolean = !openBrowser,
    workspace: File? = null,
    config: (Session, File) -> OrchestrationConfig = { session: Session, finalWorkspace: File ->
      createSettings(
        session,
        finalWorkspace,
        autoFix,
        cognitiveSettings
      )
    }
  ) {
    val completionLatch = CountDownLatch(1)
    val session = this.session
    val planApp = object : SinglePlanApp(
      path = "/test",
      applicationName = name,
      showMenubar = showMenubar,
      useExpansionSyntax = true
    ) {
      override fun onComplete(mode: CognitiveMode<*>, task: SessionTask) {
        task.resolveSystemFile("results.md")?.writeText(mode.contextData().joinToString("\n\n"))
        val usageManager = ApplicationServices.fileApplicationServices().usageDB
        task.resolveSystemFile("usage.json")?.writeText(usageManager.getSessionUsageSummary(session).toJson())
        super.onComplete(mode, task)
      }

      override fun <T : Any> initSettings(session: Session, user: User): T {
        val orchestrationConfig = config(session, getRoot(workspace, session, cognitiveSettings.type?.name ?: "plan"))
        val settingsFile = getSettingsFile(session, this@UnifiedHarness.user)
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
        applicationName = name,
        inputCnt = 0,
        stickyInput = false,
        showMenubar = showMenubar
      )
    }

    try {
      planApp.initSettings<Any>(session, user)
      val socketManager = planApp.newSession(user, session)
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
    }
  }

  fun <T : TaskExecutionConfig, U : TaskTypeConfig> runTask(
    taskType: TaskType<T, U>,
    timeoutMinutes: Long = 30,
    message: String = "Execute task",
    executionConfig: TaskExecutionConfig,
    parentSession: Session? = null,
    onComplete: (result: String, task: SessionTask) -> Unit = { _, _ -> },
    onError: (Throwable) -> Unit = { _ -> },
    initSettings: (Session) -> OrchestrationConfig
  ): Session {
    val completionLatch = CountDownLatch(1)
    var error: Throwable? = null
    val session = this.session

    val singleTaskApp = object : SingleTaskApp(
      path = "/test",
      taskType = taskType,
      instanceFn = { model, user -> modelInstanceFn(model, session, user) },
      message = message,
      taskConfig = executionConfig,
      user = user,
    ) {
      override fun instance(model: ApiChatModel) = modelInstanceFn(model, session, user)

      override fun getOrchestrationConfig(session: Session, user: User) = initSettings(session)

      override fun onTaskComplete(result: String, task: SessionTask) {
        log.info("Task completed successfully")
        task.resolveSystemFile("result.md")?.writeText(result)
        val usageManager = ApplicationServices.fileApplicationServices().usageDB
        task.resolveSystemFile("usage.json")?.writeText(usageManager.getSessionUsageSummary(session).toJson())
        completionLatch.countDown()
        onComplete(result, task)
      }

      override fun onTaskError(e: Throwable) {
        log.error("Task failed", e)
        error = e
        completionLatch.countDown()
        onError(e)
      }


      override fun newSession(user: User, session: Session): SocketManager {
        if (serverless) {
          val socketManager = ServerlessSocketManager(
            session = session,
            messageEvents = null,
            owner = user,
            clazz = this.javaClass
          )
          startSession(
            session,
            user,
            socketManager,
          )
          return socketManager
        } else {
          return super.newSession(user, session)
        }
      }
    }

    if (!serverless) {
      parentSession?.apply { SessionProxyServer.setParentSession(child = session, parent = this) }
      SessionProxyServer.chats[session] = singleTaskApp
      ApplicationServer.appInfoMap[session] = AppInfoData(
        applicationName = name,
        inputCnt = 0,
        stickyInput = false,
        showMenubar = showMenubar
      )
    }

    singleTaskApp.initSettings<Any>(session, user)
    val socketManager = singleTaskApp.newSession(user, session)

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

    log.debug("Waiting for task completion...")
    if (!completionLatch.await(timeoutMinutes, TimeUnit.MINUTES)) {
      throw RuntimeException("Task timed out after $timeoutMinutes minutes")
    }

    if (error != null) {
      throw RuntimeException("Task failed", error)
    }

    return session
  }

  open fun createSettings(
    session: Session,
    finalWorkspace: File,
    autoFix: Boolean,
    cognitiveSettings: CognitiveModeConfig
  ): OrchestrationConfig = OrchestrationConfig(
    sessionId = session.sessionId,
    workingDir = finalWorkspace.absolutePath,
    fastModel = fastModel.modelId,
    smartModel = smartModel.modelId,
    imageModel = imageModel.modelId,
    audioModel = audioModel.modelId,
    autoFix = autoFix,
    temperature = temperature,
    cognitiveSettings = cognitiveSettings,
    user = user,
  )

  open fun <U : TaskTypeConfig> createSettings(
    session: Session,
    autoFix: Boolean,
    typeConfig: U,
    workingDir: String
  ): OrchestrationConfig = OrchestrationConfig(
    sessionId = session.sessionId,
    workingDir = workingDir,
    taskSettings = mutableMapOf(
      typeConfig.name!! to typeConfig
    ),
    fastModel = fastModel.modelId,
    smartModel = smartModel.modelId,
    imageModel = imageModel.modelId,
    audioModel = audioModel.modelId,
    autoFix = autoFix,
    temperature = temperature,
    user = user,
  ).apply {
    this@apply.processor = this@UnifiedHarness.processor
  }

  open fun getRoot(
    workspace: File?,
    session: Session,
    name: String
  ): File {
    val tempDirectory = createTempDirectory(name)
    log.info("Running task in workspace: ${tempDirectory.absolutePath}")
    DataStorage.userPaths[session] = tempDirectory
    if (redirectData) DataStorage.systemPaths[session] = tempDirectory
    return workspace ?: tempDirectory
  }

  private fun getMessageLog(workspace: File?): OutputStream? =
    if (captureMessages) workspace?.resolve(".logs/messageEvents_${time()}.log")?.apply {
      parentFile?.mkdirs()
    }?.outputStream()?.buffered() else null

  protected open fun createTempDirectory(prefix: String): File {
    val time = SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())
    return File(".").resolve("workspaces/$prefix/test-$time").apply {
      mkdirs()
      log.debug("Created temp directory: ${this.absolutePath}")
    }
  }

  override fun close() {
    stop()
  }

  companion object {
    private val log = getLogger(UnifiedHarness::class.java)
    fun time(): String {
      val sdf = SimpleDateFormat("yyyyMMdd_HHmmss")
      return sdf.format(System.currentTimeMillis())
    }


    @JvmStatic
    fun configurePlatform(user: User) {
      PlanHarness.initDynamicEnums()
      ApplicationServices.authenticationManager = object : AuthenticationInterface {
        override fun getUser(accessToken: String?) = user
        override fun getAccessToken(user: User) = "test-token"
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

fun ApiChatModel.findApi(user: User): ApiData? {
  val userSettings = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(user)
  return (userSettings.apis.find { api -> api.provider?.name == provider?.name })
}