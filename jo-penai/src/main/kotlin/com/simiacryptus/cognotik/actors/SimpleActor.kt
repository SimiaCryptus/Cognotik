package com.simiacryptus.cognotik.actors

import com.simiacryptus.cognotik.API
import com.simiacryptus.cognotik.chat.model.ChatModelType
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.util.ClientUtil.toContentList

open class SimpleActor(
    prompt: String,
    name: String? = null,
    model: LLMModel,
    temperature: Double = 0.3,
) : BaseActor<List<String>, String>(
    prompt = prompt,
    name = name,
    model = model,
    temperature = temperature,
) {

    override fun respond(input: List<String>, api: API, vararg messages: ApiModel.ChatMessage): String =
        response(*messages, api = api).choices.first().message?.content ?: throw RuntimeException("No response")

    override fun chatMessages(questions: List<String>) = arrayOf(
        ApiModel.ChatMessage(
            role = ApiModel.Role.system,
            content = prompt.toContentList()
        ),
    ) + questions.map {
        ApiModel.ChatMessage(
            role = ApiModel.Role.user,
            content = it.toContentList()
        )
    }

    override fun withModel(model: ChatModelType): SimpleActor = SimpleActor(
        prompt = prompt,
        name = name,
        model = model,
        temperature = temperature,
    )
}
