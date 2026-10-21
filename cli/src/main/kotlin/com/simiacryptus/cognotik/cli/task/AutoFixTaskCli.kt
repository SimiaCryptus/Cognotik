package com.simiacryptus.cognotik.cli.task

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.autofix.AutoFixTask
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.cli.CliSupport
import com.simiacryptus.cognotik.cli.FileServer
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.registerTaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.CognotikConfig
import com.simiacryptus.cognotik.platform.CognotikPlatform
import com.simiacryptus.cognotik.platform.h2.DatabaseFacet
import com.simiacryptus.cognotik.util.TaskHarness
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

/**
 * Command line interface for exercising [AutoFixTask].
 *
 * Runs one or more commands and lets the model automatically fix any issues that arise
 * (e.g. build or test failures) by patching files under the working directory.
 *
 * By default the CLI runs against the hosted backend (device login, API calls proxied
 * through the hosted server, session cached in the global session.json). Pass `--local`
 * to use locally configured API keys instead.
 *
 * Options accept either `--key value` or `--key=value`; run with `--help` for the list.
 * The `--command` option may be repeated; everything after a bare `--` is treated as a
 * single additional command (already tokenized).
 */
object AutoFixTaskCli {
  private val log = LoggerFactory.getLogger(AutoFixTaskCli::class.java)

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

  private val FLAGS = setOf("help", "local", "hosted")

  private val KNOWN = setOf(
    "command",
    "working-dir",
    "description",
    "timeout-minutes",
    "help",
    "local",
    "hosted",
    "smart-model",
    "fast-model"
  )

  private val USAGE = """
    |Usage: AutoFixTaskCli [options] [-- <executable> [args...]]
    |  --command <cmd>            Command line to run and auto-fix (repeatable; simple quoting supported)
    |  --working-dir <path>       Directory the commands run in and files are patched under (default: .)
    |  --description <text>       Description of what the task should accomplish
    |  --timeout-minutes <n>      Harness timeout (default: 30)
    |  --smart-model <name>       Smart model (default: $DEFAULT_SMART_MODEL)
    |  --fast-model <name>        Fast model (default: $DEFAULT_FAST_MODEL)
    |  --hosted                   Use the hosted backend via device login (default)
    |  --local                    Use locally configured API keys; skip hosted login
    |  --help                     Show this help
    |
    |At least one command is required, either via --command or after a bare '--'.
    |Examples:
    |  AutoFixTaskCli --command "./gradlew test" --working-dir /path/to/project
    |  AutoFixTaskCli --working-dir . -- mvn -q test
    """.trimMargin()

  /** Parsed command line: single-valued options, plus the (repeatable) commands. */
  class ParsedArgs(
    val options: Map<String, String>,
    val commands: List<List<String>>
  )

  /** Splits a command line into tokens, honoring single quotes, double quotes and backslash escapes. */
  fun tokenize(line: String): List<String> {
    val tokens = ArrayList<String>()
    val cur = StringBuilder()
    var inToken = false
    var quote: Char? = null
    var i = 0
    while (i < line.length) {
      val c = line[i]
      when {
        quote != null -> when {
          c == quote -> quote = null
          c == '\\' && quote == '"' && i + 1 < line.length -> cur.append(line[++i])
          else -> cur.append(c)
        }

        c == '"' || c == '\'' -> {
          quote = c; inToken = true
        }

        c == '\\' && i + 1 < line.length -> {
          cur.append(line[++i]); inToken = true
        }

        c.isWhitespace() -> if (inToken) {
          tokens.add(cur.toString()); cur.setLength(0); inToken = false
        }

        else -> {
          cur.append(c); inToken = true
        }
      }
      i++
    }
    if (quote != null) throw IllegalArgumentException("Unterminated quote in command: $line")
    if (inToken) tokens.add(cur.toString())
    return tokens
  }

  fun parseArgs(args: Array<String>): ParsedArgs {
    log.debug("Parsing {} argument(s): {}", args.size, args.joinToString(" "))
    val out = LinkedHashMap<String, String>()
    val commands = ArrayList<List<String>>()
    var i = 0
    while (i < args.size) {
      val a = args[i]
      if (a == "--") {
        val rest = args.drop(i + 1)
        if (rest.isEmpty()) throw IllegalArgumentException("Expected a command after '--'")
        commands.add(rest)
        log.debug("Parsed trailing command: {}", rest)
        break
      }
      if (!a.startsWith("--")) {
        log.warn("Unexpected positional argument at index {}: {}", i, a)
        throw IllegalArgumentException("Unexpected argument: $a")
      }
      val body = a.removePrefix("--")
      val key: String
      val value: String
      when {
        body.contains('=') -> {
          key = body.substringBefore('=')
          value = body.substringAfter('=')
        }

        body in FLAGS -> {
          key = body
          value = "true"
        }

        else -> {
          val v = args.getOrNull(i + 1)
          if (v == null || v.startsWith("--")) {
            log.warn("Missing value for option --{}", body)
            throw IllegalArgumentException("Missing value for --$body")
          }
          key = body
          value = v
          i++
        }
      }
      if (key == "command") {
        val tokens = tokenize(value)
        if (tokens.isEmpty()) throw IllegalArgumentException("--command must not be empty")
        commands.add(tokens)
      } else {
        out[key] = value
      }
      log.debug("Parsed option --{}={}", key, value)
      i++
    }
    log.info("Parsed options: {}; commands: {}", out, commands)
    return ParsedArgs(out, commands)
  }

  private fun int(opts: Map<String, String>, k: String, d: Int): Int {
    val raw = opts[k] ?: return d
    return raw.toIntOrNull() ?: throw IllegalArgumentException("--$k must be an integer")
  }

  fun buildConfig(parsed: ParsedArgs): AutoFixTask.AutoFixTaskExecutionConfigData {
    val opts = parsed.options
    log.debug("Building config from options: {}", opts)
    (opts.keys - KNOWN).firstOrNull()?.let {
      log.warn("Unknown option: --{}", it)
      throw IllegalArgumentException("Unknown option: --$it")
    }
    if (parsed.commands.isEmpty()) {
      throw IllegalArgumentException("At least one command is required (use --command or '-- <executable> [args...]')")
    }
    // Always absolute: the task resolves relative paths against the harness workspace,
    // not against the CLI's working directory.
    val workingDir = File(opts["working-dir"] ?: ".").canonicalFile
    if (!workingDir.isDirectory) {
      throw IllegalArgumentException("--working-dir does not exist or is not a directory: $workingDir")
    }
    val commands = parsed.commands.map { tokens ->
      AutoFixTask.CommandWithWorkingDir(
        executable = tokens.first(),
        arguments = tokens.drop(1).toMutableList(),
        working_dir = workingDir.path
      )
    }.toMutableList()
    val cfg = AutoFixTask.AutoFixTaskExecutionConfigData(
      commands = commands,
      task_description = opts["description"],
      task_dependencies = null,
      state = null
    )
    log.info(
      "Built config: workingDir='{}', commands={}",
      workingDir,
      commands.map { (listOf(it.executable) + it.arguments).joinToString(" ") }
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
    log.info("AutoFixTaskCli starting; args={}", args.joinToString(" "))
    log.debug(
      "Environment: cwd='{}', java={}, os={} {}",
      File(".").absolutePath,
      System.getProperty("java.version"),
      System.getProperty("os.name"),
      System.getProperty("os.version")
    )
    // Only look for --help before a bare "--" (afterwards args belong to the target command).
    if (args.takeWhile { it != "--" }.any { it == "--help" }) {
      println(USAGE)
      return
    }
    val parsed = try {
      parseArgs(args)
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val config = try {
      buildConfig(parsed).also { cfg ->
        cfg.commands?.forEach { c ->
          c.validate()?.let { throw IllegalArgumentException(it) }
        }
        cfg.validate()?.let {
          log.warn("Config validation failed: {}", it)
          throw IllegalArgumentException(it)
        }
      }
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val opts = parsed.options
    val timeoutMinutes = try {
      int(opts, "timeout-minutes", 30).also { require(it > 0) { "--timeout-minutes must be positive" } }
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val (smartModel, fastModel) = try {
      CommitAnalyzerTaskCli.resolveModel(opts["smart-model"] ?: DEFAULT_SMART_MODEL) to
          CommitAnalyzerTaskCli.resolveModel(opts["fast-model"] ?: DEFAULT_FAST_MODEL)
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
      "Running AutoFix [${if (local) "local" else "hosted"}] [smart=${smartModel.name}, fast=${fastModel.name}]" +
          " on ${config.commands?.size ?: 0} command(s):"
    )
    config.commands?.forEach { c ->
      println("  * ${(listOf(c.executable) + c.arguments).joinToString(" ")}  (in ${c.working_dir})")
    }

    val exitCode = try {
      timed("AutoFix harness") {
        log.debug("Registering task type {}", AutoFixTask.AutoFix.name)
        TaskHarness(
          taskType = AutoFixTask.AutoFix.apply { registerTaskType(this) },
          typeConfig = TaskTypeConfig(
            task_type = AutoFixTask.AutoFix.name
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
      log.error("AutoFix failed", e)
      System.err.println("Error: AutoFix failed: ${e.message ?: e.javaClass.simpleName}")
      e.printStackTrace()
      1
    }
    log.info(
      "AutoFixTaskCli finished with exit code {} in {} ms", exitCode, System.currentTimeMillis() - mainStart
    )
    /* Harness/platform threads may linger; exit explicitly. */
    exitProcess(exitCode)
  }
}