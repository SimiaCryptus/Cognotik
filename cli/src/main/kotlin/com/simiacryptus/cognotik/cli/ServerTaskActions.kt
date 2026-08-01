package com.simiacryptus.cognotik.cli

    import com.simiacryptus.cognotik.platform.model.Session
    import com.simiacryptus.cognotik.webui.servlet.action.AutoFixRequest
    import com.simiacryptus.cognotik.webui.servlet.action.DocOpsFsActions
    import com.simiacryptus.cognotik.webui.servlet.action.DocOpsServlets
    import com.simiacryptus.cognotik.webui.servlet.action.TaskMonitor
    import java.io.File

    /**
     * CLI adapter for the (now shared) DocOps / AutoFix / Tasks FS API actions:
     *
     * ```
     * POST {mount}/.fsapi/v1/docops?command=plan
     * POST {mount}/.fsapi/v1/docops?command=run&path=docs/api.md
     * POST {mount}/.fsapi/v1/autofix?cmd=./gradlew%20build&dir=.
     * GET  {mount}/.fsapi/v1/tasks[?id=t3]
     * ```
     *
     * All of the machinery lives in stdtools ([DocOpsFsActions], `FsTasks`, `ServerDocOps`).
     * This object only supplies the three things that are CLI-specific:
     *
     *  1. the **endpoint**: a [CliDocProcessorServlet] bound to the local mount, published
     *     through [DocOpsServlets] (and mounted at `/docops` by [FileServerCli]) so the FS
     *     action and the HTTP endpoint can never drift apart - and so an environment that
     *     swaps in a proxy servlet is honoured automatically;
     *  2. the **AutoFix implementation** ([AutoFixCli], mapped from the shared request);
     *  3. the **monitor server** ([EphemeralMonitorServer]).
     */
    object ServerTaskActions {

      data class Config(
        /** Project root handed to the tools (defaults to the served directory). */
        val root: File,
        val readOnly: Boolean = false,
        val smartModel: String? = System.getenv("COGNOTIK_SMART_MODEL"),
        val fastModel: String? = System.getenv("COGNOTIK_FAST_MODEL"),
        val timeoutMinutes: Long = 30,
        /** true = let the tool start its own ephemeral monitor server. */
        val monitor: Boolean = false,
        /** Folder scanned for documents when the request names none (default: [root]). */
        val docsFolder: File? = null,
        /** Default `?mode=` for docops. */
        val docOpsMode: String = ServerDocOps.DEFAULT_MODE,
        /** Concurrent docops queues. */
        val docOpsConcurrency: Int = 4,
      )

      val isEnabled: Boolean get() = DocOpsFsActions.isEnabled

      @Volatile
      private var config: Config? = null

      @Volatile
      private var docServlet: CliDocProcessorServlet? = null

      /** Idempotent: builds/publishes the endpoint and (re)installs the configuration. */
      @Synchronized
      fun install(cfg: Config) {
        config = cfg
        /*
         * Built once so both entry points - the FS API action and the /docops HTTP mount -
         * share one DocProcessorServlet. Its constructor publishes it via DocOpsServlets,
         * which is what ServerDocOps resolves. Re-creating it on reconfiguration would
         * orphan the instance Jetty already mounted, so the servlet reads the live
         * ModelSelection instead of holding model ids of its own.
         */
        val servlet = docServlet ?: CliDocProcessorServlet(
          root = cfg.root.canonicalFile,
          docsFolder = cfg.docsFolder?.canonicalFile,
          readOnly = cfg.readOnly,
          mode = cfg.docOpsMode,
          concurrency = cfg.docOpsConcurrency,
          monitor = cfg.monitor,
        ).also {
          docServlet = it
          FileServerCli.docProcessorServlet = it
        }
        apply(cfg, servlet)
      }

      /**
       * Re-binds the actions to the current [ModelSelection] (the web UI changed it).
       * AutoFix takes its models by value, so it genuinely has to be re-installed.
       */
      @Synchronized
      fun refreshModels() {
        val cfg = config ?: return
        val servlet = docServlet ?: return
        apply(cfg, servlet)
      }

      private fun apply(cfg: Config, servlet: CliDocProcessorServlet) {
        DocOpsFsActions.install(
          DocOpsFsActions.Config(
            root = { servlet.taskRoot },
            user = { FileServerCli.user },
            readOnly = cfg.readOnly,
            timeoutMinutes = cfg.timeoutMinutes,
            monitor = cfg.monitor,
            monitorFactory = { EphemeralMonitor() },
            docsFolder = { cfg.docsFolder?.canonicalFile },
            docOpsMode = cfg.docOpsMode,
            docOpsConcurrency = cfg.docOpsConcurrency,
            autoFixRunner = { request -> AutoFixCli.run(argv(request)) },
            /* Late-bound: a swapped/proxy endpoint installed later still wins. */
            servlet = { DocOpsServlets.current ?: servlet },
          )
        )
      }

      /** Maps the shared [AutoFixRequest] onto [AutoFixCli]'s argv. */
      private fun argv(request: AutoFixRequest): Array<String> {
        val argv = mutableListOf("--root", request.root.absolutePath)
        if (request.dir.isNotBlank()) argv += listOf("--dir", request.dir)
        request.commands.forEach { argv += listOf("--cmd", it) }
        request.smartModel?.takeIf { it.isNotBlank() }?.let { argv += listOf("--smart-model", it) }
        request.fastModel?.takeIf { it.isNotBlank() }?.let { argv += listOf("--fast-model", it) }
        argv += listOf("-t", request.timeoutMinutes.coerceAtLeast(1).toString())
        // There is nobody to click an approval link, so a headless run must auto-apply.
        if (request.serverless) argv += "--serverless"
        return argv.toTypedArray()
      }

      /** Adapts the CLI's ephemeral monitor to the shared [TaskMonitor] contract. */
      private class EphemeralMonitor : TaskMonitor {
        private val delegate = EphemeralMonitorServer(host = "localhost", requestedPort = null)
        override fun start(): String = delegate.start()
        override fun monitorUrl(session: Session): String? = delegate.monitorUrl(session)
        override fun close() = delegate.close()
      }
    }