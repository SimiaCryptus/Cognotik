package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.User
import java.io.File
import java.text.SimpleDateFormat
import kotlin.random.Random

open class TaskHarness<T : TaskExecutionConfig, U : TaskTypeConfig>(
  val taskType: TaskType<T, U>,
  val typeConfig: U,
  val executionConfig: T,
  val modelInstanceFn: (ApiChatModel, Session, User) -> ChatInterface = { model, session, user ->
    val api = model.findApi(user)
    val model =
      model.model ?: throw IllegalArgumentException("No model found for provider: ${model.provider?.name}")
    model.instance(
      key = api?.key ?: throw IllegalArgumentException("No API key found for provider: ${model.provider?.name}"),
      base = api.baseUrl,
      onUsage = { model, usage ->
        ApplicationServices.fileApplicationServices().usageManager.incrementUsage(
          session,
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
  val fastModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
  val smartModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
  val imageModel: ChatModel = GeminiModels.GeminiFlash_25_Image_Generation,
  val workspace: File? = null,
  val temperature: Double = 0.0,
  val user: User,
) {
  val dataDir: File by lazy { createWorkspace() }
  fun run() {
    val harness = object : UnifiedHarness(
      port = port,
      serverless = serverless,
      openBrowser = openBrowser,
      modelInstanceFn = modelInstanceFn,
      fastModel = fastModel,
      smartModel = smartModel,
      imageModel = imageModel,
      temperature = temperature,
      showMenubar = true,
      user = user,
    ) {
      override fun createTempDirectory(prefix: String) = dataDir
    }
    try {
      harness.start()
      try {
        harness.runTask(
          taskType = taskType,
          timeoutMinutes = timeoutMinutes,
          executionConfig = TaskExecutionConfig(task_type = taskType.name)
        ) { initSettings(it, workspace, !openBrowser, taskType, typeConfig, harness) }
      } finally {
        harness.stop()
      }
    } catch (e: Exception) {
      fix(e)
      throw RuntimeException(e)
    }
  }

  open fun <T : TaskExecutionConfig, U : TaskTypeConfig> initSettings(
    session: Session,
    workspace: File?,
    autoFix: Boolean,
    taskType: TaskType<T, U>,
    typeConfig: U,
    harness: UnifiedHarness
  ) = harness.createSettings(
    session = session,
    autoFix = autoFix,
    typeConfig = typeConfig,
    workingDir = harness.getRoot(workspace, session, taskType.name).absolutePath,
  )

  open fun createWorkspace(): File {
    val name = this.taskType.name
    val time = SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())
    return File(".").resolve("workspaces/$name/test-$time").apply {
      mkdirs()
      log.debug("Created temp directory: ${this.absolutePath}")
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(TaskHarness::class.java)
    var fix: (Exception) -> Unit = { e ->
      log.error("Error during task execution", e)
    }
  }
}