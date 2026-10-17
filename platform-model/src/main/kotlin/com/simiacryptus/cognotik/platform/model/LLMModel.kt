package com.simiacryptus.cognotik.platform.model

open class LLMModel(
  override var modelId: String,
  override val provider: APIProvider?,
  val maxTotalTokens: Int = -1,
  val maxOutTokens: Int = maxTotalTokens,
) : AIModel
