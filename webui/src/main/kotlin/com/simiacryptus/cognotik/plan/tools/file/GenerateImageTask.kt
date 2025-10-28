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
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.slf4j.Logger
import java.util.*
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

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val imageFiles = executionConfig?.files ?: emptyList()
    if (imageFiles.isEmpty()) {
      resultFn("CONFIGURATION ERROR: No image file specified")
      return
    }

    val imageFile = imageFiles.first()
    if (!imageFile.matches(Regex(".*\\.(png|jpg|jpeg)$", RegexOption.IGNORE_CASE))) {
      resultFn("CONFIGURATION ERROR: File must have .png, .jpg, or .jpeg extension: $imageFile")
      return
    }

    task.add(MarkdownUtil.renderMarkdown("## Generating Image: `$imageFile`", ui = task.ui))

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

    task.add(MarkdownUtil.renderMarkdown("### Image Generation Prompt", ui = task.ui))
    task.add(MarkdownUtil.renderMarkdown("```\n$imagePrompt\n```", ui = task.ui))

    try {
      // Generate the image
      task.add(MarkdownUtil.renderMarkdown("### Generating image...", ui = task.ui))

      // Use the image generation agent
      val imageAgent = ImageProcessingAgent(
        prompt = "Transform the user request into an image",
        name = "ImageGenerator",
        model = orchestrationConfig.imageChatChatter,
      )

      val result = imageAgent.answer(listOf(ImageAndText(imagePrompt)))
      val generatedImage = result.image
      val optimizedPrompt = result.text

      task.add(MarkdownUtil.renderMarkdown("### Optimized Prompt Used", ui = task.ui))
      task.add(MarkdownUtil.renderMarkdown("```\n$optimizedPrompt\n```", ui = task.ui))

      // Display the generated image
      task.add(MarkdownUtil.renderMarkdown("### Generated Image Preview", ui = task.ui))
      val filename = "preview_" + UUID.randomUUID() + ".png"
      ImageIO.write(generatedImage, "png", task.resolve(filename)!!)
      val previewLink = task.linkTo(filename)
      task.add("""<a href="$previewLink" target="_blank"><img src="$previewLink" style="max-width: 600px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);" /></a>""")

      // Save the image
      val outputPath = root.resolve(imageFile)
      outputPath.toFile().parentFile?.mkdirs()

      val format = when {
        imageFile.endsWith(".png", ignoreCase = true) -> "png"
        imageFile.endsWith(".jpg", ignoreCase = true) -> "jpg"
        imageFile.endsWith(".jpeg", ignoreCase = true) -> "jpeg"
        else -> "png"
      }

      ImageIO.write(generatedImage, format, outputPath.toFile())

      val summary = "Successfully generated and saved image to <a href=\"${task.linkTo(imageFile)}\">$imageFile</a>."
      task.complete(summary)
      task.add("""<a href="${task.linkTo(imageFile)}"><img src="${task.linkTo(imageFile)}" /></a> created""")
      resultFn(summary)

    } catch (e: Exception) {
      log.error("Error generating image", e)
      task.error(e)
      resultFn("ERROR: ${e.message}")
    }
  }

  override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
    val acceptLink = ui.hrefLink("Accept and Save Image") {
      fn()
    }
    return """
        |
        |---
        |
        |$acceptLink
        """.trimMargin()
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(GenerateImageTask::class.java)
    val GenerateImage = TaskType(
      "GenerateImage",
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
            """
    )
  }
}