package com.simiacryptus.cognotik.agents

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.ChatRequest

abstract class BaseAgent<I, R>(
  open val prompt: String,
  val name: String? = null,
  val model: ChatInterface,
  val temperature: Double = 0.3,
) {
  abstract fun respond(
    input: I,
    vararg messages: ModelSchema.ChatMessage = this.chatMessages(input),
  ): R

  protected fun response1(
    vararg input: ModelSchema.ChatMessage
  ): ModelSchema.ChatResponse =
    this.model.chat(
      ChatRequest(
        model = this.model.modelType.modelId,
        messages = input.toList(),
        temperature = this.model.temperature,
        audio = this.model.audio,
      )
    )

  open fun answer(input: I): R = respond(input = input, *chatMessages(input))

  abstract fun chatMessages(questions: I): Array<ModelSchema.ChatMessage>
  abstract fun withModel(model: ChatInterface): BaseAgent<I, R>
}