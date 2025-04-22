package com.simiacryptus.jopenai.models

object DeepSeekModels {
    val DeepSeekChat = ChatModel(
        name = "DeepSeekChat",
        modelName = "deepseek-chat",
        maxTotalTokens = 64000,
        maxOutTokens = 4096,
        provider = APIProvider.DeepSeek,
        inputTokenPricePerK = 0.14 / 1000.0,
        outputTokenPricePerK = 0.28 / 1000.0
    )
    val DeepSeekCoder = ChatModel(
        name = "DeepSeekCoder",
        modelName = "deepseek-coder",
        maxTotalTokens = 64000,
        maxOutTokens = 4096,
        provider = APIProvider.DeepSeek,
        inputTokenPricePerK = 0.14 / 1000.0,
        outputTokenPricePerK = 0.28 / 1000.0
    )
    val DeepSeekReasoner = ChatModel(
        name = "DeepSeekReasoner",
        modelName = "deepseek-reasoner",
        maxTotalTokens = 64000,
        maxOutTokens = 4096,
        provider = APIProvider.DeepSeek,
        inputTokenPricePerK = 0.55 / 1000.0,
        outputTokenPricePerK = 2.19 / 1000.0
    )
    val values = mapOf(
        "DeepSeekChat" to DeepSeekModels.DeepSeekChat,
        "DeepSeekCoder" to DeepSeekModels.DeepSeekCoder,
        "DeepSeekReasoner" to DeepSeekModels.DeepSeekReasoner,
    )

}