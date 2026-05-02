package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.CoreProviders

@Suppress("unused")
object GeminiModels {
  // Deprecated 1.x models - kept for backward compatibility

  @JvmStatic
  val GeminiPro_15 = ChatModel(
    name = "GeminiPro_15",
    modelId = "gemini-1.5-pro",
    maxTotalTokens = 2097152,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.00125,
    outputTokenPricePerK = 0.005,
    deprecated = true,
  )

  @JvmStatic
  val GeminiPro_10 = ChatModel(
    name = "GeminiPro_10",
    modelId = "gemini-1.0-pro",
    maxTotalTokens = 2097152,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.00025,
    outputTokenPricePerK = 0.0005,
    deprecated = true
  )

  @JvmStatic
  val GeminiFlash_15 = ChatModel(
    name = "GeminiFlash_15",
    modelId = "gemini-1.5-flash",
    maxTotalTokens = 1048576,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.000075,
    outputTokenPricePerK = 0.0003,
    deprecated = true
  )

  @JvmStatic
  val GeminiFlash_15_8B = ChatModel(
    name = "GeminiFlash_15_8B",
    modelId = "gemini-1.5-flash-8b",
    maxTotalTokens = 1048576,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.0000375,
    outputTokenPricePerK = 0.00015,
    deprecated = true
  )
  // Gemini 2.0 models - Deprecated, shutting down June 1, 2026


  @JvmStatic
  val GeminiFlash_20 = ChatModel(
    name = "GeminiFlash_20",
    modelId = "gemini-2.0-flash",
    maxTotalTokens = 1048576,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.0001,
    outputTokenPricePerK = 0.0004,
    deprecated = true
  )

  @JvmStatic
  val GeminiFlash_20_Lite = ChatModel(
    name = "GeminiFlash_20_Lite",
    modelId = "gemini-2.0-flash-lite",
    maxTotalTokens = 1048576,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.000075,
    outputTokenPricePerK = 0.0003,
    deprecated = true
  )

  @JvmStatic
  val GeminiFlash_20_Live = ChatModel(
    name = "GeminiFlash_20_Live",
    modelId = "gemini-2.0-flash-live-001",
    maxTotalTokens = 1048576,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.0001,
    outputTokenPricePerK = 0.0004,
    deprecated = true
  )

  @JvmStatic
  val GeminiFlash_20_Preview_Image_Generation = ChatModel(
    name = "GeminiFlash_20_Preview_Image_Generation",
    modelId = "gemini-2.0-flash-exp-image-generation",
    maxTotalTokens = 1048576,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.0001,
    outputTokenPricePerK = 0.0004,
    deprecated = true
  )
  // Gemini 2.5 models


  @JvmStatic
  val GeminiFlash_25_Image_Generation = ChatModel(
    name = "GeminiFlash_25_Image_Generation",
    modelId = "gemini-2.5-flash-image",
    maxTotalTokens = 1048576,
    maxOutTokens = 32768,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.0003,
    outputTokenPricePerK = 0.03,
    deprecated = true
  )

  @JvmStatic
  val GeminiPro_25 = ChatModel(
    name = "GeminiPro_25",
    modelId = "gemini-2.5-pro",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.00125,
    outputTokenPricePerK = 0.01,
    deprecated = true
  )

  @JvmStatic
  val GeminiFlash_25 = ChatModel(
    name = "GeminiFlash_25",
    modelId = "gemini-2.5-flash",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.0003,
    outputTokenPricePerK = 0.0025,
    deprecated = true
  )

  @JvmStatic
  val GeminiFlash_25_Lite = ChatModel(
    name = "GeminiFlash_25_Lite",
    modelId = "gemini-2.5-flash-lite",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.0001,
    outputTokenPricePerK = 0.0004,
    deprecated = true
  )

  @JvmStatic
  val GeminiFlash_25_Lite_Preview = ChatModel(
    name = "GeminiFlash_25_Lite_Preview",
    modelId = "gemini-2.5-flash-lite-preview-09-2025",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.0001,
    outputTokenPricePerK = 0.0004,
    deprecated = true
  )

  @JvmStatic
  val GeminiFlash_25_Live = ChatModel(
    name = "GeminiFlash_25_Live",
    modelId = "gemini-2.5-flash-native-audio-preview-12-2025",
    maxTotalTokens = 131072,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.0005,
    outputTokenPricePerK = 0.002,
    deprecated = true
  )

  @JvmStatic
  val GeminiFlash_25_Preview_TTS = ChatModel(
    name = "GeminiFlash_25_Preview_TTS",
    modelId = "gemini-2.5-flash-preview-tts",
    maxTotalTokens = 8192,
    maxOutTokens = 16384,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.0005,
    outputTokenPricePerK = 0.01,
    deprecated = false
  )

  @JvmStatic
  val GeminiFlash_25_Lite_Preview_TTS = ChatModel(
    name = "GeminiFlash_25_Lite_Preview_TTS",
    modelId = "gemini-2.5-flash-lite-preview-tts",
    maxTotalTokens = 8192,
    maxOutTokens = 16384,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.0001,
    outputTokenPricePerK = 0.0004,
    deprecated = false
  )


  @JvmStatic
  val GeminiPro_25_Preview_TTS = ChatModel(
    name = "GeminiPro_25_Preview_TTS",
    modelId = "gemini-2.5-pro-preview-tts",
    maxTotalTokens = 8192,
    maxOutTokens = 16384,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.001,
    outputTokenPricePerK = 0.02,
    deprecated = false
  )

  @JvmStatic
  val GeminiComputerUse_25 = ChatModel(
    name = "GeminiComputerUse_25",
    modelId = "gemini-2.5-computer-use-preview-10-2025",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.00125,
    outputTokenPricePerK = 0.01,
    deprecated = true
  )

  // Gemini 3 models
  // Deprecated - shut down March 9, 2026. Migrate to Gemini 3.1 Pro Preview.
  @JvmStatic
  val GeminiPro_30_Preview = ChatModel(
    name = "GeminiPro_30_Preview",
    modelId = "gemini-3-pro-preview",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.002,
    outputTokenPricePerK = 0.012
  )

  @JvmStatic
  val GeminiPro_30_Image_Preview = ChatModel(
    name = "GeminiPro_30_Image_Preview",
    modelId = "gemini-3-pro-image-preview",
    maxTotalTokens = 65536,
    maxOutTokens = 32768,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.002,
    outputTokenPricePerK = 0.12
  )

  @JvmStatic
  val GeminiFlash_30_Preview = ChatModel(
    name = "GeminiFlash_30_Preview",
    modelId = "gemini-3-flash-preview",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.0005,
    outputTokenPricePerK = 0.003
  )
  // Gemini 3.1 models

  @JvmStatic
  val GeminiPro_31_Preview = ChatModel(
    name = "GeminiPro_31_Preview",
    modelId = "gemini-3.1-pro-preview",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.002,
    outputTokenPricePerK = 0.012
  )

  @JvmStatic
  val GeminiFlash_31_Lite_Preview = ChatModel(
    name = "GeminiFlash_31_Lite_Preview",
    modelId = "gemini-3.1-flash-lite-preview",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.00025,
    outputTokenPricePerK = 0.0015
  )

  @JvmStatic
  val GeminiFlash_31_Image_Preview = ChatModel(
    name = "GeminiFlash_31_Image_Preview",
    modelId = "gemini-3.1-flash-image-preview",
    maxTotalTokens = 1048576,
    maxOutTokens = 32768,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.0005,
    outputTokenPricePerK = 0.06
  )

  // Specialized models

  @JvmStatic
  val GeminiRobotics_15_Preview = ChatModel(
    name = "GeminiRobotics_15_Preview",
    modelId = "gemini-robotics-er-1.5-preview",
    maxTotalTokens = 1048576,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    inputTokenPricePerK = 0.0003,
    outputTokenPricePerK = 0.0025,
    deprecated = true
  )


  @JvmStatic
  val values = mapOf(
    // Legacy 1.x models
    "GeminiPro_15" to GeminiPro_15,
    "GeminiFlash_15" to GeminiFlash_15,
    "GeminiFlash_15_8B" to GeminiFlash_15_8B,
    "GeminiPro" to GeminiPro_10,
    // 2.0 models (deprecated)
    "GeminiFlash_20" to GeminiFlash_20,
    "GeminiFlash_20_Lite" to GeminiFlash_20_Lite,
    "GeminiFlash_20_Live" to GeminiFlash_20_Live,
    "GeminiFlash_20_Preview_Image_Generation" to GeminiFlash_20_Preview_Image_Generation,
    // 2.5 models
    "GeminiPro_25" to GeminiPro_25,
    "GeminiFlash_25" to GeminiFlash_25,
    "GeminiFlash_25_Lite" to GeminiFlash_25_Lite,
    "GeminiFlash_25_Lite_Preview" to GeminiFlash_25_Lite_Preview,
    "GeminiFlash_25_Live" to GeminiFlash_25_Live,
    "GeminiFlash_25_Image_Generation" to GeminiFlash_25_Image_Generation,
    "GeminiFlash_25_Preview_TTS" to GeminiFlash_25_Preview_TTS,
    "GeminiFlash_25_Lite_Preview_TTS" to GeminiFlash_25_Lite_Preview_TTS,
    "GeminiPro_25_Preview_TTS" to GeminiPro_25_Preview_TTS,
    "GeminiComputerUse_25" to GeminiComputerUse_25,
    // 3.0 models
    "GeminiPro_30_Preview" to GeminiPro_30_Preview,
    "GeminiPro_30_Image_Preview" to GeminiPro_30_Image_Preview,
    "GeminiFlash_30_Preview" to GeminiFlash_30_Preview,
    // 3.1 models
    "GeminiPro_31_Preview" to GeminiPro_31_Preview,
    "GeminiFlash_31_Lite_Preview" to GeminiFlash_31_Lite_Preview,
    "GeminiFlash_31_Image_Preview" to GeminiFlash_31_Image_Preview,
    // Specialized models
    "GeminiRobotics_15_Preview" to GeminiRobotics_15_Preview,
  )
}