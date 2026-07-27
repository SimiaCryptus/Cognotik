package com.simiacryptus.cognotik.cli

import com.simiacryptus.cognotik.autofix.AutoFixTask
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.UnifiedHarness
import java.awt.Desktop
import java.io.File
import java.io.PrintStream
import java.net.URI
import java.text.SimpleDateFormat
import kotlin.system.exitProcess

/**
 * Reference implementation of an **AutoFix command line wrapper**.
 *
 * It wraps an arbitrary inner command and applies the `AutoFix` task loop to it:
 *
 * ```
 * cognotik autofix -- ./gradlew build            # run, parse errors, patch, re-run
 * cognotik autofix "npm test" --no-auto-fix      # propose patches, approve them in the UI
 * cognotik autofix --cmd "cmake --build build" --cmd "ctest" --dir build
 * cognotik autofix --dry-run -- pytest -q        # show what would run, touch nothing
 * ```
 *
 * Design notes (this is meant to be read as documentation):
 *
 *  1. **The inner command is the contract.** The CLI exit code is 0 only when the wrapped
 *     command finally exits 0 (or a human explicitly accepted the result).
 *  2. **The server is ephemeral.** A monitor server is started only when work exists, purely
 *     so the patch/retry loop is watchable. It is stopped in `finally` and by a shutdown hook.
 *  3. **One-shot process.** `main` always ends in [exitProcess]; agent pools cannot keep the
 *     JVM alive after the wrapped command is green.
 *  4. **No orchestration logic lives here.** Everything below is argv -> `AutoFixTask`
 *     configuration -> [UnifiedHarness.runTask].
 */
object AutoFixCli {

  @JvmStatic
  fun main(args: Array<String>) {
    CliSupport.installFileServices()
    val user = CliSupport.defaultUser()
    CliSupport.bootstrapPlatform(user)
    val opts = try {
      parse(args)
    } catch (e: IllegalArgumentException) {
      System.err.println("autofix: ${e.message}")
      printUsage(System.err)
      exitProcess(2)
    }
    if (opts.help) {
      printUsage(System.out)
      exitProcess(0)
    }
    var exitCode = 0
    try {
      exitCode = execute(opts)
    } catch (e: IllegalArgumentException) {
      System.err.println("autofix: ${e.message}")
      exitCode = 2
    } catch (e: Throwable) {
      System.err.println("autofix: ${e.javaClass.simpleName}: ${e.message ?: e.toString()}")
      generateSequence(e.cause) { it.cause }.take(3).forEach {
        System.err.println("  caused by: ${it.javaClass.simpleName}: ${it.message}")
      }
      if (System.getenv("COGNOTIK_DEBUG") == null) {
        System.err.println("  (set COGNOTIK_DEBUG=1 for a stack trace)")
      } else {
        e.printStackTrace()
      }
      exitCode = 1
    }
    // One-shot CLI: never linger.
    exitProcess(exitCode)
  }

  private fun execute(opts: CliOptions): Int {
    if (!opts.root.isDirectory) throw IllegalArgumentException("root is not a directory: ${opts.root.absolutePath}")
    val commandDir = (opts.dir ?: opts.root).canonicalFile
    if (!commandDir.isDirectory) {
      throw IllegalArgumentException("working directory does not exist: ${commandDir.absolutePath}")
    }
    val user = CliSupport.defaultUser()
    if (opts.commands.isEmpty()) {
      throw IllegalArgumentException("no command given; pass it after '--' or with --cmd \"<command>\"")
    }
    printPlan(opts, commandDir)
    if (opts.dryRun) {
      println("Dry run - nothing executed, no server started.")
      return 0
    }

    // Non-interactive mode cannot render approval links, so it must auto-apply.
    if (opts.serverless && !opts.autoFix) {
      System.err.println("warning: --no-auto-fix needs the monitor UI; forcing auto-fix on for --serverless")
      opts.autoFix = true
    }
    val models = CliSupport.resolveModels(
      user = user,
      smartModel = opts.smartModel,
      fastModel = opts.fastModel,
      imageModel = opts.imageModel,
      audioModel = opts.audioModel,
    )

    val monitor = if (opts.monitor && !opts.serverless) {
      EphemeralMonitorServer(host = opts.host, requestedPort = opts.port).also { server ->
        println("Starting ephemeral monitor server (stops when this command exits)...")
        println("Monitor: ${server.start()}")
      }
    } else null
    val shutdownHook = Thread { monitor?.close() }
    Runtime.getRuntime().addShutdownHook(shutdownHook)

    // Transcripts / session data live beside the project, never inside it as clutter.
    val runStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())
    val dataDir = File(opts.root, ".cognotik/autofix/run-$runStamp").apply { mkdirs() }

    val harness = object : UnifiedHarness(
      port = monitor?.port ?: (opts.port ?: 0).coerceAtLeast(1024),
      serverless = opts.serverless,
      // The CLI owns browser launching, so the harness must not open its own URL.
      openBrowser = false,
      modelInstanceFn = { model, session, u ->
        model.instance(user = u, session = session, temperature = opts.temperature)
          ?: throw IllegalStateException("No model/provider configured for ${model.model?.modelId ?: model}")
      },
      smartModel = models.smart,
      fastModel = models.fast,
      imageModel = models.image,
      audioModel = models.audio,
      temperature = opts.temperature,
      showMenubar = false,
      name = "AutoFix",
      user = user,
    ) {
      override fun createTempDirectory(prefix: String) = dataDir
    }

    val session: Session = harness.session
    monitor?.let { server ->
      val url = server.monitorUrl(session)
      println("Session $session -> $url")
      if (opts.openBrowser) openBrowser(url)
    }

    var result: String? = null
    try {
      println("Running ${opts.commands.size} command(s) with auto-fix=${opts.autoFix} (timeout ${opts.timeoutMinutes}m)...")
      harness.runTask(
        taskType = AutoFixTask.AutoFix,
        timeoutMinutes = opts.timeoutMinutes,
        message = "Run and fix: " + opts.commands.joinToString(" && ") { render(it) },
        executionConfig = executionConfig(opts, commandDir),
        onComplete = { text, _ -> result = text },
      ) { taskSession ->
        settings(harness, taskSession, commandDir, opts.autoFix)
      }
    } finally {
      try {
        Runtime.getRuntime().removeShutdownHook(shutdownHook)
      } catch (_: Exception) {
      }
      monitor?.close()
      harness.close()
    }

    println()
    println(result?.trim() ?: "No result reported by the AutoFix task.")
    println("Transcripts: ${dataDir.absolutePath}")
    return if (result?.trimStart()?.startsWith("### Success") == true) 0 else 1
  }

  /**
   * One `AutoFix` task carrying every wrapped command, so the loop re-runs the whole
   * sequence after each patch round (a fix for step 1 must not break step 2).
   */
  private fun executionConfig(opts: CliOptions, commandDir: File) =
    AutoFixTask.AutoFixTaskExecutionConfigData(
      commands = opts.commands.map { argv ->
        AutoFixTask.CommandWithWorkingDir(
          executable = argv.first(),
          arguments = argv.drop(1).toMutableList(),
          working_dir = commandDir.absolutePath,
        )
      }.toMutableList(),
      task_description = "Run " + opts.commands.joinToString(" && ") { render(it) } +
          " in ${commandDir.absolutePath} and fix any errors it reports.",
    )

  private fun settings(
    harness: UnifiedHarness,
    session: Session,
    commandDir: File,
    autoFix: Boolean,
  ): OrchestrationConfig {
    // Registers session data/user paths (transcripts, usage.json) for this session.
    harness.getRoot(workspace = null, session = session, name = "autofix")
    return harness.createSettings(
      session = session,
      autoFix = autoFix,
      typeConfig = AutoFixTask.AutoFixTaskTypeConfig(),
      workingDir = commandDir.absolutePath,
    )
  }

  private fun printPlan(opts: CliOptions, commandDir: File) {
    println("Plan: ${opts.commands.size} command(s) in ${commandDir.absolutePath}")
    opts.commands.forEachIndexed { index, argv ->
      println("  [${index + 1}] ${render(argv)}")
    }
    println("  mode: " + (if (opts.autoFix) "auto-apply patches" else "propose patches, wait for approval"))
  }

  private fun render(argv: List<String>) = argv.joinToString(" ") { if (it.contains(' ')) "\"$it\"" else it }

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
    var root: File = File(".").canonicalFile
    var dir: File? = null

    /** Each entry is one command as an argv list; all of them run in order, every iteration. */
    val commands: MutableList<List<String>> = mutableListOf()
    var smartModel: String? = System.getenv("COGNOTIK_SMART_MODEL")
    var fastModel: String? = System.getenv("COGNOTIK_FAST_MODEL")
    var imageModel: String? = System.getenv("COGNOTIK_IMAGE_MODEL")
    var audioModel: String? = System.getenv("COGNOTIK_AUDIO_MODEL")
    var temperature: Double = 0.0
    var timeoutMinutes: Long = 30
    var port: Int? = null
    var host: String = "localhost"
    var monitor: Boolean = true
    var serverless: Boolean = false
    var openBrowser: Boolean = false
    var autoFix: Boolean = true
    var dryRun: Boolean = false
    var listModels: Boolean = false
    var help: Boolean = false
  }

  private fun parse(args: Array<String>): CliOptions {
    val opts = CliOptions()
    val positional = mutableListOf<String>()
    var i = 0
    fun value(name: String): String {
      if (i + 1 >= args.size) throw IllegalArgumentException("missing value for $name")
      return args[++i]
    }

    fun int(name: String): Int = value(name).toIntOrNull()
      ?: throw IllegalArgumentException("$name expects an integer")

    while (i < args.size) {
      val arg = args[i]
      // Everything after `--` is the inner command verbatim: no quoting games.
      if (arg == "--") {
        val rest = args.drop(i + 1)
        if (rest.isNotEmpty()) opts.commands.add(rest)
        break
      }
      when {
        arg == "-h" || arg == "--help" -> opts.help = true
        arg == "--root" -> opts.root = File(value(arg)).canonicalFile
        arg == "--dir" -> opts.dir = File(value(arg)).let { if (it.isAbsolute) it else opts.root.resolve(it) }
        arg == "--cmd" -> opts.commands.add(tokenize(value(arg)))
        arg == "--smart-model" -> opts.smartModel = value(arg)
        arg == "--fast-model" -> opts.fastModel = value(arg)
        arg == "--image-model" -> opts.imageModel = value(arg)
        arg == "--audio-model" -> opts.audioModel = value(arg)
        arg == "--temperature" -> opts.temperature = value(arg).toDoubleOrNull()
          ?: throw IllegalArgumentException("--temperature expects a number")

        arg == "-t" || arg == "--timeout" -> opts.timeoutMinutes = int(arg).coerceAtLeast(1).toLong()
        arg == "-p" || arg == "--port" -> opts.port = int(arg)
        arg == "--host" -> opts.host = value(arg)
        arg == "--no-monitor" -> opts.monitor = false
        arg == "--serverless" -> {
          opts.serverless = true
          opts.monitor = false
        }

        arg == "--open" -> opts.openBrowser = true
        arg == "--no-auto-fix" -> opts.autoFix = false
        arg == "-n" || arg == "--dry-run" -> opts.dryRun = true
        arg == "--list-models" -> opts.listModels = true
        arg.startsWith("-") -> throw IllegalArgumentException("unknown option: $arg (put the wrapped command after '--')")
        else -> positional.add(arg)
      }
      i++
    }
    if (positional.isNotEmpty()) {
      // `autofix "gradle build"` and `autofix gradle build` both mean one command.
      opts.commands.add(if (positional.size == 1) tokenize(positional.first()) else positional.toList())
    }
    if (opts.commands.any { it.isEmpty() }) throw IllegalArgumentException("empty command")
    return opts
  }

  /** Shell-ish splitter for `--cmd "..."`; supports quotes and backslash escapes only. */
  internal fun tokenize(spec: String): List<String> {
    val out = mutableListOf<String>()
    val token = StringBuilder()
    var quote: Char? = null
    var escaped = false
    for (c in spec) {
      when {
        escaped -> {
          token.append(c); escaped = false
        }

        c == '\\' && quote != '\'' -> escaped = true
        quote != null -> if (c == quote) quote = null else token.append(c)
        c == '"' || c == '\'' -> quote = c
        c.isWhitespace() -> if (token.isNotEmpty()) {
          out.add(token.toString()); token.clear()
        }

        else -> token.append(c)
      }
    }
    if (quote != null) throw IllegalArgumentException("unbalanced quote in command: $spec")
    if (token.isNotEmpty()) out.add(token.toString())
    if (out.isEmpty()) throw IllegalArgumentException("empty command: '$spec'")
    return out
  }

  private fun printUsage(out: PrintStream) {
    out.println(
      """
            AutoFix CLI - wrap any command and iteratively fix whatever it complains about.

            Usage:
              cognotik autofix [options] -- <command> [args...]
              cognotik autofix [options] "<command line>"
              cognotik autofix [options] --cmd "<command line>" [--cmd "..."]

            How it works:
              1. The wrapped command is executed in --dir (default --root).
              2. If it exits 0 the CLI exits 0 immediately - nothing is modified.
              3. Otherwise the output is parsed into discrete errors, patches are generated
                 against the referenced source files, applied (unless --no-auto-fix), and the
                 whole command sequence is re-run. Repeats until green or retries run out.

            Options:
              --root DIR             Project root used for file resolution (default: .)
              --dir DIR              Working directory for the command (default: --root)
              --cmd "CMD"            Add a command (repeatable; all re-run each iteration)
              --smart-model ID       Primary model (or COGNOTIK_SMART_MODEL) [required]
              --fast-model ID        Secondary model (default: smart model)
              --image-model ID       Image model (default: fast model)
              --audio-model ID       Audio model (default: fast model)
              --temperature N        Sampling temperature (default: 0.0)
              -t, --timeout MIN      Give up after MIN minutes (default: 30)
              -p, --port N           Monitor server port (default: an unused ephemeral port)
              --host NAME            Monitor server bind name (default: localhost)
              --no-monitor           Do not start the monitor server
              --serverless           Run fully in-process; implies --no-monitor and auto-fix
              --open                 Open the session in a browser
              --no-auto-fix          Propose patches and wait for approval in the monitor UI
              -n, --dry-run          Print the plan and exit; writes nothing, starts nothing
              --list-models          List model ids usable by the current user
              -h, --help             Show this message

            Exit codes:
              0 command finished successfully (possibly after fixes), 1 still failing, 2 bad usage

            Notes:
              No shell is spawned: '&&', '|', '>' and friends are NOT interpreted. Use --cmd
              multiple times for sequences, or point at a script. The monitor server is
              ephemeral - it stops on completion, on error, and on Ctrl-C.

            Examples:
              cognotik autofix -- ./gradlew :cli:compileKotlin
              cognotik autofix "npm test" --open
              cognotik autofix --cmd "cmake --build build" --cmd "ctest --output-on-failure" --dir build
              cognotik autofix --serverless -t 10 -- pytest -q
              cognotik autofix --no-auto-fix -- make
          """.trimIndent()
    )
  }
}