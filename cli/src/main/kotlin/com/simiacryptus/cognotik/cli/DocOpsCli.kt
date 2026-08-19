package com.simiacryptus.cognotik.cli

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.CoreTasks
import com.simiacryptus.cognotik.chat.model.ChatMessageModality
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.cli.CliSupport.email
import com.simiacryptus.cognotik.docops.DocProcessor
import com.simiacryptus.cognotik.docops.PlatformTaskKind
import com.simiacryptus.cognotik.docops.UpdateMode
import com.simiacryptus.cognotik.docops.UpdateModes
import com.simiacryptus.cognotik.docops.model.WorkPlan
import com.simiacryptus.cognotik.docops.status.DocOpsStatus
import com.simiacryptus.cognotik.docops.status.JsonFileDocStatusStore
import com.simiacryptus.cognotik.docops.status.TaskStatus
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.FileApplicationServices
import com.simiacryptus.cognotik.platform.ApiChatModel
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.FixedConcurrencyProcessor
import com.simiacryptus.cognotik.util.UnifiedHarness
import com.simiacryptus.cognotik.util.encrypt
import com.simiacryptus.cognotik.webui.servlet.ApiProviderServlet.Companion.models
import com.simiacryptus.cognotik.webui.servlet.ApiProviderServlet.Companion.userSettings
import java.awt.Desktop
import java.io.File
import java.io.PrintStream
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Reference implementation of a **DocOps command line interface**.
 *
 * ```
 * cognotik docops plan                     # pure: show what would happen
 * cognotik docops run docs/api.md          # plan + execute one document
 * cognotik docops status                   # read docops.status.json
 * cognotik docops vars                     # list declared {{ TEMPLATE_VARS }}
 * cognotik docops models                   # list usable model ids
 * ```
 *
 * Design notes (this is meant to be read as documentation):
 *
 *  1. **Planning is pure.** `plan` never mutates the workspace and never starts a server.
 *  2. **The server is ephemeral.** A Jetty instance is started *only* when there is at least
 *     one task to execute, purely so the user can watch progress. The monitor link is printed
 *     to stdout. It is stopped in a `finally` block and by a shutdown hook. There is no
 *     PID file, no detached process, no reconnect logic — this is not a daemon.
 *  3. **One-shot process.** `main` always ends in [exitProcess] so agent thread pools cannot
 *     keep the JVM alive after the work is done.
 */
object DocOpsCli {

  private val COMMANDS = setOf("plan", "run", "status", "vars", "models", "keys", "help")

  /** Cap on how many context files are enumerated per task by [printPlan]. */
  private const val MAX_CONTEXT_FILES_SHOWN = 8

  @JvmStatic
  fun main(args: Array<String>) {
    val opts = try {
      parse(args)
    } catch (e: IllegalArgumentException) {
      System.err.println("docops: ${e.message}")
      printUsage(System.err)
      exitProcess(2)
    }
    if (opts.help || opts.command == "help") {
      printUsage(System.out)
      exitProcess(0)
    }
    var exitCode = 0
    try {
      exitCode = execute(opts)
    } catch (e: Throwable) {
      System.err.println("docops: ${e.javaClass.simpleName}: ${e.message ?: e.toString()}")
      generateSequence(e.cause) { it.cause }.take(3).forEach {
        System.err.println("  caused by: ${it.javaClass.simpleName}: ${it.message}")
      }
      if (System.getenv("COGNOTIK_DEBUG") == null) {
        System.err.println("  (set COGNOTIK_DEBUG=1 for a stack trace)")
      }
      if (System.getenv("COGNOTIK_DEBUG") != null) e.printStackTrace()
      exitCode = 1
    }
    // One-shot CLI: never linger.
    exitProcess(exitCode)
  }

  /**
   * Programmatic entry point: identical to [main] but returns the exit code instead of
   * killing the JVM, so an embedding process (e.g. the file server's `docops` FS action)
   * can drive it in-process. Throws [IllegalArgumentException] on bad usage.
   */
  fun run(args: Array<String>): Int {
    val opts = parse(args)
    if (opts.help || opts.command == "help") {
      printUsage(System.out)
      return 0
    }
    return execute(opts)
  }


  private fun execute(opts: CliOptions): Int {
    val servicesCache = mutableMapOf<File, FileApplicationServices>()
    CliSupport.installFileServices()
    val root = opts.root
    val docsFolder = opts.docsFolder ?: root
    if (!root.isDirectory) throw IllegalArgumentException("root is not a directory: ${root.absolutePath}")

    // These two commands touch neither the platform nor a model.
    when (opts.command) {
      "status" -> {
        printStatus(JsonFileDocStatusStore(root).read())
        return 0
      }
      // Credential setup needs the settings store but no model and no server.
      "keys" -> return ApiKeysCli.configure(
        root = root,
        user = defaultUser(),
        installServices = false,
      )

      "vars" -> {
        val files = resolveDocFiles(opts, root, docsFolder)
        val vars = DocProcessor.listTemplateVarKeys(files)
        if (vars.isEmpty()) {
          println("No template variables declared in ${files.size} document(s).")
        } else {
          println("Template variables (${vars.size}):")
          vars.toSortedMap().forEach { (k, v) -> println("  $k = ${v.ifBlank { "<no default>" }}") }
          println()
          println("Override with: --var NAME=VALUE (repeatable)")
        }
        return 0
      }
    }

    val user = defaultUser()
    bootstrapPlatform(user)

    if (opts.command == "models") {
      println(describeModels(availableModels(user)))
      return 0
    }

    val models = resolveModels(opts, user)
    val updateMode: UpdateMode = UpdateModes.fromName(opts.mode)
      ?: throw IllegalArgumentException(
        "unknown --mode '${opts.mode}'. Known modes: " + UpdateModes.entries.joinToString(", ") { it.name })

    val docProcessor = DocProcessor(
      root = root,
      docsFolder = docsFolder,
      updateMode = updateMode,
      smartModel = models.smart,
      fastModel = models.fast,
      imageModel = models.image,
      audioModel = models.audio,
      serverless = opts.serverless,
      // The CLI owns browser launching, so the harness must not try to open its own URL.
      openBrowser = false,
      autoFix = opts.autoFix,
      user = user,
      templateVarOverrides = opts.templateVars,
      showMenubar = false,
    )

    val docFiles = resolveDocFiles(opts, root, docsFolder)
    warnAboutNonDocs(docProcessor, docFiles, opts)

    val plan = applyTargetFilter(
      plan = if (!opts.hasExplicitDocs) docProcessor.getAll() else docProcessor.getAll(*docFiles.toTypedArray()),
      root = root,
      target = opts.target,
    )
    printPlan(plan, root, updateMode)

    if (opts.command == "plan") return if (plan.failed.isNotEmpty()) 1 else 0

    if (plan.isEmpty) {
      println("Nothing to do - no ephemeral server started.")
      return 0
    }

    // Seed docops.status.json before doing anything destructive.
    docProcessor.docOps.initializeStatus(plan)

    val monitor = if (opts.monitor && !opts.serverless) {
      EphemeralMonitorServer(host = opts.host, requestedPort = opts.port).also { server ->
        println("Starting ephemeral monitor server (stops when this command exits)...")
        println("Monitor: ${server.start()}")
      }
    } else null

    val cancelFlag = AtomicBoolean(false)
    val pool = FixedConcurrencyProcessor(
      Executors.newCachedThreadPool { r -> Thread(r, "docops-cli").apply { isDaemon = true } },
      opts.concurrency
    )
    val shutdownHook = Thread {
      cancelFlag.set(true)
      monitor?.close()
    }
    Runtime.getRuntime().addShutdownHook(shutdownHook)

    val sessionCount: Int
    try {
      println("Executing ${plan.tasks.size} task(s) with concurrency ${opts.concurrency}...")
      val sessions = docProcessor.runAll(
        plan = plan,
        pool = pool,
        cancelFlag = cancelFlag,
      ) { session ->
        val url = monitor?.monitorUrl(session)
        println("  session $session" + (url?.let { " -> $it" } ?: ""))
        if (opts.openBrowser && url != null) openBrowser(url)
      }
      sessionCount = sessions.size
    } finally {
      try {
        Runtime.getRuntime().removeShutdownHook(shutdownHook)
      } catch (_: Exception) {
      }
      monitor?.close()
    }

    println("Finished: $sessionCount session(s).")
    val status = docProcessor.docOps.statusStore.read()
    printStatus(status)
    val failures = status.tasks.values.count { it.status == TaskStatus.FAILED }
    return if (failures > 0) 1 else 0
  }

  /**
   * Minimal, headless equivalent of what the app server does at boot: register dynamic enums
   * (task types, providers, runtimes), install a single-local-user auth stack, and tell the
   * orchestrator how to build chat clients from a model + user pair.
   */
  private fun defaultUser(): User = User(
    email = System.getenv("EMAIL")
      ?: System.getProperty("user.email")
      ?: "user@localhost"
  )

  private fun bootstrapPlatform(user: User) {
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

  private fun availableModels(user: User): Map<String, ChatModel> = try {
    user.userSettings().models()
  } catch (e: Exception) {
    System.err.println("warning: could not read user model settings: ${e.message}")
    emptyMap()
  }

  private fun resolveModels(opts: CliOptions, user: User): Models {
    val available = availableModels(user)
    val smartId = opts.smartModel ?: throw IllegalArgumentException(
      "no smart model selected. Pass --smart-model <id> or set COGNOTIK_SMART_MODEL.\n" +
          describeModels(available)
    )
    val smart = resolveModel(smartId, available)
    val fast = opts.fastModel?.let { resolveModel(it, available) } ?: smart
    val image = opts.imageModel?.let { resolveModel(it, available) } ?: fast
    val audio = opts.audioModel?.let { resolveModel(it, available) } ?: fast
    println("Models: smart=${smart.modelId} fast=${fast.modelId} image=${image.modelId} audio=${audio.modelId}")
    return Models(smart = smart, fast = fast, image = image, audio = audio)
  }

  private fun resolveModel(modelId: String, available: Map<String, ChatModel>): ChatModel {
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

  private fun describeModels(available: Map<String, ChatModel>): String = if (available.isEmpty()) {
    "No models are configured for this user; add an API key first."
  } else {
    "Available models (${available.size}):\n" +
        available.values.map { it.modelId }.distinct().sorted().joinToString("\n") { "  $it" }
  }

  /*
   * ------------------------------------------------------------------
   * Documents and plans
   * ------------------------------------------------------------------
   */

  private fun resolveDocFiles(opts: CliOptions, root: File, docsFolder: File): List<File> {
    // Positional arguments may be files or directories; --doc is restricted to files.
    val requested = opts.paths.map { it to false } + opts.docFiles.map { it to true }
    if (requested.isEmpty()) return markdownFiles(docsFolder)
    return requested.map { (path, mustBeFile) ->
      val file = File(path).let { if (it.isAbsolute) it else root.resolve(path) }.canonicalFile
      if (!file.exists()) throw IllegalArgumentException("document not found: $path")
      if (mustBeFile && file.isDirectory) {
        throw IllegalArgumentException("--doc expects a file (use --docs for a directory): $path")
      }
      if (file.isDirectory) return@map file
      if (!file.canonicalPath.startsWith(root.canonicalPath)) {
        throw IllegalArgumentException("document is outside --root: $path")
      }
      file
    }.flatMap { if (it.isDirectory) markdownFiles(it) else listOf(it) }
  }

  private fun markdownFiles(folder: File): List<File> = folder.walkTopDown()
    .filter { it.isFile && it.extension.lowercase() in setOf("md", "markdown") }
    .toList()

  private fun warnAboutNonDocs(docProcessor: DocProcessor, files: List<File>, opts: CliOptions) {
    if (!opts.hasExplicitDocs) return
    files.forEach { file ->
      val spec = try {
        docProcessor.docOps.loader.load(file)
      } catch (e: Exception) {
        null
      }
      if (spec == null) {
        System.err.println(
          "warning: ${file.name} has no doc-ops frontmatter " +
              "(expected one of: specifies, transforms, documents, generates, folder)"
        )
      }
    }
  }

  private fun applyTargetFilter(
    plan: WorkPlan<PlatformTaskKind>,
    root: File,
    target: String?,
  ): WorkPlan<PlatformTaskKind> {
    if (target.isNullOrBlank()) return plan
    val targetFile = (File(target).let { if (it.isAbsolute) it else root.resolve(target) }).canonicalFile
    return plan.filter { planned ->
      try {
        planned.task.data.main_file?.canonicalFile?.endsWith(targetFile) == true
      } catch (e: Exception) {
        false
      }
    }
  }

  private fun printPlan(plan: WorkPlan<PlatformTaskKind>, root: File, mode: UpdateMode) {
    println("Plan: ${plan.tasks.size} task(s) in ${plan.queues.size} queue(s) [mode=$mode]")
    var taskNumber = 0
    plan.queues.filter { !it.isEmpty }.forEachIndexed { queueIndex, queue ->
      println("  Queue ${queueIndex + 1}/${plan.queues.size} (${queue.tasks.size} task(s), run sequentially):")
      queue.tasks.forEach { planned ->
        taskNumber++
        val target = planned.target.relativeToOrAbsolute(root)
        val verb = if (planned.target.file.exists()) "update" else "create"
        println("    [$taskNumber] $verb ${'$'}target  [${planned.task.taskType.name}]")
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

  private fun openBrowser(url: String) {
    try {
      Desktop.getDesktop().browse(URI(url))
    } catch (e: Exception) {
      System.err.println("warning: could not open browser for $url: ${e.message}")
    }
  }

  /*
   * ------------------------------------------------------------------
   * Argument parsing
   * ------------------------------------------------------------------
   */

  private class CliOptions {
    var command: String = "run"
    val paths: MutableList<String> = mutableListOf()
    var root: File = File(".").canonicalFile
    var docsFolder: File? = null

    /** Explicit single documents from repeated `--doc FILE`. */
    val docFiles: MutableList<String> = mutableListOf()
    var mode: String = "PatchToUpdate"
    var target: String? = null
    var smartModel: String? = System.getenv("COGNOTIK_SMART_MODEL")
    var fastModel: String? = System.getenv("COGNOTIK_FAST_MODEL")
    var imageModel: String? = System.getenv("COGNOTIK_IMAGE_MODEL")
    var audioModel: String? = System.getenv("COGNOTIK_AUDIO_MODEL")
    var concurrency: Int = 4
    var port: Int? = null
    var host: String = "localhost"
    var monitor: Boolean = true
    var serverless: Boolean = false
    var openBrowser: Boolean = false
    var autoFix: Boolean = true
    var help: Boolean = false
    val templateVars: MutableMap<String, String> = linkedMapOf()

    /** True when the user named documents explicitly instead of scanning `--docs`. */
    val hasExplicitDocs: Boolean get() = paths.isNotEmpty() || docFiles.isNotEmpty()
  }

  private fun parse(args: Array<String>): CliOptions {
    val opts = CliOptions()
    var dryRun = false
    var commandSeen = false
    var i = 0
    fun value(name: String): String {
      if (i + 1 >= args.size) throw IllegalArgumentException("missing value for $name")
      return args[++i]
    }

    fun int(name: String): Int = value(name).toIntOrNull()
      ?: throw IllegalArgumentException("$name expects an integer")

    fun putVar(spec: String) {
      val idx = spec.indexOf('=')
      if (idx <= 0) throw IllegalArgumentException("template variable must be NAME=VALUE, got '$spec'")
      opts.templateVars[spec.substring(0, idx).trim()] = spec.substring(idx + 1)
    }

    while (i < args.size) {
      val arg = args[i]
      when {
        arg == "-h" || arg == "--help" -> opts.help = true
        arg == "--root" -> opts.root = File(value(arg)).canonicalFile
        arg == "--docs" -> opts.docsFolder = File(value(arg)).canonicalFile
        arg == "--doc" -> opts.docFiles.add(value(arg))
        arg == "-m" || arg == "--mode" -> opts.mode = value(arg)
        arg == "--target" -> opts.target = value(arg)
        arg == "--smart-model" -> opts.smartModel = value(arg)
        arg == "--fast-model" -> opts.fastModel = value(arg)
        arg == "--image-model" -> opts.imageModel = value(arg)
        arg == "--audio-model" -> opts.audioModel = value(arg)
        arg == "-c" || arg == "--concurrency" -> opts.concurrency = int(arg).coerceAtLeast(1)
        arg == "-p" || arg == "--port" -> opts.port = int(arg)
        arg == "--host" -> opts.host = value(arg)
        arg == "--no-monitor" -> opts.monitor = false
        arg == "--serverless" -> {
          opts.serverless = true
          opts.monitor = false
        }

        arg == "--email" -> email = args.getOrNull(++i) ?: throw IllegalArgumentException("missing value for --email")

        arg == "--open" -> opts.openBrowser = true
        arg == "--no-auto-fix" -> opts.autoFix = false
        arg == "-n" || arg == "--dry-run" -> dryRun = true
        arg == "--var" -> putVar(value(arg))
        arg.startsWith("-D") && arg.contains('=') -> putVar(arg.substring(2))
        arg.startsWith("-") -> throw IllegalArgumentException("unknown option: $arg")
        !commandSeen && arg.lowercase() in COMMANDS -> {
          opts.command = arg.lowercase()
          commandSeen = true
        }

        else -> opts.paths.add(arg)
      }
      i++
    }
    if (dryRun) opts.command = "plan"
    return opts
  }

  private fun printUsage(out: PrintStream) {
    out.println(
      """
            DocOps CLI - render markdown frontmatter specifications into files.

            Usage:
              cognotik docops [command] [document...] [options]

            Commands:
              run       Plan and execute (default). Starts an ephemeral monitor server if work exists.
              plan      Plan only. Pure: writes nothing, starts nothing.
              status    Print docops.status.json for --root.
              vars      List declared {{ TEMPLATE_VARS }} and their defaults.
              models    List model ids usable by the current user.
               keys      Interactively configure provider API keys.
              help      Show this message.

            Documents:
              Zero or more markdown files or directories (relative to --root).
              May also be named with --doc FILE (repeatable).
              Omit both to process every *.md / *.markdown under --docs.

            Options:
              --root DIR             Project root / working directory (default: .)
              --docs DIR             Folder to scan for documents (default: --root)
              --doc FILE             Single document file to process (repeatable)
              -m, --mode MODE        Update mode: SkipExisting, OverwriteExisting, OverwriteToUpdate,
                                     PatchExisting, PatchToUpdate, ForceUpdate, ForceOverwrite
                                     (default: PatchToUpdate)
              --target PATH          Only run the task producing this output file
              --var NAME=VALUE       Template variable override (repeatable; -DNAME=VALUE also works)
              -c, --concurrency N    Concurrent task queues (default: 4)
              --smart-model ID       Primary model (or COGNOTIK_SMART_MODEL)
              --fast-model ID        Secondary model (default: smart model)
              --image-model ID       Image model (default: fast model)
              --audio-model ID       Audio model (default: fast model)
              -p, --port N           Monitor server port (default: an unused ephemeral port)
              --host NAME            Monitor server bind name (default: localhost)
              --no-monitor           Do not start the monitor server
              --serverless           Run fully in-process; implies --no-monitor
              --open                 Open each task session in a browser
              --no-auto-fix          Do not auto-apply generated patches
              -n, --dry-run          Same as the 'plan' command
              -h, --help             Show this message

            Exit codes:
              0 success, 1 one or more tasks failed, 2 bad usage

            Notes:
              This command runs in the foreground and exits when finished. The monitor server it
              may start is ephemeral: it is stopped on completion, on error, and on Ctrl-C. No
              background service is installed and no daemon is contacted.

            Examples:
              cognotik docops plan
               cognotik docops keys
              cognotik docops run docs/api.md --smart-model claude-3-5-sonnet-20241022
              cognotik docops run --doc docs/api.md --doc docs/cli.md
              cognotik docops run --mode ForceUpdate --var MODULE=billing --open
              cognotik docops status --root /work/project
          """.trimIndent()
    )
  }
}

fun ApiChatModel.instance(
  user: User,
  session: Session = globalID,
  service: ExecutorService = ApplicationServices.threadPoolManager.getPool(session, user),
  temperature: Double = 0.1
) = model?.instance(
  key = when (provider?.key) {
    null -> null
    "NONE".encrypt -> null
    else -> provider?.key
  } ?: ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(user).apis.let {
    it.firstOrNull { it.provider == this.provider }?.key
      ?: it.firstOrNull { (it.provider?.name ?: "b") == (this.model?.provider?.name ?: "a") }?.key
      ?: throw IllegalStateException("No API key configured for model $model")
  },
  base = provider?.provider?.base ?: model?.provider?.base
  ?: throw IllegalStateException("No API base configured for model $model"),
  workPool = service,
  temperature = temperature,
  scheduledPool = ApplicationServices.threadPoolManager.getScheduledPool(session, user),
  session = session,
  user = user,
)

val globalID = Session.newGlobalID()