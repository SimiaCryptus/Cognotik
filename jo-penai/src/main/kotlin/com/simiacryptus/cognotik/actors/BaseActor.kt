package com.simiacryptus.cognotik.actors

import com.simiacryptus.cognotik.API
import com.simiacryptus.cognotik.chat.ChatClientInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.Chatter
import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.ApiModel

abstract class BaseActor<I, R>(
    open val prompt: String,
    val name: String? = null,
    val model: ChatModel,
    val temperature: Double = 0.3,
) {
    abstract fun respond(
        input: I,
        api: API,
        vararg messages: ApiModel.ChatMessage = this.chatMessages(input),
    ): R

    protected open fun response(vararg input: ApiModel.ChatMessage, model: AIModel = this.model, api: API): ApiModel.ChatResponse =
        chatter(api).chat(input.toList())

    protected open fun chatter(api: API): Chatter = (api as ChatClientInterface).let {
        model.instance(
            key = it.key(model.provider),
            base = it.apiBase(model.provider),
            logStreams = it.logStreams,
            workPool = it.workPool,
            temperature = temperature
        )
    }

    open fun answer(input: I, api: API): R = respond(input = input, api = api, *chatMessages(input))

    abstract fun chatMessages(questions: I): Array<ApiModel.ChatMessage>
    abstract fun withModel(model: ChatModel): BaseActor<I, R>
}