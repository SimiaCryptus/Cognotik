package com.simiacryptus.cognotik.agents

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.ChatMessage
import com.simiacryptus.cognotik.models.ModelSchema.ChatRequest
import com.simiacryptus.cognotik.models.ModelSchema.ContentPart
import com.simiacryptus.cognotik.util.toContentList

/**
 * Agent that processes text/images input and generates text/images output based on the prompt.
 * Can be used for image generation, image captioning, and image editing tasks.
 */
open class ImageProcessingAgent(
  prompt: String = "Analyze and describe the image based on the user's request",
  name: String? = null,
  model: ChatInterface,
  temperature: Double = 0.3,
) : BaseAgent<List<ImageAndText>, ImageAndText>(
  prompt = prompt,
  name = name,
  model = model,
  temperature = temperature,
) {

  override fun chatMessages(questions: List<ImageAndText>) = arrayOf(
    ChatMessage(
      role = ModelSchema.Role.system,
      content = prompt.toContentList()
    ),
    ChatMessage(
      role = ModelSchema.Role.user,
      content = questions.flatMap { question ->
        listOf(
          ContentPart(
            text = question.text,
          ).apply {
            image = question.image
          }
        )
      }
    )
  )

  override fun respond(
    input: List<ImageAndText>,
    vararg messages: ChatMessage
  ): ImageAndText {
    val choices = model.chat(
      ChatRequest(
        model = model.modelType.modelId,
        messages = messages.toList(),
        temperature = model.temperature,
        audio = model.audio,
      )
    ).choices
    val image = choices.firstOrNull { it.message?.image_url != null }?.let { it.message?.image }
    if (image == null) {
      log.info("No image returned in response, falling back to input image.")
    }
    val text = choices.firstOrNull()?.message?.content ?: ""
    return ImageAndText(text = text, image = image ?: input.map { it.image }.firstOrNull())
  }


  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(ImageProcessingAgent::class.java)
  }
}
