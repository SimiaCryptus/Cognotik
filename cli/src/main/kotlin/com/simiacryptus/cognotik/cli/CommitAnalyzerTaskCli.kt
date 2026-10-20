package com.simiacryptus.cognotik.cli

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.registerTaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.CommitAnalyzerTask
import com.simiacryptus.cognotik.platform.CognotikConfig
import com.simiacryptus.cognotik.platform.CognotikPlatform
import com.simiacryptus.cognotik.platform.h2.DatabaseFacet
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.util.TaskHarness
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

/**
 * Command line interface for exercising [CommitAnalyzerTask].
 *
 * By default the CLI runs against the hosted backend (device login, API calls proxied
 * through the hosted server, session cached in the global session.json). Pass `--local`
 * to use locally configured API keys instead.
 *
 * Options accept either `--key value` or `--key=value`; run with `--help` for the list.
 */
object CommitAnalyzerTaskCli {
  private val log = LoggerFactory.getLogger(CommitAnalyzerTaskCli::class.java)
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


  private val FLAGS = setOf("verify-build", "no-patches", "no-llm", "help", "local", "hosted")

  private val KNOWN = setOf(
    "repo",
    "commit",
    "base",
    "build-command",
    "verify-build",
    "build-timeout",
    "max-retries",
    "branch",
    "no-patches",
    "no-llm",
    "mechanical",
    "threshold",
    "bulk-min",
    "max-cluster",
    "max-llm-ops",
    "timeout-minutes",
    "help",
    "local",
    "hosted",
    "smart-model",
    "fast-model"
  )

  private val USAGE = """
    |Usage: CommitAnalyzerTaskCli [options]
    |  --repo <path>              Repository path (default: .)
    |  --commit <rev|A..B|A...B>  Commit to explode, or a commit range whose combined change is exploded
    |                             (A..B: diff A -> B; A...B: diff merge-base(A,B) -> B) (default: HEAD)
    |  --base <rev>               Base revision; explode the combined change <base>..<commit>
    |  --build-command <cmd>      Build command for verification
    |  --verify-build             Verify every intermediate state builds
    |  --build-timeout <sec>      Build timeout in seconds (default: 600)
    |  --max-retries <n>          Max verification repair attempts (default: 10)
    |  --branch <name>            Name of the commit-series branch, one commit per item (default: commit-analysis/<sha>)
    |  --no-patches               Do not emit patch files
    |  --no-llm                   Deterministic mode (no LLM calls)
    |  --mechanical <first|last>  Mechanical operation placement (default: first)
    |  --threshold <double>       Clustering similarity threshold (default: 0.5)
    |  --bulk-min <n>             Min occurrences for bulk replace (default: 3)
    |  --max-cluster <n>          Max deterministic cluster size (default: 40)
    |  --max-llm-ops <n>          Max operations for LLM dependency reasoning (default: 200)
    |  --timeout-minutes <n>      Harness timeout (default: 30)
    |  --smart-model <name>       Smart model (default: $DEFAULT_SMART_MODEL)
    |  --fast-model <name>        Fast model (default: $DEFAULT_FAST_MODEL)
    |  --hosted                   Use the hosted backend via device login (default)
    |  --local                    Use locally configured API keys; skip hosted login
    |  --help                     Show this help
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


  fun buildConfig(opts: Map<String, String>): CommitAnalyzerTask.CommitAnalyzerTaskExecutionConfigData {
    log.debug("Building config from options: {}", opts)
    (opts.keys - KNOWN).firstOrNull()?.let {
      log.warn("Unknown option: --{}", it)
      throw IllegalArgumentException("Unknown option: --$it")
    }
    fun bool(k: String) = opts[k]?.toBooleanStrictOrNull() ?: false
    // Always absolute: the task resolves relative paths against the harness workspace,
    // not against the CLI's working directory.
    val repo = File(opts["repo"] ?: ".").canonicalPath
    if (!File(repo).isDirectory) {
      throw IllegalArgumentException("--repo does not exist or is not a directory: $repo")
    }
    log.debug("Repository path resolved to '{}' (absolute: {})", repo, File(repo).absolutePath)
    val mechanical = (opts["mechanical"] ?: "first").lowercase()
    if (mechanical !in setOf("first", "last")) {
      log.warn("Invalid --mechanical value '{}'", mechanical)
      throw IllegalArgumentException("--mechanical must be 'first' or 'last'")
    }
    val cfg = CommitAnalyzerTask.CommitAnalyzerTaskExecutionConfigData(
      repository_path = repo,
      commit = opts["commit"] ?: "HEAD",
      base_commit = opts["base"],
      build_command = opts["build-command"],
      verify_build = bool("verify-build"),
      build_timeout_seconds = int(opts, "build-timeout", 600),
      max_verification_retries = int(opts, "max-retries", 10),
      create_branch = opts["branch"],
      emit_patches = !bool("no-patches"),
      use_llm = !bool("no-llm"),
      mechanical_placement = mechanical,
      similarity_threshold = opts["threshold"]?.let {
        it.toDoubleOrNull() ?: throw IllegalArgumentException("--threshold must be a number")
      } ?: 0.5,
      bulk_replace_min_occurrences = int(opts, "bulk-min", 3),
      max_cluster_size = int(opts, "max-cluster", 40),
      max_llm_operations = int(opts, "max-llm-ops", 200),
    )
    log.info(
      "Built config: repo='{}', commit='{}', base='{}', verifyBuild={}, buildCommand={}, useLlm={}, emitPatches={}, branch={}",
      cfg.repository_path,
      cfg.commit,
      cfg.base_commit,
      cfg.verify_build,
      cfg.build_command,
      cfg.use_llm,
      cfg.emit_patches,
      cfg.create_branch
    )
    log.debug("Full config: {}", cfg)
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
    log.info("CommitAnalyzerTaskCli starting; args={}", args.joinToString(" "))
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
    timed("CognotikPlatform.init") { CognotikPlatform.init() }/* Register providers (incl. the 'Cognotik' hosted proxy) before login touches settings. */
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
    )/*
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

    val targetLabel = config.base_commit?.let { "$it..${config.commit}" } ?: config.commit
    println(
      "Running CommitAnalyzer on '${config.repository_path}' @ '$targetLabel'" + " [${if (local) "local" else "hosted"}]" + " [smart=${smartModel.name}, fast=${fastModel.name}]" + (if (config.use_llm) "" else " (deterministic, no LLM)") + (if (config.verify_build) " with build verification: ${config.build_command}" else "")
    )

    val exitCode = try {
      timed("CommitAnalyzer harness") {
        // The harness may not propagate executionConfig to the task instance; hand it over explicitly.
        CommitAnalyzerTask.executionConfigOverride = config
        log.debug("Registering task type {}", CommitAnalyzerTask.CommitAnalyzer.name)
        TaskHarness(
          taskType = CommitAnalyzerTask.CommitAnalyzer.apply { registerTaskType(this) },
          typeConfig = TaskTypeConfig(
            task_type = CommitAnalyzerTask.CommitAnalyzer.name
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
      log.error("CommitAnalyzer failed", e)
      System.err.println("Error: CommitAnalyzer failed: ${e.message ?: e.javaClass.simpleName}")
      e.printStackTrace()
      1
    } finally {
      CommitAnalyzerTask.executionConfigOverride = null
    }
    log.info(
      "CommitAnalyzerTaskCli finished with exit code {} in {} ms", exitCode, System.currentTimeMillis() - mainStart
    )/* Harness/platform threads may linger; exit explicitly. */
    exitProcess(exitCode)
  }
}