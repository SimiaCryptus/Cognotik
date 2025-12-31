package com.simiacryptus.cognotik.agents

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.ModelSchema

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

    protected open fun response(
        vararg input: ModelSchema.ChatMessage,
        model: AIModel = this.model.modelType
    ): ModelSchema.ChatResponse =
        this.model.chat(input.toList())

    open fun answer(input: I): R = respond(input = input, *chatMessages(input))

    abstract fun chatMessages(questions: I): Array<ModelSchema.ChatMessage>
    abstract fun withModel(model: ChatInterface): BaseAgent<I, R>
}