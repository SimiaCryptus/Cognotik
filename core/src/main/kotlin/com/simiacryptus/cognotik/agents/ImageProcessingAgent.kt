package com.simiacryptus.cognotik.agents

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.ChatMessage
import com.simiacryptus.cognotik.models.ModelSchema.ContentPart
import com.simiacryptus.cognotik.util.toContentList
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO

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
            image_url = question.image?.let { "data:image/png;base64,${it.encodeImageToBase64()}" },
          )
        )
      }
    )
  )

  override fun respond(
    input: List<ImageAndText>,
    vararg messages: ChatMessage
  ): ImageAndText {
    val choices = response(*messages).choices
    val image = choices.firstOrNull { it.message?.image_url != null }?.let { it.message?.image }
    if (image == null) {
      log.info("No image returned in response, falling back to input image.")
    }
    val text = choices.firstOrNull()?.message?.content ?: ""
    return ImageAndText(text = text, image = image ?: input.map { it.image }.firstOrNull())
  }

  override fun withModel(model: ChatInterface): ImageProcessingAgent = ImageProcessingAgent(
    prompt = prompt,
    name = name,
    model = model,
    temperature = temperature,
  )

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(ImageProcessingAgent::class.java)
  }
}

/**
 * Encodes a BufferedImage to a Base64 string in PNG format
 */
fun BufferedImage.encodeImageToBase64(): String {
  val outputStream = ByteArrayOutputStream()
  ImageIO.write(this, "png", outputStream)
  return Base64.getEncoder().encodeToString(outputStream.toByteArray())
}