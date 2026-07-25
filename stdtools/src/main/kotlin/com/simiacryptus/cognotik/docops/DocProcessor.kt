package com.simiacryptus.cognotik.docops

import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.docops.exec.DocExecutionContext
import com.simiacryptus.cognotik.docops.exec.DocTaskCallbacks
import com.simiacryptus.cognotik.docops.exec.DocTaskInferenceRequest
import com.simiacryptus.cognotik.docops.exec.DocTaskKind
import com.simiacryptus.cognotik.docops.exec.DocTaskKindResolver
import com.simiacryptus.cognotik.docops.exec.DocTaskRequest
import com.simiacryptus.cognotik.docops.exec.DocTaskScheduler
import com.simiacryptus.cognotik.docops.model.DocSpec
import com.simiacryptus.cognotik.docops.model.WorkPlan
import com.simiacryptus.cognotik.docops.spec.TemplateEngine
import com.simiacryptus.cognotik.plan.cognitive.ConversationalMode
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask.FileTaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.plan.tools.newSettings
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask
import com.simiacryptus.cognotik.plan.tools.writing.RenderErbTemplateTask.RenderErbTemplateTaskExecutionConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.FixedConcurrencyProcessor
import com.simiacryptus.cognotik.util.PlanHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import com.simiacryptus.cognotik.util.asChatInterface
import com.simiacryptus.cognotik.util.jsonCast
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adapts a platform [TaskType] to the platform-neutral [DocTaskKind] abstraction.
 */
data class PlatformTaskKind(val taskType: TaskType<*, *>) : DocTaskKind {
  override val name: String get() = taskType.name

  override val isFileTask: Boolean
    get() = FileTaskExecutionConfig::class.java.isAssignableFrom(taskType.executionConfigClass)

  override val isSubPlanTask: Boolean
    get() = SubPlanTask.SubPlanTaskExecutionConfigData::class.java
      .isAssignableFrom(taskType.executionConfigClass)

  override val isTemplateTask: Boolean
    get() = RenderErbTemplateTaskExecutionConfig::class.java
      .isAssignableFrom(taskType.executionConfigClass)

  override fun defaultConfig(): Map<String, Any>? = try {
    taskType.newSettings()?.jsonCast<Map<String, Any>>()
  } catch (e: Exception) {
    DocProcessor.log.warn("Failed to serialize default settings for task type ${taskType.name}", e)
    null
  }
}

/** Resolves `task_type:` frontmatter values against the platform [TaskType] registry. */
object PlatformTaskKinds : DocTaskKindResolver<PlatformTaskKind> {
  override val default: PlatformTaskKind = PlatformTaskKind(FileModification)
  override fun byName(name: String): PlatformTaskKind? = try {
    PlatformTaskKind(TaskType.valueOf(name.replace(" ", "")))
  } catch (e: IllegalArgumentException) {
    DocProcessor.log.debug("Unknown task type name: $name", e)
    null
  }
}

/** [DocTaskScheduler] backed by the platform thread pools. */
class FixedConcurrencyScheduler(private val pool: FixedConcurrencyProcessor) : DocTaskScheduler {
  override fun submit(block: () -> Unit): CompletableFuture<*> = pool.submit { block() }
}

/**
 * Platform binding of the doc-ops engine: task kinds are [TaskType]s and sessions are [Session]s.
 *
 * This class contains *only* the three [DocOpsHost] bindings plus a thin convenience API;
 * all planning lives in [DocOps] and all execution in `DocTaskRunner`/[UnifiedHarness].
 */
class DocProcessor(
  val root: File,
  val docsFolder: File,
  updateMode: UpdateMode = UpdateModes.PatchToUpdate,
  additionalContext: (DocSpec, File) -> List<String> = { _, _ -> emptyList() },
  val smartModel: ChatModel,
  val fastModel: ChatModel = smartModel,
  val imageModel: ChatModel = fastModel,
  val audioModel: ChatModel = imageModel,
  val serverless: Boolean = false,
  val openBrowser: Boolean = false,
  urlCacheDir: File = File(root, ".doc-processor-cache/url-cache"),
  val autoFix: Boolean,
  val user: User,
  val parentSession: Session? = null,
  templateVarOverrides: Map<String, String> = emptyMap(),
  var showMenubar: Boolean = true,
) : DocOpsHost<PlatformTaskKind, Session> {

  val config: DocOpsConfig = DocOpsConfig(
    root = root,
    docsFolder = docsFolder,
    updateMode = updateMode,
    additionalContext = additionalContext,
    urlCacheDir = urlCacheDir,
    templateVarOverrides = templateVarOverrides,
  )

  val docOps: DocOps<PlatformTaskKind, Session> = DocOps(config, this)

  /*
   * ------------------------------------------------------------------
   * Host bindings (the entire contract)
   * ------------------------------------------------------------------
   */

  override val taskKinds: DocTaskKindResolver<PlatformTaskKind> = PlatformTaskKinds

  override fun newScheduler(): DocTaskScheduler = FixedConcurrencyScheduler(newProcessor(user = user))

  override fun newExecutionContext(): DocExecutionContext<PlatformTaskKind, Session> =
    HarnessExecutionContext()

  /*
   * ------------------------------------------------------------------
   * Convenience API (delegates to DocOps)
   * ------------------------------------------------------------------
   */

  /** Plan (no execution) for specific doc files; omit the argument to plan the whole folder. */
  fun getAll(vararg markdownFiles: File): WorkPlan<PlatformTaskKind> =
    if (markdownFiles.isEmpty()) docOps.plan() else docOps.plan(markdownFiles.toList())

  /** Plan and execute every document under [docsFolder]. */
  fun run(): List<Session> = docOps.run()

  fun runAll(
    plan: WorkPlan<PlatformTaskKind>,
    pool: FixedConcurrencyProcessor,
    cancelFlag: AtomicBoolean = AtomicBoolean(false),
    onNewSession: (Session) -> Unit = { _ -> }
  ): Array<Session> = execute(plan, FixedConcurrencyScheduler(pool), cancelFlag, onNewSession)

  fun runAll(
    plan: WorkPlan<PlatformTaskKind> = docOps.plan(),
    cancelFlag: AtomicBoolean = AtomicBoolean(false),
    onNewSession: (Session) -> Unit = { _ -> }
  ): Array<Session> = execute(plan, newScheduler(), cancelFlag, onNewSession)

  private fun execute(
    plan: WorkPlan<PlatformTaskKind>,
    scheduler: DocTaskScheduler,
    cancelFlag: AtomicBoolean,
    onNewSession: (Session) -> Unit,
  ): Array<Session> = docOps.run(
    plan = plan,
    scheduler = scheduler,
    cancelFlag = cancelFlag,
    onNewSession = onNewSession,
  ).toTypedArray()

  /*
   * ------------------------------------------------------------------
   * Execution scope (one per work queue)
   * ------------------------------------------------------------------
   */
  inner class HarnessExecutionContext : DocExecutionContext<PlatformTaskKind, Session> {

    val harness: UnifiedHarness = object : UnifiedHarness(
      serverless = serverless,
      openBrowser = openBrowser,
      fastModel = fastModel,
      smartModel = smartModel,
      imageModel = imageModel,
      audioModel = audioModel,
      showMenubar = showMenubar,
      user = user
    ) {
      override fun createTempDirectory(prefix: String) = root
        .resolve("workspaces/${javaClass.simpleName}/test-${PlanHarness.now()}")
        .apply { mkdirs() }
    }

    override fun reset() {
      harness.resetSession()
    }

    override fun inferTaskConfig(
      request: DocTaskInferenceRequest<PlatformTaskKind>
    ): Map<String, Any> {
      request.patchProcessor?.apply { harness.processor = this }
      val model = chatInterface()
      val (_, taskConfig) = ConversationalMode.requestToTask(
        defaultModel = model,
        fastModel = model,
        userMessage = request.taskDescription,
        orchestrationConfig = harness.createSettings(
          session = Session.newUserID(),
          autoFix = true,
          typeConfig = request.typeConfig.toTypeConfig(request.taskKind),
          workingDir = request.workingDir.toString(),
        ),
        prompt = request.prompt,
        history = request.history,
        singleStage = true,
        taskTypes = listOf(request.taskKind.taskType)
      )
      return taskConfig.jsonCast<Map<String, Any>>()
    }

    override fun execute(
      request: DocTaskRequest<PlatformTaskKind>,
      callbacks: DocTaskCallbacks<Session>
    ) {
      harness.runTask(
        taskType = request.taskKind.taskType,
        timeoutMinutes = request.timeoutMinutes.toLong(),
        message = request.message,
        executionConfig = request.executionConfig
          .jsonCast(request.taskKind.taskType.executionConfigClass),
        parentSession = parentSession,
        onComplete = { _: String, task: SessionTask ->
          callbacks.onCompleted(task.ui.sessionId.toString())
        },
        onError = { error: Throwable ->
          callbacks.onFailed(error)
        }
      ) { session ->
        callbacks.onSessionStarted(session, session.toString())
        harness.createSettings(
          session = session,
          autoFix = autoFix,
          typeConfig = request.typeConfig.toTypeConfig(request.taskKind),
          workingDir = request.workingDir.toString()
        ).apply {
          processor = request.patchProcessor ?: processor
        }
      }
    }

    override fun close() {
      harness.close()
    }

    private fun chatInterface(): ChatInterface = harness.fastModel.asChatInterface(user)
      .let { if (parentSession != null) it.getChildClient(parentSession) else it }

    private fun Map<String, Any>.toTypeConfig(kind: PlatformTaskKind): TaskTypeConfig = try {
      jsonCast<TaskTypeConfig>()
    } catch (e: Exception) {
      log.warn("Failed to deserialize type config for ${kind.name}, using defaults", e)
      kind.taskType.newSettings() ?: TaskTypeConfig(task_type = kind.name)
    }
  }

  companion object {
    internal val log = LoggerFactory.getLogger(DocProcessor::class.java)

    fun listTemplateVarKeys(file: File): Map<String, String> = TemplateEngine.listKeys(file)

    fun listTemplateVarKeys(files: Iterable<File>): Map<String, String> = TemplateEngine.listKeys(files)

    fun newProcessor(
      session: Session = Session.newUserID(), concurrency: Int = 4, user: User
    ): FixedConcurrencyProcessor =
      FixedConcurrencyProcessor(
        ApplicationServices.threadPoolManager.getPool(
          session,
          user
        ), concurrency
      )
  }
}