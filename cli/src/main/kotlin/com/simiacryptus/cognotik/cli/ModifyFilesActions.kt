package com.simiacryptus.cognotik.cli

import com.simiacryptus.cognotik.webui.servlet.action.ModifyFilesFsAction
import java.io.File
import java.net.URI

/**
 * CLI adapter for the (now shared) patch-chat action - the port of the IDE's
 * `ModifyFilesAction`:
 *
 * ```
 * POST {mount}/.fsapi/v1/modify?path=src/Foo.kt[&path=...][&lineNumbers=true]
 *   -> { "session": "...", "url": "http://host:port/#<session>", "files": [...] }
 * ```
 *
 * The implementation (selection jailing, chat manager, diff instrumentation) lives in
 * stdtools [ModifyFilesFsAction]; here we only bind it to the local mount, the bootstrapped
 * CLI user, and the on-demand chat server.
 */
object ModifyFilesActions {

  data class Config(
    /** Project root handed to the chat (defaults to the served directory). */
    val root: File,
    /** Base URI of the chat UI; resolved lazily so the server starts on demand. */
    val chatUri: () -> URI,
    val readOnly: Boolean = false,
    val smartModel: String? = System.getenv("COGNOTIK_SMART_MODEL"),
    val fastModel: String? = System.getenv("COGNOTIK_FAST_MODEL"),
    /** Default for `?lineNumbers=` (IntelliJ: the MultiDiffChatWithLineNumbers variant). */
    val showLineNumbers: Boolean = false,
    val budget: Double = 2.0,
  )

  /** Seam: the patch processor used for both the prompt and the diff instrumentation. */
  @JvmStatic
  var patchProcessor
    get() = ModifyFilesFsAction.patchProcessor
    set(value) {
      ModifyFilesFsAction.patchProcessor = value
    }

  val isEnabled: Boolean get() = ModifyFilesFsAction.isEnabled

  @Volatile
  private var config: Config? = null

  @Synchronized
  fun install(cfg: Config) {
    config = cfg
    apply(cfg)
  }

  /** Re-binds the chat to the current [ModelSelection] (the web UI changed it). */
  @Synchronized
  fun refreshModels() {
    config?.let { apply(it) }
  }

  private fun apply(cfg: Config) = ModifyFilesFsAction.install(
    ModifyFilesFsAction.Config(
      root = { cfg.root.canonicalFile },
      user = { FileServerCli.user },
      chatUri = cfg.chatUri,
      readOnly = cfg.readOnly,
      showLineNumbers = cfg.showLineNumbers,
      budget = cfg.budget,
    )
  )
}