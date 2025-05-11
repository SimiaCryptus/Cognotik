package com.simiacryptus.jopenai.models

object GoogleModels {

    val GeminiPro_15 = ChatModel(
        name = "GeminiPro_15",
        modelName = "models/gemini-1.5-pro",
        maxTotalTokens = 2097152,
        maxOutTokens = 8192,
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.00125,

        outputTokenPricePerK = 0.005

    )
    val GeminiPro_10 = ChatModel(
        name = "GeminiPro_10",
        modelName = "models/gemini-1.0-pro",
        maxTotalTokens = 2097152,
        maxOutTokens = 8192,
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.00025,

        outputTokenPricePerK = 0.0005

    )
    val GeminiFlash_15 = ChatModel(
        name = "GeminiFlash_15",
        modelName = "models/gemini-1.5-flash",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.000075,

        outputTokenPricePerK = 0.0003

    )
    val GeminiFlash_15_8B = ChatModel(
        name = "GeminiFlash_15_8B",
        modelName = "models/gemini-1.5-flash-8b",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.0000375,

        outputTokenPricePerK = 0.00015

    )
    val GeminiFlash_20 = ChatModel(
        name = "GeminiFlash_20",
        modelName = "models/gemini-2.0-flash",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.0001,

        outputTokenPricePerK = 0.0004

    )
    val GeminiFlash_20_Lite = ChatModel(
        name = "GeminiFlash_20_Lite",
        modelName = "models/gemini-2.0-flash-lite",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.00005,

        outputTokenPricePerK = 0.0002

    )
    val GeminiFlash_20_Thinking_Experimental_01_21 = ChatModel(
        name = "GeminiFlash_20_Thinking_Experimental_01_21",
        modelName = "models/gemini-2.0-flash-thinking-exp-01-21",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.0001,

        outputTokenPricePerK = 0.0004

    )
    val GeminiPro_25_Experimental_03_25 = ChatModel(
        name = "GeminiPro_25_Experimental_03_25",
        modelName = "models/gemini-2.5-pro-exp-03-25",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.0015,
        outputTokenPricePerK = 0.006
    )
    val GeminiPro_25_Preview_05_06 = ChatModel(
        name = "GeminiPro_25_Preview_05_06",
        modelName = "models/gemini-2.5-pro-preview-05-06",
        maxTotalTokens = 1048576, // Input token limit
        maxOutTokens = 65536,    // Output token limit
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.0015, // NOTE: Pricing assumed (same as exp-03-25) as not provided in source material
        outputTokenPricePerK = 0.006   // NOTE: Pricing assumed (same as exp-03-25) as not provided in source material
    )
    val GeminiFlash_25_Preview_04_17 = ChatModel(
        name = "GeminiFlash_25_Preview_04_17",
        modelName = "models/gemini-2.5-flash-preview-04-17",
        maxTotalTokens = 1048576, // Input token limit
        maxOutTokens = 65536,    // Output token limit
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.00015, // NOTE: Pricing assumed as not provided in source material
        outputTokenPricePerK = 0.0006  // NOTE: Pricing assumed as not provided in source material
    )
    val values = mapOf(
        "GeminiPro_15" to GeminiPro_15,
        "GeminiFlash_15" to GeminiFlash_15,
        "GeminiFlash_15_8B" to GeminiFlash_15_8B,
        "GeminiPro" to GeminiPro_10,
        "GeminiFlash_20" to GeminiFlash_20,
        "GeminiFlash_20_Lite" to GeminiFlash_20_Lite,
        "GeminiFlash_20_Thinking_Experimental_01_21" to GeminiFlash_20_Thinking_Experimental_01_21,
        "GeminiPro_25_Experimental_03_25" to GeminiPro_25_Experimental_03_25,
        "GeminiPro_25_Preview_05_06" to GeminiPro_25_Preview_05_06,
        "GeminiFlash_25_Preview_04_17" to GeminiFlash_25_Preview_04_17,
    )
}