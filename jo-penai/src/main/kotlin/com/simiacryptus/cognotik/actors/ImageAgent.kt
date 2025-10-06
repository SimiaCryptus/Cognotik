package com.simiacryptus.cognotik.actors

import com.simiacryptus.cognotik.OpenAIClient
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.image.ImageModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.ChatMessage
import com.simiacryptus.cognotik.models.ModelSchema.ImageGenerationRequest
import com.simiacryptus.cognotik.image.ImageModels
import com.simiacryptus.cognotik.util.toChatMessage
import com.simiacryptus.cognotik.util.toContentList
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO

open class ImageAgent(
    prompt: String = "Transform the user request into an image generation prompt that the user will like",
    name: String? = null,
    textModel: ChatInterface,
    val imageModel: ImageModel = ImageModels.DallE2,
    temperature: Double = 0.3,
    val width: Int = 1024,
    val height: Int = 1024,
    var openAI: OpenAIClient? = null,
) : BaseAgent<List<String>, ImageResponse>(
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

    inner class ImageResponseImpl(
        override val text: String,
        private val api: OpenAIClient
    ) : ImageResponse {
        private val _image: BufferedImage by lazy { render(text, api) }
        override val image: BufferedImage get() = _image
    }

    open fun render(
        text: String,
        api: OpenAIClient,
    ): BufferedImage {
        val url = (api as OpenAIClient).createImage(
            ImageGenerationRequest(
                prompt = text,
                model = imageModel.modelName,
                size = "${width}x$height"
            )
        ).data.first().url
        return ImageIO.read(URL(url))
    }

    override fun respond(input: List<String>, vararg messages: ChatMessage): ImageResponse {
        var text = response(*messages).choices.first().message?.content
            ?: throw RuntimeException("No response")
        while (imageModel.maxPrompt <= text.length && null != openAI) {
            text = response(
                *listOf(
                    messages.toList(),
                    listOf(
                        text.toChatMessage(),
                        "Please shorten the description".toChatMessage(),
                    ),
                ).flatten().toTypedArray(),
                model = imageModel
            ).choices.first().message?.content ?: throw RuntimeException("No response")
        }
        return ImageResponseImpl(text, api = this.openAI ?: throw RuntimeException("No API"))
    }

    override fun withModel(model: ChatInterface): ImageAgent = ImageAgent(
        prompt = prompt,
        name = name,
        textModel = model,
        imageModel = imageModel,
        temperature = temperature,
        width = width,
        height = height,
        openAI = openAI
    )

    fun setImageAPI(openAI: OpenAIClient): ImageAgent {
        this.openAI = openAI
        return this
    }

}

