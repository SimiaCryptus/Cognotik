package com.simiacryptus.jopenai.models

object OpenAIModels {
    val GPT4Turbo = ChatModel(
        name = "GPT4Turbo",
        modelName = "gpt-4-turbo",
        maxTotalTokens = 128000,
        provider = APIProvider.OpenAI,
        inputTokenPricePerK = 0.010,
        outputTokenPricePerK = 0.030
    )
    
    val GPT4o = ChatModel(
        name = "GPT4o",
        modelName = "gpt-4o",
        maxTotalTokens = 128000,
        provider = APIProvider.OpenAI,
        inputTokenPricePerK = 0.0025,
        outputTokenPricePerK = 0.010
    )
    
    val GPT45 = ChatModel(
        name = "GPT45",
        modelName = "gpt-4.5-preview-2025-02-27",
        maxTotalTokens = 128000,
        provider = APIProvider.OpenAI,
        inputTokenPricePerK = 0.075,
        outputTokenPricePerK = 0.150
    )

    val GPT4oMini = ChatModel(
        name = "GPT4oMini",
        modelName = "gpt-4o-mini",
        maxTotalTokens = 128000,
        provider = APIProvider.OpenAI,
        inputTokenPricePerK = 0.00015,
        outputTokenPricePerK = 0.00060
    )

    val O1Preview = ChatModel(
        name = "O1Preview",
        modelName = "o1-preview",
        maxTotalTokens = 128 * 1024,
        provider = APIProvider.OpenAI,
        inputTokenPricePerK = 0.0005, 
        outputTokenPricePerK = 0.0015,
        hasTemperature = false,
        hasReasoningEffort = true,
    )

    val O1 = ChatModel(
        name = "O1",
        modelName = "o1",
        maxTotalTokens = 128 * 1024,
        provider = APIProvider.OpenAI,
        inputTokenPricePerK = 0.015, 
        outputTokenPricePerK = 0.060,
        hasTemperature = false,
        hasReasoningEffort = true,
    )

    val O1Mini = ChatModel(
        name = "O1Mini",
        modelName = "o1-mini",
        maxTotalTokens = 128 * 1024,
        provider = APIProvider.OpenAI,
        inputTokenPricePerK = 0.00110, 
        outputTokenPricePerK = 0.00440,
        hasTemperature = false,
        hasReasoningEffort = false,
    )

    val O3Mini = ChatModel(
        name = "O3Mini",
        modelName = "o3-mini",
        maxTotalTokens = 128 * 1024,
        provider = APIProvider.OpenAI,
        inputTokenPricePerK = 0.00110, 
        outputTokenPricePerK = 0.00440,
        hasTemperature = false,
        hasReasoningEffort = true,
    )
    val O4Mini = ChatModel(
        name = "O4Mini",
        modelName = "o4-mini",
        maxTotalTokens = 200000,
        provider = APIProvider.OpenAI,
        inputTokenPricePerK = 0.00110,
        outputTokenPricePerK = 0.00440,
        hasTemperature = false,
        hasReasoningEffort = true,
    )
    val O3 = ChatModel(
        name = "O3",
        modelName = "o3",
        maxTotalTokens = 200000,
        provider = APIProvider.OpenAI,
        inputTokenPricePerK = 0.010,
        outputTokenPricePerK = 0.040,
        hasTemperature = false,
        hasReasoningEffort = true,
    )
    

    val GPT41 = ChatModel(
        name = "GPT 4.1",
        modelName = "gpt-4.1-2025-04-14",
        maxTotalTokens = 1048576,
        provider = APIProvider.OpenAI,
        inputTokenPricePerK = 2.00 / 1000,
        outputTokenPricePerK = 8.00 / 1000,
        hasTemperature = true,
        hasReasoningEffort = false,
    )

    val GPT41Mini = ChatModel(
        name = "GPT 4.1 Mini",
        modelName = "gpt-4.1-mini-2025-04-14",
        maxTotalTokens = 1048576,
        provider = APIProvider.OpenAI,
        inputTokenPricePerK = 0.40 / 1000,
        outputTokenPricePerK = 1.60 / 1000,
        hasTemperature = true,
        hasReasoningEffort = false,
    )

    val GPT41Nano = ChatModel(
        name = "GPT 4.1 Nano",
        modelName = "gpt-4.1-nano-2025-04-14",
        maxTotalTokens = 1048576,
        provider = APIProvider.OpenAI,
        inputTokenPricePerK = 0.10 / 1000,
        outputTokenPricePerK = 0.40 / 1000,
        hasTemperature = true,
        hasReasoningEffort = false,
    )
    
    val values = mapOf(
        "GPT4Turbo" to GPT4Turbo,
        "GPT4o" to GPT4o,
        "GPT4oMini" to GPT4oMini,
        "O1Preview" to O1Preview,
        "O1Mini" to O1Mini,
        "O3Mini" to O3Mini,
        "O1" to O1,
        "O4Mini" to O4Mini,
        "O3" to O3,
        "GPT45" to GPT45,
        "GPT41" to GPT41,
        "GPT41Mini" to GPT41Mini,
        "GPT41Nano" to GPT41Nano,
    )

}