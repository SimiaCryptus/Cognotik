package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.CoreProviders

object DeepSeekModels {
  val DeepSeekChat = ChatModel(
    name = "DeepSeekChat",
    modelId = "deepseek-chat",
    maxTotalTokens = 64000,
    maxOutTokens = 4096,
    provider = CoreProviders.DeepSeek,
    inputTokenPricePerK = 0.14 / 1000.0,
    outputTokenPricePerK = 0.28 / 1000.0
  )
  val DeepSeekCoder = ChatModel(
    name = "DeepSeekCoder",
    modelId = "deepseek-coder",
    maxTotalTokens = 64000,
    maxOutTokens = 4096,
    provider = CoreProviders.DeepSeek,
    inputTokenPricePerK = 0.14 / 1000.0,
    outputTokenPricePerK = 0.28 / 1000.0
  )
  val DeepSeekReasoner = ChatModel(
    name = "DeepSeekReasoner",
    modelId = "deepseek-reasoner",
    maxTotalTokens = 64000,
    maxOutTokens = 4096,
    provider = CoreProviders.DeepSeek,
    inputTokenPricePerK = 0.55 / 1000.0,
    outputTokenPricePerK = 2.19 / 1000.0
  )
  val values = mapOf(
    "DeepSeekChat" to DeepSeekChat,
    "DeepSeekCoder" to DeepSeekCoder,
    "DeepSeekReasoner" to DeepSeekReasoner,
  )

}