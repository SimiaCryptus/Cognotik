package com.simiacryptus.cognotik.actors

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.toContentList

open class ChatAgent(
    prompt: String,
    name: String? = null,
    model: ChatInterface,
    temperature: Double = 0.3,
) : BaseAgent<List<String>, String>(
    prompt = prompt,
    name = name,
    model = model,
    temperature = temperature,
) {

    override fun respond(input: List<String>, vararg messages: ModelSchema.ChatMessage): String =
        response(*messages).choices.first().message?.content ?: throw RuntimeException("No response")

    override fun chatMessages(questions: List<String>) = arrayOf(
        ModelSchema.ChatMessage(
            role = ModelSchema.Role.system,
            content = prompt.toContentList()
        ),
    ) + questions.map {
        ModelSchema.ChatMessage(
            role = ModelSchema.Role.user,
            content = it.toContentList()
        )
    }

    override fun withModel(model: ChatInterface): ChatAgent = ChatAgent(
        prompt = prompt,
        name = name,
        model = model,
        temperature = temperature,
    )
}
