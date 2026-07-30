package com.simiacryptus.cognotik.cli

    import com.simiacryptus.cognotik.chat.model.ChatModel
    import com.simiacryptus.cognotik.platform.model.Session
    import com.simiacryptus.cognotik.platform.model.User
    import com.simiacryptus.cognotik.webui.servlet.DocProcessorServlet
    import jakarta.servlet.http.HttpServletRequest
    import jakarta.servlet.http.HttpServletResponse
    import java.io.File

    /**
     * [DocProcessorServlet] bound to a **local mount** instead of a chat session.
     *
     * It is mounted by [FileServerCli] at `FileServerCli.DOCOPS_PREFIX` and is also
     * the object [ServerDocOps] / [ServerTaskActions] invoke, so the file server has
     * exactly one DocOps implementation:
     *
     * ```
     * POST /docops?doc=docs/api.md&mode=PatchToUpdate&var.NAME=VALUE
     * GET  /docops?doc=docs/api.md&listTemplateVars=true
     * ```
     *
     * Differences from the session-backed servlet:
     *  - the working directory is the served/`--task-root` directory, not a user dir;
     *  - the user is the one `FileServerCli` bootstrapped (no HTTP auth round-trip);
     *  - there is no parent session, so generated sessions are standalone;
     *  - models default to the `--smart-model` / `--fast-model` selection;
     *  - a read-only mount refuses execution (`listTemplateVars` still answers).
     */
    class CliDocProcessorServlet(
      private val root: File,
      private val docsFolder: File? = null,
      private val smartModel: String? = null,
      private val fastModel: String? = null,
      private val imageModel: String? = System.getenv("COGNOTIK_IMAGE_MODEL"),
      private val audioModel: String? = System.getenv("COGNOTIK_AUDIO_MODEL"),
      private val readOnly: Boolean = false,
      private val mode: String = ServerDocOps.DEFAULT_MODE,
      private val concurrency: Int = 4,
      /** true = the tools may start their own ephemeral monitor server. */
      private val monitor: Boolean = false,
    ) : DocProcessorServlet() {

      override val defaultMode: String get() = mode
      override val defaultSmartModel: String? get() = smartModel
      override val defaultFastModel: String? get() = fastModel
      override val defaultImageModel: String? get() = imageModel
      override val defaultAudioModel: String? get() = audioModel
      override val defaultConcurrency: Int get() = concurrency

      /* No monitor server means fully in-process: never start a second web server. */
      override val defaultServerless: Boolean get() = !monitor
      override val defaultShowMenubar: Boolean get() = false

      /** The mount root, as handed to `--task-root` (or the served directory). */
      val taskRoot: File get() = root.canonicalFile

      override fun resolveUser(request: HttpServletRequest, response: HttpServletResponse): User = FileServerCli.user

      /** A local mount has no chat session to hang the generated sessions off. */
      override fun resolveSession(request: HttpServletRequest): Session? = null

      override fun resolveRoot(
        request: HttpServletRequest,
        response: HttpServletResponse,
        user: User,
      ): File = taskRoot

      override fun resolveDocsFolder(request: HttpServletRequest, root: File): File =
        (docsFolder ?: root).canonicalFile

      /** Prefers the set the server already resolved at start-up; falls back to the user's. */
      override fun availableModels(user: User): Map<String, ChatModel> {
        val cached = FileServerCli.available
        if (cached.isNotEmpty()) return cached
        return try {
          CliSupport.availableModels(user)
        } catch (e: Exception) {
          emptyMap()
        }
      }

      override fun isMutationAllowed(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        if (!readOnly) return true
        writeError(
          response, HttpServletResponse.SC_FORBIDDEN,
          "this mount is read-only; running doc-ops is refused"
        )
        return false
      }
    }