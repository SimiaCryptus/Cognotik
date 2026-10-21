package com.simiacryptus.cognotik.cli.task

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.cli.CliSupport
import com.simiacryptus.cognotik.cli.FileServer
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.registerTaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.ImageGenerationTask
import com.simiacryptus.cognotik.platform.CognotikConfig
import com.simiacryptus.cognotik.platform.CognotikPlatform
import com.simiacryptus.cognotik.platform.h2.DatabaseFacet
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.util.TaskHarness
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

/**
 * Command line interface for exercising [ImageGenerationTask].
 *
 * By default the CLI runs against the hosted backend (device login, API calls proxied
 * through the hosted server, session cached in the global session.json). Pass `--local`
 * to use locally configured API keys instead.
 *
 * Options accept either `--key value` or `--key=value`; run with `--help` for the list.
 */
object ImageGenerationTaskCli {
  private val log = LoggerFactory.getLogger(ImageGenerationTaskCli::class.java)

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
  const val DEFAULT_IMAGE_MODEL = "GeminiFlash_31_Image_Preview"

  private val FLAGS = setOf("help", "local", "hosted")

  private val KNOWN = setOf(
    "output",
    "prompt",
    "prompt-file",
    "reference",
    "context",
    "workspace",
    "timeout-minutes",
    "smart-model",
    "fast-model",
    "image-model",
    "help",
    "local",
    "hosted"
  )

  private val IMAGE_EXTENSION = Regex(".*\\.(png|jpg|jpeg)$", RegexOption.IGNORE_CASE)

  private val USAGE = """
    |Usage: ImageGenerationTaskCli --output <file> (--prompt <text> | --prompt-file <file>) [options]
    |  --output <file>            Output image path (.png, .jpg or .jpeg); relative paths resolve against --workspace
    |  --prompt <text>            Description of the image: subject, style, composition, colors, mood
    |  --prompt-file <file>       Read the description from a file instead of --prompt
    |  --reference <a,b,...>      Comma-separated reference images (png/jpg/jpeg) used as visual context
    |  --context <a,b,...>        Comma-separated additional (non-image) context files
    |  --workspace <dir>          Base directory for relative paths (default: .)
    |  --timeout-minutes <n>      Harness timeout (default: 30)
    |  --smart-model <name>       Smart model (default: $DEFAULT_SMART_MODEL)
    |  --fast-model <name>        Fast model (default: $DEFAULT_FAST_MODEL)
    |  --image-model <name>       Image model (default: $DEFAULT_IMAGE_MODEL)
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

  private fun list(opts: Map<String, String>, k: String): List<String> =
    opts[k]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

  /** Resolves [path] against [workspace]; always returns an absolute canonical path. */
  private fun resolve(workspace: File, path: String): String {
    val f = File(path)
    return (if (f.isAbsolute) f else File(workspace, path)).canonicalPath
  }

  fun buildConfig(opts: Map<String, String>): ImageGenerationTask.GenerateImageTaskExecutionConfigData {
    log.debug("Building config from options: {}", opts)
    (opts.keys - KNOWN).firstOrNull()?.let {
      log.warn("Unknown option: --{}", it)
      throw IllegalArgumentException("Unknown option: --$it")
    }
    val workspace = File(opts["workspace"] ?: ".").canonicalFile
    if (!workspace.isDirectory) {
      throw IllegalArgumentException("--workspace does not exist or is not a directory: $workspace")
    }
    val output = opts["output"] ?: throw IllegalArgumentException("--output is required")
    if (!output.matches(IMAGE_EXTENSION)) {
      throw IllegalArgumentException("--output must have a .png, .jpg or .jpeg extension: $output")
    }
    if (opts.containsKey("prompt") && opts.containsKey("prompt-file")) {
      throw IllegalArgumentException("Specify only one of --prompt and --prompt-file")
    }
    val prompt = opts["prompt"] ?: opts["prompt-file"]?.let { p ->
      val f = File(resolve(workspace, p))
      if (!f.isFile) throw IllegalArgumentException("--prompt-file does not exist: $f")
      f.readText()
    } ?: throw IllegalArgumentException("--prompt or --prompt-file is required")
    if (prompt.isBlank()) throw IllegalArgumentException("Image prompt must not be blank")

    val references = list(opts, "reference").onEach {
      if (!it.matches(IMAGE_EXTENSION)) {
        throw IllegalArgumentException("--reference files must be png/jpg/jpeg: $it")
      }
    }.map { resolve(workspace, it) }
    references.firstOrNull { !File(it).isFile }?.let {
      throw IllegalArgumentException("Reference image does not exist: $it")
    }
    val context = list(opts, "context").map { resolve(workspace, it) }
    context.firstOrNull { !File(it).isFile }?.let {
      throw IllegalArgumentException("Context file does not exist: $it")
    }

    val outputPath = resolve(workspace, output)
    val cfg = ImageGenerationTask.GenerateImageTaskExecutionConfigData(
      related_files = references + context,
      task_description = prompt,
    ).apply { main_file = outputPath }
    log.info(
      "Built config: output='{}', references={}, context={}, promptLength={}",
      outputPath, references.size, context.size, prompt.length
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
    log.info("ImageGenerationTaskCli starting; args={}", args.joinToString(" "))
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
      CommitAnalyzerTaskCli.resolveModel(opts["smart-model"] ?: DEFAULT_SMART_MODEL) to
          CommitAnalyzerTaskCli.resolveModel(opts["fast-model"] ?: DEFAULT_FAST_MODEL)
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val imageModel: ChatModel = try {
      opts["image-model"]?.let { CommitAnalyzerTaskCli.resolveModel(it) } ?: GeminiModels.GeminiFlash_31_Image_Preview
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val local = opts["local"]?.toBooleanStrictOrNull() ?: false
    log.info(
      "Mode: {}; timeout={} min; smart={}; fast={}; image={}",
      if (local) "local" else "hosted",
      timeoutMinutes,
      smartModel.name,
      fastModel.name,
      imageModel.name
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

    val outputFile = File(config.main_file)
    println(
      "Running ImageGeneration -> '${outputFile.path}'" +
          " [${if (local) "local" else "hosted"}]" +
          " [smart=${smartModel.name}, fast=${fastModel.name}, image=${imageModel.name}]"
    )

    val exitCode = try {
      timed("ImageGeneration harness") {
        log.debug("Registering task type {}", ImageGenerationTask.GenerateImage.name)
        TaskHarness(
          taskType = ImageGenerationTask.GenerateImage.apply { registerTaskType(this) },
          typeConfig = TaskTypeConfig(
            task_type = ImageGenerationTask.GenerateImage.name
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
      if (outputFile.isFile) {
        println("Image written to ${outputFile.path}")
        0
      } else {
        log.error("Harness finished but output file does not exist: {}", outputFile)
        System.err.println("Error: image generation finished but no file was written to ${outputFile.path}")
        1
      }
    } catch (e: Exception) {
      log.error("ImageGeneration failed", e)
      System.err.println("Error: ImageGeneration failed: ${e.message ?: e.javaClass.simpleName}")
      e.printStackTrace()
      1
    }
    log.info(
      "ImageGenerationTaskCli finished with exit code {} in {} ms", exitCode, System.currentTimeMillis() - mainStart
    )
    /* Harness/platform threads may linger; exit explicitly. */
    exitProcess(exitCode)
  }
}