package com.simiacryptus.cognotik.cli

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.CoreTasks
import com.simiacryptus.cognotik.chat.model.ChatMessageModality
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.FileApplicationServices
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.hsql.DatabaseFacet
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.UserSettingsInterface
import com.simiacryptus.cognotik.util.UnifiedHarness
import com.simiacryptus.cognotik.webui.servlet.ApiProviderServlet.Companion.models
import com.simiacryptus.cognotik.webui.servlet.ApiProviderServlet.Companion.userSettings
import com.simiacryptus.cognotik.webui.servlet.FileServlet
import java.io.File
import kotlin.system.exitProcess

/**
 * Shared, headless bootstrap for the reference CLIs (`docops`, `autofix`, ...).
 *
 * Everything here is intentionally *pure setup*: install a settings store rooted at the
 * project directory, register dynamic enums, install a permissive single-local-user auth
 * stack, and teach the orchestrator how to build chat clients. No servers, no threads.
 */
object CliSupport {

  /** Model quadruple used by every task-bearing CLI. */
  class Models(
    val smart: ChatModel,
    val fast: ChatModel,
    val image: ChatModel,
    val audio: ChatModel,
  )

  var email = (System.getenv("EMAIL")
    ?: System.getProperty("user.email")
    ?: "user@localhost")

  fun defaultUser(): User = User(id = "1", email = email)

  init {
    FileServlet.userResolver = object : com.simiacryptus.cognotik.platform.web.UserProvider {
      override fun authenticate(
        request: jakarta.servlet.http.HttpServletRequest,
        response: jakarta.servlet.http.HttpServletResponse?
      ) = defaultUser()
    }
  }

  /**
   * Points [ApplicationServices.fileApplicationServices] at per-root instances so user
   * settings (API keys, model registrations) are read from the project directory.
   */
  fun installFileServices() {
    val servicesCache = mutableMapOf<File, FileApplicationServices>()
    DatabaseFacet.root = File(".").absolutePath
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
   * Minimal, headless equivalent of what the app server does at boot.
   * Safe to call more than once.
   */
  fun bootstrapPlatform(user: User) {
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

  fun describeModels(available: Map<String, ChatModel>): String = if (available.isEmpty()) {
    "No models are configured for this user; add an API key first (try: cognotik docops keys)."
  } else {
    "Available models (${available.size}):\n" +
        available.values.map { it.modelId }.distinct().sorted().joinToString("\n") { "  $it" }
  }

  fun escapeJs(s: String): String = s
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("'", "\\'")
    .replace("\n", "\\n")
    .replace("\r", "\\r")

  fun fail(message: String): Nothing {
    System.err.println("error: $message")
    System.err.println()
    exitProcess(2)
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

  /** `smart` is mandatory; the rest cascade smart -> fast -> image/audio. */
  fun resolveModels(
    user: User,
    smartModel: String?,
    fastModel: String? = null,
    imageModel: String? = null,
    audioModel: String? = null,
    quiet: Boolean = false,
  ): Models {
    val available = availableModels(user)
    val smartId = smartModel ?: throw IllegalArgumentException(
      "no smart model selected. Pass --smart-model <id> or set COGNOTIK_SMART_MODEL.\n" +
          describeModels(available)
    )
    val smart = resolveModel(smartId, available)
    val fast = fastModel?.let { resolveModel(it, available) } ?: smart
    val image = imageModel?.let { resolveModel(it, available) } ?: fast
    val audio = audioModel?.let { resolveModel(it, available) } ?: fast
    if (!quiet) {
      println("Models: smart=${smart.modelId} fast=${fast.modelId} image=${image.modelId} audio=${audio.modelId}")
    }
    return Models(smart = smart, fast = fast, image = image, audio = audio)
  }
}