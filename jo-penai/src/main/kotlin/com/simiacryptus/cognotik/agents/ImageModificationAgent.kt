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
 * Agent that processes images using multimodal chat models.
 * Takes an input image and text prompt, and returns modified image with description.
 */
open class ImageModificationAgent(
  prompt: String = "Analyze and describe the image based on the user's request",
  name: String? = null,
  model: ChatInterface,
  temperature: Double = 0.3,
) : BaseAgent<ImageAndText, ImageAndText>(
  prompt = prompt,
  name = name,
  model = model,
  temperature = temperature,
) {

  override fun chatMessages(questions: ImageAndText): Array<ChatMessage> {
    val imageBase64 = encodeImageToBase64(questions.image)

    return arrayOf(
      ChatMessage(
        role = ModelSchema.Role.system,
        content = prompt.toContentList()
      ),
      ChatMessage(
        role = ModelSchema.Role.user,
        content = listOf(
          ContentPart(
            type = "text",
            text = questions.text
          ),
          ContentPart(
            type = "image_url",
            image_url = "data:image/png;base64,$imageBase64"
          )
        )
      )
    )
  }

  override fun respond(
    input: ImageAndText,
    vararg messages: ChatMessage
  ): ImageAndText {
    val choices = response(*messages).choices
    return ImageAndText(
      text = choices.first().message?.content ?: throw RuntimeException("No response from model"),
      image = choices.firstOrNull { it.message?.image_url != null }
        ?.let { it.message?.image } ?: input.image
    )
  }

  /**
   * Encodes a BufferedImage to a Base64 string in PNG format
   */
  private fun encodeImageToBase64(image: BufferedImage): String {
    val outputStream = ByteArrayOutputStream()
    ImageIO.write(image, "png", outputStream)
    return Base64.getEncoder().encodeToString(outputStream.toByteArray())
  }

  override fun withModel(model: ChatInterface): ImageModificationAgent = ImageModificationAgent(
    prompt = prompt,
    name = name,
    model = model,
    temperature = temperature,
  )
}
