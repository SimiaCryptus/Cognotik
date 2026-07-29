package com.simiacryptus.cognotik.cli

    import com.simiacryptus.cognotik.chat.model.ChatMessageModality
    import com.simiacryptus.cognotik.chat.model.ChatModel
    import com.simiacryptus.cognotik.docops.DocProcessor
    import com.simiacryptus.cognotik.docops.PlatformTaskKind
    import com.simiacryptus.cognotik.docops.UpdateMode
    import com.simiacryptus.cognotik.docops.UpdateModes
    import com.simiacryptus.cognotik.docops.model.WorkPlan
    import com.simiacryptus.cognotik.docops.status.DocOpsStatus
    import com.simiacryptus.cognotik.docops.status.JsonFileDocStatusStore
    import com.simiacryptus.cognotik.docops.status.TaskStatus
    import com.simiacryptus.cognotik.platform.model.User
    import com.simiacryptus.cognotik.util.FixedConcurrencyProcessor
    import java.io.File
    import java.util.concurrent.Executors
    import java.util.concurrent.atomic.AtomicBoolean

    /**
     * Server-side driver for the **DocOps API**, used by [ServerTaskActions] to back
     * `POST {mount}/.fsapi/v1/docops`.
     *
     * This is deliberately *not* a call into [DocOpsCli]: that object is a reference
     * command line interface (argv parsing, `exitProcess`, usage text) and must stay
     * independent. Both drive the same underlying API — `DocProcessor` for planning
     * and execution, `JsonFileDocStatusStore` for status — so read [DocOpsCli] as the
     * documentation of that API and this as its embedded sibling.
     *
     * Differences from the CLI, all of them consequences of being embedded in a server:
     *
     *  1. **No platform bootstrap.** `FileServerCli` already called
     *     `CliSupport.installFileServices()` / `CliSupport.bootstrapPlatform(user)`,
     *     and doing it twice per request would be wasteful. Nothing here touches
     *     `ApplicationServices` global state.
     *  2. **No `exitProcess`, no shutdown hooks.** Every entry point returns an exit
     *     code and throws `IllegalArgumentException` for bad input, so the caller can
     *     translate it into an `EINVAL` FS API error.
     *  3. **Paths are untrusted.** Requested documents are canonicalised and jailed
     *     under [Request.root] before being handed to `DocProcessor`.
     *  4. **Progress goes to stdout**, which `ServerTaskActions` tees into the task
     *     record; that is what the browser polls with `GET /.fsapi/v1/tasks?id=`.
     */
    object ServerDocOps {

      /** Cap on how many context files are enumerated per task by [printPlan]. */
      private const val MAX_CONTEXT_FILES_SHOWN = 8

      /** One planned output file, for `?resolveParam=target` option lists. */
      data class Target(val path: String, val description: String)

      class Request(
        /** Project root / working directory handed to `DocProcessor`. */
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
        val smartModel: String? = null,
        val fastModel: String? = null,
        val imageModel: String? = System.getenv("COGNOTIK_IMAGE_MODEL"),
        val audioModel: String? = System.getenv("COGNOTIK_AUDIO_MODEL"),
        val concurrency: Int = 4,
        /** true = start an ephemeral monitor server so sessions can be watched. */
        val monitor: Boolean = false,
        val autoFix: Boolean = true,
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

      /** `docops models` — model ids usable by the server's user. */
      fun models(user: User): Int {
        println(describeModels(availableModels(user)))
        return 0
      }

      /** `docops plan` — pure: writes nothing, starts nothing. */
      fun plan(req: Request): Int {
        val processor = newProcessor(req)
        val plan = buildPlan(processor, req)
        printPlan(plan, req.root, checkMode(req.mode))
        return if (plan.failed.isNotEmpty()) 1 else 0
      }

      /** `docops run` — plan, seed the status file, then execute. Mutates the workspace. */
      fun run(req: Request): Int {
        val processor = newProcessor(req)
        val plan = buildPlan(processor, req)
        printPlan(plan, req.root, checkMode(req.mode))
        if (plan.isEmpty) {
          println("Nothing to do.")
          return 0
        }
        /* Seed docops.status.json before doing anything destructive. */
        processor.docOps.initializeStatus(plan)

        val monitor = if (!req.monitor) null else
          EphemeralMonitorServer(host = "localhost", requestedPort = null).also { server ->
            println("Starting ephemeral monitor server (stops when this task finishes)...")
            println("Monitor: ${server.start()}")
          }
        val cancelFlag = AtomicBoolean(false)
        val pool = FixedConcurrencyProcessor(
          Executors.newCachedThreadPool { r -> Thread(r, "docops-fsapi").apply { isDaemon = true } },
          req.concurrency
        )
        try {
          println("Executing ${plan.tasks.size} task(s) with concurrency ${req.concurrency}...")
          val sessions = processor.runAll(
            plan = plan,
            pool = pool,
            cancelFlag = cancelFlag,
          ) { session ->
            val url = monitor?.monitorUrl(session)
            println("  session $session" + (url?.let { " -> $it" } ?: ""))
          }
          println("Finished: ${sessions.size} session(s).")
        } finally {
          monitor?.close()
        }

        val status = processor.docOps.statusStore.read()
        printStatus(status)
        val failures = status.tasks.values.count { it.status == TaskStatus.FAILED }
        return if (failures > 0) 1 else 0
      }

      /**
       * Options for `?target=`: the real [WorkPlan] for the current selection, so no
       * output scraping is involved. `plan` is pure, hence safe on a read-only mount.
       */
      fun targets(req: Request): List<Target> {
        val processor = newProcessor(req)
        /* The target filter is what we are enumerating, so it must not be applied. */
        val plan = buildPlan(processor, req, applyTarget = false)
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
       * Plans
       * ------------------------------------------------------------------
       */

      private fun newProcessor(req: Request): DocProcessor {
        val models = resolveModels(req)
        return DocProcessor(
          root = req.root,
          docsFolder = req.docsFolder,
          updateMode = checkMode(req.mode),
          smartModel = models.smart,
          fastModel = models.fast,
          imageModel = models.image,
          audioModel = models.audio,
          /* No monitor means fully in-process: never start a second web server. */
          serverless = !req.monitor,
          openBrowser = false,
          autoFix = req.autoFix,
          user = req.user,
          templateVarOverrides = req.templateVars,
          showMenubar = false,
        )
      }

      private fun buildPlan(
        processor: DocProcessor,
        req: Request,
        applyTarget: Boolean = true,
      ): WorkPlan<PlatformTaskKind> {
        val files = resolveDocFiles(req)
        warnAboutNonDocs(processor, req, files)
        val plan = if (req.paths.isEmpty()) processor.getAll() else processor.getAll(*files.toTypedArray())
            return if (applyTarget) applyTargetFilter(plan, req.root, req.targets) else plan
      }

      /** Canonicalise, jail to the root, expand folders. Requested paths are untrusted. */
      private fun resolveDocFiles(req: Request): List<File> {
        if (req.paths.isEmpty()) return markdownFiles(req.docsFolder)
        val rootPath = req.root.canonicalFile.toPath()
        return req.paths.map { path ->
          val file = File(req.root, path.removePrefix("/")).canonicalFile
          if (!file.toPath().startsWith(rootPath)) {
            throw IllegalArgumentException("document escapes the served root: $path")
          }
          if (!file.exists()) throw IllegalArgumentException("document not found: $path")
          file
        }.flatMap { if (it.isDirectory) markdownFiles(it) else listOf(it) }
      }

      private fun markdownFiles(folder: File): List<File> = folder.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() in setOf("md", "markdown") }
        .toList()

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

      private fun applyTargetFilter(
        plan: WorkPlan<PlatformTaskKind>,
        root: File,
            targets: List<String>,
      ): WorkPlan<PlatformTaskKind> {
            val wanted = targets
              .filter { it.isNotBlank() }
              .map { File(root, it.removePrefix("/")).canonicalFile }
            if (wanted.isEmpty()) return plan
        return plan.filter { planned ->
              wanted.any { targetFile ->
                try {
                  planned.task.data.main_file?.canonicalFile?.endsWith(targetFile) == true
                      || planned.target.file.canonicalFile == targetFile
                } catch (e: Exception) {
                  false
                }
          }
        }
      }

      /*
       * ------------------------------------------------------------------
       * Models
       * ------------------------------------------------------------------
       */

      private class Models(
        val smart: ChatModel,
        val fast: ChatModel,
        val image: ChatModel,
        val audio: ChatModel,
      )

      /** Prefers the set the server already resolved at start-up; falls back to the user's. */
      private fun availableModels(user: User): Map<String, ChatModel> {
        val cached = FileServerCli.available
        if (cached.isNotEmpty()) return cached
        return try {
          CliSupport.availableModels(user)
        } catch (e: Exception) {
          println("warning: could not read user model settings: ${e.message}")
          emptyMap()
        }
      }

      private fun resolveModels(req: Request): Models {
        val available = availableModels(req.user)
        val smartId = req.smartModel?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException(
          "no smart model selected. Start the server with --smart-model <id> " +
              "or set COGNOTIK_SMART_MODEL.\n" + describeModels(available)
        )
        val smart = resolveModel(smartId, available)
        val fast = req.fastModel?.takeIf { it.isNotBlank() }?.let { resolveModel(it, available) } ?: smart
        val image = req.imageModel?.takeIf { it.isNotBlank() }?.let { resolveModel(it, available) } ?: fast
        val audio = req.audioModel?.takeIf { it.isNotBlank() }?.let { resolveModel(it, available) } ?: fast
        println("Models: smart=${smart.modelId} fast=${fast.modelId} image=${image.modelId} audio=${audio.modelId}")
        return Models(smart = smart, fast = fast, image = image, audio = audio)
      }

      private fun resolveModel(modelId: String, available: Map<String, ChatModel>): ChatModel {
        available.values.firstOrNull { it.modelId == modelId }?.let { return it }
        available[modelId]?.let { return it }
        available.entries.firstOrNull { it.key.equals(modelId, ignoreCase = true) }?.let { return it.value }
        println("warning: model '$modelId' is not registered; using an unregistered text-only reference")
        return ChatModel(
          modelId = modelId,
          inputModalities = setOf(ChatMessageModality.TEXT),
          outputModalities = setOf(ChatMessageModality.TEXT),
        )
      }

      private fun describeModels(available: Map<String, ChatModel>): String = if (available.isEmpty()) {
        "No models are configured for this user; add an API key first."
      } else {
        "Available models (${available.size}):\n" +
            available.values.map { it.modelId }.distinct().sorted().joinToString("\n") { "  $it" }
      }

      /*
       * ------------------------------------------------------------------
       * Rendering (stdout is tee'd into the task record by ServerTaskActions)
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