package com.simiacryptus.cognotik.image

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.platform.model.ImageModel

object GeminiImageModels {
  val Imagen3Generate = ImageModel(
    name = "Imagen3Generate",
    modelId = "imagen-3.0-generate-002",
    maxPrompt = 2048,
    provider = CoreProviders.Gemini,
    pricingFunction = { width, height ->
      // Pricing based on number of images generated
      // Standard pricing: $0.04 per image for standard quality
      0.04
    })

  val Imagen4GeneratePreview = ImageModel(
    name = "Imagen4GeneratePreview",
    modelId = "imagen-4.0-generate-preview-06-06",
    maxPrompt = 2048,
    provider = CoreProviders.Gemini,
    pricingFunction = { width, height ->
      // Preview pricing: $0.04 per image
      0.04
    })
  val Imagen4UltraGeneratePreview = ImageModel(
    name = "Imagen4UltraGeneratePreview",
    modelId = "imagen-4.0-ultra-generate-preview-06-06",
    maxPrompt = 2048,
    provider = CoreProviders.Gemini,
    pricingFunction = { width, height ->
      // Ultra preview pricing: $0.08 per image
      0.08
    })
  val Imagen4Generate = ImageModel(
    name = "Imagen4Generate",
    modelId = "imagen-4.0-generate-001",
    maxPrompt = 2048,
    provider = CoreProviders.Gemini,
    pricingFunction = { width, height ->
      // Standard Imagen 4.0 pricing: $0.05 per image
      0.05
    })
  val Imagen4UltraGenerate = ImageModel(
    name = "Imagen4UltraGenerate",
    modelId = "imagen-4.0-ultra-generate-001",
    maxPrompt = 2048,
    provider = CoreProviders.Gemini,
    pricingFunction = { width, height ->
      // Ultra quality pricing: $0.10 per image
      0.10
    })
  val Imagen4Fast = ImageModel(
    name = "Imagen4Fast",
    modelId = "imagen-4.0-fast-generate-001",
    maxPrompt = 2048,
    provider = CoreProviders.Gemini,
    pricingFunction = { width, height ->
      // Fast generation pricing: $0.03 per image
      0.03
    })


  val values: Map<String, ImageModel> = mapOf(
    "Imagen3Generate" to Imagen3Generate,
    "Imagen4GeneratePreview" to Imagen4GeneratePreview,
    "Imagen4UltraGeneratePreview" to Imagen4UltraGeneratePreview,
    "Imagen4Generate" to Imagen4Generate,
    "Imagen4UltraGenerate" to Imagen4UltraGenerate,
    "Imagen4Fast" to Imagen4Fast
  )

  @Suppress("unused")
  fun valueOf(name: String): ImageModel? = values[name]
  fun entries(): Collection<ImageModel> = values.values
}