package com.simiacryptus.cognotik.cli.task

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.cli.CliSupport
import com.simiacryptus.cognotik.cli.FileServer
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.registerTaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.social.GameTheoryTask
import com.simiacryptus.cognotik.platform.CognotikConfig
import com.simiacryptus.cognotik.platform.CognotikPlatform
import com.simiacryptus.cognotik.platform.h2.DatabaseFacet
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.util.TaskHarness
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

/**
 * Command line interface for exercising [GameTheoryTask].
 *
 * By default the CLI runs against the hosted backend (device login, API calls proxied
 * through the hosted server, session cached in the global session.json). Pass `--local`
 * to use locally configured API keys instead.
 *
 * Options accept either `--key value` or `--key=value`; run with `--help` for the list.
 */
object GameTheoryTaskCli {
  private val log = LoggerFactory.getLogger(GameTheoryTaskCli::class.java)
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

  private val GAME_TYPES = setOf("cooperative", "non-cooperative", "zero-sum", "repeated", "sequential")

  private val FLAGS = setOf(
    "no-payoff-matrix",
    "no-nash",
    "no-dominant",
    "no-pareto",
    "no-recommendations",
    "repeated",
    "help",
    "local",
    "hosted"
  )

  private val KNOWN = setOf(
    "scenario",
    "scenario-file",
    "players",
    "strategies",
    "game-type",
    "no-payoff-matrix",
    "no-nash",
    "no-dominant",
    "no-pareto",
    "no-recommendations",
    "repeated",
    "iterations",
    "context",
    "files",
    "timeout-minutes",
    "help",
    "local",
    "hosted",
    "smart-model",
    "fast-model"
  )

  private val USAGE = """
    |Usage: GameTheoryTaskCli [options]
    |  --scenario <text>          The strategic situation to analyze (required unless --scenario-file)
    |  --scenario-file <path>     Read the scenario description from a file
    |  --players <a,b,...>        Comma-separated list of players (required)
    |  --strategies <spec>        Optional known strategies: "PlayerA=s1,s2;PlayerB=s3,s4"
    |  --game-type <type>         cooperative | non-cooperative | zero-sum | repeated | sequential
    |                             (default: non-cooperative)
    |  --no-payoff-matrix         Skip payoff matrix construction
    |  --no-nash                  Skip Nash equilibrium analysis
    |  --no-dominant              Skip dominant strategy analysis
    |  --no-pareto                Skip Pareto optimality analysis
    |  --no-recommendations       Skip strategic recommendations
    |  --repeated                 Also analyze the game as a repeated game
    |  --iterations <n>           Iterations for repeated game analysis (default: 10)
    |  --context <text>           Additional context or constraints
    |  --files <glob,glob,...>    Comma-separated files/patterns to include as input
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

  private fun csv(raw: String?): List<String> =
    raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

  /** Parses "A=s1,s2;B=s3" into a map of player -> strategies. */
  fun parseStrategies(raw: String): Map<String, List<String>> {
    val out = LinkedHashMap<String, List<String>>()
    raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { entry ->
      if (!entry.contains('=')) {
        throw IllegalArgumentException("--strategies entry must look like Player=s1,s2: '$entry'")
      }
      val player = entry.substringBefore('=').trim()
      val strategies = csv(entry.substringAfter('='))
      if (player.isEmpty() || strategies.isEmpty()) {
        throw IllegalArgumentException("--strategies entry needs a player and at least one strategy: '$entry'")
      }
      out[player] = strategies
    }
    return out
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

  fun buildConfig(opts: Map<String, String>): GameTheoryTask.GameTheoryTaskExecutionConfigData {
    log.debug("Building config from options: {}", opts)
    (opts.keys - KNOWN).firstOrNull()?.let {
      log.warn("Unknown option: --{}", it)
      throw IllegalArgumentException("Unknown option: --$it")
    }
    fun bool(k: String) = opts[k]?.toBooleanStrictOrNull() ?: false

    val scenario = when {
      opts["scenario"] != null -> opts["scenario"]
      opts["scenario-file"] != null -> {
        val f = File(opts["scenario-file"]!!)
        if (!f.isFile) throw IllegalArgumentException("--scenario-file does not exist: ${f.absolutePath}")
        f.readText()
      }

      else -> null
    }
    if (scenario.isNullOrBlank()) {
      throw IllegalArgumentException("--scenario or --scenario-file is required")
    }
    val players = csv(opts["players"])
    if (players.isEmpty()) {
      throw IllegalArgumentException("--players is required (comma-separated)")
    }
    val gameType = (opts["game-type"] ?: "non-cooperative").trim().lowercase()
    if (gameType !in GAME_TYPES) {
      log.warn("Invalid --game-type value '{}'", gameType)
      throw IllegalArgumentException("--game-type must be one of: ${GAME_TYPES.joinToString(", ")}")
    }
    val repeated = bool("repeated") || gameType == "repeated"
    val iterations = int(opts, "iterations", 10)
    if (iterations < 1) throw IllegalArgumentException("--iterations must be positive")

    val cfg = GameTheoryTask.GameTheoryTaskExecutionConfigData(
      game_scenario = scenario,
      players = players,
      player_strategies = opts["strategies"]?.let { parseStrategies(it) },
      game_type = gameType,
      build_payoff_matrix = !bool("no-payoff-matrix"),
      find_nash_equilibria = !bool("no-nash"),
      analyze_dominant_strategies = !bool("no-dominant"),
      find_pareto_optimal = !bool("no-pareto"),
      provide_recommendations = !bool("no-recommendations"),
      repeated_game_analysis = repeated,
      iterations = iterations,
      additional_context = opts["context"],
      related_files = opts["files"]?.let { csv(it) }?.takeIf { it.isNotEmpty() },
    )
    log.info(
      "Built config: players={}, gameType='{}', payoff={}, nash={}, dominant={}, pareto={}, repeated={}({}), recommendations={}, files={}",
      cfg.players,
      cfg.game_type,
      cfg.build_payoff_matrix,
      cfg.find_nash_equilibria,
      cfg.analyze_dominant_strategies,
      cfg.find_pareto_optimal,
      cfg.repeated_game_analysis,
      cfg.iterations,
      cfg.provide_recommendations,
      cfg.related_files
    )
    log.debug("Scenario: {}", scenario.take(500))
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
    log.info("GameTheoryTaskCli starting; args={}", args.joinToString(" "))
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
      "Running GameTheory analysis (${config.game_type}) with players ${config.players?.joinToString(", ")}" +
          " [${if (local) "local" else "hosted"}]" +
          " [smart=${smartModel.name}, fast=${fastModel.name}]" +
          (if (config.repeated_game_analysis) " (repeated x${config.iterations})" else "")
    )

    val exitCode = try {
      timed("GameTheory harness") {
        log.debug("Registering task type {}", GameTheoryTask.GameTheory.name)
        TaskHarness(
          taskType = GameTheoryTask.GameTheory.apply { registerTaskType(this) },
          typeConfig = TaskTypeConfig(
            task_type = GameTheoryTask.GameTheory.name
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
      log.error("GameTheory failed", e)
      System.err.println("Error: GameTheory failed: ${e.message ?: e.javaClass.simpleName}")
      e.printStackTrace()
      1
    }
    log.info(
      "GameTheoryTaskCli finished with exit code {} in {} ms", exitCode, System.currentTimeMillis() - mainStart
    )
    /* Harness/platform threads may linger; exit explicitly. */
    exitProcess(exitCode)
  }
}