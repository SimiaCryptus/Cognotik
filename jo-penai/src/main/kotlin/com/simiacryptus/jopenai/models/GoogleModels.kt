package com.simiacryptus.jopenai.models

object GoogleModels {

    val GeminiPro_15 = ChatModel(
        name = "GeminiPro_15",
        modelName = "models/gemini-1.5-pro",
        maxTotalTokens = 2097152,
        maxOutTokens = 8192,
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.00125, // Updated based on latest Gemini 1.5 Pro pricing
        outputTokenPricePerK = 0.005   // Updated based on latest Gemini 1.5 Pro pricing
    )
    val GeminiPro_10 = ChatModel(
        name = "GeminiPro_10",
        modelName = "models/gemini-1.0-pro",
        maxTotalTokens = 2097152,
        maxOutTokens = 8192,
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.00025, // Pricing not provided, using a placeholder
        outputTokenPricePerK = 0.0005  // Pricing not provided, using a placeholder
    )
    val GeminiFlash_15 = ChatModel(
        name = "GeminiFlash_15",
        modelName = "models/gemini-1.5-flash",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.000075, // Updated based on latest Gemini 1.5 Flash pricing
        outputTokenPricePerK = 0.0003   // Updated based on latest Gemini 1.5 Flash pricing
    )
    val GeminiFlash_15_8B = ChatModel(
        name = "GeminiFlash_15_8B",
        modelName = "models/gemini-1.5-flash-8b",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.0000375, // Updated based on latest Gemini 1.5 Flash-8B pricing
        outputTokenPricePerK = 0.00015   // Updated based on latest Gemini 1.5 Flash-8B pricing
    )
    val GeminiFlash_20 = ChatModel(
        name = "GeminiFlash_20",
        modelName = "models/gemini-2.0-flash",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.0001, // Pricing not provided, using a placeholder
        outputTokenPricePerK = 0.0004 // Updated based on latest Gemini 2.0 Flash pricing
    )
    val GeminiFlash_20_Lite = ChatModel(
        name = "GeminiFlash_20_Lite",
        modelName = "models/gemini-2.0-flash-lite",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.00005, // Pricing not provided, using a placeholder (likely cheaper than Flash)
        outputTokenPricePerK = 0.0002  // Pricing not provided, using a placeholder (likely cheaper than Flash)
    )
    val GeminiFlash_20_Thinking_Experimental_01_21 = ChatModel(
        name = "GeminiFlash_20_Thinking_Experimental_01_21",
        modelName = "models/gemini-2.0-flash-thinking-exp-01-21",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.0001, // Pricing not provided, using a placeholder
        outputTokenPricePerK = 0.0004 // Matches Gemini 2.0 Flash paid tier output pricing
    )
    val GeminiPro_25_Experimental_03_25 = ChatModel(
        name = "GeminiPro_25_Experimental_03_25",
        modelName = "models/gemini-2.5-pro-exp-03-25",
        maxTotalTokens = 1000000,
        maxOutTokens = 64000,
        provider = APIProvider.Google,
        inputTokenPricePerK = 0.0015, // Pricing not provided, using a placeholder based on similar models
        outputTokenPricePerK = 0.006  // Pricing not provided, using a placeholder based on similar models
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
    )
}