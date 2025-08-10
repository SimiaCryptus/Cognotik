package com.simiacryptus.jopenai.chat.model

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.simiacryptus.jopenai.models.AIModel
import com.simiacryptus.jopenai.models.APIProvider
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ApiModel.Usage
import com.simiacryptus.jopenai.models.EmbeddingModels
import com.simiacryptus.jopenai.models.ImageModels
import com.simiacryptus.jopenai.chat.model.ChatModelType.Companion.values
import com.simiacryptus.jopenai.models.LLMModel
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import kotlin.collections.toMutableMap

@JsonDeserialize(using = ChatModelsDeserializer::class)
@JsonSerialize(using = ChatModelsSerializer::class)
open class ChatModelType(
    val name: String,
    modelName: String,
    maxTotalTokens: Int,
    maxOutTokens: Int = maxTotalTokens,
    provider: APIProvider,
    val inputTokenPricePerK: Double,
    val outputTokenPricePerK: Double,
    hasTemperature: Boolean = true,
    hasReasoningEffort: Boolean = false,
) : LLMModel(
    modelName = modelName,
    maxTotalTokens = maxTotalTokens,
    maxOutTokens = maxOutTokens,
    provider = provider,
    hasTemperature = hasTemperature,
    hasReasoningEffort = hasReasoningEffort,
) {
    override fun toString() = modelName

    override fun pricing(usage: Usage) =
        ((usage.prompt_tokens ?: 0L) * inputTokenPricePerK + (usage.completion_tokens ?: 0L) * outputTokenPricePerK) / 1000.0

    interface ChatModel {
        fun chat(
            chatRequest: ApiModel.ChatRequest,
            workPool: ExecutorService,
        ): ApiModel.ChatResponse
        val modelType: ChatModelType
    }

    fun instance(
        key: String,
        base: String = provider.base!!,
        logLevel: Level = Level.INFO,
        logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    ) = object : ChatModel {
        override val modelType = this@ChatModelType
        override fun chat(
            chatRequest: ApiModel.ChatRequest,
            workPool: ExecutorService
        ) = provider.getChatClient(
            key = key,
            base = base,
            workPool = workPool,
            logLevel = logLevel,
            logStreams = logStreams
        ).chat(
            chatRequest = chatRequest,
            model = this@ChatModelType,
            logStreams = logStreams
        )
    }

    companion object {

        fun values() = values.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
        val values: MutableMap<String, ChatModelType?> by lazy { defaultValues().toMutableMap() }

        private fun defaultValues() = OpenAIModels.values +
                PerplexityModels.values +
                MistralModels.values +
                GroqModels.values +
                ModelsLabModels.values +
                AWSModels.values +
                AnthropicModels.values +
                DeepSeekModels.values +
                GoogleModels.values
    }
}

class ChatModelsSerializer : StdSerializer<ChatModelType>(ChatModelType::class.java) {
    override fun serialize(value: ChatModelType, gen: JsonGenerator, provider: SerializerProvider) {
        val modelKey = ChatModelType.Companion.values().entries.find { it.value == value }?.key
        gen.writeString(modelKey ?: value.modelName)
    }
}

class ChatModelsDeserializer : JsonDeserializer<ChatModelType>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ChatModelType {
        val modelName = p.readValueAs(String::class.java)
        return ChatModelType.Companion.values()[modelName] ?: ChatModelType(
            name = modelName,
            modelName = modelName,
            maxTotalTokens = 4096,
            provider = APIProvider.Companion.OpenAI,
            inputTokenPricePerK = 0.0,
            outputTokenPricePerK = 0.0
        )
    }
}

fun String.chatModelType() = (ChatModelType.Companion.values().entries.find {
    it.key.equals(this, true) || it.value.modelName.equals(this, true)
}?.value ?: ChatModelType(
    name = this,
    modelName = this,
    maxTotalTokens = 4096,
    provider = APIProvider.Companion.OpenAI,
    inputTokenPricePerK = 0.0,
    outputTokenPricePerK = 0.0
))
fun getModel(modelName: String?): AIModel? = values().values.find { it.modelName == modelName }
    ?: EmbeddingModels.values().values.find { it.modelName == modelName }
    ?: ImageModels.values().find { it.modelName == modelName }

