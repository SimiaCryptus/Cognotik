package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider

@Suppress("unused")
object GeminiModels {
    val GeminiPro_15 = ChatModel(
        name = "GeminiPro_15",
        modelName = "gemini-1.5-pro",
        maxTotalTokens = 2097152,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00125,
        outputTokenPricePerK = 0.005
    )
    
    val GeminiPro_10 = ChatModel(
        name = "GeminiPro_10",
        modelName = "gemini-1.0-pro",
        maxTotalTokens = 2097152,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00025,
        outputTokenPricePerK = 0.0005
    )
    
    val GeminiFlash_15 = ChatModel(
        name = "GeminiFlash_15",
        modelName = "gemini-1.5-flash",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.000075,
        outputTokenPricePerK = 0.0003
    )
    // Deprecated
    val GeminiFlash_15_8B = ChatModel(
        name = "GeminiFlash_15_8B",
        modelName = "gemini-1.5-flash-8b",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.0000375,
        outputTokenPricePerK = 0.00015
    )
    
    val GeminiFlash_20 = ChatModel(
        name = "GeminiFlash_20",
        modelName = "gemini-2.0-flash",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.0001,
        outputTokenPricePerK = 0.0004
    )
    
    val GeminiFlash_20_Lite = ChatModel(
        name = "GeminiFlash_20_Lite",
        modelName = "gemini-2.0-flash-lite",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00005,
        outputTokenPricePerK = 0.0002
    )
    
    val GeminiFlash_20_Live = ChatModel(
        name = "GeminiFlash_20_Live",
        modelName = "gemini-2.0-flash-live-001",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.0001,
        outputTokenPricePerK = 0.0004
    )
    val GeminiFlash_20_Preview_Image_Generation = ChatModel(
        name = "GeminiFlash_20_Preview_Image_Generation",
        modelName = "gemini-2.0-flash-preview-image-generation",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.0001,
        outputTokenPricePerK = 0.0004
    )

    val GeminiPro_25 = ChatModel(
        name = "GeminiPro_25",
        modelName = "gemini-2.5-pro",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.0015,
        outputTokenPricePerK = 0.006
    )
    
    val GeminiFlash_25 = ChatModel(
        name = "GeminiFlash_25",
        modelName = "gemini-2.5-flash",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00015,
        outputTokenPricePerK = 0.0006
    )
    val GeminiFlash_25_Lite = ChatModel(
        name = "GeminiFlash_25_Lite",
        modelName = "gemini-2.5-flash-lite",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00005,
        outputTokenPricePerK = 0.0002
    )
    val GeminiFlash_25_Live = ChatModel(
        name = "GeminiFlash_25_Live",
        modelName = "gemini-live-2.5-flash-preview",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00015,
        outputTokenPricePerK = 0.0006
    )
    val GeminiFlash_25_Preview_Native_Audio_Dialog = ChatModel(
        name = "GeminiFlash_25_Preview_Native_Audio_Dialog",
        modelName = "gemini-2.5-flash-preview-native-audio-dialog",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00015,
        outputTokenPricePerK = 0.0006
    )
    val GeminiFlash_25_Exp_Native_Audio_Thinking_Dialog = ChatModel(
        name = "GeminiFlash_25_Exp_Native_Audio_Thinking_Dialog",
        modelName = "gemini-2.5-flash-exp-native-audio-thinking-dialog",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00015,
        outputTokenPricePerK = 0.0006
    )
    val GeminiFlash_25_Preview_TTS = ChatModel(
        name = "GeminiFlash_25_Preview_TTS",
        modelName = "gemini-2.5-flash-preview-tts",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00015,
        outputTokenPricePerK = 0.0006
    )
    val GeminiPro_25_Preview_TTS = ChatModel(
        name = "GeminiPro_25_Preview_TTS",
        modelName = "gemini-2.5-pro-preview-tts",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.0015,
        outputTokenPricePerK = 0.006
    )

    val values = mapOf(
        "GeminiPro_15" to GeminiPro_15,
        "GeminiFlash_15" to GeminiFlash_15,
        "GeminiFlash_15_8B" to GeminiFlash_15_8B,
        "GeminiPro" to GeminiPro_10,
        "GeminiFlash_20" to GeminiFlash_20,
        "GeminiFlash_20_Lite" to GeminiFlash_20_Lite,
        "GeminiFlash_20_Live" to GeminiFlash_20_Live,
        "GeminiPro_25" to GeminiPro_25,
        "GeminiFlash_25" to GeminiFlash_25,
        "GeminiFlash_25_Lite" to GeminiFlash_25_Lite,
        "GeminiFlash_25_Live" to GeminiFlash_25_Live,
    )
}