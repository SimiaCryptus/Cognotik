package com.simiacryptus.cognotik.image

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.platform.model.ImageModel

object OpenAIImageModels {
  val DallE2 = ImageModel(
    name = "DallE2",
    modelId = "dall-e-2",
    maxPrompt = 1000,
    provider = CoreProviders.OpenAI,
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
    modelId = "dall-e-3",
    maxPrompt = 1000,
    provider = CoreProviders.OpenAI,
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
    modelId = "dall-e-3",
    maxPrompt = 1000,
    provider = CoreProviders.OpenAI,
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