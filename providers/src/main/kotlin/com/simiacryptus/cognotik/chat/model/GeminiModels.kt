package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.platform.model.ChatMessageModality
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.model.ModelSchema.TokenTypes


/**
 * Catalog of Google Gemini models exposed through the Gemini Developer API.
 *
 * Pricing is expressed as USD per 1,000 tokens, keyed by [TokenTypes]:
 *  - [TokenTypes.Prompt]     : standard (uncached) input tokens
 *  - [TokenTypes.Cached]     : cached input tokens (context caching read price)
 *  - [TokenTypes.Completion] : output tokens
 *  - [TokenTypes.Thinking]   : reasoning tokens (Gemini bills these at the output rate)
 *  - [TokenTypes.Image]      : image output tokens (for native image generation models)
 *
 * Where Google publishes tiered pricing (e.g. prompts <= 200k vs > 200k tokens), the
 * lower/base tier is used, matching the most common usage pattern.
 */
@Suppress("unused")
object GeminiModels {
  // =========================================================
  // Deprecated 1.x models - kept for backward compatibility
  // =========================================================

  @JvmStatic
  val GeminiPro_15 = ChatModel(
    name = "GeminiPro_15",
    modelId = "gemini-1.5-pro",
    maxTotalTokens = 2097152,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.00125,
      TokenTypes.Completion to 0.005,
    ),
    deprecated = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val GeminiPro_10 = ChatModel(
    name = "GeminiPro_10",
    modelId = "gemini-1.0-pro",
    maxTotalTokens = 2097152,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.00025,
      TokenTypes.Completion to 0.0005,
    ),
    deprecated = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiFlash_15 = ChatModel(
    name = "GeminiFlash_15",
    modelId = "gemini-1.5-flash",
    maxTotalTokens = 1048576,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.000075,
      TokenTypes.Completion to 0.0003,
    ),
    deprecated = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiFlash_15_8B = ChatModel(
    name = "GeminiFlash_15_8B",
    modelId = "gemini-1.5-flash-8b",
    maxTotalTokens = 1048576,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0000375,
      TokenTypes.Completion to 0.00015,
    ),
    deprecated = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  // =========================================================
  // Gemini 2.0 models - Shut down
  // =========================================================

  @JvmStatic
  val GeminiFlash_20 = ChatModel(
    name = "GeminiFlash_20",
    modelId = "gemini-2.0-flash",
    maxTotalTokens = 1048576,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0001,      // $0.10/1M text/image/video
      TokenTypes.Cached to 0.000025,    // $0.025/1M cached input
      TokenTypes.Completion to 0.0004,  // $0.40/1M
    ),
    deprecated = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiFlash_20_Lite = ChatModel(
    name = "GeminiFlash_20_Lite",
    modelId = "gemini-2.0-flash-lite",
    maxTotalTokens = 1048576,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.000075,    // $0.075/1M
      TokenTypes.Completion to 0.0003,  // $0.30/1M
    ),
    deprecated = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE, ChatMessageModality.VIDEO),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiFlash_20_Live = ChatModel(
    name = "GeminiFlash_20_Live",
    modelId = "gemini-2.0-flash-live-001",
    maxTotalTokens = 1048576,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0001,
      TokenTypes.Completion to 0.0004,
    ),
    deprecated = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.AUDIO, ChatMessageModality.VIDEO),
    outputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.AUDIO)
  )

  @JvmStatic
  val GeminiFlash_20_Preview_Image_Generation = ChatModel(
    name = "GeminiFlash_20_Preview_Image_Generation",
    modelId = "gemini-2.0-flash-exp-image-generation",
    maxTotalTokens = 1048576,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0001,
      TokenTypes.Completion to 0.0004,
    ),
    deprecated = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE)
  )

  // =========================================================
  // Gemini 2.5 models
  // =========================================================

  @JvmStatic
  val GeminiFlash_25_Image_Generation = ChatModel(
    name = "GeminiFlash_25_Image_Generation",
    modelId = "gemini-2.5-flash-image",
    maxTotalTokens = 1048576,
    maxOutTokens = 32768,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0003,     // $0.30/1M text/image input
      TokenTypes.Completion to 0.0025, // text output priced as 2.5 Flash
      TokenTypes.Image to 0.03,        // $30/1M image output tokens; $0.039/image (1024x1024)
    ),
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE)
  )

  @JvmStatic
  val GeminiPro_25 = ChatModel(
    name = "GeminiPro_25",
    modelId = "gemini-2.5-pro",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.00125,    // $1.25/1M (prompts <= 200k tokens)
      TokenTypes.Completion to 0.01,   // $10.00/1M (prompts <= 200k tokens)
      TokenTypes.Thinking to 0.01,     // thinking tokens billed at output rate
      TokenTypes.Cached to 0.000125,   // $0.125/1M cached input (prompts <= 200k)
    ),
    supportsReasoning = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiFlash_25 = ChatModel(
    name = "GeminiFlash_25",
    modelId = "gemini-2.5-flash",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0003,     // $0.30/1M text/image/video
      TokenTypes.Completion to 0.0025, // $2.50/1M (including thinking tokens)
      TokenTypes.Thinking to 0.0025,
      TokenTypes.Cached to 0.00003,    // $0.03/1M cached input text/image/video
    ),
    supportsReasoning = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiFlash_25_Lite = ChatModel(
    name = "GeminiFlash_25_Lite",
    modelId = "gemini-2.5-flash-lite",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0001,     // $0.10/1M text/image/video
      TokenTypes.Completion to 0.0004, // $0.40/1M (including thinking tokens)
      TokenTypes.Thinking to 0.0004,
      TokenTypes.Cached to 0.00001,    // $0.01/1M cached input text/image/video
    ),
    supportsReasoning = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiFlash_25_Lite_Preview = ChatModel(
    name = "GeminiFlash_25_Lite_Preview",
    modelId = "gemini-2.5-flash-lite-preview-09-2025",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0001,
      TokenTypes.Completion to 0.0004,
      TokenTypes.Thinking to 0.0004,
      TokenTypes.Cached to 0.00001,
    ),
    supportsReasoning = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiFlash_25_Live = ChatModel(
    name = "GeminiFlash_25_Live",
    modelId = "gemini-2.5-flash-native-audio-preview-12-2025",
    maxTotalTokens = 131072,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0005,     // $0.50/1M text input ($3.00/1M audio/video)
      TokenTypes.Completion to 0.002,  // $2.00/1M text output ($12.00/1M audio)
    ),
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.AUDIO, ChatMessageModality.VIDEO),
    outputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.AUDIO)
  )

  @JvmStatic
  val GeminiFlash_25_Preview_TTS = ChatModel(
    name = "GeminiFlash_25_Preview_TTS",
    modelId = "gemini-2.5-flash-preview-tts",
    maxTotalTokens = 8192,
    maxOutTokens = 16384,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0005,     // $0.50/1M text input
      TokenTypes.Completion to 0.01,   // $10.00/1M audio output
    ),
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.AUDIO)
  )

  @JvmStatic
  val GeminiPro_25_Preview_TTS = ChatModel(
    name = "GeminiPro_25_Preview_TTS",
    modelId = "gemini-2.5-pro-preview-tts",
    maxTotalTokens = 8192,
    maxOutTokens = 16384,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.001,      // $1.00/1M text input
      TokenTypes.Completion to 0.02,   // $20.00/1M audio output
    ),
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.AUDIO)
  )

  @JvmStatic
  val GeminiComputerUse_25 = ChatModel(
    name = "GeminiComputerUse_25",
    modelId = "gemini-2.5-computer-use-preview-10-2025",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.00125,    // $1.25/1M (prompts <= 200k tokens)
      TokenTypes.Completion to 0.01,   // $10.00/1M (prompts <= 200k tokens)
    ),
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  // =========================================================
  // Embedding models
  // =========================================================

  @JvmStatic
  val GeminiEmbedding_2 = ChatModel(
    name = "GeminiEmbedding_2",
    modelId = "gemini-embedding-2",
    maxTotalTokens = 8192,
    maxOutTokens = 0,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0002,     // $0.20/1M text input
      TokenTypes.Completion to 0.0,
    ),
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf()
  )

  @JvmStatic
  val GeminiEmbedding_001 = ChatModel(
    name = "GeminiEmbedding_001",
    modelId = "gemini-embedding-001",
    maxTotalTokens = 8192,
    maxOutTokens = 0,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.00015,    // $0.15/1M text input
      TokenTypes.Completion to 0.0,
    ),
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf()
  )

  // =========================================================
  // Gemini 3 models
  // Note: gemini-3-pro-preview was shut down; retained for compatibility.
  // =========================================================

  @JvmStatic
  val GeminiPro_30_Preview = ChatModel(
    name = "GeminiPro_30_Preview",
    modelId = "gemini-3-pro-preview",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.002,
      TokenTypes.Completion to 0.012,
      TokenTypes.Thinking to 0.012,
      TokenTypes.Cached to 0.0002,
    ),
    supportsReasoning = true,
    deprecated = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  /** Nano Banana Pro (GA). */
  @JvmStatic
  val GeminiPro_30_Image = ChatModel(
    name = "GeminiPro_30_Image",
    modelId = "gemini-3-pro-image",
    maxTotalTokens = 65536,
    maxOutTokens = 32768,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.002,      // $2.00/1M text/image input
      TokenTypes.Completion to 0.012,  // $12.00/1M text and thinking output
      TokenTypes.Thinking to 0.012,
      TokenTypes.Image to 0.12,        // $120/1M image output; $0.134/image (1K/2K), $0.24/image (4K)
    ),
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE)
  )

  @JvmStatic
  val GeminiPro_30_Image_Preview = ChatModel(
    name = "GeminiPro_30_Image_Preview",
    modelId = "gemini-3-pro-image-preview",
    maxTotalTokens = 65536,
    maxOutTokens = 32768,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.002,
      TokenTypes.Completion to 0.012,
      TokenTypes.Thinking to 0.012,
      TokenTypes.Image to 0.12,
    ),
    supportsReasoning = true,
    deprecated = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE)
  )

  @JvmStatic
  val GeminiFlash_30_Preview = ChatModel(
    name = "GeminiFlash_30_Preview",
    modelId = "gemini-3-flash-preview",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0005,     // $0.50/1M text/image/video ($1.00/1M audio)
      TokenTypes.Completion to 0.003,  // $3.00/1M (including thinking tokens)
      TokenTypes.Thinking to 0.003,
      TokenTypes.Cached to 0.00005,    // $0.05/1M cached input text/image/video
    ),
    supportsReasoning = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  // =========================================================
  // Gemini 3.1 models
  // =========================================================

  @JvmStatic
  val GeminiPro_31_Preview = ChatModel(
    name = "GeminiPro_31_Preview",
    modelId = "gemini-3.1-pro-preview",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.002,      // $2.00/1M (prompts <= 200k tokens)
      TokenTypes.Completion to 0.012,  // $12.00/1M (prompts <= 200k tokens)
      TokenTypes.Thinking to 0.012,
      TokenTypes.Cached to 0.0002,     // $0.20/1M cached input (prompts <= 200k)
    ),
    supportsReasoning = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiFlash_31_Lite = ChatModel(
    name = "GeminiFlash_31_Lite",
    modelId = "gemini-3.1-flash-lite",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.00025,    // $0.25/1M text/image/video ($0.50/1M audio)
      TokenTypes.Completion to 0.0015, // $1.50/1M (including thinking tokens)
      TokenTypes.Thinking to 0.0015,
      TokenTypes.Cached to 0.000025,   // $0.025/1M cached input text/image/video
    ),
    supportsReasoning = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiFlash_31_Lite_Preview = ChatModel(
    name = "GeminiFlash_31_Lite_Preview",
    modelId = "gemini-3.1-flash-lite-preview",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.00025,
      TokenTypes.Completion to 0.0015,
      TokenTypes.Thinking to 0.0015,
      TokenTypes.Cached to 0.000025,
    ),
    supportsReasoning = true,
    deprecated = true, // Shut down per Gemini deprecations page
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  /** Nano Banana 2 (GA). */
  @JvmStatic
  val GeminiFlash_31_Image = ChatModel(
    name = "GeminiFlash_31_Image",
    modelId = "gemini-3.1-flash-image",
    maxTotalTokens = 1048576,
    maxOutTokens = 32768,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0005,     // $0.50/1M text/image input
      TokenTypes.Completion to 0.003,  // $3.00/1M text and thinking output
      TokenTypes.Thinking to 0.003,
      TokenTypes.Image to 0.06,        // $60/1M image output; $0.067/image (1K), $0.151/image (4K)
    ),
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE)
  )

  @JvmStatic
  val GeminiFlash_31_Image_Preview = ChatModel(
    name = "GeminiFlash_31_Image_Preview",
    modelId = "gemini-3.1-flash-image-preview",
    maxTotalTokens = 1048576,
    maxOutTokens = 32768,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0005,
      TokenTypes.Completion to 0.003,
      TokenTypes.Thinking to 0.003,
      TokenTypes.Image to 0.06,
    ),
    supportsReasoning = true,
    deprecated = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE)
  )

  /** Nano Banana 2 Lite (GA). */
  @JvmStatic
  val GeminiFlash_31_Lite_Image = ChatModel(
    name = "GeminiFlash_31_Lite_Image",
    modelId = "gemini-3.1-flash-lite-image",
    maxTotalTokens = 1048576,
    maxOutTokens = 32768,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.00025,    // $0.25/1M text/image/video input
      TokenTypes.Completion to 0.0015, // $1.50/1M text and thinking output
      TokenTypes.Thinking to 0.0015,
      TokenTypes.Image to 0.03,        // $30/1M image output; $0.0336/image (1K)
    ),
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE)
  )

  @JvmStatic
  val GeminiFlash_31_Live_Preview = ChatModel(
    name = "GeminiFlash_31_Live_Preview",
    modelId = "gemini-3.1-flash-live-preview",
    maxTotalTokens = 1048576,
    maxOutTokens = 32768,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.00075,    // $0.75/1M text ($3.00/1M audio, $1.00/1M image/video)
      TokenTypes.Completion to 0.0045, // $4.50/1M text ($12.00/1M audio)
    ),
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.AUDIO,
      ChatMessageModality.IMAGE,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.AUDIO)
  )

  @JvmStatic
  val GeminiFlash_31_TTS_Preview = ChatModel(
    name = "GeminiFlash_31_TTS_Preview",
    modelId = "gemini-3.1-flash-tts-preview",
    maxTotalTokens = 8192,
    maxOutTokens = 16384,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.001,      // $1.00/1M text input
      TokenTypes.Completion to 0.02,   // $20.00/1M audio output
    ),
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.AUDIO)
  )

  // =========================================================
  // Gemini 3.5 / 3.6 models - Current flagship tier
  // =========================================================

  @JvmStatic
  val GeminiFlash_36 = ChatModel(
    name = "GeminiFlash_36",
    modelId = "gemini-3.6-flash",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0015,     // $1.50/1M
      TokenTypes.Completion to 0.0075, // $7.50/1M (including thinking tokens)
      TokenTypes.Thinking to 0.0075,
      TokenTypes.Cached to 0.00015,    // $0.15/1M cached input
    ),
    supportsReasoning = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  // =========================================================
  // Gemini 3.7 Flash - latest, most capable Flash model
  // =========================================================
  @JvmStatic
  val GeminiFlash_38 = ChatModel(
    name = "GeminiFlash_38",
    modelId = "gemini-3.8-flash",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.00075,    // $0.75/1M through Dec 31, 2026
      TokenTypes.Completion to 0.00375, // $3.75/1M (including thinking tokens)
      TokenTypes.Thinking to 0.00375,
      TokenTypes.Cached to 0.000075,   // $0.075/1M cached input
    ),
    supportsReasoning = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiFlash_37 = ChatModel(
    name = "GeminiFlash_37",
    modelId = "gemini-3.7-flash",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.00075,    // $0.75/1M through Dec 31, 2026
      TokenTypes.Completion to 0.00375, // $3.75/1M (including thinking tokens)
      TokenTypes.Thinking to 0.00375,
      TokenTypes.Cached to 0.000075,   // $0.075/1M cached input
    ),
    supportsReasoning = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiFlash_35 = ChatModel(
    name = "GeminiFlash_35",
    modelId = "gemini-3.5-flash",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0015,     // $1.50/1M
      TokenTypes.Completion to 0.009,  // $9.00/1M (including thinking tokens)
      TokenTypes.Thinking to 0.009,
      TokenTypes.Cached to 0.00015,    // $0.15/1M cached input
    ),
    supportsReasoning = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiFlash_35_Lite = ChatModel(
    name = "GeminiFlash_35_Lite",
    modelId = "gemini-3.5-flash-lite",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0003,     // $0.30/1M text/image/video/audio
      TokenTypes.Completion to 0.0025, // $2.50/1M (including thinking tokens)
      TokenTypes.Thinking to 0.0025,
      TokenTypes.Cached to 0.00003,    // $0.03/1M cached input
    ),
    supportsReasoning = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiLiveTranslate_35_Preview = ChatModel(
    name = "GeminiLiveTranslate_35_Preview",
    modelId = "gemini-3.5-live-translate-preview",
    maxTotalTokens = 131072,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0035,     // $3.50/1M audio input (~$0.0053/min)
      TokenTypes.Completion to 0.021,  // $21.00/1M audio output (~$0.0315/min)
    ),
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.AUDIO),
    outputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.AUDIO)
  )

  @JvmStatic
  val GeminiOmniFlash_Preview = ChatModel(
    name = "GeminiOmniFlash_Preview",
    modelId = "gemini-omni-flash-preview",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0015,     // $1.50/1M text/image/video/audio input
      TokenTypes.Completion to 0.009,  // $9.00/1M text output ($17.50/1M video output)
    ),
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.VIDEO)
  )

  // =========================================================
  // Gemini Omni Flash - stable release
  // =========================================================
  @JvmStatic
  val GeminiOmniFlash = ChatModel(
    name = "GeminiOmniFlash",
    modelId = "gemini-omni-flash",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0015,     // $1.50/1M text/image/video/audio input
      TokenTypes.Completion to 0.009,  // $9.00/1M text output ($17.50/1M video output)
    ),
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.VIDEO)
  )


  // =========================================================
  // Specialized / Tool & Agent models
  // =========================================================

  @JvmStatic
  val GeminiRobotics_2_Preview = ChatModel(
    name = "GeminiRobotics_2_Preview",
    modelId = "gemini-robotics-er-2-preview",
    maxTotalTokens = 1048576,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.002,      // $2.00/1M text/image/video/audio
      TokenTypes.Completion to 0.01,   // $10.00/1M (including thinking tokens)
      TokenTypes.Thinking to 0.01,
      TokenTypes.Cached to 0.0002,     // $0.20/1M cached input
    ),
    supportsReasoning = true,
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiRobotics_2_Streaming_Preview = ChatModel(
    name = "GeminiRobotics_2_Streaming_Preview",
    modelId = "gemini-robotics-er-2-streaming-preview",
    maxTotalTokens = 1048576,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.002,      // $2.00/1M text/image/video/audio
      TokenTypes.Completion to 0.01,   // $10.00/1M
    ),
    inputModalities = setOf(
      ChatMessageModality.TEXT,
      ChatMessageModality.IMAGE,
      ChatMessageModality.AUDIO,
      ChatMessageModality.VIDEO
    ),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiRobotics_16_Preview = ChatModel(
    name = "GeminiRobotics_16_Preview",
    modelId = "gemini-robotics-er-1.6-preview",
    maxTotalTokens = 1048576,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.001,      // $1.00/1M text/image/video ($2.00/1M audio)
      TokenTypes.Completion to 0.005,  // $5.00/1M (including thinking tokens)
      TokenTypes.Thinking to 0.005,
    ),
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE, ChatMessageModality.VIDEO),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiRobotics_15_Preview = ChatModel(
    name = "GeminiRobotics_15_Preview",
    modelId = "gemini-robotics-er-1.5-preview",
    maxTotalTokens = 1048576,
    maxOutTokens = 8192,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.0003,
      TokenTypes.Completion to 0.0025,
    ),
    deprecated = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE, ChatMessageModality.VIDEO),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiDeepResearch = ChatModel(
    name = "GeminiDeepResearch",
    modelId = "deep-research-preview-04-2026",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    // Billed at standard Gemini list rates for underlying model inference
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.00125,
      TokenTypes.Completion to 0.01,
      TokenTypes.Thinking to 0.01,
      TokenTypes.Cached to 0.000125,
    ),
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiDeepResearchMax = ChatModel(
    name = "GeminiDeepResearchMax",
    modelId = "deep-research-max-preview-04-2026",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.00125,
      TokenTypes.Completion to 0.01,
      TokenTypes.Thinking to 0.01,
      TokenTypes.Cached to 0.000125,
    ),
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val GeminiAntigravityAgent = ChatModel(
    name = "GeminiAntigravityAgent",
    modelId = "antigravity-preview-05-2026",
    maxTotalTokens = 1048576,
    maxOutTokens = 65536,
    provider = CoreProviders.Gemini,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.00125,
      TokenTypes.Completion to 0.01,
      TokenTypes.Thinking to 0.01,
      TokenTypes.Cached to 0.000125,
    ),
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  @JvmStatic
  val values = mapOf(
    // Legacy 1.x models
    "GeminiPro_15" to GeminiPro_15,
    "GeminiFlash_15" to GeminiFlash_15,
    "GeminiFlash_15_8B" to GeminiFlash_15_8B,
    "GeminiPro" to GeminiPro_10,
    // 2.0 models (shut down)
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
    "GeminiPro_25_Preview_TTS" to GeminiPro_25_Preview_TTS,
    "GeminiComputerUse_25" to GeminiComputerUse_25,
    "GeminiEmbedding_2" to GeminiEmbedding_2,
    "GeminiEmbedding_001" to GeminiEmbedding_001,
    // 3.0 models
    "GeminiPro_30_Preview" to GeminiPro_30_Preview,
    "GeminiPro_30_Image" to GeminiPro_30_Image,
    "GeminiPro_30_Image_Preview" to GeminiPro_30_Image_Preview,
    "GeminiFlash_30_Preview" to GeminiFlash_30_Preview,
    // 3.1 models
    "GeminiPro_31_Preview" to GeminiPro_31_Preview,
    "GeminiFlash_31_Lite" to GeminiFlash_31_Lite,
    "GeminiFlash_31_Lite_Preview" to GeminiFlash_31_Lite_Preview,
    "GeminiFlash_31_Image" to GeminiFlash_31_Image,
    "GeminiFlash_31_Image_Preview" to GeminiFlash_31_Image_Preview,
    "GeminiFlash_31_Lite_Image" to GeminiFlash_31_Lite_Image,
    "GeminiFlash_31_Live_Preview" to GeminiFlash_31_Live_Preview,
    "GeminiFlash_31_TTS_Preview" to GeminiFlash_31_TTS_Preview,
    // 3.5 / 3.6 models
    "GeminiFlash_38" to GeminiFlash_38,
    "GeminiFlash_37" to GeminiFlash_37,
    "GeminiFlash_36" to GeminiFlash_36,
    "GeminiFlash_35" to GeminiFlash_35,
    "GeminiFlash_35_Lite" to GeminiFlash_35_Lite,
    "GeminiLiveTranslate_35_Preview" to GeminiLiveTranslate_35_Preview,
    "GeminiOmniFlash_Preview" to GeminiOmniFlash_Preview,
    "GeminiOmniFlash" to GeminiOmniFlash,
    // Specialized models
    "GeminiRobotics_2_Preview" to GeminiRobotics_2_Preview,
    "GeminiRobotics_2_Streaming_Preview" to GeminiRobotics_2_Streaming_Preview,
    "GeminiRobotics_16_Preview" to GeminiRobotics_16_Preview,
    "GeminiRobotics_15_Preview" to GeminiRobotics_15_Preview,
    "GeminiDeepResearch" to GeminiDeepResearch,
    "GeminiDeepResearchMax" to GeminiDeepResearchMax,
    "GeminiAntigravityAgent" to GeminiAntigravityAgent,
  )
}