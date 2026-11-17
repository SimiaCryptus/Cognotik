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
 *
 * Any [TokenTypes] not listed for a model is resolved by [ChatModel.priceStructure] via the
 * token type's parent chain (falling back to 0.0 if no ancestor is priced), so entries are only
 * required where Google publishes a distinct rate. Thinking and Cached entries are declared
 * explicitly wherever a rate is known.
 */
@Suppress("unused")
object GeminiModels {


  /** Nano Banana Pro (GA). */
  @JvmStatic
  val GeminiPro_30_Image by lazy {
    ChatModel(
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
  }

  @JvmStatic
  val GeminiPro_30_Image_Preview by lazy {
    ChatModel(
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
  }

  /** Nano Banana 2 (GA). */
  @JvmStatic
  val GeminiFlash_31_Image by lazy {
    ChatModel(
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
  }

  @JvmStatic
  val GeminiFlash_31_Image_Preview by lazy {
    ChatModel(
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
  }

  /** Nano Banana 2 Lite (GA). */
  @JvmStatic
  val GeminiFlash_31_Lite_Image by lazy {
    ChatModel(
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
  }

  /** Nano Banana 2.1 (GA). */
  @JvmStatic
  val GeminiNanoBanana_21 by lazy {
    ChatModel(
      name = "GeminiNanoBanana_21",
      modelId = "gemini-nano-banana-2.1",
      maxTotalTokens = 1048576,
      maxOutTokens = 32768,
      provider = CoreProviders.Gemini,
      tokenPricingPerK = mapOf(
        TokenTypes.Prompt to 0.0015,     // $1.50/1M text/image/video
        TokenTypes.Completion to 0.0075, // $7.50/1M text and thinking output
        TokenTypes.Thinking to 0.0075,
        TokenTypes.Image to 0.03,        // $30/1M image output tokens
      ),
      supportsReasoning = true,
      inputModalities = setOf(
        ChatMessageModality.TEXT,
        ChatMessageModality.IMAGE,
        ChatMessageModality.VIDEO
      ),
      outputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE)
    )
  }

  @JvmStatic
  val GeminiFlash_36 by lazy {
    ChatModel(
      name = "GeminiFlash_36",
      modelId = "gemini-3.6-flash",
      maxTotalTokens = 1048576,
      maxOutTokens = 65536,
      provider = CoreProviders.Gemini,
      tokenPricingPerK = mapOf(
        TokenTypes.Prompt to 0.00075,    // $0.75/1M through Dec 31, 2026 ($1.50 starting 2027)
        TokenTypes.Completion to 0.00375, // $3.75/1M through Dec 31, 2026 ($7.50 starting 2027)
        TokenTypes.Thinking to 0.00375,
        TokenTypes.Cached to 0.000075,   // $0.075/1M cached input through Dec 31, 2026 ($0.15 starting 2027)
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
  }

  // =========================================================
  // Gemini 3.8 Flash / Live / TTS - latest Flash tier
  // =========================================================
  @JvmStatic
  val GeminiFlash_38 by lazy {
    ChatModel(
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
  }

  @JvmStatic
  val GeminiLive_38 by lazy {
    ChatModel(
      name = "GeminiLive_38",
      modelId = "gemini-3.8-live",
      maxTotalTokens = 131072,
      maxOutTokens = 8192,
      provider = CoreProviders.Gemini,
      tokenPricingPerK = mapOf(
        TokenTypes.Prompt to 0.00075,    // $0.75/1M text ($3.00/1M audio, $1.00/1M video)
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
  }

  @JvmStatic
  val GeminiLive_38_ExtendedThinking by lazy {
    ChatModel(
      name = "GeminiLive_38_ExtendedThinking",
      modelId = "gemini-3.8-live-extended-thinking",
      maxTotalTokens = 131072,
      maxOutTokens = 8192,
      provider = CoreProviders.Gemini,
      tokenPricingPerK = mapOf(
        TokenTypes.Prompt to 0.00075,    // $0.75/1M text ($3.00/1M audio, $1.00/1M video)
        TokenTypes.Completion to 0.0045, // $4.50/1M text ($12.00/1M audio)
        TokenTypes.Thinking to 0.0045,
      ),
      supportsReasoning = true,
      inputModalities = setOf(
        ChatMessageModality.TEXT,
        ChatMessageModality.AUDIO,
        ChatMessageModality.IMAGE,
        ChatMessageModality.VIDEO
      ),
      outputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.AUDIO)
    )
  }

  @JvmStatic
  val GeminiFlash_38_TTS by lazy {
    ChatModel(
      name = "GeminiFlash_38_TTS",
      modelId = "gemini-3.8-flash-tts",
      maxTotalTokens = 8192,
      maxOutTokens = 16384,
      provider = CoreProviders.Gemini,
      tokenPricingPerK = mapOf(
        TokenTypes.Prompt to 0.0005,     // $0.50/1M text through Dec 31, 2026 ($1.00 starting 2027)
        TokenTypes.Completion to 0.009,  // $9.00/1M audio through Dec 31, 2026 ($18.00 starting 2027)
      ),
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO)
    )
  }

  @JvmStatic
  val GeminiFlash_38_Lite_TTS by lazy {
    ChatModel(
      name = "GeminiFlash_38_Lite_TTS",
      modelId = "gemini-3.8-flash-lite-tts",
      maxTotalTokens = 8192,
      maxOutTokens = 16384,
      provider = CoreProviders.Gemini,
      tokenPricingPerK = mapOf(
        TokenTypes.Prompt to 0.0005,     // $0.50/1M text through Dec 31, 2026 ($1.00 starting 2027)
        TokenTypes.Completion to 0.006,  // $6.00/1M audio through Dec 31, 2026 ($12.00 starting 2027)
      ),
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO)
    )
  }


  // =========================================================
  // Gemini 4 models
  // =========================================================
  @JvmStatic
  val Gemini4_Argon by lazy {
    ChatModel(
      name = "Gemini4_Argon",
      modelId = "gemini-4-argon",
      maxTotalTokens = 1048576,
      maxOutTokens = 1048576,
      provider = CoreProviders.Gemini,
      tokenPricingPerK = mapOf(
        TokenTypes.Prompt to 0.002,      // $2.00/1M through Nov 30, 2026 ($4.00 starting Dec 1, 2026)
        TokenTypes.Completion to 0.01,   // $10.00/1M through Nov 30, 2026 ($20.00 starting Dec 1, 2026)
        TokenTypes.Thinking to 0.01,     // thinking tokens billed at output rate
        TokenTypes.Cached to 0.0001,     // $0.10/1M through Nov 30, 2026 ($0.20 starting Dec 1, 2026)
      ),
      supportsReasoning = true,
      inputModalities = setOf(
        ChatMessageModality.TEXT,
        ChatMessageModality.IMAGE,
        ChatMessageModality.AUDIO,
        ChatMessageModality.VIDEO
      ),
      outputModalities = setOf(ChatMessageModality.TEXT),
      supportsInteractiveTools = true,
    )
  }

  @JvmStatic
  val Gemini_4_Argon by lazy { Gemini4_Argon }

  /** Our strongest reasoning model (complex software engineering, multimodal work, science, cybersecurity). */
  @JvmStatic
  val Gemini4_Carbon by lazy {
    ChatModel(
      name = "Gemini4_Carbon",
      modelId = "gemini-4-carbon",
      maxTotalTokens = 1048576,
      maxOutTokens = 1048576, // 64K default / up to 1M long decodes
      provider = CoreProviders.Gemini,
      tokenPricingPerK = mapOf(
        TokenTypes.Prompt to 0.002,      // $2.00/1M through Nov 30, 2026 ($4.00 starting Dec 1, 2026)
        TokenTypes.Completion to 0.01,   // $10.00/1M through Nov 30, 2026 ($20.00 starting Dec 1, 2026)
        TokenTypes.Thinking to 0.01,
        TokenTypes.Cached to 0.0001,     // $0.10/1M through Nov 30, 2026 ($0.20 starting Dec 1, 2026)
      ),
      supportsReasoning = true,
      inputModalities = setOf(
        ChatMessageModality.TEXT,
        ChatMessageModality.IMAGE,
        ChatMessageModality.AUDIO,
        ChatMessageModality.VIDEO
      ),
      outputModalities = setOf(ChatMessageModality.TEXT),
      supportsInteractiveTools = true,
    )
  }

  @JvmStatic
  val Gemini_4_Carbon by lazy { Gemini4_Carbon }

  /** Fast, efficient Gemini 4 model for everyday coding and agentic workflows. */
  @JvmStatic
  val Gemini4_Hydrogen by lazy {
    ChatModel(
      name = "Gemini4_Hydrogen",
      modelId = "gemini-4-hydrogen",
      maxTotalTokens = 1048576,
      maxOutTokens = 1048576, // 64K default / up to 1M long decodes
      provider = CoreProviders.Gemini,
      tokenPricingPerK = mapOf(
        TokenTypes.Prompt to 0.0015,     // $1.50/1M
        TokenTypes.Completion to 0.0075, // $7.50/1M (including thinking tokens)
        TokenTypes.Thinking to 0.0075,
        TokenTypes.Cached to 0.000075,   // $0.075/1M cached input
      ),
      supportsReasoning = true,
      inputModalities = setOf(
        ChatMessageModality.TEXT,
        ChatMessageModality.IMAGE,
        ChatMessageModality.AUDIO,
        ChatMessageModality.VIDEO
      ),
      outputModalities = setOf(ChatMessageModality.TEXT),
      supportsInteractiveTools = true,
    )
  }

  @JvmStatic
  val Gemini_4_Hydrogen by lazy { Gemini4_Hydrogen }

  /** Fastest, most cost-effective 3.5 model for high-throughput execution. */
  @JvmStatic
  val GeminiFlash_35_Lite by lazy {
    ChatModel(
      name = "GeminiFlash_35_Lite",
      modelId = "gemini-3.5-flash-lite",
      maxTotalTokens = 1048576,
      maxOutTokens = 65536,
      provider = CoreProviders.Gemini,
      tokenPricingPerK = mapOf(
        TokenTypes.Prompt to 0.0003,     // $0.30/1M (text / image / video / audio)
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
  }

  /** Frontier-class performance at a fraction of the cost. */
  @JvmStatic
  val GeminiFlash_31_Lite by lazy {
    ChatModel(
      name = "GeminiFlash_31_Lite",
      modelId = "gemini-3.1-flash-lite",
      maxTotalTokens = 1048576,
      maxOutTokens = 65536,
      provider = CoreProviders.Gemini,
      tokenPricingPerK = mapOf(
        TokenTypes.Prompt to 0.00025,    // $0.25/1M text/image/video ($0.50/1M audio)
        TokenTypes.Completion to 0.0015, // $1.50/1M (including thinking tokens)
        TokenTypes.Thinking to 0.0015,
        TokenTypes.Cached to 0.000025,   // $0.025/1M text/image/video ($0.05/1M audio)
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
  }


  @JvmStatic
  val GeminiDeepResearch by lazy {
    ChatModel(
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
  }

  @JvmStatic
  val GeminiDeepResearchMax by lazy {
    ChatModel(
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
  }

  @JvmStatic
  val GeminiAntigravityAgent by lazy {
    ChatModel(
      name = "GeminiAntigravityAgent",
      modelId = "antigravity-preview-09-2026",
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
  }

  /** Gemini 3.1 Pro Preview (also serves gemini-3.1-pro-preview-customtools). */
  @JvmStatic
  val GeminiPro_31_Preview by lazy {
    ChatModel(
      name = "GeminiPro_31_Preview",
      modelId = "gemini-3.1-pro-preview",
      maxTotalTokens = 1048576,
      maxOutTokens = 65536,
      provider = CoreProviders.Gemini,
      tokenPricingPerK = mapOf(
        TokenTypes.Prompt to 0.002,      // $2.00/1M prompts <= 200k ($4.00 > 200k)
        TokenTypes.Completion to 0.012,  // $12.00/1M prompts <= 200k ($18.00 > 200k)
        TokenTypes.Thinking to 0.012,
        TokenTypes.Cached to 0.0002,     // $0.20/1M prompts <= 200k ($0.40 > 200k)
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
  }

  /** Legacy Flash model. */
  @JvmStatic
  val GeminiFlash_30_Preview by lazy {
    ChatModel(
      name = "GeminiFlash_30_Preview",
      modelId = "gemini-3-flash-preview",
      maxTotalTokens = 1048576,
      maxOutTokens = 65536,
      provider = CoreProviders.Gemini,
      tokenPricingPerK = mapOf(
        TokenTypes.Prompt to 0.0005,     // $0.50/1M text/image/video ($1.00 audio)
        TokenTypes.Completion to 0.003,  // $3.00/1M (including thinking tokens)
        TokenTypes.Thinking to 0.003,
        TokenTypes.Cached to 0.00005,    // $0.05/1M text/image/video ($0.10 audio)
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
  }

  @JvmStatic
  val GeminiFlash_31_TTS_Preview by lazy {
    ChatModel(
      name = "GeminiFlash_31_TTS_Preview",
      modelId = "gemini-3.1-flash-tts-preview",
      maxTotalTokens = 8192,
      maxOutTokens = 16384,
      provider = CoreProviders.Gemini,
      tokenPricingPerK = mapOf(
        TokenTypes.Prompt to 0.001,      // $1.00/1M text
        TokenTypes.Completion to 0.02,   // $20.00/1M audio
      ),
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO)
    )
  }


  val DEFAULT: ChatModel = GeminiFlash_38

  @JvmStatic
  val values by lazy {
    mapOf(
      "GeminiPro_30_Image" to GeminiPro_30_Image,
      "GeminiPro_30_Image_Preview" to GeminiPro_30_Image_Preview,
      "GeminiFlash_31_Image" to GeminiFlash_31_Image,
      "GeminiFlash_31_Image_Preview" to GeminiFlash_31_Image_Preview,
      "GeminiFlash_31_Lite_Image" to GeminiFlash_31_Lite_Image,
      "GeminiNanoBanana_21" to GeminiNanoBanana_21,
      "GeminiNanoBanana_2_1" to GeminiNanoBanana_21,
      "gemini-nano-banana-2.1" to GeminiNanoBanana_21,
      "GeminiFlash_38" to GeminiFlash_38,
      "GeminiLive_38" to GeminiLive_38,
      "GeminiLive_38_ExtendedThinking" to GeminiLive_38_ExtendedThinking,
      "GeminiFlash_38_TTS" to GeminiFlash_38_TTS,
      "GeminiFlash_38_Lite_TTS" to GeminiFlash_38_Lite_TTS,
      "GeminiFlash_36" to GeminiFlash_36,
      "Gemini4_Argon" to Gemini4_Argon,
      "Gemini_4_Argon" to Gemini4_Argon,
      "gemini-4-argon" to Gemini4_Argon,
      "Gemini4_Carbon" to Gemini4_Carbon,
      "Gemini_4_Carbon" to Gemini4_Carbon,
      "gemini-4-carbon" to Gemini4_Carbon,
      "Gemini4_Hydrogen" to Gemini4_Hydrogen,
      "Gemini_4_Hydrogen" to Gemini4_Hydrogen,
      "gemini-4-hydrogen" to Gemini4_Hydrogen,
      "GeminiFlash_35_Lite" to GeminiFlash_35_Lite,
      "gemini-3.5-flash-lite" to GeminiFlash_35_Lite,
      "GeminiFlash_31_Lite" to GeminiFlash_31_Lite,
      "gemini-3.1-flash-lite" to GeminiFlash_31_Lite,
      "GeminiDeepResearch" to GeminiDeepResearch,
      "GeminiDeepResearchMax" to GeminiDeepResearchMax,
      "GeminiAntigravityAgent" to GeminiAntigravityAgent,
      "GeminiPro_31_Preview" to GeminiPro_31_Preview,
      "gemini-3.1-pro-preview" to GeminiPro_31_Preview,
      "GeminiFlash_30_Preview" to GeminiFlash_30_Preview,
      "gemini-3-flash-preview" to GeminiFlash_30_Preview,
      "GeminiFlash_31_TTS_Preview" to GeminiFlash_31_TTS_Preview,
      "gemini-3.1-flash-tts-preview" to GeminiFlash_31_TTS_Preview,
    )
  }
}