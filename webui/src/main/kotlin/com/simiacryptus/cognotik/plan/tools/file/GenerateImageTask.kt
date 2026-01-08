package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.io.BufferedWriter
import java.io.File
import javax.imageio.ImageIO

class GenerateImageTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: GenerateImageTaskExecutionConfigData?
) : AbstractFileTask<GenerateImageTask.GenerateImageTaskExecutionConfigData>(orchestrationConfig, planTask) {

    class GenerateImageTaskExecutionConfigData(
        @Description("The image file to be created (relative path, must end with .png, .jpg, or .jpeg)")
        files: List<String>? = null,
        @Description("Additional files for context (e.g., reference images, style guides)")
        related_files: List<String>? = null,
        @Description("Detailed description of the image to generate including subject, style, composition, colors, mood, and any specific requirements")
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : ValidatedObject, FileTaskExecutionConfig(
        task_type = GenerateImage.name,
        task_description = task_description,
        files = files,
        related_files = related_files,
        task_dependencies = task_dependencies,
        state = state
    ) {
        override fun validate(): String? {
            // Validate that at least one file is specified
            if (files.isNullOrEmpty()) {
                return "GenerateImageTask requires at least one file to be specified"
            }
            if (files.size > 1) {
                return "GenerateImageTask currently supports generating only one image at a time"
            }

            // Validate that the file has a valid image extension
            val imageFile = files.first()
            if (!imageFile.matches(Regex(".*\\.(png|jpg|jpeg)$", RegexOption.IGNORE_CASE))) {
                return "GenerateImageTask file must have .png, .jpg, or .jpeg extension: $imageFile"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
GenerateImage - Create images using AI image generation models
        """.trimIndent()
    }

    override fun toString(relativePath: File): CharSequence? {
        return when (relativePath.name.split('.').last()) {
            "png", "jpg", "jpeg" -> null
            else -> super.toString(relativePath)
        }
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val transcript = task.transcript("GenerateImage")?.bufferedWriter()
        transcript?.write("# Generate Image Task\n\n")
        val tabs = TabbedDisplay(task)

        val imageFiles = executionConfig?.files ?: emptyList()
        if (imageFiles.isEmpty()) {
            resultFn("CONFIGURATION ERROR: No image file specified")
            return
        }
        val inputImageFiles = executionConfig?.related_files?.filter {
            it.matches(Regex(".*\\.(png|jpg|jpeg)$", RegexOption.IGNORE_CASE))
        } ?: emptyList()
        val inputImages = inputImageFiles.mapNotNull { filePath ->
            val file = root.resolve(filePath)
            if (file.toFile().exists()) {
                val image = ImageIO.read(file.toFile())
                ImageAndText(image = image, text = "Reference image: $filePath")
            } else {
                null
            }
        }

        val imageOutputFile = imageFiles.first()
        if (!imageOutputFile.matches(Regex(".*\\.(png|jpg|jpeg)$", RegexOption.IGNORE_CASE))) {
            resultFn("CONFIGURATION ERROR: File must have .png, .jpg, or .jpeg extension: $imageOutputFile")
            return
        }

        val previewTask = tabs.newTask("Preview")
        val promptTask = tabs.newTask("Prompt")

        previewTask.header("Generating Image: $imageOutputFile", level = 2)

        val contextFiles = getInputFileCode()
        val priorCode = getPriorCode(agent.executionState)

        // Build the image generation prompt
        val imagePrompt = buildString {
            append(executionConfig?.task_description ?: "Generate an image")

            if (contextFiles.isNotEmpty()) {
                append("\n\nContext from related files:\n")
                append(contextFiles)
            }

            if (priorCode.isNotEmpty()) {
                append("\n\nPrevious task results:\n")
                append(priorCode)
            }
        }

        promptTask.add(MarkdownUtil.renderMarkdown("### Image Generation Prompt\n\n```\n$imagePrompt\n```", ui = task.ui))
        transcript?.write("## Prompt\n\n$imagePrompt\n\n")
        transcript?.flush()

        try {
            // Generate the image
            previewTask.add("Generating image...", additionalClasses = "text-info")

            // Use the image generation agent
            val imageAgent = ImageProcessingAgent(
                prompt = "Transform the user request into an image",
                name = "ImageGenerator",
                model = orchestrationConfig.defaultImage.getChildClient(task),
            )

            val result = imageAgent.answer(listOf(ImageAndText(imagePrompt)) + inputImages)
            val generatedImage = result.image
                ?: throw RuntimeException("No image generated by the agent")
            val optimizedPrompt = result.text

            promptTask.add(
                MarkdownUtil.renderMarkdown("### Optimized Prompt Used\n\n```\n$optimizedPrompt\n```", ui = task.ui)
            )
            transcript?.write("## Optimized Prompt\n\n$optimizedPrompt\n\n")
            transcript?.flush()

            // Display the generated image
            previewTask.header("Generated Image Preview", level = 3)
            previewTask.image(generatedImage)

            // Save the image
            val outputPath = root.resolve(imageOutputFile)
            outputPath.toFile().parentFile?.mkdirs()

            val format = when {
                imageOutputFile.endsWith(".png", ignoreCase = true) -> "png"
                imageOutputFile.endsWith(".jpg", ignoreCase = true) -> "jpg"
                imageOutputFile.endsWith(".jpeg", ignoreCase = true) -> "jpeg"
                else -> "png"
            }

            ImageIO.write(generatedImage, format, outputPath.toFile())
            val link = task.linkTo(imageOutputFile)

            val summary =
                "Successfully generated and saved image to <a href=\"$link\">$imageOutputFile</a>."
            previewTask.add(summary)
            transcript?.write("## Result\n\n$summary\n\n")
            transcript?.close()

            previewTask.complete()
            resultFn(summary)

        } catch (e: Exception) {
            log.error("Error generating image", e)
            previewTask.error(e)
            transcript?.write("## Error\n\n${e.message}\n")
            transcript?.close()
            resultFn("ERROR: ${e.message}")
        }
    }

    override fun isIgnored(file: File) = when (file.extension.lowercase()) {
        "png", "jpg", "jpeg" -> true
        else -> super.isIgnored(file)
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(GenerateImageTask::class.java)
        val GenerateImage = TaskType(
            "GenerateImage",
            "Writing",
            GenerateImageTask::class.java,
            GenerateImageTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Generate images using AI image generation models",
            """
              Creates images from text descriptions using AI models like DALL-E.
              <ul>
                <li>Generates high-quality images from detailed prompts</li>
                <li>Context-aware generation using related files</li>
                <li>Integration with previous task results</li>
              </ul>
            """,
        )
    }
}