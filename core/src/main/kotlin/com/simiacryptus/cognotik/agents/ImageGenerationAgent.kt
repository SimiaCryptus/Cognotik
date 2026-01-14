package com.simiacryptus.cognotik.agents

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.image.ImageClientInterface
import com.simiacryptus.cognotik.image.ImageModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.ChatMessage
import com.simiacryptus.cognotik.models.ModelSchema.ImageGenerationRequest
import com.simiacryptus.cognotik.util.toChatMessage
import com.simiacryptus.cognotik.util.toContentList
import java.awt.image.BufferedImage
import java.io.Serializable
import java.net.URL
import javax.imageio.ImageIO

open class ImageGenerationAgent(
    prompt: String = "Transform the user request into an image generation prompt that the user will like",
    name: String? = null,
    textModel: ChatInterface,
    var imageModel: ImageModel?,
    val imageClient: ImageClientInterface?,
    temperature: Double = 0.3,
    val width: Int = 1024,
    val height: Int = 1024,
) : BaseAgent<List<String>, ImageAndText>(
    prompt = prompt,
    name = name,
    model = textModel,
    temperature = temperature,
) {
    override fun chatMessages(questions: List<String>) = arrayOf(
        ChatMessage(
            role = ModelSchema.Role.system,
            content = prompt.toContentList()
        ),
    ) + questions.map {
        ChatMessage(
            role = ModelSchema.Role.user,
            content = it.toContentList()
        )
    }

    open fun render(
        text: String,
        api: ImageClientInterface,
    ): BufferedImage {
        val data = api.createImage(
            ImageGenerationRequest(
                prompt = text,
                model = imageModel?.modelName ?: throw RuntimeException("No image model configured"),
                size = "${width}x$height"
            )
        ).data
        val first = data.first()
        return when {
            first.url != null -> ImageIO.read(URL(first.url))
            first.b64_json != null -> ImageIO.read(first.b64_json?.decodeBase64()?.inputStream())
            else -> throw RuntimeException("No image data returned")
        }
    }

    override fun respond(input: List<String>, vararg messages: ChatMessage): ImageAndText {
        var text = response(*messages).choices.first().message?.content
            ?: throw RuntimeException("No response")
        val maxPrompt = imageModel?.maxPrompt ?: Int.MAX_VALUE
        while (maxPrompt <= text.length && null != imageClient) {
            text = response(
                *listOf(
                    messages.toList(),
                    listOf(
                        text.toChatMessage(),
                        "Please shorten the description".toChatMessage(),
                    ),
                ).flatten().toTypedArray(),
                model = imageModel!!
            ).choices.first().message?.content ?: throw RuntimeException("No response")
        }
        return ImageAndText(
            text = text,
            image = render(
                text,
                api = this.imageClient ?: throw RuntimeException("No image client configured")
            )
        )
    }

    override fun withModel(model: ChatInterface): ImageGenerationAgent = ImageGenerationAgent(
        prompt = prompt,
        name = name,
        textModel = model,
        imageModel = imageModel,
        imageClient = imageClient,
        temperature = temperature,
        width = width,
        height = height,
    )

}

private fun String?.decodeBase64(): ByteArray? {
    return if (this == null) {
        null
    } else {
        java.util.Base64.getDecoder().decode(this)
    }
}

