package com.simiacryptus.cognotik.cli

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.CoreTasks
import com.simiacryptus.cognotik.chat.model.ChatMessageModality
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.docops.DocProcessor
import com.simiacryptus.cognotik.docops.PlatformTaskKind
import com.simiacryptus.cognotik.docops.UpdateMode
import com.simiacryptus.cognotik.docops.model.WorkPlan
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.FileApplicationServices
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.UserSettingsInterface
import com.simiacryptus.cognotik.util.UnifiedHarness
import com.simiacryptus.cognotik.webui.servlet.ApiProviderServlet.Companion.models
import com.simiacryptus.cognotik.webui.servlet.ApiProviderServlet.Companion.userSettings
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Headless DocOps runtime plumbing (platform bootstrap, model resolution, and [DocProcessor]
 * construction) shared by [DocOpsCli] and [ServerTaskActions].
 *
 * This exists so that in-process embedders - the file server's `docops` FS action in
 * particular - can plan and run documents directly against the [DocProcessor] /
 * [com.simiacryptus.cognotik.docops.DocOps] API instead of shelling out to [DocOpsCli.run]'s
 * argv parsing and human-oriented stdout.
 */
object DocOpsSupport {

  data class Models(
    val smart: ChatModel,
    val fast: ChatModel,
    val image: ChatModel,
    val audio: ChatModel,
  )

  private val servicesCache = ConcurrentHashMap<File, FileApplicationServices>()
  private val bootstrapped = AtomicBoolean(false)

  fun defaultUser(): User = User(
    id = "1",
    email = System.getenv("EMAIL")
      ?: System.getProperty("user.email")
      ?: "user@localhost"
  )

  /** Idempotent per (root) - safe to call from every request. */
  fun installFileApplicationServices() {
    ApplicationServices.fileApplicationServices = { rootDir ->
      servicesCache.getOrPut(rootDir) {
        object : FileApplicationServices(rootDir) {
          override val userSettingsManager: UserSettingsInterface
            get() = UserSettingsManager(rootDir)
        }
      }
    }
  }

  /**
   * Minimal, headless equivalent of what the app server does at boot: register dynamic enums
   * (task types, providers, runtimes), install a single-local-user auth stack, and tell the
   * orchestrator how to build chat clients from a model + user pair. Idempotent for the
   * lifetime of the process, so repeated calls (e.g. from a long-running embedding server)
   * are cheap no-ops after the first.
   */
  fun bootstrapPlatform(user: User) {
    if (!bootstrapped.compareAndSet(false, true)) return
    require(null != CodeRuntimes.GroovyRuntime) { "Groovy runtime not initialized" }
    CoreProviders.init()
    CoreTasks.init()
    try {
      ApplicationServices.pluginManager.getLoadedPlugins()
    } catch (e: Exception) {
      System.err.println("warning: plugin loading failed: ${e.message}")
    }
    // Also calls PlanHarness.initDynamicEnums() and installs permissive local auth.
    UnifiedHarness.configurePlatform(user)
    OrchestrationConfig.instanceFn = { model, u ->
      model.instance(user = u)
        ?: throw IllegalStateException("No model/provider configured for ${model.model?.modelId ?: model}")
    }
  }

  fun availableModels(user: User): Map<String, ChatModel> = try {
    user.userSettings().models()
  } catch (e: Exception) {
    System.err.println("warning: could not read user model settings: ${e.message}")
    emptyMap()
  }

  fun resolveModel(modelId: String, available: Map<String, ChatModel>): ChatModel {
    available.values.firstOrNull { it.modelId == modelId }?.let { return it }
    available[modelId]?.let { return it }
    available.entries.firstOrNull { it.key.equals(modelId, ignoreCase = true) }?.let { return it.value }
    System.err.println("warning: model '$modelId' is not registered; using an unregistered text-only reference")
    return ChatModel(
      modelId = modelId,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT),
    )
  }

  fun describeModels(available: Map<String, ChatModel>): String = if (available.isEmpty()) {
    "No models are configured for this user; add an API key first."
  } else {
    "Available models (${available.size}):\n" +
        available.values.map { it.modelId }.distinct().sorted().joinToString("\n") { "  $it" }
  }

  fun resolveModels(
    smartModelId: String,
    fastModelId: String? = null,
    imageModelId: String? = null,
    audioModelId: String? = null,
    user: User,
  ): Models {
    val available = availableModels(user)
    val smart = resolveModel(smartModelId, available)
    val fast = fastModelId?.let { resolveModel(it, available) } ?: smart
    val image = imageModelId?.let { resolveModel(it, available) } ?: fast
    val audio = audioModelId?.let { resolveModel(it, available) } ?: fast
    return Models(smart = smart, fast = fast, image = image, audio = audio)
  }

  fun markdownFiles(folder: File): List<File> = folder.walkTopDown()
    .filter { it.isFile && it.extension.lowercase() in setOf("md", "markdown") }
    .toList()

  fun buildDocProcessor(
    root: File,
    docsFolder: File,
    updateMode: UpdateMode,
    models: Models,
    serverless: Boolean,
    autoFix: Boolean,
    user: User,
    templateVarOverrides: Map<String, String> = emptyMap(),
  ): DocProcessor = DocProcessor(
    root = root,
    docsFolder = docsFolder,
    updateMode = updateMode,
    smartModel = models.smart,
    fastModel = models.fast,
    imageModel = models.image,
    audioModel = models.audio,
    serverless = serverless,
    // Embedders own browser launching (or don't want one at all); the harness must not
    // try to open its own URL.
    openBrowser = false,
    autoFix = autoFix,
    user = user,
    templateVarOverrides = templateVarOverrides,
    showMenubar = false,
  )

  /** Narrow a plan to the tasks producing any of [targets] (paths relative to [root], or absolute). */
  fun applyTargetFilter(
    plan: WorkPlan<PlatformTaskKind>,
    root: File,
    targets: List<String>,
  ): WorkPlan<PlatformTaskKind> {
    if (targets.isEmpty()) return plan
    val targetFiles = targets.mapNotNull { target ->
      try {
        (File(target).let { if (it.isAbsolute) it else root.resolve(target) }).canonicalFile
      } catch (e: Exception) {
        null
      }
    }
    if (targetFiles.isEmpty()) return plan
    return plan.filter { planned ->
      try {
        val main = planned.task.data.main_file?.canonicalFile ?: return@filter false
        targetFiles.any { main.endsWith(it) }
      } catch (e: Exception) {
        false
      }
    }
  }
}