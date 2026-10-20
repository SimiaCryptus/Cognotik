package com.simiacryptus.cognotik.cli.task

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.cli.CliSupport
import com.simiacryptus.cognotik.cli.FileServer
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.registerTaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.platform.CognotikConfig
import com.simiacryptus.cognotik.platform.CognotikPlatform
import com.simiacryptus.cognotik.platform.h2.DatabaseFacet
import com.simiacryptus.cognotik.util.TaskHarness
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

/**
 * Command line interface for exercising [FileModificationTask].
 *
 * By default the CLI runs against the hosted backend (device login, API calls proxied
 * through the hosted server, session cached in the global session.json). Pass `--local`
 * to use locally configured API keys instead.
 *
 * Options accept either `--key value` or `--key=value`; run with `--help` for the list.
 */
object FileModificationTaskCli {
  private val log = LoggerFactory.getLogger(FileModificationTaskCli::class.java)

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

  private val FLAGS = setOf("include-git-diff", "help", "local", "hosted")

  private val KNOWN = setOf(
    "root",
    "files",
    "description",
    "description-file",
    "include-git-diff",
    "timeout-minutes",
    "help",
    "local",
    "hosted",
    "smart-model",
    "fast-model"
  )

  private val USAGE = """
    |Usage: FileModificationTaskCli [options]
    |  --root <dir>               Workspace root; file paths are resolved against it (default: .)
    |  --files <a,b,...>          Comma-separated files to modify or create (relative to --root)
    |  --description <text>       Description of the modification(s) to make
    |  --description-file <path>  Read the description from a file (alternative to --description)
    |  --include-git-diff         Include the git diff against HEAD for each input file
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
          log.debug("Parsed option --{}", body.substringBefore('='))
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
          log.debug("Parsed option --{}", body)
          i++
        }
      }
      i++
    }
    log.info("Parsed option names: {}", out.keys)
    return out
  }

  private fun int(opts: Map<String, String>, k: String, d: Int): Int {
    val raw = opts[k] ?: return d.also { log.debug("Option --{} not set; using default {}", k, d) }
    return raw.toIntOrNull() ?: run {
      log.warn("Option --{} has non-integer value '{}'", k, raw)
      throw IllegalArgumentException("--$k must be an integer")
    }
  }

  /** Paths under [root] become relative to it; everything else is kept as given. */
  private fun normalizePath(root: String, path: String): String {
    val f = File(path)
    if (!f.isAbsolute) return path
    val canonical = f.canonicalFile
    val rootFile = File(root)
    return if (canonical.path.startsWith(rootFile.path + File.separator)) {
      canonical.relativeTo(rootFile).path
    } else canonical.path
  }

  /** Resolved options for a run: the workspace root and the task configuration. */
  class Parsed(val root: String, val config: FileModificationTask.FileModificationTaskExecutionConfigData)

  fun buildConfig(opts: Map<String, String>): Parsed {
    log.debug("Building config from options")
    (opts.keys - KNOWN).firstOrNull()?.let {
      log.warn("Unknown option: --{}", it)
      throw IllegalArgumentException("Unknown option: --$it")
    }
    val root = File(opts["root"] ?: ".").canonicalPath
    if (!File(root).isDirectory) {
      throw IllegalArgumentException("--root does not exist or is not a directory: $root")
    }
    val files = (opts["files"] ?: throw IllegalArgumentException("--files is required"))
      .split(',').map { it.trim() }.filter { it.isNotEmpty() }
      .map { normalizePath(root, it) }
    if (files.isEmpty()) throw IllegalArgumentException("--files must list at least one file")
    files.filterNot { File(root, it).exists() || File(it).exists() }.forEach {
      log.info("File does not exist yet (will be created if the task produces it): {}", it)
    }

    if (opts.containsKey("description") && opts.containsKey("description-file")) {
      throw IllegalArgumentException("Specify only one of --description and --description-file")
    }
    val description = when {
      opts.containsKey("description") -> opts.getValue("description")
      opts.containsKey("description-file") -> {
        val f = File(opts.getValue("description-file"))
        if (!f.isFile) throw IllegalArgumentException("--description-file does not exist: ${f.absolutePath}")
        f.readText()
      }

      else -> throw IllegalArgumentException("--description or --description-file is required")
    }
    if (description.isBlank()) throw IllegalArgumentException("The description must not be blank")

    val cfg = FileModificationTask.FileModificationTaskExecutionConfigData(
      related_files = files,
      includeGitDiff = opts["include-git-diff"]?.toBooleanStrictOrNull() ?: false,
      task_description = description,
    )
    log.info(
      "Built config: root='{}', files={}, includeGitDiff={}, descriptionLength={}",
      root, files, cfg.includeGitDiff, description.length
    )
    return Parsed(root, cfg)
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
    log.info("FileModificationTaskCli starting; args={}", args.joinToString(" "))
    if (args.any { it == "--help" }) {
      println(USAGE)
      return
    }
    val opts = try {
      parseArgs(args)
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val parsed = try {
      buildConfig(opts).also { p ->
        p.config.validate()?.let {
          log.warn("Config validation failed: {}", it)
          throw IllegalArgumentException(it)
        }
      }
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val config = parsed.config
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
    if (local && opts["hosted"]?.toBooleanStrictOrNull() == true) {
      fail("--local and --hosted are mutually exclusive")
    }
    log.info(
      "Mode: {}; timeout={} min; smart={}; fast={}",
      if (local) "local" else "hosted", timeoutMinutes, smartModel.name, fastModel.name
    )

    /* Platform init, mirroring FileServer.run(). */
    timed("CognotikPlatform.init") { CognotikPlatform.init() }
    /* Register providers (incl. the 'Cognotik' hosted proxy) before login touches settings. */
    timed("CoreProviders.init") { CoreProviders.init() }
    val globalRoot = File(DatabaseFacet.root)
    DatabaseFacet.root = parsed.root
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
    log.info("Using user: {} (source: {})", user, if (CliSupport.defaultUser != null) "CliSupport" else "CognotikConfig")

    /* Shared headless bootstrap (providers, tasks, plugins, orchestrator instanceFn, dynamic enums, permissive local auth). */
    try {
      timed("platform bootstrap") { CliSupport.bootstrapPlatform(user) }
    } catch (e: Exception) {
      log.error("Platform bootstrap failed", e)
      System.err.println("Error: platform bootstrap failed: ${e.message ?: e.javaClass.simpleName}")
      e.printStackTrace()
      exitProcess(2)
    }

    println(
      "Running FileModification in '${parsed.root}' on ${config.related_files}" +
          " [${if (local) "local" else "hosted"}]" +
          " [smart=${smartModel.name}, fast=${fastModel.name}]"
    )

    val exitCode = try {
      timed("FileModification harness") {
        TaskHarness(
          taskType = FileModificationTask.FileModification.apply { registerTaskType(this) },
          typeConfig = TaskTypeConfig(
            task_type = FileModificationTask.FileModification.name
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
      log.error("FileModification failed", e)
      System.err.println("Error: FileModification failed: ${e.message ?: e.javaClass.simpleName}")
      e.printStackTrace()
      1
    }
    log.info(
      "FileModificationTaskCli finished with exit code {} in {} ms", exitCode, System.currentTimeMillis() - mainStart
    )
    /* Harness/platform threads may linger; exit explicitly. */
    exitProcess(exitCode)
  }
}