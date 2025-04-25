package com.simiacryptus.cognotik.actors

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModel
import com.simiacryptus.jopenai.models.TextModel

abstract class BaseActor<I, R>(
  open val prompt: String,
  val name: String? = null,
  val model: TextModel,
  val temperature: Double = 0.3,
) {
  abstract fun respond(
    input: I,
    api: API,
    vararg messages: ApiModel.ChatMessage = this.chatMessages(input),
  ): R

  protected open fun response(vararg input: ApiModel.ChatMessage, model: OpenAIModel = this.model, api: API) =
    (api as ChatClient).chat(
      ApiModel.ChatRequest(
        messages = ArrayList(input.toList()),
        temperature = temperature,
        model = this.model.modelName,
      ),
      model = this.model
    )

  open fun answer(input: I, api: API): R = respond(input = input, api = api, *chatMessages(input))

  abstract fun chatMessages(questions: I): Array<ApiModel.ChatMessage>
  abstract fun withModel(model: ChatModel): BaseActor<I, R>
}