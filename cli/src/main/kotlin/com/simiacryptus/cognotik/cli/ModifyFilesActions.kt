package com.simiacryptus.cognotik.cli

  import com.simiacryptus.cognotik.chat.ChatInterface
  import com.simiacryptus.cognotik.cli.FileServerCli.available
  import com.simiacryptus.cognotik.cli.FileServerCli.models
  import com.simiacryptus.cognotik.cli.FileServerCli.user
  import com.simiacryptus.cognotik.models.ModelSchema
  import com.simiacryptus.cognotik.platform.instance
  import com.simiacryptus.cognotik.platform.model.Session
  import com.simiacryptus.cognotik.platform.model.User
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
  import com.simiacryptus.cognotik.webui.servlet.action.ActionParam
   import com.simiacryptus.cognotik.webui.servlet.action.ActionMenu
   import com.simiacryptus.cognotik.webui.servlet.action.ActionSelection
   import com.simiacryptus.cognotik.webui.servlet.action.ActionUi
  import com.simiacryptus.cognotik.webui.servlet.action.FsAction
  import com.simiacryptus.cognotik.webui.servlet.action.FsActionContext
  import com.simiacryptus.cognotik.webui.servlet.handler.FsErrorCode
  import com.simiacryptus.cognotik.webui.servlet.handler.FsErrors
  import com.simiacryptus.cognotik.webui.servlet.handler.FsException
  import com.simiacryptus.cognotik.webui.session.SessionTask
  import jakarta.servlet.http.HttpServletResponse
  import java.io.File
  import java.io.OutputStream
  import java.net.URI
  import java.nio.file.Path
  import java.text.SimpleDateFormat
  import java.util.concurrent.atomic.AtomicBoolean
  import kotlin.io.path.relativeTo

  /**
   * Port of the IntelliJ `ModifyFilesAction` onto the file server's FS API.
   *
   * The IDE action selects files in the project view, spins up a [ChatSocketManager]
   * whose system prompt embeds those files, and opens a browser at `#session`.
   * Here the *selection* arrives as query parameters and the *browser* is whatever
   * client called us, so the operation answers with the session id and the URL:
   *
   * ```
   * POST {mount}/.fsapi/v1/modify?path=src/Foo.kt&path=src/Bar.kt[&lineNumbers=true]
   *   -> { "session": "...", "url": "http://host:port/#...", "files": [ ... ] }
   * ```
   *
   * Design notes (mirrors [ServerTaskActions]):
   *
   *  1. **No task record.** Unlike docops/autofix this is not a batch job: the work
   *     happens interactively over the chat websocket, so the response is just a
   *     pointer. Patches are applied by [DiffInstrumentor] exactly as in the IDE.
   *  2. **Read-only mounts are refused** with `EROFS` — the whole point of the
   *     session is to write files back.
   *  3. **The chat UI is a second server.** [Config.chatUri] is called lazily on the
   *     first request, so a `FileServerCli` that never sees a modify request never
   *     starts it (and a missing API key never breaks start-up).
   *  4. **Path safety**: every requested path is canonicalised and must stay under
   *     the configured root; folders are expanded with [FileSelectionUtils].
   */
  object ModifyFilesActions {

    const val MODIFY_OP = "modify"

    /** Guard rail: the summary goes into a prompt, not into a mmap. */
    private const val MAX_FILES = 64

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

    /**
     * Seam #2 — the patch processor (IntelliJ: `AppSettingsState.instance.processor`).
     * It supplies both `patchFormatPrompt` and the [DiffInstrumentor] behaviour.
     */
    @JvmStatic
    var patchProcessor = PatchProcessors.Fuzzy

    /** The CLI is single-user and unauthenticated (it binds to loopback by default). */
    private val localUser = User(id = "local", email = "local@localhost", name = "Local User")

    @Volatile
    private var config: Config? = null
    private val installed = AtomicBoolean(false)

    val isEnabled: Boolean get() = config != null

    /** Idempotent: registers the action once, and (re)installs the configuration. */
    @Synchronized
    fun install(cfg: Config) {
      config = cfg
      if (!installed.compareAndSet(false, true)) return
      FsAction.register(
        FsAction(
          op = MODIFY_OP,
          method = "POST",
          description = "Open a patch chat session over the selected files",
          parameters = listOf(
            ActionParam("path", required = false, description = "file or folder relative to the root (repeatable)"),
             /* 'lineNumbers' stays an API-only knob (Config.showLineNumbers is the default):
                the dialog would only stand between the click and the chat session. */
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
             /* Both parameters are supplied by the invocation, so no dialog is shown at
                all: the action opens the session straight away. */
             hiddenParams = setOf("path", "name", "prompt"),
             sendSelection = "paths", selectionParam = "path",
           ),
        ) { ctx -> handleModify(ctx) },
        replace = true,
      )
    }

    /*
     * ------------------------------------------------------------------
     * Handler
     * ------------------------------------------------------------------
     */

    private fun handleModify(ctx: FsActionContext) {
      val cfg = config ?: return fail(ctx.resp, FsErrorCode.ENOSYS, MODIFY_OP, "modify action is not enabled")
      if (cfg.readOnly || ctx.config.readOnly) {
        return fail(ctx.resp, FsErrorCode.EROFS, MODIFY_OP, "this mount is read-only; patch chat is refused")
      }
      val root = cfg.root.canonicalFile
      val requested = ctx.req.getParameterValues("path").orEmpty().map { it.trim() }.filter { it.isNotBlank() }
      val selected = try {
        select(root, requested)
      } catch (e: FsException) {
        return FsErrors.write(ctx.resp, e)
      }
      if (selected.isEmpty()) {
        return fail(ctx.resp, FsErrorCode.EINVAL, MODIFY_OP, "no readable files in the selection")
      }
      if (selected.size > MAX_FILES) {
        return fail(
          ctx.resp, FsErrorCode.EINVAL, MODIFY_OP,
          "selection of ${selected.size} files exceeds the limit of $MAX_FILES; narrow it with more specific paths"
        )
      }
      val lineNumbers = ctx.req.getParameter("lineNumbers")
        ?.let { it == "1" || it.equals("true", ignoreCase = true) }
        ?: cfg.showLineNumbers

      val session = Session.newUserID()
       val prompt = ctx.req.getParameter("prompt")?.takeIf { it.isNotBlank() }
      val label = ctx.req.getParameter("name")?.takeIf { it.isNotBlank() }
         ?: prompt?.lineSequence()?.firstOrNull()?.trim()?.take(60)
        ?: "ModifyFiles @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
      SessionProxyServer.metadataStorage.setSessionName(null, session, label)
      SessionProxyServer.agents[session] = PatchChatManager(
        session = session,
        model = models?.smart?.asChatInterface(user ?: throw IllegalStateException("No user configured")) ?: throw IllegalStateException("No smart model configured"),
        fastModel = models?.fast?.asChatInterface(user ?: throw IllegalStateException("No user configured")) ?: throw IllegalStateException("No fast model configured"),
        root = root,
        files = selected,
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
      val url = cfg.chatUri().resolve("/#$session").toString()
      println("modify: session $session over ${selected.size} file(s) -> $url")

      val sb = StringBuilder("{")
      sb.append("\"session\":\"").append(esc(session.toString())).append("\",")
      sb.append("\"url\":\"").append(esc(url)).append("\",")
       sb.append("\"prompt\":").append(prompt?.let { "\"" + esc(it) + "\"" } ?: "null").append(",")
      sb.append("\"lineNumbers\":").append(lineNumbers).append(",")
      sb.append("\"files\":[")
      sb.append(selected.joinToString(",") { "\"" + esc(it.toString().replace(File.separatorChar, '/')) + "\"" })
      sb.append("]}")
      writeJson(ctx.resp, 200, sb.toString())
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
      private val showLineNumbers: Boolean = false,
      budget: Double = 2.0,
    ) : ChatSocketManager(
      session = session,
      smartModel = model,
      fastModel = fastModel,
      systemPrompt = "",
      applicationClass = ApplicationServer::class.java,
      budget = budget,
      owner = localUser,
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

    /*
     * ------------------------------------------------------------------
     * Tiny JSON helpers (kept local so this file drops in next to ServerTaskActions)
     * ------------------------------------------------------------------
     */

    private fun writeJson(resp: HttpServletResponse, status: Int, body: String) {
      resp.status = status
      resp.contentType = "application/json"
      resp.characterEncoding = "UTF-8"
      resp.writer.write(body)
    }

    private fun fail(resp: HttpServletResponse, code: FsErrorCode, syscall: String, message: String) {
      FsErrors.write(resp, FsException(code, syscall, null, message))
    }

    private fun esc(s: String): String {
      val sb = StringBuilder(s.length + 16)
      for (c in s) when {
        c == '"' -> sb.append("\\\"")
        c == '\\' -> sb.append("\\\\")
        c == '\n' -> sb.append("\\n")
        c == '\r' -> sb.append("\\r")
        c == '\t' -> sb.append("\\t")
        c < ' ' -> sb.append(String.format("\\u%04x", c.code))
        else -> sb.append(c)
      }
      return sb.toString()
    }
  }