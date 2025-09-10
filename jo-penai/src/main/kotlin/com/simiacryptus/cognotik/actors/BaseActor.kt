package com.simiacryptus.cognotik.actors

import com.simiacryptus.cognotik.chat.ChatClientInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.Chatter
import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.ApiModel

abstract class BaseActor<I, R>(
    open val prompt: String,
    val name: String? = null,
    val model: Chatter,
    val temperature: Double = 0.3,
) {
    abstract fun respond(
        input: I,
        vararg messages: ApiModel.ChatMessage = this.chatMessages(input),
    ): R

    protected open fun response(vararg input: ApiModel.ChatMessage, model: AIModel = this.model.modelType): ApiModel.ChatResponse =
        this.model.chat(input.toList())

    open fun answer(input: I): R = respond(input = input, *chatMessages(input))

    abstract fun chatMessages(questions: I): Array<ApiModel.ChatMessage>
    abstract fun withModel(model: Chatter): BaseActor<I, R>
}