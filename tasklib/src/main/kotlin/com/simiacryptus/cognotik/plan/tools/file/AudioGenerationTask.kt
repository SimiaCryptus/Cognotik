package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.AudioAndText
import com.simiacryptus.cognotik.agents.AudioProcessingAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.models.AudioSegment
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

class AudioGenerationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: GenerateAudioTaskExecutionConfigData?
) : AbstractFileTask<AudioGenerationTask.GenerateAudioTaskExecutionConfigData>(orchestrationConfig, planTask) {

    class GenerateAudioTaskExecutionConfigData(
        @Description("Additional files for context (e.g., reference audio clips, scripts, style guides)")
        related_files: List<String>? = null,
        @Description("Detailed description of the audio to generate including content, tone, voice, style, mood, pacing, and any specific requirements")
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : ValidatedObject, FileTaskExecutionConfig(
        task_type = GenerateAudio.name,
        task_description = task_description,
        related_files = related_files,
        task_dependencies = task_dependencies,
        state = state
    ) {
        override fun validate(): String? {
            // Validate that at least one file is specified
            val audioFile = listOf(main_file).firstOrNull()
            if (audioFile.isNullOrEmpty()) {
                return "GenerateAudioTask requires at least one file to be specified"
            }

            // Validate that the file has a valid audio extension
            if (!audioFile.matches(Regex(".*\\.(mp3|wav|ogg|flac|m4a|aac)$", RegexOption.IGNORE_CASE))) {
                return "GenerateAudioTask file must have .mp3, .wav, .ogg, .flac, .m4a, or .aac extension: $audioFile"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
        GenerateAudio - Create high-quality audio using AI generation models.
          * Specify a single output file path (mp3, wav, ogg, flac, m4a, or aac).
          * Provide a detailed task_description covering content, tone, voice, and style.
          * Use related_files to provide audio context, scripts, or style references.
          * Useful for narration, voice-overs, sound effects, and audio assets.
        """.trimIndent()
    }

    override fun formatFileForLLM(relativePath: File): CharSequence {
        return when (relativePath.name.split('.').last().lowercase()) {
            "mp3", "wav", "ogg", "flac", "m4a", "aac" -> ""
            else -> super.formatFileForLLM(relativePath)
        }
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val transcript = task.newUserFileStream(transcriptFile())
        try {
            transcript?.write("# Generate Audio Task\n\n".toByteArray())
            val tabs = TabbedDisplay(task)

            val audioOutputFile = (executionConfig?.let { listOf(it.main_file) } ?: emptyList()).firstOrNull()
            if (audioOutputFile == null) {
                val err = "CONFIGURATION ERROR: No audio file specified"
                task.add(err.renderMarkdown())
                resultFn(err)
                return
            }

            val previewTask = tabs.newTask("Preview")
            val promptTask = tabs.newTask("Prompt")

            task.ui.pool.submit {
                try {
                    log.info("Starting audio generation for $audioOutputFile")
                    previewTask.header("Generating Audio: $audioOutputFile", level = 2)

                    val inputAudioFiles = executionConfig?.related_files?.filter {
                        it.matches(Regex(".*\\.(mp3|wav|ogg|flac|m4a|aac)$", RegexOption.IGNORE_CASE))
                    } ?: emptyList()
                    val inputAudios = inputAudioFiles.mapNotNull { filePath ->
                        val file = root.resolve(filePath)
                        if (file.toFile().exists()) {
                            val format = filePath.substringAfterLast('.').lowercase()
                            val data = Base64.getEncoder().encodeToString(file.toFile().readBytes())
                            AudioAndText(
                                text = "Reference audio: $filePath",
                                audio = AudioSegment(data = data, format = format)
                            )
                        } else {
                            null
                        }
                    }

                    val contextFiles = getInputFileCode()
                    val priorCode = getPriorCode(agent.executionState)

                    val audioPrompt = buildString {
                        append(executionConfig?.task_description ?: "Generate audio")
                        if (contextFiles.isNotEmpty()) {
                            append("\n\nContext from related files:\n")
                            append(contextFiles)
                        }
                        if (priorCode.isNotEmpty()) {
                            append("\n\nPrevious task results:\n")
                            append(priorCode)
                        }
                    }

                    promptTask.add("### Audio Generation Prompt\n\n```\n$audioPrompt\n```".renderMarkdown())
                    transcript?.write("## Prompt\n\n$audioPrompt\n\n".toByteArray())

                    previewTask.add("Generating audio...".renderMarkdown())

                    val audioAgent = AudioProcessingAgent(
                        prompt = "Transform the user request into audio output",
                        name = "AudioGenerator",
                        model = orchestrationConfig.defaultAudio.getChildClient(task),
                        textModel = orchestrationConfig.defaultSmart.getChildClient(task),
                    )

                    val input1 = listOf(AudioAndText(text = audioPrompt)) + inputAudios
                    val result = audioAgent.respond(input = input1, *audioAgent.chatMessages(input1))
                    val generatedAudio = result.audio
                    if (generatedAudio == null) {
                      throw RuntimeException("No audio generated by the agent")
                    }
                    val responseText = result.text

                    if (responseText.isNotBlank()) {
                        promptTask.add("### Agent Response Text\n\n```\n$responseText\n```".renderMarkdown())
                        transcript?.write("## Agent Response Text\n\n$responseText\n\n".toByteArray())
                    }

                    previewTask.header("Generated Audio Preview", level = 3)

                    val saveAction = {
                        val outputPath = root.resolve(audioOutputFile)
                        outputPath.toFile().parentFile?.mkdirs()

                        generatedAudio.writeAudio(outputPath)

                        val link = task.linkTo(audioOutputFile)
                        val summary =
                            "Successfully generated and saved audio to <a href=\"$link\">$audioOutputFile</a>."

                        previewTask.add(summary.renderMarkdown())
                        transcript?.write("## Result\n\n$summary\n\n".toByteArray())
                        log.info("Audio saved successfully to $audioOutputFile")

                        previewTask.complete()
                        resultFn(summary)
                    }

                    if (orchestrationConfig.autoFix) {
                        saveAction()
                    } else {
                        previewTask.add("Audio generated. Click below to save to workspace.".renderMarkdown())
                        previewTask.add(acceptButtonFooter(task.ui) {
                            saveAction()
                        })
                    }

                } catch (e: Exception) {
                    // Triple Log Rule
                    log.error("Error in AudioGenerationTask for $audioOutputFile", e)
                    previewTask.error(e)
                    transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
                    resultFn("ERROR: ${e.message}")
                } finally {
                    transcript?.close()
                }
            }
        } catch (e: Exception) {
            log.error("Failed to schedule AudioGenerationTask", e)
            task.error(e)
            transcript?.close()
            resultFn("ERROR: ${e.message}")
        }
    }

    override fun isIgnored(file: File) = when (file.extension.lowercase()) {
        "mp3", "wav", "ogg", "flac", "m4a", "aac" -> true
        else -> super.isIgnored(file)
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(AudioGenerationTask::class.java)

        @JvmStatic
        val GenerateAudio = TaskType(
            name = "GenerateAudio",
            category = "Writing",
            taskClass = AudioGenerationTask::class.java,
            executionConfigClass = GenerateAudioTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Generate audio using AI audio generation models",
            tooltipHtml = """
                        Creates audio from text descriptions using AI models.
                        <ul>
                          <li>Generates high-quality audio from detailed prompts</li>
                          <li>Context-aware generation using related audio files</li>
                          <li>Integration with previous task results</li>
                          <li>Supports multiple formats: mp3, wav, ogg, flac, m4a, aac</li>
                        </ul>
                      """,
        )
    }
}

