package com.simiacryptus.cognotik.cli.task

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.cli.CliSupport
import com.simiacryptus.cognotik.cli.FileServer
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.registerTaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.IllustrateDocumentTask
import com.simiacryptus.cognotik.platform.CognotikConfig
import com.simiacryptus.cognotik.platform.CognotikPlatform
import com.simiacryptus.cognotik.platform.h2.DatabaseFacet
import com.simiacryptus.cognotik.util.TaskHarness
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

/**
 * Command line interface for exercising [IllustrateDocumentTask].
 *
 * The task analyzes a single Markdown/HTML document, generates images that enhance it,
 * saves them next to the document and (optionally) inserts references via diff patches.
 *
 * By default the CLI runs against the hosted backend (device login, API calls proxied
 * through the hosted server, session cached in the global session.json). Pass `--local`
 * to use locally configured API keys instead.
 *
 * Options accept either `--key value` or `--key=value`; run with `--help` for the list.
 */
object IllustrateDocumentTaskCli {
  private val log = LoggerFactory.getLogger(IllustrateDocumentTaskCli::class.java)

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

  private val FLAGS = setOf("no-insert", "help", "local", "hosted")

  private val KNOWN = setOf(
    "file",
    "max-images",
    "image-format",
    "no-insert",
    "image-instructions",
    "composer-directive",
    "integrator-directive",
    "timeout-minutes",
    "help",
    "local",
    "hosted",
    "smart-model",
    "fast-model"
  )

  private val USAGE = """
    |Usage: IllustrateDocumentTaskCli --file <path> [options]
    |  --file <path>                   Markdown (.md) or HTML (.html) document to illustrate (required)
    |  --max-images <n>                Maximum number of images to generate, 1-20 (default: 5)
    |  --image-format <png|jpg|jpeg>   Image format (default: png)
    |  --no-insert                     Do not insert image references into the document
    |  --image-instructions <text>     Additional instructions appended to every image prompt
    |  --composer-directive <text>     Directive controlling what images are suggested/generated
    |  --integrator-directive <text>   Directive controlling how images are inserted
    |  --timeout-minutes <n>           Harness timeout (default: 30)
    |  --smart-model <name>            Smart model (default: $DEFAULT_SMART_MODEL)
    |  --fast-model <name>             Fast model (default: $DEFAULT_FAST_MODEL)
    |  --hosted                        Use the hosted backend via device login (default)
    |  --local                         Use locally configured API keys; skip hosted login
    |  --help                          Show this help
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

  fun buildConfig(opts: Map<String, String>): IllustrateDocumentTask.IllustrateDocumentTaskExecutionConfigData {
    log.debug("Building config from options: {}", opts)
    (opts.keys - KNOWN).firstOrNull()?.let {
      log.warn("Unknown option: --{}", it)
      throw IllegalArgumentException("Unknown option: --$it")
    }
    val fileOpt = opts["file"] ?: throw IllegalArgumentException("--file is required")
    // Always absolute: the task resolves relative paths against the harness workspace,
    // not against the CLI's working directory.
    val file = File(fileOpt).canonicalFile
    if (!file.isFile) {
      throw IllegalArgumentException("--file does not exist or is not a regular file: ${file.path}")
    }
    log.debug("Document path resolved to '{}'", file.path)
    val format = (opts["image-format"] ?: "png").lowercase()
    if (format !in setOf("png", "jpg", "jpeg")) {
      throw IllegalArgumentException("--image-format must be 'png', 'jpg' or 'jpeg'")
    }
    val cfg = IllustrateDocumentTask.IllustrateDocumentTaskExecutionConfigData(
      max_images = int(opts, "max-images", 5),
      image_format = format,
      auto_insert = opts["no-insert"]?.toBooleanStrictOrNull()?.not() ?: true,
      image_instructions = opts["image-instructions"],
      composer_directive = opts["composer-directive"],
      integrator_directive = opts["integrator-directive"],
    ).apply {
      main_file = file.path
    }
    log.info(
      "Built config: file='{}', maxImages={}, format={}, autoInsert={}, composerDirective={}, integratorDirective={}",
      cfg.main_file,
      cfg.max_images,
      cfg.image_format,
      cfg.auto_insert,
      cfg.composer_directive,
      cfg.integrator_directive
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
    log.info("IllustrateDocumentTaskCli starting; args={}", args.joinToString(" "))
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
      "Running IllustrateDocument on '${config.main_file}'" +
          " [${if (local) "local" else "hosted"}]" +
          " [smart=${smartModel.name}, fast=${fastModel.name}]" +
          " [maxImages=${config.max_images}, format=${config.image_format}]" +
          (if (config.auto_insert) "" else " (no insertion)")
    )

    val exitCode = try {
      timed("IllustrateDocument harness") {
        log.debug("Registering task type {}", IllustrateDocumentTask.IllustrateDocument.name)
        TaskHarness(
          taskType = IllustrateDocumentTask.IllustrateDocument.apply { registerTaskType(this) },
          typeConfig = TaskTypeConfig(
            task_type = IllustrateDocumentTask.IllustrateDocument.name
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
      log.error("IllustrateDocument failed", e)
      System.err.println("Error: IllustrateDocument failed: ${e.message ?: e.javaClass.simpleName}")
      e.printStackTrace()
      1
    }
    log.info(
      "IllustrateDocumentTaskCli finished with exit code {} in {} ms", exitCode, System.currentTimeMillis() - mainStart
    )
    /* Harness/platform threads may linger; exit explicitly. */
    exitProcess(exitCode)
  }
}