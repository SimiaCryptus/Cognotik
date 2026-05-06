package com.simiacryptus.cognotik.models

open class LLMModel(
  override val modelId: String,
  override val provider: APIProvider?,
  val maxTotalTokens: Int = -1,
  val maxOutTokens: Int = maxTotalTokens,
) : AIModel
