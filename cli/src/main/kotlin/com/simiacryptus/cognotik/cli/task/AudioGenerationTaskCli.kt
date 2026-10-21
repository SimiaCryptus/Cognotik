package com.simiacryptus.cognotik.cli.task

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.cli.CliSupport
import com.simiacryptus.cognotik.cli.FileServer
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.registerTaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.AudioGenerationTask
import com.simiacryptus.cognotik.platform.CognotikConfig
import com.simiacryptus.cognotik.platform.CognotikPlatform
import com.simiacryptus.cognotik.platform.h2.DatabaseFacet
import com.simiacryptus.cognotik.util.TaskHarness
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

/**
 * Command line interface for exercising [AudioGenerationTask].
 *
 * By default the CLI runs against the hosted backend (device login, API calls proxied
 * through the hosted server, session cached in the global session.json). Pass `--local`
 * to use locally configured API keys instead.
 *
 * Options accept either `--key value` or `--key=value`; run with `--help` for the list.
 */
object AudioGenerationTaskCli {
  private val log = LoggerFactory.getLogger(AudioGenerationTaskCli::class.java)

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

  private val AUDIO_EXTENSION = Regex(".*\\.(mp3|wav|ogg|flac|m4a|aac)$", RegexOption.IGNORE_CASE)

  private val FLAGS = setOf("help", "local", "hosted")

  private val KNOWN = setOf(
    "prompt",
    "output",
    "related",
    "workspace",
    "timeout-minutes",
    "smart-model",
    "fast-model",
    "audio-model",
    "help",
    "local",
    "hosted",
  )

  private val USAGE = """
    |Usage: AudioGenerationTaskCli --prompt <text> --output <file> [options]
    |  --prompt <text>            Detailed description of the audio to generate (content, tone, voice, style, ...)
    |  --output <file>            Output audio file (.mp3, .wav, .ogg, .flac, .m4a, .aac);
    |                             relative to --workspace, or an absolute path inside it
    |  --related <files>          Comma-separated context files (reference audio, scripts, style guides)
    |  --workspace <dir>          Workspace directory (default: .)
    |  --timeout-minutes <n>      Harness timeout (default: 30)
    |  --smart-model <name>       Smart model (default: $DEFAULT_SMART_MODEL)
    |  --fast-model <name>        Fast model (default: $DEFAULT_FAST_MODEL)
    |  --audio-model <name>       Audio model (default: the fast model)
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
    log.info("Parsed option keys: {}", out.keys)
    return out
  }

  private fun int(opts: Map<String, String>, k: String, d: Int): Int {
    val raw = opts[k] ?: return d
    return raw.toIntOrNull() ?: throw IllegalArgumentException("--$k must be an integer")
  }

  /** Resolves [raw] against [workspace] and returns a workspace-relative, '/'-separated path. */
  private fun toWorkspaceRelative(workspace: File, raw: String, label: String): String {
    val file = File(raw)
    val abs = (if (file.isAbsolute) file else File(workspace, raw)).canonicalFile
    val wsPath = workspace.toPath()
    if (!abs.toPath().startsWith(wsPath)) {
      throw IllegalArgumentException("$label must be inside the workspace ($workspace): $raw")
    }
    return wsPath.relativize(abs.toPath()).toString().replace(File.separatorChar, '/')
  }

  /**
   * The output file lives in the inherited `main_file` property, which is not exposed through the
   * config constructor; set it directly on the backing field (works for both val and var).
   */
  private fun setMainFile(cfg: Any, value: String) {
    var cls: Class<*>? = cfg.javaClass
    while (cls != null) {
      val current: Class<*> = cls
      val field = runCatching { current.getDeclaredField("main_file") }.getOrNull()
      if (field != null) {
        field.isAccessible = true
        field.set(cfg, value)
        return
      }
      cls = current.superclass
    }
    throw IllegalStateException("Could not locate 'main_file' on ${cfg.javaClass.name}")
  }

  fun buildConfig(opts: Map<String, String>): AudioGenerationTask.GenerateAudioTaskExecutionConfigData {
    log.debug("Building config from option keys: {}", opts.keys)
    (opts.keys - KNOWN).firstOrNull()?.let {
      log.warn("Unknown option: --{}", it)
      throw IllegalArgumentException("Unknown option: --$it")
    }
    val prompt = opts["prompt"]?.takeIf { it.isNotBlank() }
      ?: throw IllegalArgumentException("--prompt is required")
    val outputRaw = opts["output"]?.takeIf { it.isNotBlank() }
      ?: throw IllegalArgumentException("--output is required")
    if (!AUDIO_EXTENSION.matches(outputRaw)) {
      throw IllegalArgumentException(
        "--output must have a .mp3, .wav, .ogg, .flac, .m4a, or .aac extension: $outputRaw"
      )
    }
    val workspace = File(opts["workspace"] ?: ".").canonicalFile
    if (!workspace.isDirectory) {
      throw IllegalArgumentException("--workspace does not exist or is not a directory: $workspace")
    }
    val output = toWorkspaceRelative(workspace, outputRaw, "--output")
    val related = opts["related"]
      ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
      ?.map { raw ->
        toWorkspaceRelative(workspace, raw, "--related").also {
          if (!File(workspace, it).exists()) log.warn("Related file does not exist: {}", it)
        }
      }
    val cfg = AudioGenerationTask.GenerateAudioTaskExecutionConfigData(
      related_files = related,
      task_description = prompt,
    )
    setMainFile(cfg, output)
    log.info(
      "Built config: workspace='{}', output='{}', related={}", workspace, output, related
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
    log.info("AudioGenerationTaskCli starting")
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
      CommitAnalyzerTaskCli.resolveModel(opts["smart-model"] ?: DEFAULT_SMART_MODEL) to
          CommitAnalyzerTaskCli.resolveModel(opts["fast-model"] ?: DEFAULT_FAST_MODEL)
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val audioModel = try {
      opts["audio-model"]?.let { CommitAnalyzerTaskCli.resolveModel(it) } ?: fastModel
    } catch (e: IllegalArgumentException) {
      fail(e.message)
    }
    val workspace = File(opts["workspace"] ?: ".").canonicalFile
    val local = opts["local"]?.toBooleanStrictOrNull() ?: false
    log.info(
      "Mode: {}; timeout={} min; smart={}; fast={}; audio={}",
      if (local) "local" else "hosted", timeoutMinutes, smartModel.name, fastModel.name, audioModel.name
    )

    /* Platform init, mirroring FileServer.run(). */
    timed("CognotikPlatform.init") { CognotikPlatform.init() }
    /* Register providers (incl. the 'Cognotik' hosted proxy) before login touches settings. */
    timed("CoreProviders.init") { CoreProviders.init() }
    val globalRoot = File(DatabaseFacet.root)
    DatabaseFacet.root = workspace.absolutePath
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
    log.info("Using user: {}", user)
    try {
      timed("platform bootstrap") { CliSupport.bootstrapPlatform(user) }
    } catch (e: Exception) {
      log.error("Platform bootstrap failed", e)
      System.err.println("Error: platform bootstrap failed: ${e.message ?: e.javaClass.simpleName}")
      e.printStackTrace()
      exitProcess(2)
    }

    println(
      "Running AudioGeneration in '$workspace' -> '${config.main_file}'" +
          " [${if (local) "local" else "hosted"}]" +
          " [smart=${smartModel.name}, fast=${fastModel.name}, audio=${audioModel.name}]"
    )

    val exitCode = try {
      timed("AudioGeneration harness") {
        TaskHarness(
          taskType = AudioGenerationTask.GenerateAudio.apply { registerTaskType(this) },
          typeConfig = TaskTypeConfig(
            task_type = AudioGenerationTask.GenerateAudio.name
          ),
          executionConfig = config,
          timeoutMinutes = timeoutMinutes.toLong(),
          user = user,
          smartModel = smartModel,
          fastModel = fastModel,
          imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
          audioModel = audioModel,
        ).run()
      }
      val produced = File(workspace, config.main_file)
      if (produced.isFile) {
        println("Audio written to ${produced.absolutePath}")
        0
      } else {
        log.warn("Task finished but output file was not found: {}", produced)
        System.err.println("Error: task finished but no audio file was written to ${produced.absolutePath}")
        1
      }
    } catch (e: Exception) {
      log.error("AudioGeneration failed", e)
      System.err.println("Error: AudioGeneration failed: ${e.message ?: e.javaClass.simpleName}")
      e.printStackTrace()
      1
    }
    log.info(
      "AudioGenerationTaskCli finished with exit code {} in {} ms", exitCode, System.currentTimeMillis() - mainStart
    )
    /* Harness/platform threads may linger; exit explicitly. */
    exitProcess(exitCode)
  }
}