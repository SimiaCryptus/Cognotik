package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.AudioAndText
import com.simiacryptus.cognotik.agents.AudioProcessingAgent
import com.simiacryptus.cognotik.agents.pickVoices
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.models.AudioSegment
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.instance
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.safeComplete
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GenerateAudioFilesTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: GenerateAudioFilesTaskExecutionConfigData?
) : AbstractFileTask<GenerateAudioFilesTask.GenerateAudioFilesTaskExecutionConfigData>(
  orchestrationConfig,
  planTask
) {

  class GenerateAudioFilesTaskExecutionConfigData(
    @Description("The directory (relative path) where audio segment files and metadata.json will be written")
    var output_directory: String? = null,
    @Description("The JSON metadata file to be created (relative path, must end with .json). If unset, defaults to <output_directory>/metadata.json")
    var metadata_file: String? = null,
    @Description("The audio file format/extension to use for individual segment files (e.g. 'wav', 'mp3'). Defaults to 'wav'.")
    var audio_format: String? = "wav",
    @Description("Optional base name (no extension) for segment files. Defaults to 'segment'. Files will be named '<base>_<index>.<ext>'.")
    var file_base_name: String? = "segment",
    @Description("Optional default voice id to use for segments without an explicit [voice:Name] directive.")
    var default_voice: String? = null,
    @Description("Whether to use the two-phase approach (text model converts user request to a speaking script before audio generation).")
    var two_phase: Boolean? = true,
    @Description("Detailed description of the audio script to generate, or the script itself.")
    task_description: String? = null,
    related_files: List<String>? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : ValidatedObject, FileTaskExecutionConfig(
    task_type = GenerateAudioFiles.name,
    task_description = task_description,
    task_dependencies = task_dependencies,
    related_files = related_files,
    state = state
  ) {
    override fun validate(): String? {
      if (output_directory.isNullOrBlank()) return "Output directory must be specified"
      val md = metadata_file
      if (!md.isNullOrBlank() && !md.endsWith(".json", ignoreCase = true)) {
        return "Metadata file must be .json"
      }
      if (audio_format.isNullOrBlank()) return "Audio format must be specified"
      return ValidatedObject.Companion.validateFields(this)
    }
  }

  data class AudioSegmentMetadata(
    @Description("Sequential index of this segment in the script")
    var index: Int = 0,
    @Description("Relative path to the audio file for this segment")
    var file: String = "",
    @Description("Voice id used to render this segment (null for silence segments or single-call rendering)")
    var voice: String? = null,
    @Description("If non-null, this segment is silence of the given duration in seconds")
    var silenceSeconds: Double? = null,
    @Description("The portion of the script (text) used to render this segment")
    var text: String = "",
    @Description("Duration of the rendered audio in seconds (0.0 if rendering failed)")
    var durationSeconds: Double = 0.0,
    @Description("Audio format / container of the file (e.g. 'wav', 'mp3')")
    var format: String = "wav",
    @Description("Sample rate in Hz")
    var sampleRate: Int = 0,
    @Description("Number of audio channels")
    var channels: Int = 0,
    @Description("Bits per sample")
    var bitsPerSample: Int = 0,
    @Description("True if audio rendering produced no audio (write was skipped)")
    var renderFailed: Boolean = false,
  )

  data class AudioFilesManifest(
    @Description("General description of the audio content / script")
    var description: String = "",
    @Description("Default voice id used when a segment did not specify one")
    var defaultVoice: String? = null,
    @Description("Total duration of the rendered audio (sum of segments) in seconds")
    var totalDurationSeconds: Double = 0.0,
    @Description("Per-segment metadata, in script order")
    var segments: List<AudioSegmentMetadata> = emptyList(),
  )

  override fun promptSegment(): String {
    return """
GenerateAudioFiles - Render an audio script as individual per-segment audio files plus a JSON manifest
  * Splits the script using `---` separators (and/or has the model produce them in two-phase mode)
  * Renders each segment in parallel using AudioProcessingAgent (with retry / timeout)
  * Writes one audio file per segment to the output directory
  * Writes a metadata.json manifest mapping each file to its script text, voice, and duration
        """.trimIndent()
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val cfg = executionConfig ?: return resultFn("No execution configuration provided")
    val outputDir = cfg.output_directory ?: "."
    val audioFormat = (cfg.audio_format ?: "wav").lowercase()
    val baseName = cfg.file_base_name?.takeIf { it.isNotBlank() } ?: "segment"
    val metadataFile = cfg.metadata_file?.takeIf { it.isNotBlank() }
      ?: ("$outputDir/metadata.json").replace(Regex("/+"), "/")

    val transcript = task.newUserFileStream(transcriptFile())

    try {
      transcript?.write("## Generating Audio Files: $outputDir\n".toByteArray())
      task.header("Generating Audio Files: $outputDir", level = 2)

      task.pool.submit {
        try {
          log.info("Starting audio file generation in {}", outputDir)
          task.add("### Step 1: Rendering Audio Segments...".renderMarkdown())

          val audioModel = orchestrationConfig.defaultAudio.getChildClient(task)
          val textModel = if (cfg.two_phase != false) {
            (typeConfig?.model?.let { it.instance(orchestrationConfig.user) }
              ?: defaultSmart).getChildClient(task)
          } else null

          val voices = audioModel.provider.pickVoices()
          val resolvedDefaultVoice = cfg.default_voice
            ?.takeIf { v -> voices.keys.any { it.equals(v, ignoreCase = true) } }
            ?: voices.keys.firstOrNull()
            ?: "Callirrhoe"


          val contextFiles = getInputFileCode()
          val priorCode = getPriorCode(agent.executionState)
          val prompt = buildString {
            append(executionConfig?.task_description ?: "You are an expert audio narrator and producer.")
            if (contextFiles.isNotEmpty()) {
              append("\n\nContext from related files:\n")
              append(contextFiles)
            }
            if (priorCode.isNotEmpty()) {
              append("\n\nPrevious task results:\n")
              append(priorCode)
            }
          }

          val audioAgent = AudioProcessingAgent(
            prompt = "You are an expert audio narrator and producer.",
            name = "AudioFilesGenerator",
            model = audioModel,
            textModel = textModel,
            voices = voices,
            defaultVoice = resolvedDefaultVoice,
          )

          val segmentResults = audioAgent.let { ag ->
            val input = listOf(AudioAndText(text = prompt))
            val chatMessages = ag.chatMessages(input)
            ag.renderSegments(input, *chatMessages)
          }

          if (segmentResults.isEmpty()) {
            task.error(RuntimeException("No audio segments produced"))
            transcript?.write("\n## Error\nNo audio segments produced.\n".toByteArray())
            resultFn("ERROR: No audio segments produced")
            return@submit
          }

          task.add("### Step 2: Saving ${segmentResults.size} Segment(s)...".renderMarkdown())

          // Prepare segment files (in-memory) for preview / commit.
          data class PreparedSegment(
            val meta: AudioSegmentMetadata,
            val audio: AudioSegment?,
            val relativePath: String,
          )

          val prepared = segmentResults.map { result ->
            val parsed = result.parsedSegment
            val isSilence = parsed?.silenceSeconds != null
            val voice = if (isSilence) null else (parsed?.voice ?: resolvedDefaultVoice)
            val segIndex = result.index
            val ext = audioFormat
            val segFileName = "${baseName}_${"%04d".format(segIndex)}.$ext"
            val relativePath = "$outputDir/$segFileName".replace(Regex("/+"), "/")
            val audio = result.audio
            val convertedAudio = audio?.let {
              try {
                if (it.format.equals(audioFormat, ignoreCase = true)) it
                else it.convert(audioFormat)
              } catch (e: Exception) {
                log.warn("Failed to convert segment $segIndex audio from ${it.format} to $audioFormat", e)
                it
              }
            }
            val meta = AudioSegmentMetadata(
              index = segIndex,
              file = segFileName,
              voice = voice,
              silenceSeconds = parsed?.silenceSeconds,
              text = parsed?.text ?: result.text,
              durationSeconds = convertedAudio?.durationSeconds ?: 0.0,
              format = convertedAudio?.format ?: audioFormat,
              sampleRate = convertedAudio?.sampleRate ?: 0,
              channels = convertedAudio?.channels ?: 0,
              bitsPerSample = convertedAudio?.bitsPerSample ?: 0,
              renderFailed = convertedAudio == null && parsed?.silenceSeconds == null,
            )
            PreparedSegment(meta = meta, audio = convertedAudio, relativePath = relativePath)
          }

          val manifest = AudioFilesManifest(
            description = executionConfig?.task_description ?: "Audio script generated by GenerateAudioFilesTask",
            defaultVoice = resolvedDefaultVoice,
            totalDurationSeconds = prepared.sumOf { it.meta.durationSeconds },
            segments = prepared.map { it.meta },
          )

          // Build previews by saving to ephemeral preview locations.
          val previewHtml = StringBuilder()
          prepared.forEach { seg ->
            try {
              if (seg.audio != null) {
                val previewUrl = task.saveFile(
                  "previews/${seg.relativePath}",
                  seg.audio.audioBytes
                )
                val mime = when (seg.meta.format.lowercase()) {
                  "mp3" -> "audio/mpeg"
                  "wav" -> "audio/wav"
                  "ogg" -> "audio/ogg"
                  else -> "audio/${seg.meta.format.lowercase()}"
                }
                previewHtml.append(
                  """
                  <div style="margin:6px 0; padding:6px; border:1px solid #ccc; border-radius:4px;">
                    <div><b>#${seg.meta.index}</b> ${if (seg.meta.silenceSeconds != null) "[silence ${seg.meta.silenceSeconds}s]" else "voice=${seg.meta.voice}"} - ${
                    "%.2f".format(
                      seg.meta.durationSeconds
                    )
                  }s</div>
                    <div style="white-space:pre-wrap; font-size: 0.9em; color:#444; margin: 4px 0;">${escapeHtml(seg.meta.text)}</div>
                    <audio controls preload="none" src="$previewUrl" type="$mime" style="width:100%"></audio>
                    <div style="font-size:0.8em;"><a href="$previewUrl" target="_blank">${seg.relativePath}</a></div>
                  </div>
                  """.trimIndent()
                )
              } else {
                previewHtml.append(
                  """
                  <div style="margin:6px 0; padding:6px; border:1px solid #c66; border-radius:4px; background:#fee;">
                    <div><b>#${seg.meta.index}</b> (no audio)</div>
                    <div style="white-space:pre-wrap; font-size: 0.9em; color:#444; margin: 4px 0;">${escapeHtml(seg.meta.text)}</div>
                  </div>
                  """.trimIndent()
                )
              }
            } catch (e: Exception) {
              log.warn("Failed to build preview for segment ${seg.meta.index}", e)
            }
          }

          transcript?.write(
            "<details><summary>Audio Manifest</summary>\n\n```json\n${
              manifest.toJson()
            }\n```\n</details>\n".toByteArray()
          )

          val tabs = TabbedDisplay(task)
          tabs["Overview"] = """
            <ul>
              <li><b>Output Directory:</b> $outputDir</li>
              <li><b>Metadata File:</b> $metadataFile</li>
              <li><b>Segment Count:</b> ${prepared.size}</li>
              <li><b>Total Duration:</b> ${"%.2f".format(manifest.totalDurationSeconds)}s</li>
              <li><b>Default Voice:</b> $resolvedDefaultVoice</li>
              <li><b>Two-Phase:</b> ${textModel != null}</li>
            </ul>
          """.trimIndent()
          tabs["Segments"] = previewHtml.toString()
          tabs["Manifest"] = MarkdownUtil.renderMarkdown(
            "```json\n${manifest.toJson()}\n```",
          )
          tabs["Script"] = MarkdownUtil.renderMarkdown(
            prepared.joinToString("\n\n---\n\n") { seg ->
              val header = if (seg.meta.silenceSeconds != null) {
                "**#${seg.meta.index}** _[silence ${seg.meta.silenceSeconds}s]_"
              } else {
                "**#${seg.meta.index}** _voice=${seg.meta.voice}_"
              }
              "$header\n\n${seg.meta.text}"
            },
          )

          val commitAction = {
            log.info("Committing {} audio segments to disk under '{}'", prepared.size, outputDir)
            log.info("Root path: {}", root)
            val outDir = root.resolve(outputDir).toFile()
            log.info("Resolved output directory: {} (absolute: {})", outDir, outDir.absolutePath)
            val dirCreated = outDir.mkdirs()
            log.info("Output directory created: {} (exists: {})", dirCreated, outDir.exists())
            var successCount = 0
            var failureCount = 0
            prepared.forEach { seg ->
              try {
                val target = root.resolve(seg.relativePath)
                log.info("Writing segment {} to '{}' (absolute: {})", seg.meta.index, target, target.toAbsolutePath())
                target.toFile().parentFile?.mkdirs()
                if (seg.audio != null) {
                  log.info(
                    "Segment {} audio: format={}, sampleRate={}, channels={}, bytes={}",
                    seg.meta.index,
                    seg.audio.format,
                    seg.audio.sampleRate,
                    seg.audio.channels,
                    seg.audio.audioBytes.size
                  )
                  seg.audio.writeAudio(target)
                  val written = target.toFile()
                  log.info(
                    "Wrote segment {} -> exists={}, size={} bytes",
                    seg.meta.index,
                    written.exists(),
                    if (written.exists()) written.length() else -1L
                  )
                  successCount++
                } else {
                  log.warn("Skipping write for segment {} (no audio).", seg.meta.index)
                  failureCount++
                }
              } catch (e: Exception) {
                log.error("Failed to write audio file for segment {}", seg.meta.index, e)
                failureCount++
              }
            }
            val metaPath = root.resolve(metadataFile)
            log.info("Writing manifest to '{}' (absolute: {})", metaPath, metaPath.toAbsolutePath())
            metaPath.toFile().parentFile?.mkdirs()
            val manifestJson = manifest.toJson()
            metaPath.toFile().writeText(manifestJson)
            log.info(
              "Manifest written: exists={}, size={} bytes",
              metaPath.toFile().exists(),
              if (metaPath.toFile().exists()) metaPath.toFile().length() else -1L
            )
            log.info(
              "Audio file generation complete: {} succeeded, {} failed, manifest at '{}'",
              successCount,
              failureCount,
              metaPath
            )
            transcript?.write(
              "\n## Files Saved\n\n- Output directory: `$outputDir`\n- Manifest: `$metadataFile`\n- Successful: $successCount\n- Failed: $failureCount\n".toByteArray()
            )

            task.safeComplete(
              "Generated $successCount/${prepared.size} audio files in $outputDir (manifest: $metadataFile)",
              log
            )
            resultFn(
              "Generated $successCount/${prepared.size} audio files in '$outputDir'. Manifest: '$metadataFile'. Total duration: ${
                "%.2f".format(
                  manifest.totalDurationSeconds
                )
              }s."
            )
          }

          if (orchestrationConfig.autoFix) {
            log.info("autoFix=true, committing audio files immediately")
            commitAction()
          } else {
            log.info("autoFix=false, presenting accept button to user")
            task.add(acceptButtonFooter(task, commitAction).renderMarkdown())
          }

        } catch (e: Exception) {
          task.error(e)
          log.error("Error in GenerateAudioFilesTask async execution", e)
          transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
          resultFn("ERROR: ${e.message}")
        } finally {
          try {
            transcript?.close()
          } catch (e: Exception) {
            log.warn("Failed to close transcript", e)
          }
        }
      }

    } catch (e: Exception) {
      task.error(e)
      log.error("Critical error in GenerateAudioFilesTask", e)
      transcript?.write("\n## Critical Error\n```\n${e.message}\n```\n".toByteArray())
      resultFn("ERROR: ${e.message}")
      try {
        transcript?.close()
      } catch (ce: Exception) {
        log.warn("Failed to close transcript", ce)
      }
    }
  }

  private fun escapeHtml(s: String): String =
    s.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")

  override fun acceptButtonFooter(task: SessionTask, fn: () -> Unit): String {
    return task.hrefLink("Accept Audio Files") {
      log.info("Accept Audio Files button clicked - committing audio files")
      try {
        fn()
        log.info("Audio files committed successfully via accept button")
      } catch (e: Exception) {
        log.error("Error committing audio files via accept button", e)
        throw e
      }
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(GenerateAudioFilesTask::class.java)

    @JvmStatic
    val GenerateAudioFiles = TaskType(
      name = "GenerateAudioFiles",
      category = "Writing",
      taskClass = GenerateAudioFilesTask::class.java,
      executionConfigClass = GenerateAudioFilesTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Generate audio files for each script segment plus a JSON manifest",
      tooltipHtml = """
        Renders an audio script as a set of individual per-segment audio files, plus a JSON
        metadata file describing each segment.
        <ul>
          <li>Splits scripts on <code>---</code> boundaries (or lets a text model produce them)</li>
          <li>Honors <code>[voice:Name]</code> and <code>[silence:Seconds]</code> directives</li>
          <li>Saves one audio file per segment, plus <code>metadata.json</code></li>
          <li>Manifest links each file to its script portion, voice, and duration</li>
        </ul>
      """,
    )
  }
}