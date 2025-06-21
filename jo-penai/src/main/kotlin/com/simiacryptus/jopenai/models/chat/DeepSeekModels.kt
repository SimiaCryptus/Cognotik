package com.simiacryptus.jopenai.models.chat

import com.simiacryptus.jopenai.models.APIProvider

object DeepSeekModels {
    val DeepSeekChat = ChatModelType(
        name = "DeepSeekChat",
        modelName = "deepseek-chat",
        maxTotalTokens = 64000,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.DeepSeek,
        inputTokenPricePerK = 0.14 / 1000.0,
        outputTokenPricePerK = 0.28 / 1000.0
    )
    val DeepSeekCoder = ChatModelType(
        name = "DeepSeekCoder",
        modelName = "deepseek-coder",
        maxTotalTokens = 64000,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.DeepSeek,
        inputTokenPricePerK = 0.14 / 1000.0,
        outputTokenPricePerK = 0.28 / 1000.0
    )
    val DeepSeekReasoner = ChatModelType(
        name = "DeepSeekReasoner",
        modelName = "deepseek-reasoner",
        maxTotalTokens = 64000,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.DeepSeek,
        inputTokenPricePerK = 0.55 / 1000.0,
        outputTokenPricePerK = 2.19 / 1000.0
    )
    val values = mapOf(
        "DeepSeekChat" to DeepSeekChat,
        "DeepSeekCoder" to DeepSeekCoder,
        "DeepSeekReasoner" to DeepSeekReasoner,
    )

}