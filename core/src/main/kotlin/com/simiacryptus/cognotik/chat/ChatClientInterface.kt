package com.simiacryptus.cognotik.chat

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.model.Session
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

interface ChatClientInterface {
  val logStreams: MutableList<BufferedOutputStream>
  val workPool: ExecutorService
  fun getModels(): List<ChatModel> = emptyList()

  val session : Session

  /**
   * Sends a chat request to the configured model and returns the response
   * @param chatRequest The chat request containing messages and parameters
   * @param model The text model to use for the chat
   * @return The chat response from the model
   * @throws IllegalArgumentException if the request is invalid
   * @throws RuntimeException if the API call fails
   */
  @Deprecated("Use chat with messages parameter instead via preauthenticated chat models")
  fun chat(
    chatRequest: ModelSchema.ChatRequest,
    model: ChatModel,
    logStreams: MutableList<BufferedOutputStream> = this.logStreams,
    usageHandler: UsageListener
  ): ModelSchema.ChatResponse

}

interface UsageListener {
  val sessionId: Session
  fun onUsage(model: LLMModel, usage: ModelSchema.Usage)

  companion object {
    fun fn(
      sessionId: Session,
      fn: (model: LLMModel, usage: ModelSchema.Usage) -> Unit
    ): UsageListener {
      return object : UsageListener {
        override val sessionId: Session get() =  sessionId
        override fun onUsage(model: LLMModel, usage: ModelSchema.Usage) {
          fn(model, usage)
        }
      }
    }
  }
}