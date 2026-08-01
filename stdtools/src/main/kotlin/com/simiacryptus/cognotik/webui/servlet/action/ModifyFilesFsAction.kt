package com.simiacryptus.cognotik.webui.servlet.action

    import com.simiacryptus.cognotik.chat.ChatInterface
    import com.simiacryptus.cognotik.models.ModelSchema
    import com.simiacryptus.cognotik.platform.model.Session
    import com.simiacryptus.cognotik.platform.model.User
    import com.simiacryptus.cognotik.platform.model.defaultUser
    import com.simiacryptus.cognotik.text.patch.PatchProcessors
    import com.simiacryptus.cognotik.text.ui.DiffInstrumentor
    import com.simiacryptus.cognotik.ui.patch.SessionRenderer
    import com.simiacryptus.cognotik.util.FileSelectionUtils
    import com.simiacryptus.cognotik.util.FileSelectionUtils.prefilterFilename
    import com.simiacryptus.cognotik.util.FileSelectionUtils.resolveToRelativePath
    import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
    import com.simiacryptus.cognotik.util.SessionProxyServer
    import com.simiacryptus.cognotik.util.asChatInterface
    import com.simiacryptus.cognotik.util.renderMarkdown
    import com.simiacryptus.cognotik.webui.application.AppInfoData
    import com.simiacryptus.cognotik.webui.application.ApplicationServer
    import com.simiacryptus.cognotik.webui.chat.ChatSocketManager
    import com.simiacryptus.cognotik.webui.servlet.ApiProviderServlet.Companion.models
    import com.simiacryptus.cognotik.webui.servlet.ApiProviderServlet.Companion.userSettings
    import com.simiacryptus.cognotik.webui.servlet.DocProcessorServlet
    import com.simiacryptus.cognotik.webui.servlet.handler.FsErrorCode
    import com.simiacryptus.cognotik.webui.servlet.handler.FsErrors
    import com.simiacryptus.cognotik.webui.servlet.handler.FsException
    import com.simiacryptus.cognotik.webui.session.SessionTask
    import java.io.File
    import java.io.OutputStream
    import java.net.URI
    import java.nio.file.Path
    import java.text.SimpleDateFormat
    import java.util.concurrent.atomic.AtomicBoolean
    import kotlin.io.path.relativeTo

    /**
     * Port of the IntelliJ `ModifyFilesAction` onto the FS API (previously CLI-only).
     *
     * The IDE action selects files in the project view, spins up a [ChatSocketManager] whose
     * system prompt embeds those files, and opens a browser at `#session`. Here the *selection*
     * arrives as query parameters and the *browser* is whatever client called us, so the
     * operation answers with the session id and the URL:
     *
     * ```
     * POST {mount}/.fsapi/v1/modify?path=src/Foo.kt&path=src/Bar.kt[&lineNumbers=true]
     *   -> { "session": "...", "url": "http://host:port/#...", "files": [ ... ] }
     * ```
     *
     * Design notes (mirrors [DocOpsFsActions]):
     *
     *  1. **No task record.** The work happens interactively over the chat websocket, so the
     *     response is just a pointer. Patches are applied by [DiffInstrumentor] as in the IDE.
     *  2. **Read-only mounts are refused** with `EROFS`.
     *  3. **The chat UI may be a second server.** [Config.chatUri] is called lazily on the
     *     first request, so a server that never sees a modify request never starts it.
     *  4. **Path safety**: every requested path is canonicalised and must stay under the
     *     resolved root; folders are expanded with [FileSelectionUtils].
     *  5. **Models are live.** The pair is read from the calling user's settings on every
     *     request (there is no configured default), so "Select Models…" immediately
     *     retargets the patch chat.
     */
    object ModifyFilesFsAction {

      const val MODIFY_OP = "modify"
     /**
      * Read-only companion to [MODIFY_OP]: resolves the chat UI URL of a session
      * that already exists. Clients that hold an id (the IDE view rendering a
      * `docops.status.json`) must not have to re-derive `{chatUri}/proxy/?session=…`.
      */
     const val SESSION_OP = "session"

      /** Guard rail: the summary goes into a prompt, not into a mmap. */
      private const val MAX_FILES = 64

      data class Config(
        /** Project root for the request (served dir, `--task-root`, or session dir). */
        val root: (FsActionContext) -> File,
        /** Owner of the chat session and of the model credentials. */
        val user: (FsActionContext) -> User,
        /** Base URI of the chat UI; resolved lazily so the server can start on demand. */
        val chatUri: () -> URI,
        val readOnly: Boolean = false,
        /**
         * Model resolution. Defaults to the installed DocOps endpoint's resolution, so a
          * swapped proxy also decides which models a patch chat may use. Either way the ids
          * come from the calling user's persisted selection - there is no other source.
         */
         val models: (User) -> DocProcessorServlet.Models = { user -> defaultModels(user) },
        /** Default for `?lineNumbers=` (IntelliJ: the MultiDiffChatWithLineNumbers variant). */
        val showLineNumbers: Boolean = false,
        val budget: Double = 2.0,
      )

      /**
       * Seam — the patch processor (IntelliJ: `AppSettingsState.instance.processor`).
       * It supplies both `patchFormatPrompt` and the [DiffInstrumentor] behaviour.
       */
      @JvmStatic
      var patchProcessor = PatchProcessors.Fuzzy

      @Volatile
      private var config: Config? = null
      private val installed = AtomicBoolean(false)

      val isEnabled: Boolean get() = config != null

       private fun defaultModels(user: User): DocProcessorServlet.Models {
         DocOpsServlets.current?.let { return it.modelsFor(user) }
        /* No endpoint installed: read the same authoritative source it would. */
        val settings = user.userSettings()
        return DocProcessorServlet.models(
          smartModel = settings.smartModel?.takeIf { it.isNotBlank() },
          fastModel = settings.fastModel?.takeIf { it.isNotBlank() },
          imageModel = null,
          audioModel = null,
          available = settings.models(),
        )
      }

      /** Idempotent: registers the action once, and (re)installs the configuration. */
      @Synchronized
      fun install(cfg: Config) {
        config = cfg
         /* Share the request-scoped user with the picker; the pair itself lives in settings. */
         ModelSelection.install(user = { ctx -> ctx?.let { cfg.user(it) } ?: defaultUser })
        ModelSelectionActions.install()
        if (!installed.compareAndSet(false, true)) return
        FsAction.register(
          FsAction(
            op = MODIFY_OP,
            method = "POST",
            description = "Open a patch chat session over the selected files",
            parameters = listOf(
              ActionParam("path", required = false, description = "file or folder relative to the root (repeatable)"),
              /* 'lineNumbers' stays an API-only knob (Config.showLineNumbers is the default). */
              ActionParam(
                "name", required = false, label = "Session name",
                description = "label shown in the chat history"
              ),
              /* Opening instruction from the client's selection-edit action; it
                 labels the session (the chat app owns its own input box). */
              ActionParam(
                "prompt", required = false, label = "Instruction",
                description = "opening instruction, used as the session label"
              ),
            ),
            mutating = true,
            ui = ActionUi(
              title = "Modify Files…", icon = "✏️", category = "Cognotik",
              menus = listOf(
                ActionMenu("explorer/context", "7_run", 30),
                ActionMenu("main/tools", "7_run", 30),
              ),
              selection = ActionSelection(min = 0, kinds = listOf("file", "dir")),
              /* Every parameter is supplied by the invocation, so no dialog is shown: the
                 action opens the session straight away. */
              hiddenParams = setOf("path", "name", "prompt"),
              sendSelection = "paths", selectionParam = "path",
            ),
          ) { ctx -> handleModify(ctx) },
          replace = true,
        )
       /* Same URL construction as handleModify, exposed for existing sessions so
          that exactly one place owns the '{chatUri}/proxy/?session=…' contract. */
       FsAction.register(
         FsAction(
           op = SESSION_OP,
           method = "GET",
           description = "Resolve the chat UI URL of an existing session id",
           parameters = listOf(
             ActionParam(
               "id", required = false,
               description = "session id; omit to fetch only the base and the URL template"
             ),
           ),
           mutating = false,
         ) { ctx -> handleSessionLink(ctx) },
         replace = true,
       )
      }

      /*
       * ------------------------------------------------------------------
       * Handler
       * ------------------------------------------------------------------
       */

      private fun handleModify(ctx: FsActionContext) {
        val cfg = config
          ?: return FsJson.fail(ctx.resp, FsErrorCode.ENOSYS, MODIFY_OP, "modify action is not enabled")
        if (cfg.readOnly || ctx.config.readOnly) {
          return FsJson.fail(ctx.resp, FsErrorCode.EROFS, MODIFY_OP, "this mount is read-only; patch chat is refused")
        }
        val root = cfg.root(ctx).canonicalFile
        val user = cfg.user(ctx)
        val requested = ctx.req.getParameterValues("path").orEmpty().map { it.trim() }.filter { it.isNotBlank() }
        val selected = try {
          select(root, requested)
        } catch (e: FsException) {
          return FsErrors.write(ctx.resp, e)
        }
        if (selected.isEmpty()) {
          return FsJson.fail(ctx.resp, FsErrorCode.EINVAL, MODIFY_OP, "no readable files in the selection")
        }
        if (selected.size > MAX_FILES) {
          return FsJson.fail(
            ctx.resp, FsErrorCode.EINVAL, MODIFY_OP,
            "selection of ${selected.size} files exceeds the limit of $MAX_FILES; narrow it with more specific paths"
          )
        }
        val lineNumbers = ctx.req.getParameter("lineNumbers")
          ?.let { it == "1" || it.equals("true", ignoreCase = true) }
          ?: cfg.showLineNumbers

        val models = try {
          cfg.models(user)
        } catch (e: IllegalArgumentException) {
          return FsJson.fail(
            ctx.resp, FsErrorCode.EINVAL, MODIFY_OP,
            (e.message ?: "no smart model configured") + "; pick one with the \"Select Models…\" action"
          )
        }

        val session = Session.newUserID()
        val prompt = ctx.req.getParameter("prompt")?.takeIf { it.isNotBlank() }
        val label = ctx.req.getParameter("name")?.takeIf { it.isNotBlank() }
          ?: prompt?.lineSequence()?.firstOrNull()?.trim()?.take(60)
          ?: "ModifyFiles @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        SessionProxyServer.metadataStorage.setSessionName(null, session, label)
        SessionProxyServer.agents[session] = PatchChatManager(
          session = session,
          model = models.smart.asChatInterface(user),
          fastModel = models.fast.asChatInterface(user),
          root = root,
          files = selected,
          owner = user,
          showLineNumbers = lineNumbers,
          budget = cfg.budget,
        )
        ApplicationServer.appInfoMap[session] = AppInfoData(
          applicationName = "Code Chat",
          inputCnt = 0,
          stickyInput = true,
          loadImages = false,
          showMenubar = false
        )

        /* Starts the chat server on first use; see Config.chatUri. */
//        http://localhost:12891/proxy/?session=U-20260731-bx1tRLb0
        val url = cfg.chatUri()
          .resolve("/proxy/?session=$session")
          .toString()
        println("modify: session $session over ${selected.size} file(s) -> $url")

        // TODO: use JsonUtil.toJson(mapOf(...))
        val sb = StringBuilder("{")
        sb.append("\"session\":\"").append(FsJson.esc(session.toString())).append("\",")
        sb.append("\"url\":\"").append(FsJson.esc(url)).append("\",")
        sb.append("\"prompt\":").append(prompt?.let { "\"" + FsJson.esc(it) + "\"" } ?: "null").append(",")
        sb.append("\"lineNumbers\":").append(lineNumbers).append(",")
        sb.append("\"files\":[")
        sb.append(selected.joinToString(",") { "\"" + FsJson.esc(it.toString().replace(File.separatorChar, '/')) + "\"" })
        sb.append("]}")
        FsJson.write(ctx.resp, 200, sb.toString())
      }
     /** Ids are echoed into a URL: keep them to the shape [Session.newUserID] produces. */
     private val SESSION_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
     /**
      * `GET {mount}/.fsapi/v1/session[?id=…]` ->
      * `{ "base": "...", "template": "…/proxy/?session={session}", "session": …, "url": … }`
      *
      * The template is answered even without an id so a client can resolve a whole
      * document of session ids (a `docops.status.json`) in one round trip.
      */
     private fun handleSessionLink(ctx: FsActionContext) {
       val cfg = config
         ?: return FsJson.fail(ctx.resp, FsErrorCode.ENOSYS, SESSION_OP, "session links are not enabled")
       val id = ctx.req.getParameter("id")?.trim().orEmpty()
       if (id.isNotBlank() && !SESSION_ID.matches(id)) {
         return FsJson.fail(ctx.resp, FsErrorCode.EINVAL, SESSION_OP, "malformed session id")
       }
       val chatUri = cfg.chatUri()
       val base = chatUri.resolve("/").toString().trimEnd('/')
       val template = chatUri.resolve("/proxy/?session=").toString() + "{session}"
       val url = if (id.isBlank()) null else chatUri.resolve("/proxy/?session=$id").toString()
       val sb = StringBuilder("{")
       sb.append("\"base\":\"").append(FsJson.esc(base)).append("\",")
       sb.append("\"template\":\"").append(FsJson.esc(template)).append("\",")
       sb.append("\"session\":").append(id.takeIf { it.isNotBlank() }?.let { "\"" + FsJson.esc(it) + "\"" } ?: "null")
       sb.append(",\"url\":").append(url?.let { "\"" + FsJson.esc(it) + "\"" } ?: "null")
       sb.append("}")
       FsJson.write(ctx.resp, 200, sb.toString())
     }


      /** Canonicalise, jail to [root], expand folders — the IDE's VIRTUAL_FILE_ARRAY equivalent. */
      private fun select(root: File, paths: List<String>): Set<Path> {
        val rootPath = root.toPath()
        val roots = if (paths.isEmpty()) listOf(root) else paths.map { rel ->
          val file = File(root, rel.removePrefix("/")).canonicalFile
          if (!file.toPath().startsWith(rootPath)) {
            throw FsException(FsErrorCode.EACCES, MODIFY_OP, rel, "path escapes the served root")
          }
          if (!file.exists()) throw FsException(FsErrorCode.ENOENT, MODIFY_OP, rel, "no such file or directory")
          file
        }
        return FileSelectionUtils.expandFileList(*roots.toTypedArray())
          .filter { it.isFile && it.canRead() }
          .map { it.canonicalFile.toPath().relativeTo(rootPath) }
          .toSet()
      }

      /*
       * ------------------------------------------------------------------
       * The chat agent (ported verbatim from cognotik.actions.chat.ModifyFilesAction)
       * ------------------------------------------------------------------
       */

      class PatchChatManager(
        session: Session,
        model: ChatInterface,
        fastModel: ChatInterface,
        val root: File,
        private val files: Set<Path>,
        owner: User,
        private val showLineNumbers: Boolean = false,
        budget: Double = 2.0,
      ) : ChatSocketManager(
        session = session,
        smartModel = model,
        fastModel = fastModel,
        systemPrompt = "",
        applicationClass = ApplicationServer::class.java,
        budget = budget,
        owner = owner,
      ) {
        override val systemPrompt: String
          get() = """
            You are a helpful AI that helps people with coding.
            You will be answering questions about the following code:
            ${codeSummary()}
            ${if (showLineNumbers) "\nNote: Line numbers are shown at the beginning of each line in the format 'NUMBER | CODE'. These are for reference only and should not be included in any patches or code modifications.\n" else ""}
            ${patchProcessor.patchFormatPrompt}
          """.trimIndent()

        private fun getCodeFiles(): Set<Path> {
          if (!root.exists()) {
            println("warning: root directory does not exist: $root")
            return emptySet()
          }
          return files.filter { path ->
            val file = root.toPath().resolve(path).toFile()
            val exists = file.exists()
            if (!exists) println("warning: file does not exist: $file")
            exists
          }.toSet()
        }

        private fun codeSummary(): String {
          return getCodeFiles().associateWith { root.toPath().resolve(it).toFile().readText(Charsets.UTF_8) }
            .entries.joinToString("\n\n") { (path, code) ->
              val extension = path.toString().split('.').lastOrNull()
              if (showLineNumbers) {
                val lines = code.lines()
                val lineNumberWidth = lines.size.toString().length
                val numberedLines = lines.mapIndexed { index, line ->
                  String.format("%${lineNumberWidth}d | %s", index + 1, line)
                }.joinToString("\n")
                "# $path\n```$extension\n$numberedLines\n```"
              } else {
                "# $path\n```$extension\n$code\n```"
              }
            }
        }

        override fun renderResponse(response: String, task: SessionTask) = renderMarkdown(response, tabs = true) { html ->
          DiffInstrumentor(
            patchProcessor,
            SessionRenderer(task),
          ).instrument(
            root = root.toPath(),
            response = html,
            handle = { newCodeMap: Map<Path, String> ->
              newCodeMap.forEach { (path, _) ->
                task.complete("<a href='${"fileIndex/$sessionId/$path"}'>$path</a> Updated")
              }
            },
            defaultFile = if (files.size == 1) files.first().let {
              root.toPath().resolve(it).toFile().absolutePath
            } else null,
            resolver = ::resolveToRelativePath,
            prefilterFilename = ::prefilterFilename
          )
        }

        override fun respond(
          task: SessionTask,
          userMessage: String,
          currentChatMessages: List<ModelSchema.ChatMessage>,
          transcriptStream: OutputStream?
        ): String {
          task.verbose((getCodeFiles().joinToString("\n") { path ->
            "* $path - ${root.resolve(path.toFile()).length()} bytes"
          }).renderMarkdown())
          return super.respond(task, userMessage, currentChatMessages, transcriptStream)
        }
      }
    }