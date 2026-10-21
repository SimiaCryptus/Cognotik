package com.simiacryptus.cognotik.cli.task

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.cli.CliSupport
import com.simiacryptus.cognotik.cli.FileServer
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.registerTaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.WriteHtmlTask
import com.simiacryptus.cognotik.platform.CognotikConfig
import com.simiacryptus.cognotik.platform.CognotikPlatform
import com.simiacryptus.cognotik.platform.h2.DatabaseFacet
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.util.TaskHarness
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

/**
 * Command line interface for exercising [WriteHtmlTask].
 *
 * By default the CLI runs against the hosted backend (device login, API calls proxied
 * through the hosted server, session cached in the global session.json). Pass `--local`
 * to use locally configured API keys instead.
 *
 * Options accept either `--key value` or `--key=value`; run with `--help` for the list.
 */
object WriteHtmlTaskCli {
  private val log = LoggerFactory.getLogger(WriteHtmlTaskCli::class.java)

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
  const val MAX_IMAGES = 10

  private val FLAGS = setOf("help", "local", "hosted")

  private val KNOWN = setOf(
    "file",
    "description",
    "description-file",
    "related-files",
    "images",
    "timeout-minutes",
    "smart-model",
    "fast-model",
    "image-model",
    "help",
    "local",
    "hosted"
  )

  private val USAGE = """
    |Usage: WriteHtmlTaskCli --file <path.html> (--description <text> | --description-file <path>) [options]
    |  --file <path>              Output HTML file (must end with .html)
    |  --description <text>       Description of the page to create (layout, styling, behavior)
    |  --description-file <path>  Read the page description from a file instead of --description
    |  --related-files <a,b,...>  Comma-separated files to provide as context (templates, references)
    |  --images <n>               Number of AI-generated images, 0-$MAX_IMAGES (default: 0 = no images)
    |  --timeout-minutes <n>      Harness timeout (default: 30)
    |  --smart-model <name>       Smart model (default: $DEFAULT_SMART_MODEL)
    |  --fast-model <name>        Fast model (default: $DEFAULT_FAST_MODEL)
    |  --image-model <name>       Image model (default: GeminiFlash_31_Image_Preview)
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
        }

        body in FLAGS -> {
          out[body] = "true"
        }

        else -> {
          val v = args.getOrNull(i + 1)
          if (v == null || v.startsWith("--")) {
            log.warn("Missing value for option --{}", body)
            throw IllegalArgumentException("Missing value for --$body")
          }
          out[body] = v
          i++
        }
      }
      i++
    }
    log.info("Parsed options: {}", out)
    return out
  }

  private fun int(opts: Map<String, String>, k: String, d: Int): Int {
    val raw = opts[k] ?: return d
    return raw.toIntOrNull() ?: throw IllegalArgumentException("--$k must be an integer")
  }

  /**
   * `main_file` is declared on the shared file-task base config; assign it via its setter or
   * backing field so this wrapper does not depend on how the base class declares it.
   */
  private fun setMainFile(cfg: WriteHtmlTask.WriteHtmlTaskExecutionConfigData, path: String) {
    val cls = cfg.javaClass
    val setter = generateSequence<Class<*>>(cls) { it.superclass }
      .flatMap { it.declaredMethods.asSequence() }
      .firstOrNull { it.name == "setMain_file" && it.parameterCount == 1 }
    if (setter != null) {
      setter.isAccessible = true
      setter.invoke(cfg, path)
      return
    }
    val field = generateSequence<Class<*>>(cls) { it.superclass }
      .flatMap { it.declaredFields.asSequence() }
      .firstOrNull { it.name == "main_file" }
      ?: throw IllegalStateException("Unable to set main_file on ${cls.name}")
    field.isAccessible = true
    field.set(cfg, path)
  }

  fun buildConfig(opts: Map<String, String>): WriteHtmlTask.WriteHtmlTaskExecutionConfigData {
    log.debug("Building config from options: {}", opts)
    (opts.keys - KNOWN).firstOrNull()?.let {
      throw IllegalArgumentException("Unknown option: --$it")
    }
    val fileOpt = opts["file"] ?: throw IllegalArgumentException("--file is required")
    if (!fileOpt.endsWith(".html", ignoreCase = true)) {
      throw IllegalArgumentException("--file must have a .html extension: $fileOpt")
    }
    // Always absolute: the task resolves relative paths against the harness workspace,
    // not against the CLI's working directory.
    val file = File(fileOpt).absoluteFile.normalize().path

    if (opts["description"] != null && opts["description-file"] != null) {
      throw IllegalArgumentException("Specify only one of --description and --description-file")
    }
    val description = opts["description"] ?: opts["description-file"]?.let {
      val f = File(it)
      if (!f.isFile) throw IllegalArgumentException("--description-file does not exist: ${f.absolutePath}")
      f.readText()
    } ?: throw IllegalArgumentException("--description or --description-file is required")
    if (description.isBlank()) throw IllegalArgumentException("Page description must not be blank")

    val related = opts["related-files"]
      ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
      ?.map { f ->
        val rf = File(f).absoluteFile.normalize()
        if (!rf.exists()) throw IllegalArgumentException("Related file does not exist: ${rf.path}")
        rf.path
      }

    val images = int(opts, "images", 0)
    if (images < 0 || images > MAX_IMAGES) {
      throw IllegalArgumentException("--images must be between 0 and $MAX_IMAGES")
    }

    val cfg = WriteHtmlTask.WriteHtmlTaskExecutionConfigData(
      related_files = related,
      task_description = description,
      generate_images = images > 0,
      image_count = images,
    )
    setMainFile(cfg, file)
    log.info(
      "Built config: file='{}', related={}, images={}, descriptionChars={}",
      file, related, images, description.length
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
    log.info("WriteHtmlTaskCli starting; args={}", args.joinToString(" "))
    if (args.any { it == "--help" }) {
      println(USAGE)
      return
    }
    val opts = try {
      parseArgs(args)
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    if (opts.containsKey("local") && opts.containsKey("hosted")) {
      fail("--local and --hosted are mutually exclusive")
    }
    val config = try {
      buildConfig(opts).also { cfg ->
        cfg.validate()?.let { throw IllegalArgumentException(it) }
      }
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val timeoutMinutes = try {
      int(opts, "timeout-minutes", 30).also { require(it > 0) { "--timeout-minutes must be positive" } }
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val smartModel: ChatModel
    val fastModel: ChatModel
    val imageModel: ChatModel
    try {
      smartModel = CommitAnalyzerTaskCli.resolveModel(opts["smart-model"] ?: DEFAULT_SMART_MODEL)
      fastModel = CommitAnalyzerTaskCli.resolveModel(opts["fast-model"] ?: DEFAULT_FAST_MODEL)
      imageModel = opts["image-model"]?.let { CommitAnalyzerTaskCli.resolveModel(it) }
        ?: GeminiModels.GeminiFlash_31_Image_Preview
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val local = opts["local"]?.toBooleanStrictOrNull() ?: false
    log.info(
      "Mode: {}; timeout={} min; smart={}; fast={}; image={}",
      if (local) "local" else "hosted", timeoutMinutes, smartModel.name, fastModel.name, imageModel.name
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
      "Using user: {} (source: {})",
      user,
      if (CliSupport.defaultUser != null) "CliSupport" else "CognotikConfig"
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
      "Running WriteHtml -> '${config.main_file}' [${if (local) "local" else "hosted"}]" +
          " [smart=${smartModel.name}, fast=${fastModel.name}]" +
          (if (config.generate_images) " with ${config.image_count} generated image(s) [image=${imageModel.name}]" else "")
    )

    val exitCode = try {
      timed("WriteHtml harness") {
        TaskHarness(
          taskType = WriteHtmlTask.WriteHtml.apply { registerTaskType(this) },
          typeConfig = TaskTypeConfig(
            task_type = WriteHtmlTask.WriteHtml.name
          ),
          executionConfig = config,
          timeoutMinutes = timeoutMinutes.toLong(),
          user = user,
          smartModel = smartModel,
          fastModel = fastModel,
          imageModel = imageModel,
          audioModel = fastModel,
        ).run()
      }
      0
    } catch (e: Exception) {
      log.error("WriteHtml failed", e)
      System.err.println("Error: WriteHtml failed: ${e.message ?: e.javaClass.simpleName}")
      e.printStackTrace()
      1
    }
    log.info("WriteHtmlTaskCli finished with exit code {} in {} ms", exitCode, System.currentTimeMillis() - mainStart)
    /* Harness/platform threads may linger; exit explicitly. */
    exitProcess(exitCode)
  }
}