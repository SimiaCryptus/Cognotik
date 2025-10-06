package com.simiacryptus.cognotik.image

import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.Usage
import com.simiacryptus.cognotik.util.LoggerFactory

class ImageModel(
    val name: String,
    override val modelName: String,
    val maxPrompt: Int,
    provider: APIProvider,
    val quality: String = "standard",
    val pricingFunction: (width: Int, height: Int) -> Double
) : LLMModel(
    modelName = modelName, provider = provider, maxTotalTokens = maxPrompt, maxOutTokens = 0
) {
    override fun pricing(usage: Usage): Double {
        return usage.cost ?: 0.0
    }

    fun pricing(width: Int, height: Int): Double = pricingFunction(width, height)

    fun generate(
        prompt: String, width: Int = 1024, height: Int = 1024, count: Int = 1, quality: String = this.quality
    ): ModelSchema.ImageGenerationResponse {
        return ImageClient.generate(
            request = ModelSchema.ImageGenerationRequest(
                prompt = prompt, model = modelName, n = count, size = "${width}x${height}", quality = quality
            ), model = this, apiKey = TODO()
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ImageModel::class.java)
    }
}

object ImageModels {
    val DallE2 = ImageModel(
        name = "DallE2",
        modelName = "dall-e-2",
        maxPrompt = 1000,
        provider = APIProvider.Companion.OpenAI,
        pricingFunction = { width, height ->
            when {
                width == 1024 && height == 1024 -> 0.02
                width == 512 && height == 512 -> 0.018
                width == 256 && height == 256 -> 0.016
                else -> throw IllegalArgumentException("Unsupported image size: $width x $height")
            }
        })

    val DallE3 = ImageModel(
        name = "DallE3",
        modelName = "dall-e-3",
        maxPrompt = 1000,
        provider = APIProvider.Companion.OpenAI,
        pricingFunction = { width, height ->
            when {
                width == 1024 && height == 1024 -> 0.04
                width == 1024 && height == 1792 -> 0.08
                width == 1792 && height == 1024 -> 0.08
                else -> throw IllegalArgumentException("Unsupported image size: $width x $height")
            }
        })

    val DallE3_HD = ImageModel(
        name = "DallE3_HD",
        modelName = "dall-e-3",
        maxPrompt = 1000,
        provider = APIProvider.Companion.OpenAI,
        quality = "hd",
        pricingFunction = { width, height ->
            when {
                width == 1024 && height == 1024 -> 0.08
                width == 1024 && height == 1792 -> 0.12
                width == 1792 && height == 1024 -> 0.12
                else -> throw IllegalArgumentException("Unsupported image size: $width x $height")
            }
        })

    val values: Map<String, ImageModel> = mapOf(
        "DallE2" to DallE2, "DallE3" to DallE3, "DallE3_HD" to DallE3_HD
    )

    fun valueOf(name: String): ImageModel? = values[name]
    fun entries(): Collection<ImageModel> = values.values
}