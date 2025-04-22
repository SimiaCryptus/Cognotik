package com.simiacryptus.jopenai

import com.simiacryptus.jopenai.models.APIProvider
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.util.ClientUtil
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
class ModelTests {

    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    fun generateChatModelTests(): Array<DynamicNode> {
        // Retrieve all ChatModels
        return APIProvider.values().filter {
            when (it) {
//                APIProvider.Google -> true
//                APIProvider.OpenAI -> true
//                APIProvider.Anthropic -> true
//                APIProvider.AWS -> true
//                APIProvider.Mistral -> true
//                APIProvider.Groq -> true
//                APIProvider.Perplexity -> true
//                APIProvider.ModelsLab -> true
//                else -> true
                else -> false
            }
        }.flatMap { provider ->
            // Generate a dynamic test for each model
            ChatModel.values()
                .filter { it.value.provider == provider }
                .values
                .filter { model ->
                    when (model) {
//                        LLaMA38bInstructAWS -> true
//                        LLaMA370bInstructAWS -> true
//                        else -> false
                        else -> true
                    }
                }.map { model ->
                    DynamicTest.dynamicTest("${provider.name} - ${model.modelName}") {
                        testChatWithModel(model)
                    }
                }.map { it as DynamicNode }.toList()
        }.toTypedArray()
    }

    private fun testChatWithModel(model: ChatModel) {
        val prov = ClientUtil.keyMap[ClientUtil.defaultApiProvider.name] ?: return
        if (prov.isBlank()) return
        val client = ChatClient(ClientUtil.keyMap.mapKeys { APIProvider.valueOf(it.key) })
        val request = ApiModel.ChatRequest(
            model = model.modelName,
            messages = ArrayList(
                listOf(
                    ApiModel.ChatMessage(ApiModel.Role.system, "You are a spiritual teacher".toContentList()),
                    ApiModel.ChatMessage(ApiModel.Role.user, "What is the meaning of life?".toContentList()),
                )
            )
        )
        val chatResponse = client.chat(request, model)
        println(chatResponse.choices.first().message?.content ?: "No response")
    }

}