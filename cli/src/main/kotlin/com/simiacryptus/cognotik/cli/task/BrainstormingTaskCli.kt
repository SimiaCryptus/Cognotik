package com.simiacryptus.cognotik.cli.task

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.cli.CliSupport
import com.simiacryptus.cognotik.cli.FileServer
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.registerTaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.BrainstormingTask
import com.simiacryptus.cognotik.platform.CognotikConfig
import com.simiacryptus.cognotik.platform.CognotikPlatform
import com.simiacryptus.cognotik.platform.h2.DatabaseFacet
import com.simiacryptus.cognotik.util.TaskHarness
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

/**
 * Command line interface for exercising [BrainstormingTask].
 *
 * By default the CLI runs against the hosted backend (device login, API calls proxied
 * through the hosted server, session cached in the global session.json). Pass `--local`
 * to use locally configured API keys instead.
 *
 * Options accept either `--key value` or `--key=value`; run with `--help` for the list.
 * `--file`, `--category` and `--constraint` may be repeated.
 */
object BrainstormingTaskCli {
  private val log = LoggerFactory.getLogger(BrainstormingTaskCli::class.java)

  private inline fun <T> timed(step: String, block: () -> T): T {
    log.info("[{}] starting", step)
    val start = System.currentTimeMillis()
    try {
      val r = block()
      log.info("[{}] completed in {} ms", step, System.currentTimeMillis() - start)
      return r
    } catch (e: Throwable) {
      log.error("[{}] failed after {} ms: {}", step, System.currentTimeMillis() - start, e.message, e)
      throw e
    }
  }

  const val DEFAULT_SMART_MODEL = CommitAnalyzerTaskCli.DEFAULT_SMART_MODEL
  const val DEFAULT_FAST_MODEL = CommitAnalyzerTaskCli.DEFAULT_FAST_MODEL

  private val FLAGS = setOf("no-creative", "help", "local", "hosted")

  private val KNOWN = setOf(
    "problem",
    "problem-file",
    "file",
    "category",
    "constraint",
    "count",
    "depth",
    "no-creative",
    "timeout-minutes",
    "help",
    "local",
    "hosted",
    "smart-model",
    "fast-model"
  )

  private val DEPTHS = setOf("brief", "moderate", "detailed")

  private val USAGE = """
    |Usage: BrainstormingTaskCli --problem <text> [options]
    |  --problem <text>           The problem or question to brainstorm solutions for
    |  --problem-file <path>      Read the problem statement from a file (alternative to --problem)
    |  --file <glob>              Input file or glob pattern (e.g. **/*.kt), relative to the task
    |                             workspace root; repeatable or comma-separated
    |  --category <name>          Category/domain to consider; repeatable or comma-separated
    |  --constraint <text>        Constraint or requirement to consider; repeatable
    |  --count <n>                Number of options to generate, 3-20 (default: 7)
    |  --depth <level>            Analysis depth: brief|moderate|detailed (default: moderate)
    |  --no-creative              Prefer practical, proven options over unconventional ones
    |  --timeout-minutes <n>      Harness timeout (default: 30)
    |  --smart-model <name>       Smart model (default: $DEFAULT_SMART_MODEL)
    |  --fast-model <name>        Fast model (default: $DEFAULT_FAST_MODEL)
    |  --hosted                   Use the hosted backend via device login (default)
    |  --local                    Use locally configured API keys; skip hosted login
    |  --help                     Show this help
    """.trimMargin()

  /** Parses `--key value`, `--key=value` and bare flags. Repeated keys accumulate in order. */
  fun parseArgs(args: Array<String>): Map<String, List<String>> {
    log.debug("Parsing {} argument(s): {}", args.size, args.joinToString(" "))
    val out = LinkedHashMap<String, MutableList<String>>()
    var i = 0
    while (i < args.size) {
      val a = args[i]
      if (!a.startsWith("--")) {
        log.warn("Unexpected positional argument at index {}: {}", i, a)
        throw IllegalArgumentException("Unexpected argument: $a")
      }
      val body = a.removePrefix("--")
      val (key, value) = when {
        body.contains('=') -> body.substringBefore('=') to body.substringAfter('=')
        body in FLAGS -> body to "true"
        else -> {
          val v = args.getOrNull(i + 1)
          if (v == null || v.startsWith("--")) {
            log.warn("Missing value for option --{}", body)
            throw IllegalArgumentException("Missing value for --$body")
          }
          i++
          body to v
        }
      }
      log.debug("Parsed option --{}={}", key, value)
      out.getOrPut(key) { mutableListOf() }.add(value)
      i++
    }
    log.info("Parsed options: {}", out)
    return out
  }

  private fun single(opts: Map<String, List<String>>, k: String): String? = opts[k]?.lastOrNull()

  private fun bool(opts: Map<String, List<String>>, k: String): Boolean =
    single(opts, k)?.toBooleanStrictOrNull() ?: false

  private fun int(opts: Map<String, List<String>>, k: String, d: Int): Int {
    val raw = single(opts, k)
    if (raw == null) {
      log.debug("Option --{} not set; using default {}", k, d)
      return d
    }
    return raw.toIntOrNull() ?: run {
      log.warn("Option --{} has non-integer value '{}'", k, raw)
      throw IllegalArgumentException("--$k must be an integer")
    }
  }

  private fun commaList(opts: Map<String, List<String>>, k: String): List<String> =
    opts[k].orEmpty().flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }

  fun buildConfig(opts: Map<String, List<String>>): BrainstormingTask.BrainstormingTaskExecutionConfigData {
    log.debug("Building config from options: {}", opts)
    (opts.keys - KNOWN).firstOrNull()?.let {
      log.warn("Unknown option: --{}", it)
      throw IllegalArgumentException("Unknown option: --$it")
    }
    val inline = single(opts, "problem")
    val problemFile = single(opts, "problem-file")
    if (inline != null && problemFile != null) {
      throw IllegalArgumentException("Specify only one of --problem and --problem-file")
    }
    val problem = when {
      inline != null -> inline
      problemFile != null -> {
        val f = File(problemFile)
        if (!f.isFile) throw IllegalArgumentException("--problem-file does not exist or is not a file: ${f.absolutePath}")
        f.readText()
      }

      else -> throw IllegalArgumentException("Missing required option --problem (or --problem-file)")
    }.trim()
    if (problem.isEmpty()) throw IllegalArgumentException("Problem statement cannot be blank")

    val count = int(opts, "count", 7)
    if (count !in 3..20) throw IllegalArgumentException("--count must be between 3 and 20")

    val depth = (single(opts, "depth") ?: "moderate").lowercase()
    if (depth !in DEPTHS) throw IllegalArgumentException("--depth must be one of: ${DEPTHS.joinToString("|")}")

    val files = commaList(opts, "file")
    val categories = commaList(opts, "category")
    val constraints = opts["constraint"].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }

    val cfg = BrainstormingTask.BrainstormingTaskExecutionConfigData(
      problem_statement = problem,
      related_files = files.ifEmpty { null },
      target_option_count = count,
      categories = categories.ifEmpty { null },
      constraints = constraints.ifEmpty { null },
      include_creative_options = !bool(opts, "no-creative"),
      analysis_depth = depth,
    )
    log.info(
      "Built config: problem='{}', files={}, count={}, categories={}, constraints={}, creative={}, depth={}",
      cfg.problem_statement?.take(120),
      cfg.related_files,
      cfg.target_option_count,
      cfg.categories,
      cfg.constraints,
      cfg.include_creative_options,
      cfg.analysis_depth
    )
    return cfg
  }

  private fun fail(msg: String?): Nothing {
    log.error("CLI failure: {}", msg)
    System.err.println("Error: $msg\n\n$USAGE")
    exitProcess(2)
  }

  /**
   * Hosted-mode bootstrap for this CLI: reuses [FileServer]'s device login / session
   * handling and hosted service wiring (proxied API key, usage client, permissive auth).
   */
  private class CliHostedServer : FileServer()

  @JvmStatic
  fun main(args: Array<String>) {
    val mainStart = System.currentTimeMillis()
    log.info("BrainstormingTaskCli starting; args={}", args.joinToString(" "))
    log.debug(
      "Environment: cwd='{}', java={}, os={} {}",
      File(".").absolutePath,
      System.getProperty("java.version"),
      System.getProperty("os.name"),
      System.getProperty("os.version")
    )
    if (args.any { it == "--help" }) {
      println(USAGE)
      return
    }
    val opts = try {
      parseArgs(args)
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val config = try {
      buildConfig(opts).also { cfg ->
        cfg.validate()?.let {
          log.warn("Config validation failed: {}", it)
          throw IllegalArgumentException(it)
        }
      }
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val timeoutMinutes = try {
      int(opts, "timeout-minutes", 30).also { require(it > 0) { "--timeout-minutes must be positive" } }
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val (smartModel, fastModel) = try {
      CommitAnalyzerTaskCli.resolveModel(single(opts, "smart-model") ?: DEFAULT_SMART_MODEL) to
          CommitAnalyzerTaskCli.resolveModel(single(opts, "fast-model") ?: DEFAULT_FAST_MODEL)
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val local = bool(opts, "local")
    log.info(
      "Mode: {}; timeout={} min; smart={}; fast={}",
      if (local) "local" else "hosted",
      timeoutMinutes,
      smartModel.name,
      fastModel.name
    )

    /* Platform init, mirroring FileServer.run(). */
    timed("CognotikPlatform.init") { CognotikPlatform.init() }
    /* Register providers (incl. the 'Cognotik' hosted proxy) before login touches settings. */
    timed("CoreProviders.init") { CoreProviders.init() }
    val globalRoot = File(DatabaseFacet.root)
    DatabaseFacet.root = File(".").absolutePath
    log.info("Database root changed from '{}' to '{}'", globalRoot.absolutePath, DatabaseFacet.root)
    if (!local) {
      try {
        timed("hosted login") { CliHostedServer().initHostedEnv(globalRoot) }
      } catch (e: Exception) {
        log.error("Hosted login failed", e)
        System.err.println("Error: hosted login failed: ${e.message ?: e.javaClass.simpleName}")
        System.err.println("Check your hosted account balance, or use --local with locally configured API keys.")
        exitProcess(2)
      }
    } else {
      log.info("Local mode: skipping hosted login")
    }
    val user = CliSupport.defaultUser ?: CognotikConfig.defaultUser
    log.info(
      "Using user: {} (source: {})", user, if (CliSupport.defaultUser != null) "CliSupport" else "CognotikConfig"
    )

    /* Shared headless bootstrap, the same as FileServer.initializeUser(). */
    try {
      timed("platform bootstrap") { CliSupport.bootstrapPlatform(user) }
    } catch (e: Exception) {
      log.error("Platform bootstrap failed", e)
      System.err.println("Error: platform bootstrap failed: ${e.message ?: e.javaClass.simpleName}")
      e.printStackTrace()
      exitProcess(2)
    }

    println(
      "Running Brainstorming on: '${config.problem_statement?.lineSequence()?.firstOrNull()?.take(100)}'" +
          " [${if (local) "local" else "hosted"}]" +
          " [smart=${smartModel.name}, fast=${fastModel.name}]" +
          " [options=${config.target_option_count}, depth=${config.analysis_depth}]"
    )

    val exitCode = try {
      timed("Brainstorming harness") {
        log.debug("Registering task type {}", BrainstormingTask.Brainstorming.name)
        TaskHarness(
          taskType = BrainstormingTask.Brainstorming.apply { registerTaskType(this) },
          typeConfig = TaskTypeConfig(
            task_type = BrainstormingTask.Brainstorming.name
          ),
          executionConfig = config,
          timeoutMinutes = timeoutMinutes.toLong(),
          user = user,
          smartModel = smartModel,
          fastModel = fastModel,
          imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
          audioModel = fastModel,
        ).run()
      }
      0
    } catch (e: Exception) {
      log.error("Brainstorming failed", e)
      System.err.println("Error: Brainstorming failed: ${e.message ?: e.javaClass.simpleName}")
      e.printStackTrace()
      1
    }
    log.info(
      "BrainstormingTaskCli finished with exit code {} in {} ms", exitCode, System.currentTimeMillis() - mainStart
    )
    /* Harness/platform threads may linger; exit explicitly. */
    exitProcess(exitCode)
  }
}