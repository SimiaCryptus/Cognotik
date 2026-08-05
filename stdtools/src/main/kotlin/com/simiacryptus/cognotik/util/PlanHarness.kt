package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ToolProvider
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.ApiChatModel
import com.simiacryptus.cognotik.platform.model.User
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.text.SimpleDateFormat
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
      session = session,
      user = user,
    )
  },
  val port: Int = Random.nextInt(1024, 65535),
  val serverless: Boolean = true,
  val openBrowser: Boolean = false,
  val timeoutMinutes: Long = 30,
  val fastModel: ChatModel,
  var smartModel: ChatModel,
  val imageModel: ChatModel,
  val audioModel: ChatModel,
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
    audioModel = audioModel,
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
    audioModel = audioModel.modelId,
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
        session = session,
        user = user,
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

    private val log = getLogger(PlanHarness::class.java)
    fun now(): String? = SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())
    var fix: (Exception) -> Unit = { e ->
      log.error("Error during task execution", e)
    }
  }
}