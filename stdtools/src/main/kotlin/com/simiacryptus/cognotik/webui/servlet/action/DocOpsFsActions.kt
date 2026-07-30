package com.simiacryptus.cognotik.webui.servlet.action

    import com.simiacryptus.cognotik.platform.model.User
    import com.simiacryptus.cognotik.webui.servlet.DocProcessorServlet
    import com.simiacryptus.cognotik.webui.servlet.handler.FsErrorCode
    import java.io.File
    import java.util.concurrent.atomic.AtomicBoolean

    /**
     * Bridges **DocOps** and **AutoFix** onto the FS API, so a browser sitting on a directory
     * listing (or any HTTP client) can run them against the served tree:
     *
     * ```
     * POST {mount}/.fsapi/v1/docops?command=plan
     * POST {mount}/.fsapi/v1/docops?command=run&path=docs/api.md
     * POST {mount}/.fsapi/v1/autofix?cmd=./gradlew%20build&dir=.
     * GET  {mount}/.fsapi/v1/tasks[?id=t3]
     * ```
     *
     * Ported out of the CLI file server so every server (session-backed app servers included)
     * gets the same operations. Two things are host-specific and therefore injected:
     *
     *  - **the root/user**, resolved per request from [FsActionContext] (a fixed directory for
     *    a local mount, the session directory for a multi-user server);
     *  - **the endpoint**, which is always the installed [DocProcessorServlet] - possibly a
     *    swapped proxy - never a locally built `DocProcessor` (see [DocOpsServlets]).
     */
    object DocOpsFsActions {

      const val DOCOPS_OP = "docops"
      const val AUTOFIX_OP = "autofix"

      /** Commands accepted by `?command=`; only `run` mutates the workspace. */
      private val DOCOPS_COMMANDS = listOf("plan", "run", "status", "vars", "models")

      data class Config(
        /** Project root for the request (the served dir, `--task-root`, or the session dir). */
        val root: (FsActionContext) -> File,
        /** Owner of the model credentials and of the generated sessions. */
        val user: (FsActionContext) -> User,
        val readOnly: Boolean = false,
        val smartModel: String? = System.getenv("COGNOTIK_SMART_MODEL"),
        val fastModel: String? = System.getenv("COGNOTIK_FAST_MODEL"),
        val timeoutMinutes: Long = 30,
        /** true = let a run start its own monitor server (requires [monitorFactory]). */
        val monitor: Boolean = false,
        val monitorFactory: (() -> TaskMonitor)? = null,
        /** Folder scanned when the request names no document (default: the root). */
        val docsFolder: ((FsActionContext) -> File?)? = null,
        /** Default `?mode=` for docops. */
        val docOpsMode: String = ServerDocOps.DEFAULT_MODE,
        /** Concurrent docops queues. */
        val docOpsConcurrency: Int = 4,
        /** null = do not expose the `autofix` operation. */
        val autoFixRunner: AutoFixRunner? = null,
        /** Endpoint resolution; late-bound so a proxy installed later still wins. */
        val servlet: () -> DocProcessorServlet? = { DocOpsServlets.current },
      )

      @Volatile
      private var config: Config? = null
      private val installed = AtomicBoolean(false)

      val isEnabled: Boolean get() = config != null

      /** Idempotent: registers the actions once, and (re)installs the configuration. */
      @Synchronized
      fun install(cfg: Config) {
        config = cfg
        FsTasks.install()
        if (!installed.compareAndSet(false, true)) return
        FsAction.register(
          FsAction(
            op = DOCOPS_OP,
            method = "POST",
            description = "Run DocOps against the served tree",
            parameters = listOf(
              ActionParam(
                /* API-only: the UI never asks, it always runs (see hiddenParams below). */
                "command", required = false, default = "run", label = "Command",
                description = "only \"run\" mutates the workspace; other values are API-only",
                options = DOCOPS_COMMANDS
              ),
              ActionParam("path", required = false, description = "document or folder, relative to the root (repeatable)"),
              ActionParam(
                "mode", required = false, label = "Update mode",
                default = ServerDocOps.DEFAULT_MODE,
                description = "how existing targets are updated",
                /* Enumerated from UpdateModes, so the dialog is a select and not a free field. */
                options = ServerDocOps.MODES
              ),
              ActionParam(
                "target", required = false, label = "Targets",
                description = "only run the tasks producing these output files (repeatable)",
                /* Enumerated live from the current selection; see resolveDocOpsTargets. */
                dynamic = true, multi = true
              ),
              ActionParam("var", required = false, description = "template variable override NAME=VALUE (repeatable)"),
            ),
            mutating = true,
            ui = ActionUi(
              title = "DocOps…", icon = "📘", category = "Cognotik",
              menus = listOf(
                ActionMenu("main/tools", "7_run", 40),
                ActionMenu("explorer/context", "7_run", 40),
              ),
              selection = ActionSelection(min = 0, kinds = listOf("file", "dir")),
              /* 'command' is fixed to "run" for UI invocations: there is no picker. */
              hiddenParams = setOf("path", "var", "command"),
              sendSelection = "paths", selectionParam = "path",
            ),
            paramResolvers = mapOf("target" to ::resolveDocOpsTargets),
          ) { ctx -> handleDocOps(ctx) },
          replace = true,
        )
        if (cfg.autoFixRunner != null) FsAction.register(
          FsAction(
            op = AUTOFIX_OP,
            method = "POST",
            description = "Run a command and iteratively fix whatever it reports",
            parameters = listOf(
              ActionParam("cmd", required = true, label = "Command", description = "command line to run (repeatable)"),
              ActionParam("dir", required = false, description = "working directory, relative to the root"),
              ActionParam("autoFix", required = false, default = "true", description = "apply generated patches"),
              ActionParam("timeout", "int", required = false, label = "Timeout (minutes)"),
            ),
            mutating = true,
            ui = ActionUi(
              title = "AutoFix…", icon = "🩺", category = "Cognotik",
              menus = listOf(
                ActionMenu("main/tools", "7_run", 50),
                ActionMenu("explorer/context", "7_run", 50),
                /* Right-clicking the empty explorer background means "this folder",
                   which is exactly AutoFix's unit of work. */
                ActionMenu("explorer/empty", "7_run", 50),
                /* Parity with "Run…": every surface that offers to run something also
                   offers to run-and-fix it, including the editor and its tab. */
                ActionMenu("tab/context", "7_run", 50),
                ActionMenu("editor/context", "7_run", 50),
              ),
              /* A file selection simply names its parent folder, which is what the
                 client's sendSelection = "folder" sends. */
              selection = ActionSelection(min = 0, kinds = listOf("file", "dir")),
              hiddenParams = setOf("dir", "autoFix"),
              sendSelection = "folder", selectionParam = "dir",
            ),
          ) { ctx -> handleAutoFix(ctx) },
          replace = true,
        )
      }

      /*
       * ------------------------------------------------------------------
       * Handlers
       * ------------------------------------------------------------------
       */

      private fun handleDocOps(ctx: FsActionContext) {
        val cfg = config
          ?: return FsJson.fail(ctx.resp, FsErrorCode.ENOSYS, DOCOPS_OP, "task actions are not enabled")
        /* '?resolveParam=target' asks for that parameter's option list, not for a run. */
        if (FsAction.serveParamResolution(ctx)) return
        /* The generated dialog carries no 'command': every UI invocation is a run. */
        val command = (ctx.req.getParameter("command") ?: "run").trim().lowercase()
        if (command !in DOCOPS_COMMANDS) {
          return FsJson.fail(
            ctx.resp, FsErrorCode.EINVAL, DOCOPS_OP,
            "unknown command '$command' (known: ${DOCOPS_COMMANDS.joinToString(", ")})"
          )
        }
        val mutates = command == "run"
        if (mutates && (cfg.readOnly || ctx.config.readOnly)) {
          return FsJson.fail(ctx.resp, FsErrorCode.EROFS, DOCOPS_OP, "this mount is read-only; 'run' is refused")
        }

        /* Validation (endpoint, mode, path jailing) happens here so it answers EINVAL. */
        val request = try {
          docOpsRequest(cfg, ctx)
        } catch (e: IllegalArgumentException) {
          return FsJson.fail(ctx.resp, FsErrorCode.EINVAL, DOCOPS_OP, e.message ?: "invalid docops request")
        } catch (e: IllegalStateException) {
          return FsJson.fail(ctx.resp, FsErrorCode.ENOSYS, DOCOPS_OP, e.message ?: "no docops endpoint")
        }

        val paths = ctx.req.getParameterValues("path").orEmpty().filter { it.isNotBlank() }
        val label = "docops $command" + (if (paths.isEmpty()) "" else paths.joinToString(" ", " "))
        FsTasks.dispatch(ctx.resp, kind = DOCOPS_OP, label = label, async = mutates) {
          when (command) {
            "status" -> ServerDocOps.status(request.root)
            "vars" -> ServerDocOps.vars(request)
            "models" -> ServerDocOps.models(request)
            "plan" -> ServerDocOps.plan(request)
            else -> ServerDocOps.run(request)
          }
        }
      }

      /**
       * Maps the query parameters onto a [ServerDocOps.Request] bound to the **installed**
       * [DocProcessorServlet]; model selection comes from the server's [Config], the user and
       * root from the request.
       */
      private fun docOpsRequest(cfg: Config, ctx: FsActionContext): ServerDocOps.Request {
        val vars = linkedMapOf<String, String>()
        ctx.req.getParameterValues("var")?.forEach { spec ->
          val idx = spec.indexOf('=')
          if (idx > 0) vars[spec.substring(0, idx).trim()] = spec.substring(idx + 1)
        }
        val root = cfg.root(ctx).canonicalFile
        val mode = ctx.req.getParameter("mode")?.takeIf { it.isNotBlank() } ?: cfg.docOpsMode
        ServerDocOps.checkMode(mode)
        val servlet = cfg.servlet() ?: DocOpsServlets.current
          ?: throw IllegalStateException("the DocOps endpoint is not installed")
        return ServerDocOps.Request(
          root = root,
          user = cfg.user(ctx),
          docsFolder = cfg.docsFolder?.invoke(ctx)?.canonicalFile ?: root,
          paths = ctx.req.getParameterValues("path").orEmpty().map { it.trim() }.filter { it.isNotBlank() },
          mode = mode,
          /* Repeatable: the dialog renders a checkbox list of planned outputs. */
          targets = ctx.req.getParameterValues("target").orEmpty().map { it.trim() }.filter { it.isNotBlank() },
          templateVars = vars,
          smartModel = cfg.smartModel,
          fastModel = cfg.fastModel,
          concurrency = cfg.docOpsConcurrency,
          monitor = cfg.monitor && cfg.monitorFactory != null,
          monitorFactory = cfg.monitorFactory,
          servlet = servlet,
        )
      }

      private fun handleAutoFix(ctx: FsActionContext) {
        val cfg = config
          ?: return FsJson.fail(ctx.resp, FsErrorCode.ENOSYS, AUTOFIX_OP, "task actions are not enabled")
        val runner = cfg.autoFixRunner
          ?: return FsJson.fail(ctx.resp, FsErrorCode.ENOSYS, AUTOFIX_OP, "no autofix implementation is installed")
        if (cfg.readOnly || ctx.config.readOnly) {
          return FsJson.fail(ctx.resp, FsErrorCode.EROFS, AUTOFIX_OP, "this mount is read-only")
        }
        val commands = ctx.req.getParameterValues("cmd").orEmpty().filter { it.isNotBlank() }
        if (commands.isEmpty()) {
          return FsJson.fail(ctx.resp, FsErrorCode.EINVAL, AUTOFIX_OP, "missing 'cmd' parameter")
        }
        val dir = ctx.req.getParameter("dir")?.trim().orEmpty()
        val timeout = ctx.req.getParameter("timeout")?.toLongOrNull() ?: cfg.timeoutMinutes
        val request = AutoFixRequest(
          root = cfg.root(ctx).canonicalFile,
          dir = if (dir == "/" || dir == ".") "" else dir,
          commands = commands,
          autoFix = ctx.req.getParameter("autoFix")?.toBoolean() ?: true,
          timeoutMinutes = timeout.coerceAtLeast(1),
          smartModel = cfg.smartModel,
          fastModel = cfg.fastModel,
          // There is nobody to click an approval link, so a headless run must auto-apply.
          serverless = !cfg.monitor,
        )
        FsTasks.dispatch(ctx.resp, kind = AUTOFIX_OP, label = commands.joinToString(" && "), async = true) {
          runner.run(request)
        }
      }

      /*
       * ------------------------------------------------------------------
       * Live parameter options
       * ------------------------------------------------------------------
       */

      /**
       * Options for `?target=`: plans the selection through the endpoint and lists the output
       * files it would produce. `plan` is read-only, so this is safe on a read-only mount, and
       * nothing is registered in the task list (option discovery is plumbing, not a run).
       */
      private fun resolveDocOpsTargets(ctx: FsActionContext): List<ActionOption> {
        val cfg = config ?: return emptyList()
        return try {
          ServerDocOps.targets(docOpsRequest(cfg, ctx))
            .map { ActionOption(it.path, it.path, it.description) }
        } catch (e: Exception) {
          emptyList()
        }
      }
    }