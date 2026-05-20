package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.CoreProviders

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
        inputTokenPricePerK = 0.00125,
        outputTokenPricePerK = 0.005,
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
        inputTokenPricePerK = 0.00025,
        outputTokenPricePerK = 0.0005,
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
        inputTokenPricePerK = 0.000075,
        outputTokenPricePerK = 0.0003,
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
        inputTokenPricePerK = 0.0000375,
        outputTokenPricePerK = 0.00015,
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
    // Gemini 2.0 models - Deprecated, shutting down June 1, 2026
    // =========================================================


    @JvmStatic
    val GeminiFlash_20 = ChatModel(
        name = "GeminiFlash_20",
        modelId = "gemini-2.0-flash",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = CoreProviders.Gemini,
        inputTokenPricePerK = 0.0001,
        outputTokenPricePerK = 0.0004,
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
        inputTokenPricePerK = 0.000075,  // $0.075/1M
        outputTokenPricePerK = 0.0003,   // $0.30/1M
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
        inputTokenPricePerK = 0.0001,
        outputTokenPricePerK = 0.0004,
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
        inputTokenPricePerK = 0.0001,
        outputTokenPricePerK = 0.0004,
        deprecated = true,
        inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
        outputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE)
    )

    // =========================================================
    // Gemini 2.5 models - Current stable models
    // =========================================================


    @JvmStatic
    val GeminiFlash_25_Image_Generation = ChatModel(
        name = "GeminiFlash_25_Image_Generation",
        modelId = "gemini-2.5-flash-image",
        maxTotalTokens = 1048576,
        maxOutTokens = 32768,
        provider = CoreProviders.Gemini,
        inputTokenPricePerK = 0.0003,   // $0.30/1M text/image input
        outputTokenPricePerK = 0.03,    // $30/1M tokens; $0.039/image (1024x1024)
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
        inputTokenPricePerK = 0.00125,  // $1.25/1M (prompts <= 200k tokens)
        outputTokenPricePerK = 0.01,    // $10.00/1M (prompts <= 200k tokens)
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
        inputTokenPricePerK = 0.0003,   // $0.30/1M text/image/video
        outputTokenPricePerK = 0.0025,  // $2.50/1M (including thinking tokens)
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
        inputTokenPricePerK = 0.0001,   // $0.10/1M text/image/video
        outputTokenPricePerK = 0.0004,  // $0.40/1M (including thinking tokens)
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
        inputTokenPricePerK = 0.0001,
        outputTokenPricePerK = 0.0004,
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
        inputTokenPricePerK = 0.0005,   // $0.50/1M text input
        outputTokenPricePerK = 0.002,   // $2.00/1M text output
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
        inputTokenPricePerK = 0.0005,   // $0.50/1M text input
        outputTokenPricePerK = 0.01,    // $10.00/1M audio output
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
        inputTokenPricePerK = 0.001,    // $1.00/1M text input
        outputTokenPricePerK = 0.02,    // $20.00/1M audio output
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
        inputTokenPricePerK = 0.00125,  // $1.25/1M (prompts <= 200k tokens)
        outputTokenPricePerK = 0.01,    // $10.00/1M (prompts <= 200k tokens)
        inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
        outputModalities = setOf(ChatMessageModality.TEXT)
    )

    // =========================================================
    // Gemini 2.5 Embedding models
    // =========================================================

    @JvmStatic
    val GeminiEmbedding_2 = ChatModel(
        name = "GeminiEmbedding_2",
        modelId = "gemini-embedding-2",
        maxTotalTokens = 8192,
        maxOutTokens = 0,
        provider = CoreProviders.Gemini,
        inputTokenPricePerK = 0.0002,   // $0.20/1M text input
        outputTokenPricePerK = 0.0,
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
        inputTokenPricePerK = 0.00015,  // $0.15/1M text input
        outputTokenPricePerK = 0.0,
        inputModalities = setOf(ChatMessageModality.TEXT),
        outputModalities = setOf()
    )

    // =========================================================
    // Gemini 3 models - Preview
    // Note: Gemini 3 Pro Preview was shut down March 9, 2026.
    // =========================================================

    @JvmStatic
    val GeminiPro_30_Preview = ChatModel(
        name = "GeminiPro_30_Preview",
        modelId = "gemini-3-pro-preview",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = CoreProviders.Gemini,
        inputTokenPricePerK = 0.002,
        outputTokenPricePerK = 0.012,
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
    val GeminiPro_30_Image_Preview = ChatModel(
        name = "GeminiPro_30_Image_Preview",
        modelId = "gemini-3-pro-image-preview",
        maxTotalTokens = 65536,
        maxOutTokens = 32768,
        provider = CoreProviders.Gemini,
        inputTokenPricePerK = 0.002,    // $2.00/1M text/image input
        outputTokenPricePerK = 0.12,    // $120.00/1M image output tokens; $0.134/image (1K/2K), $0.24/image (4K)
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
        inputTokenPricePerK = 0.0005,   // $0.50/1M text/image/video
        outputTokenPricePerK = 0.003,   // $3.00/1M (including thinking tokens)
        inputModalities = setOf(
            ChatMessageModality.TEXT,
            ChatMessageModality.IMAGE,
            ChatMessageModality.AUDIO,
            ChatMessageModality.VIDEO
        ),
        outputModalities = setOf(ChatMessageModality.TEXT)
    )

    // =========================================================
    // Gemini 3.1 models - Current generation
    // =========================================================

    @JvmStatic
    val GeminiPro_31_Preview = ChatModel(
        name = "GeminiPro_31_Preview",
        modelId = "gemini-3.1-pro-preview",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = CoreProviders.Gemini,
        inputTokenPricePerK = 0.002,    // $2.00/1M (prompts <= 200k tokens)
        outputTokenPricePerK = 0.012,   // $12.00/1M (prompts <= 200k tokens)
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
        inputTokenPricePerK = 0.00025,  // $0.25/1M text/image/video
        outputTokenPricePerK = 0.0015,  // $1.50/1M (including thinking tokens)
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
        inputTokenPricePerK = 0.00025,  // $0.25/1M text/image/video
        outputTokenPricePerK = 0.0015,  // $1.50/1M (including thinking tokens)
        inputModalities = setOf(
            ChatMessageModality.TEXT,
            ChatMessageModality.IMAGE,
            ChatMessageModality.AUDIO,
            ChatMessageModality.VIDEO
        ),
        outputModalities = setOf(ChatMessageModality.TEXT)
    )

    @JvmStatic
    val GeminiFlash_31_Image_Preview = ChatModel(
        name = "GeminiFlash_31_Image_Preview",
        modelId = "gemini-3.1-flash-image-preview",
        maxTotalTokens = 1048576,
        maxOutTokens = 32768,
        provider = CoreProviders.Gemini,
        inputTokenPricePerK = 0.0005,   // $0.50/1M text/image input
        outputTokenPricePerK = 0.06,    // $60.00/1M image output tokens
        inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
        outputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE)
    )

    @JvmStatic
    val GeminiFlash_31_Live_Preview = ChatModel(
        name = "GeminiFlash_31_Live_Preview",
        modelId = "gemini-3.1-flash-live-preview",
        maxTotalTokens = 1048576,
        maxOutTokens = 32768,
        provider = CoreProviders.Gemini,
        inputTokenPricePerK = 0.00075,  // $0.75/1M text input
        outputTokenPricePerK = 0.0045,  // $4.50/1M text output
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
        inputTokenPricePerK = 0.001,    // $1.00/1M text input
        outputTokenPricePerK = 0.02,    // $20.00/1M audio output
        inputModalities = setOf(ChatMessageModality.TEXT),
        outputModalities = setOf(ChatMessageModality.AUDIO)
    )

    // =========================================================
    // Gemini 3.5 models - Current flagship
    // =========================================================

    @JvmStatic
    val GeminiFlash_35 = ChatModel(
        name = "GeminiFlash_35",
        modelId = "gemini-3.5-flash",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = CoreProviders.Gemini,
        inputTokenPricePerK = 0.0015,   // $1.50/1M
        outputTokenPricePerK = 0.009,   // $9.00/1M (including thinking tokens)
        inputModalities = setOf(
            ChatMessageModality.TEXT,
            ChatMessageModality.IMAGE,
            ChatMessageModality.AUDIO,
            ChatMessageModality.VIDEO
        ),
        outputModalities = setOf(ChatMessageModality.TEXT)
    )

    // =========================================================
    // Specialized / Tool & Agent models
    // =========================================================

    @JvmStatic
    val GeminiRobotics_16_Preview = ChatModel(
        name = "GeminiRobotics_16_Preview",
        modelId = "gemini-robotics-er-1.6-preview",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = CoreProviders.Gemini,
        inputTokenPricePerK = 0.001,    // $1.00/1M text/image/video
        outputTokenPricePerK = 0.005,   // $5.00/1M (including thinking tokens)
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
        inputTokenPricePerK = 0.0003,
        outputTokenPricePerK = 0.0025,
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
        inputTokenPricePerK = 0.00125,
        outputTokenPricePerK = 0.01,
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
        inputTokenPricePerK = 0.00125,
        outputTokenPricePerK = 0.01,
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
        inputTokenPricePerK = 0.00125,
        outputTokenPricePerK = 0.01,
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
        "GeminiPro_25_Preview_TTS" to GeminiPro_25_Preview_TTS,
        "GeminiComputerUse_25" to GeminiComputerUse_25,
        "GeminiEmbedding_2" to GeminiEmbedding_2,
        "GeminiEmbedding_001" to GeminiEmbedding_001,
        // 3.0 models
        "GeminiPro_30_Preview" to GeminiPro_30_Preview,
        "GeminiPro_30_Image_Preview" to GeminiPro_30_Image_Preview,
        "GeminiFlash_30_Preview" to GeminiFlash_30_Preview,
        // 3.1 models
        "GeminiPro_31_Preview" to GeminiPro_31_Preview,
        "GeminiFlash_31_Lite" to GeminiFlash_31_Lite,
        "GeminiFlash_31_Lite_Preview" to GeminiFlash_31_Lite_Preview,
        "GeminiFlash_31_Image_Preview" to GeminiFlash_31_Image_Preview,
        "GeminiFlash_31_Live_Preview" to GeminiFlash_31_Live_Preview,
        "GeminiFlash_31_TTS_Preview" to GeminiFlash_31_TTS_Preview,
        // 3.5 models
        "GeminiFlash_35" to GeminiFlash_35,
        // Specialized models
        "GeminiRobotics_16_Preview" to GeminiRobotics_16_Preview,
        "GeminiRobotics_15_Preview" to GeminiRobotics_15_Preview,
        "GeminiDeepResearch" to GeminiDeepResearch,
        "GeminiDeepResearchMax" to GeminiDeepResearchMax,
        "GeminiAntigravityAgent" to GeminiAntigravityAgent,
    )
}