package com.simiacryptus.cognotik.webui.servlet.action

    import com.simiacryptus.cognotik.docops.DocProcessor
    import com.simiacryptus.cognotik.docops.PlatformTaskKind
    import com.simiacryptus.cognotik.docops.UpdateMode
    import com.simiacryptus.cognotik.docops.UpdateModes
    import com.simiacryptus.cognotik.docops.model.WorkPlan
    import com.simiacryptus.cognotik.docops.status.DocOpsStatus
    import com.simiacryptus.cognotik.docops.status.JsonFileDocStatusStore
    import com.simiacryptus.cognotik.docops.status.TaskStatus
    import com.simiacryptus.cognotik.platform.model.Session
    import com.simiacryptus.cognotik.platform.model.User
    import com.simiacryptus.cognotik.webui.servlet.DocProcessorServlet
    import java.io.File
    import java.util.concurrent.atomic.AtomicBoolean

    /**
     * Optional monitor server for a doc-ops run, so generated sessions can be watched.
     * Abstracted because the implementation is host-specific (the CLI has an ephemeral
     * server; an app server already has a UI).
     */
    interface TaskMonitor : AutoCloseable {
      /** Starts it and returns the human-readable base URL. */
      fun start(): String
      fun monitorUrl(session: Session): String?
    }

    /**
     * Headless driver for the DocOps commands exposed over the FS API.
     *
     * **All work is performed by the installed [DocProcessorServlet]** (see [DocOpsServlets]):
     * this object only maps a request onto [DocProcessorServlet.DocOpsRequest], calls
     * `newProcessor`/`plan`/`initializeStatus`/`runPlan`/`modelsFor`, and renders the result to
     * stdout (which [FsTasks] tees into the task record). That is what makes a swapped proxy
     * servlet authoritative in the environments that need one.
     */
    object ServerDocOps {

      /** Cap on how many context files are enumerated per task by [printPlan]. */
      private const val MAX_CONTEXT_FILES_SHOWN = 8

      /** One planned output file, for `?resolveParam=target` option lists. */
      data class Target(val path: String, val description: String)

      class Request(
        /** Project root / working directory handed to the servlet. */
        val root: File,
        /** Owner of the model credentials and of the generated sessions. */
        val user: User,
        /** Folder scanned when no explicit document is named (default: the root). */
        val docsFolder: File = root,
        /** Explicit documents or folders, relative to [root]. */
        val paths: List<String> = emptyList(),
        val mode: String = DEFAULT_MODE,
        /** Only run the tasks producing these output files (empty = all of them). */
        val targets: List<String> = emptyList(),
        val templateVars: Map<String, String> = emptyMap(),
        val concurrency: Int = 4,
        /** true = start a monitor server (when [monitorFactory] supplies one). */
        val monitor: Boolean = false,
        val monitorFactory: (() -> TaskMonitor)? = null,
        val autoFix: Boolean = true,
        /** The endpoint that does the work; defaults to whatever is installed (proxy included). */
        val servlet: DocProcessorServlet = DocOpsServlets.require(),
      )

      const val DEFAULT_MODE = "PatchToUpdate"

      /** Every selectable `?mode=`, so the UI can render a select instead of a text field. */
      val MODES: List<String> = UpdateModes.entries.map { it.name }

      /** Fails fast (before a task record is created) on an unknown `?mode=`. */
      fun checkMode(mode: String): UpdateMode = UpdateModes.fromName(mode)
        ?: throw IllegalArgumentException(
          "unknown mode '$mode'. Known modes: " + UpdateModes.entries.joinToString(", ") { it.name }
        )

      /*
       * ------------------------------------------------------------------
       * Commands
       * ------------------------------------------------------------------
       */

      /** `docops status` — reads `docops.status.json`; touches no model. */
      fun status(root: File): Int {
        printStatus(JsonFileDocStatusStore(root).read())
        return 0
      }

      /** `docops vars` — declared `{{ TEMPLATE_VARS }}`; touches no model. */
      fun vars(req: Request): Int {
        val files = resolveDocFiles(req)
        val vars = DocProcessor.listTemplateVarKeys(files)
        if (vars.isEmpty()) {
          println("No template variables declared in ${files.size} document(s).")
        } else {
          println("Template variables (${vars.size}):")
          vars.toSortedMap().forEach { (k, v) -> println("  $k = ${v.ifBlank { "<no default>" }}") }
          println()
          println("Override with: &var=NAME%3DVALUE (repeatable)")
        }
        return 0
      }

      /** `docops models` — model ids the endpoint can use for this user. */
      fun models(req: Request): Int {
        println(DocProcessorServlet.describeModels(availableModels(req)))
        return 0
      }

      /** `docops plan` — pure: writes nothing, starts nothing. */
      fun plan(req: Request): Int {
        val prepared = prepare(req)
        val plan = prepared.plan()
        printPlan(plan, req.root, prepared.request.updateMode)
        return if (plan.failed.isNotEmpty()) 1 else 0
      }

      /** `docops run` — plan, seed the status file, then execute. Mutates the workspace. */
      fun run(req: Request): Int {
        val prepared = prepare(req)
        val plan = prepared.plan()
        printPlan(plan, req.root, prepared.request.updateMode)
        if (plan.isEmpty) {
          println("Nothing to do.")
          return 0
        }
        /* Seed docops.status.json before doing anything destructive. */
        prepared.servlet.initializeStatus(prepared.processor, plan)

        val monitor = if (!req.monitor) null else req.monitorFactory?.invoke()?.also { server ->
          println("Starting monitor server (stops when this task finishes)...")
          println("Monitor: ${server.start()}")
        }
        val cancelFlag = AtomicBoolean(false)
        try {
          println("Executing ${plan.tasks.size} task(s) with concurrency ${req.concurrency}...")
          val sessions = prepared.servlet.runPlan(
            processor = prepared.processor,
            plan = plan,
            concurrency = req.concurrency,
            cancelFlag = cancelFlag,
          ) { session ->
            val url = monitor?.monitorUrl(session)
            println("  session $session" + (url?.let { " -> $it" } ?: ""))
          }
          println("Finished: ${sessions.size} session(s).")
        } finally {
          monitor?.close()
        }

        val status = prepared.processor.docOps.statusStore.read()
        printStatus(status)
        val failures = status.tasks.values.count { it.status == TaskStatus.FAILED }
        return if (failures > 0) 1 else 0
      }

      /**
       * Options for `?target=`: the real [WorkPlan] for the current selection, so no output
       * scraping is involved. Planning is pure, hence safe on a read-only mount.
       */
      fun targets(req: Request): List<Target> {
        val prepared = prepare(req)
        /* The target filter is what we are enumerating, so it must not be applied. */
        val plan = prepared.plan(applyTargets = false)
        val found = LinkedHashMap<String, String>()
        plan.tasks.forEach { planned ->
          val path = planned.target.relativeToOrAbsolute(req.root)
          val verb = if (planned.target.file.exists()) "update" else "create"
          found.putIfAbsent(path, "$verb [${planned.task.taskType.name}]")
        }
        return found.entries.sortedBy { it.key }.map { Target(it.key, it.value) }
      }

      /*
       * ------------------------------------------------------------------
       * Plans (delegated to the servlet)
       * ------------------------------------------------------------------
       */

      private class Prepared(
        val servlet: DocProcessorServlet,
        val request: DocProcessorServlet.DocOpsRequest,
        val processor: DocProcessor,
      ) {
        fun plan(applyTargets: Boolean = true): WorkPlan<PlatformTaskKind> =
          servlet.plan(request, processor, applyTargets = applyTargets)
      }

      private fun prepare(req: Request): Prepared {
        val servlet = req.servlet
        val docRequest = docOpsRequest(req)
        val processor = servlet.newProcessor(docRequest)
        warnAboutNonDocs(processor, req, docRequest.docFiles)
        return Prepared(servlet, docRequest, processor)
      }

      /** Translates the FS-API shaped [Request] into the servlet's request. */
      private fun docOpsRequest(req: Request): DocProcessorServlet.DocOpsRequest {
        /* Model resolution goes through the servlet, which reads the user's stored
           settings — the only source of a selection — so a proxy's credentials win. */
        val models = try {
          req.servlet.modelsFor(req.user)
        } catch (e: IllegalArgumentException) {
          throw IllegalArgumentException(
            (e.message ?: "no smart model selected") +
                "\nSelect one with the \"Select Models…\" action (POST .fsapi/v1/models?smart=<id>)," +
                " which stores it in your user settings."
          )
        }
        println(
          "Models: smart=${models.smart.modelId} fast=${models.fast.modelId} " +
              "image=${models.image.modelId} audio=${models.audio.modelId}"
        )
        return DocProcessorServlet.DocOpsRequest(
          root = req.root.canonicalFile,
          user = req.user,
          models = models,
          docFiles = if (req.paths.isEmpty()) emptyList() else resolveDocFiles(req),
          docsFolder = req.docsFolder.canonicalFile,
          session = null,
          targets = req.targets
            .filter { it.isNotBlank() }
            .map { File(req.root, it.removePrefix("/")).canonicalFile },
          updateMode = checkMode(req.mode),
          templateVars = req.templateVars,
          autoFix = req.autoFix,
          /* No monitor means fully in-process: never start a second web server. */
          serverless = !req.monitor,
          openBrowser = false,
          showMenubar = false,
          concurrency = req.concurrency,
        )
      }

      /** Canonicalise, jail to the root, expand folders — all inside the servlet. */
      private fun resolveDocFiles(req: Request): List<File> =
        DocProcessorServlet.resolveDocFiles(req.root, req.docsFolder, req.paths)

      private fun availableModels(req: Request): Map<String, com.simiacryptus.cognotik.chat.model.ChatModel> = try {
        req.servlet.availableModels(req.user)
      } catch (e: Exception) {
        println("warning: could not read user model settings: ${e.message}")
        emptyMap()
      }

      private fun warnAboutNonDocs(processor: DocProcessor, req: Request, files: List<File>) {
        if (req.paths.isEmpty()) return
        files.forEach { file ->
          val spec = try {
            processor.docOps.loader.load(file)
          } catch (e: Exception) {
            null
          }
          if (spec == null) {
            println(
              "warning: ${file.name} has no doc-ops frontmatter " +
                  "(expected one of: specifies, transforms, documents, generates, folder)"
            )
          }
        }
      }

      /*
       * ------------------------------------------------------------------
       * Rendering (stdout is tee'd into the task record by FsTasks)
       * ------------------------------------------------------------------
       */

      private fun printPlan(plan: WorkPlan<PlatformTaskKind>, root: File, mode: UpdateMode) {
        println("Plan: ${plan.tasks.size} task(s) in ${plan.queues.size} queue(s) [mode=$mode]")
        var taskNumber = 0
        plan.queues.filter { !it.isEmpty }.forEachIndexed { queueIndex, queue ->
          println("  Queue ${queueIndex + 1}/${plan.queues.size} (${queue.tasks.size} task(s), run sequentially):")
          queue.tasks.forEach { planned ->
            taskNumber++
            val target = planned.target.relativeToOrAbsolute(root)
            val verb = if (planned.target.file.exists()) "update" else "create"
            println("    [$taskNumber] $verb $target  [${planned.task.taskType.name}]")
            val data = planned.task.data
            data.main_file?.let { main ->
              val mainPath = relative(root, main)
              if (mainPath != target) println("         main:    $mainPath")
            }
            if (data.doc_files.isNotEmpty()) {
              println("         doc(s):  " + data.doc_files.joinToString(", ") { relative(root, it) })
            }
            val context = data.related_files.orEmpty()
            if (context.isNotEmpty()) {
              println("         context (${context.size}):")
              context.take(MAX_CONTEXT_FILES_SHOWN).forEach { println("           - ${relative(root, it)}") }
              if (context.size > MAX_CONTEXT_FILES_SHOWN) {
                println("           - ... and ${context.size - MAX_CONTEXT_FILES_SHOWN} more")
              }
            }
            if (planned.preparation.deleteTargetBeforeRun) {
              println("         note:    existing target is deleted before this task runs")
            }
          }
        }
        val targets = plan.tasks.map { it.target }.distinct().sorted()
        if (targets.isNotEmpty()) {
          println("Target files (${targets.size}):")
          targets.forEach { target ->
            val verb = if (target.file.exists()) "update" else "create"
            println("  ${verb.padEnd(6)} ${target.relativeToOrAbsolute(root)}")
          }
        }
        if (plan.skipped.isNotEmpty()) {
          println("Skipped (${plan.skipped.size}):")
          plan.skipped.forEach { println("  ${it.target.relativeToOrAbsolute(root)}: ${it.reason}") }
        }
        if (plan.failed.isNotEmpty()) {
          println("Failed to plan (${plan.failed.size}):")
          plan.failed.forEach { println("  ${it.target.relativeToOrAbsolute(root)}: ${it.error}") }
        }
      }

      private fun printStatus(status: DocOpsStatus) {
        if (status.tasks.isEmpty()) {
          println("No docops status recorded.")
          return
        }
        println("Status (updated ${status.lastUpdated}):")
        status.tasks.values.sortedBy { it.target }.forEach { entry ->
          val session = entry.sessionId?.let { " session=$it" } ?: ""
          val error = entry.error?.let { " error=${it.lines().first()}" } ?: ""
          println("  ${entry.status.name.padEnd(9)} ${entry.target}$session$error")
        }
        val counts = status.tasks.values.groupingBy { it.status }.eachCount()
        println("  -- " + counts.entries.sortedBy { it.key.name }.joinToString(", ") { "${it.key}=${it.value}" })
      }

      private fun relative(root: File, file: File): String = try {
        file.relativeTo(root).path.ifBlank { file.path }
      } catch (e: Exception) {
        file.path
      }
    }