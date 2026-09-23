package com.simiacryptus.cognotik.platform.model

interface UsageListener {
  val sessionId: Session
  fun onUsage(model: LLMModel, usage: ModelSchema.Usage, data: ModelSchema.UsageData? = null)

  companion object {
    fun fn(
      sessionId: Session,
      fn: (model: LLMModel, usage: ModelSchema.Usage, data: ModelSchema.UsageData?) -> Unit
    ): UsageListener {
       val session = sessionId
      return object : UsageListener {
         override val sessionId: Session get() = session
        override fun onUsage(model: LLMModel, usage: ModelSchema.Usage, data: ModelSchema.UsageData?) {
          fn(model, usage, data)
        }
      }
    }
  }
}