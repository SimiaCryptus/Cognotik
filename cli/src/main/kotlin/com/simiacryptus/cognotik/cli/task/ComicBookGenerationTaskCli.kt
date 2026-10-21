package com.simiacryptus.cognotik.cli.task

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.cli.CliSupport
import com.simiacryptus.cognotik.cli.FileServer
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.registerTaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.ComicBookGenerationTask
import com.simiacryptus.cognotik.platform.CognotikConfig
import com.simiacryptus.cognotik.platform.CognotikPlatform
import com.simiacryptus.cognotik.platform.h2.DatabaseFacet
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.TaskHarness
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

/**
 * Command line interface for exercising [ComicBookGenerationTask].
 *
 * By default the CLI runs against the hosted backend (device login, API calls proxied
 * through the hosted server, session cached in the global session.json). Pass `--local`
 * to use locally configured API keys instead.
 *
 * Options accept either `--key value` or `--key=value`; run with `--help` for the list.
 */
object ComicBookGenerationTaskCli {
  private val log = LoggerFactory.getLogger(ComicBookGenerationTaskCli::class.java)

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
  const val DEFAULT_IMAGE_MODEL = "GeminiFlash_31_Image_Preview"

  private val FLAGS = setOf("no-images", "help", "local", "hosted")

  private val KNOWN = setOf(
    "description",
    "description-file",
    "pages",
    "art-style",
    "style-details",
    "no-images",
    "characters-file",
    "plot-file",
    "timeout-minutes",
    "help",
    "local",
    "hosted",
    "smart-model",
    "fast-model",
    "image-model"
  )

  private val USAGE = """
    |Usage: ComicBookGenerationTaskCli [options]
    |  --description <text>       Subject or scenario to develop into a comic book
    |  --description-file <path>  Read the subject/scenario from a file (alternative to --description)
    |  --pages <n>                Target number of pages, 1-100 (default: 5)
    |  --art-style <style>        Art style, e.g. manga, noir, cartoon (default: western superhero)
    |  --style-details <text>     Additional style details or visual guidelines
    |  --no-images                Generate the script only; skip image generation
    |  --characters-file <path>   JSON array of character references
    |                             ([{"character_name": "...", "reference_image_path": "...",
    |                               "visual_description": "...", "personality_notes": "...",
    |                               "role": "...", "relationships": "..."}])
    |  --plot-file <path>         JSON object with plot continuity details
    |                             (narrative_structure, key_plot_points, setting_details, themes, tone,
    |                              prior_story_context, continuity_constraints, ending_notes, additional_notes)
    |  --timeout-minutes <n>      Harness timeout (default: 60)
    |  --smart-model <name>       Smart model (default: $DEFAULT_SMART_MODEL)
    |  --fast-model <name>        Fast model (default: $DEFAULT_FAST_MODEL)
    |  --image-model <name>       Image model (default: $DEFAULT_IMAGE_MODEL)
    |  --hosted                   Use the hosted backend via device login (default)
    |  --local                    Use locally configured API keys; skip hosted login
    |  --help                     Show this help
    """.trimMargin()

  fun parseArgs(args: Array<String>): Map<String, String> {
    log.debug("Parsing {} argument(s)", args.size)
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
        body.contains('=') -> out[body.substringBefore('=')] = body.substringAfter('=')
        body in FLAGS -> out[body] = "true"
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
    log.info("Parsed options: {}", out.keys)
    return out
  }

  private fun int(opts: Map<String, String>, k: String, d: Int): Int {
    val raw = opts[k] ?: return d
    return raw.toIntOrNull() ?: throw IllegalArgumentException("--$k must be an integer")
  }

  private fun readFile(path: String, option: String): String {
    val f = File(path)
    if (!f.isFile) throw IllegalArgumentException("--$option does not exist or is not a file: ${f.absolutePath}")
    return f.readText()
  }

  private fun loadCharacters(path: String): List<ComicBookGenerationTask.CharacterReference> {
    val file = File(path).absoluteFile
    val parsed = try {
      JsonUtil.fromJson<Array<ComicBookGenerationTask.CharacterReference>>(
        readFile(path, "characters-file"), Array<ComicBookGenerationTask.CharacterReference>::class.java
      ).toList()
    } catch (e: IllegalArgumentException) {
      throw e
    } catch (e: Exception) {
      throw IllegalArgumentException("--characters-file is not a valid JSON array of character references: ${e.message}")
    }
    // Relative reference image paths are resolved against the characters file, not the harness workspace.
    parsed.forEach { ref ->
      ref.validate()?.let { throw IllegalArgumentException("Invalid character reference: $it") }
      val img = ref.reference_image_path
      if (!img.isNullOrBlank() && !img.contains("://") && !File(img).isAbsolute) {
        ref.reference_image_path = File(file.parentFile, img).canonicalPath
      }
    }
    return parsed
  }

  private fun loadPlot(path: String): ComicBookGenerationTask.PlotContinuityDetails = try {
    JsonUtil.fromJson<ComicBookGenerationTask.PlotContinuityDetails>(
      readFile(path, "plot-file"), ComicBookGenerationTask.PlotContinuityDetails::class.java
    )
  } catch (e: IllegalArgumentException) {
    throw e
  } catch (e: Exception) {
    throw IllegalArgumentException("--plot-file is not a valid plot continuity JSON object: ${e.message}")
  }

  fun buildConfig(opts: Map<String, String>): ComicBookGenerationTask.ComicBookGenerationTaskExecutionConfigData {
    log.debug("Building config from options: {}", opts.keys)
    (opts.keys - KNOWN).firstOrNull()?.let {
      log.warn("Unknown option: --{}", it)
      throw IllegalArgumentException("Unknown option: --$it")
    }
    if (opts.containsKey("description") && opts.containsKey("description-file")) {
      throw IllegalArgumentException("Specify only one of --description and --description-file")
    }
    val description = (opts["description"] ?: opts["description-file"]?.let { readFile(it, "description-file") })
      ?.trim()
    if (description.isNullOrBlank()) {
      throw IllegalArgumentException("--description or --description-file is required")
    }
    val pages = int(opts, "pages", 5)
    if (pages < 1) throw IllegalArgumentException("--pages must be a positive integer")

    val cfg = ComicBookGenerationTask.ComicBookGenerationTaskExecutionConfigData(
      task_description = description,
      target_pages = pages,
      art_style = opts["art-style"] ?: "western superhero",
      style_details = opts["style-details"] ?: "",
      generate_images = opts["no-images"]?.toBooleanStrictOrNull() != true,
      character_references = opts["characters-file"]?.let { loadCharacters(it) } ?: emptyList(),
      plot_continuity = opts["plot-file"]?.let { loadPlot(it) },
    )
    log.info(
      "Built config: pages={}, style='{}', generateImages={}, characters={}, plotContinuity={}",
      cfg.target_pages,
      cfg.art_style,
      cfg.generate_images,
      cfg.character_references.size,
      cfg.plot_continuity != null
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
    log.info("ComicBookGenerationTaskCli starting")
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
        cfg.validate()?.let { throw IllegalArgumentException(it) }
      }
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val timeoutMinutes = try {
      int(opts, "timeout-minutes", 60).also { require(it > 0) { "--timeout-minutes must be positive" } }
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val (smartModel, fastModel) = try {
      CommitAnalyzerTaskCli.resolveModel(opts["smart-model"] ?: DEFAULT_SMART_MODEL) to
          CommitAnalyzerTaskCli.resolveModel(opts["fast-model"] ?: DEFAULT_FAST_MODEL)
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val imageModel = try {
      CommitAnalyzerTaskCli.resolveModel(opts["image-model"] ?: DEFAULT_IMAGE_MODEL)
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
      "Running ComicBookGeneration: ${config.target_pages} page(s), style '${config.art_style}'" +
          " [${if (local) "local" else "hosted"}]" +
          " [smart=${smartModel.name}, fast=${fastModel.name}" +
          (if (config.generate_images) ", image=${imageModel.name}" else "") + "]" +
          (if (config.generate_images) "" else " (script only, no images)")
    )

    val exitCode = try {
      timed("ComicBookGeneration harness") {
        TaskHarness(
          taskType = ComicBookGenerationTask.ComicBookGeneration.apply { registerTaskType(this) },
          typeConfig = TaskTypeConfig(
            task_type = ComicBookGenerationTask.ComicBookGeneration.name
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
      log.error("ComicBookGeneration failed", e)
      System.err.println("Error: ComicBookGeneration failed: ${e.message ?: e.javaClass.simpleName}")
      e.printStackTrace()
      1
    }
    log.info(
      "ComicBookGenerationTaskCli finished with exit code {} in {} ms", exitCode, System.currentTimeMillis() - mainStart
    )
    /* Harness/platform threads may linger; exit explicitly. */
    exitProcess(exitCode)
  }
}