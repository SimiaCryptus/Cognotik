package com.simiacryptus.cognotik.cli.task

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.cli.CliSupport
import com.simiacryptus.cognotik.cli.FileServer
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.registerTaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.social.MultiPerspectiveAnalysisTask
import com.simiacryptus.cognotik.platform.CognotikConfig
import com.simiacryptus.cognotik.platform.CognotikPlatform
import com.simiacryptus.cognotik.platform.h2.DatabaseFacet
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.util.TaskHarness
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

/**
 * Command line interface for exercising [MultiPerspectiveAnalysisTask].
 *
 * By default the CLI runs against the hosted backend (device login, API calls proxied
 * through the hosted server, session cached in the global session.json). Pass `--local`
 * to use locally configured API keys instead.
 *
 * Options accept either `--key value` or `--key=value`; run with `--help` for the list.
 */
object MultiPerspectiveAnalysisTaskCli {
  private val log = LoggerFactory.getLogger(MultiPerspectiveAnalysisTaskCli::class.java)

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

  const val DEFAULT_SMART_MODEL = "GeminiFlash_38"
  const val DEFAULT_FAST_MODEL = "GeminiFlash_38"
  const val DEFAULT_PERSPECTIVES = "technical,business,user experience,risk"

  private val FLAGS = setOf("no-synthesize", "help", "local", "hosted")

  private val KNOWN = setOf(
    "subject",
    "subject-file",
    "perspectives",
    "files",
    "no-synthesize",
    "consensus-threshold",
    "timeout-minutes",
    "help",
    "local",
    "hosted",
    "smart-model",
    "fast-model"
  )

  private val USAGE = """
    |Usage: MultiPerspectiveAnalysisTaskCli [options]
    |  --subject <text>              Topic or problem to analyze (required unless --subject-file is given)
    |  --subject-file <path>         Read the subject from a file
    |  --perspectives <a,b,c>        Comma-separated perspectives (default: $DEFAULT_PERSPECTIVES)
    |  --files <p1,p2,...>           Comma-separated input files or glob patterns (e.g. **/*.kt) for context
    |  --no-synthesize               Skip the unified synthesis step
    |  --consensus-threshold <0..1>  Minimum perspective agreement threshold (default: 0.7)
    |  --timeout-minutes <n>         Harness timeout (default: 30)
    |  --smart-model <name>          Smart model (default: $DEFAULT_SMART_MODEL)
    |  --fast-model <name>           Fast model (default: $DEFAULT_FAST_MODEL)
    |  --hosted                      Use the hosted backend via device login (default)
    |  --local                       Use locally configured API keys; skip hosted login
    |  --help                        Show this help
    """.trimMargin()

  fun parseArgs(args: Array<String>): Map<String, String> {
    log.debug("Parsing {} argument(s): {}", args.size, args.joinToString(" "))
    val out = LinkedHashMap<String, String>()
    var i = 0
    while (i < args.size) {
      val a = args[i]
      if (!a.startsWith("--")) {
        log.warn("Unexpected positional argument at index {}: {}", i, a)
        throw IllegalArgumentException("Unexpected argument: $a")
      }
      val body = a.removePrefix("--")
      when {
        body.contains('=') -> {
          out[body.substringBefore('=')] = body.substringAfter('=')
          log.debug("Parsed option --{}={}", body.substringBefore('='), body.substringAfter('='))
        }

        body in FLAGS -> {
          out[body] = "true"
          log.debug("Parsed flag --{}", body)
        }

        else -> {
          val v = args.getOrNull(i + 1)
          if (v == null || v.startsWith("--")) {
            log.warn("Missing value for option --{}", body)
            throw IllegalArgumentException("Missing value for --$body")
          }
          out[body] = v
          log.debug("Parsed option --{} {}", body, v)
          i++
        }
      }
      i++
    }
    log.info("Parsed options: {}", out)
    return out
  }

  private fun int(opts: Map<String, String>, k: String, d: Int): Int {
    val raw = opts[k]
    if (raw == null) {
      log.debug("Option --{} not set; using default {}", k, d)
      return d
    }
    return raw.toIntOrNull() ?: run {
      log.warn("Option --{} has non-integer value '{}'", k, raw)
      throw IllegalArgumentException("--$k must be an integer")
    }
  }

  private fun list(raw: String?): List<String> =
    raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

  /** All ChatModel instances exposed as properties of [GeminiModels]. */
  private fun knownModels(): List<ChatModel> =
    GeminiModels.javaClass.methods.filter { it.parameterCount == 0 && it.name.startsWith("get") }.mapNotNull { m ->
      runCatching { m.invoke(GeminiModels) }.onFailure {
        log.trace(
          "Skipping accessor {}: {}",
          m.name,
          it.toString()
        )
      }.getOrNull() as? ChatModel
    }.distinctBy { it.name }
      .also { log.debug("Discovered {} known model(s): {}", it.size, it.joinToString(", ") { m -> m.name }) }

  fun resolveModel(name: String): ChatModel {
    log.debug("Resolving model '{}'", name)
    val models = knownModels()
    return models.firstOrNull { it.name.equals(name, ignoreCase = true) }
      ?.also { log.info("Resolved model '{}' -> {}", name, it.name) } ?: run {
      log.warn("Unknown model '{}'", name)
      throw IllegalArgumentException(
        "Unknown model: $name; known: ${models.joinToString(", ") { it.name }}"
      )
    }
  }

  fun buildConfig(opts: Map<String, String>): MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysisTaskExecutionConfigData {
    log.debug("Building config from options: {}", opts)
    (opts.keys - KNOWN).firstOrNull()?.let {
      log.warn("Unknown option: --{}", it)
      throw IllegalArgumentException("Unknown option: --$it")
    }
    val subject = when {
      opts["subject"] != null && opts["subject-file"] != null ->
        throw IllegalArgumentException("Specify only one of --subject and --subject-file")

      opts["subject"] != null -> opts["subject"]!!
      opts["subject-file"] != null -> {
        val f = File(opts["subject-file"]!!)
        if (!f.isFile) throw IllegalArgumentException("--subject-file does not exist or is not a file: ${f.absolutePath}")
        f.readText().trim()
      }

      else -> throw IllegalArgumentException("--subject (or --subject-file) is required")
    }
    val perspectives = list(opts["perspectives"] ?: DEFAULT_PERSPECTIVES)
    val files = list(opts["files"])
    val threshold = opts["consensus-threshold"]?.let {
      it.toDoubleOrNull() ?: throw IllegalArgumentException("--consensus-threshold must be a number")
    } ?: 0.7
    val synthesize = opts["no-synthesize"]?.toBooleanStrictOrNull() != true

    val cfg = MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysisTaskExecutionConfigData(
      analysis_subject = subject,
      perspectives = perspectives,
      related_files = files.ifEmpty { null },
      synthesize = synthesize,
      consensus_threshold = threshold,
    )
    log.info(
      "Built config: subjectLength={}, perspectives={}, files={}, synthesize={}, threshold={}",
      subject.length,
      perspectives,
      files,
      synthesize,
      threshold
    )
    return cfg
  }

  private fun fail(msg: String?): Nothing {
    log.error("CLI failure: {}", msg)
    System.err.println("Error: $msg\n\n$USAGE")
    exitProcess(2)
  }

  /**
   * Hosted-mode bootstrap for this CLI: reuses [com.simiacryptus.cognotik.cli.FileServer]'s device login / session
   * handling and hosted service wiring (proxied API key, usage client, permissive auth).
   */
  private class CliHostedServer : FileServer()

  @JvmStatic
  fun main(args: Array<String>) {
    val mainStart = System.currentTimeMillis()
    log.info("MultiPerspectiveAnalysisTaskCli starting; args={}", args.joinToString(" "))
    log.debug(
      "Environment: cwd='{}', java={}, os={} {}",
      File(".").absolutePath,
      System.getProperty("java.version"),
      System.getProperty("os.name"),
      System.getProperty("os.version")
    )
    if (args.any { it == "--help" }) {
      log.debug("Help requested; printing usage")
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
        log.debug("Config validation passed")
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
      resolveModel(opts["smart-model"] ?: DEFAULT_SMART_MODEL) to resolveModel(opts["fast-model"] ?: DEFAULT_FAST_MODEL)
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val local = opts["local"]?.toBooleanStrictOrNull() ?: false
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
    /*
     * Shared headless bootstrap (providers, tasks, plugins, orchestrator instanceFn,
     * dynamic enums and permissive local auth), the same as FileServer.initializeUser().
     */
    try {
      timed("platform bootstrap") { CliSupport.bootstrapPlatform(user) }
    } catch (e: Exception) {
      log.error("Platform bootstrap failed", e)
      System.err.println("Error: platform bootstrap failed: ${e.message ?: e.javaClass.simpleName}")
      e.printStackTrace()
      exitProcess(2)
    }

    println(
      "Running MultiPerspectiveAnalysis on perspectives [${config.perspectives?.joinToString(", ")}]" +
          " [${if (local) "local" else "hosted"}]" +
          " [smart=${smartModel.name}, fast=${fastModel.name}]" +
          (if (config.synthesize) " with synthesis" else " without synthesis")
    )

    val exitCode = try {
      timed("MultiPerspectiveAnalysis harness") {
        log.debug("Registering task type {}", MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysis.name)
        TaskHarness(
          taskType = MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysis.apply { registerTaskType(this) },
          typeConfig = TaskTypeConfig(
            task_type = MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysis.name
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
      log.error("MultiPerspectiveAnalysis failed", e)
      System.err.println("Error: MultiPerspectiveAnalysis failed: ${e.message ?: e.javaClass.simpleName}")
      e.printStackTrace()
      1
    }
    log.info(
      "MultiPerspectiveAnalysisTaskCli finished with exit code {} in {} ms",
      exitCode,
      System.currentTimeMillis() - mainStart
    )
    /* Harness/platform threads may linger; exit explicitly. */
    exitProcess(exitCode)
  }
}