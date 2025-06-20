package com.simiacryptus.jopenai.models

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.simiacryptus.jopenai.models.ApiModel.Usage
import com.simiacryptus.jopenai.models.ChatModel.Companion.values

@JsonDeserialize(using = ChatModelsDeserializer::class)
@JsonSerialize(using = ChatModelsSerializer::class)
open class ChatModel(
    val name: String,
    modelName: String,
    maxTotalTokens: Int,
    maxOutTokens: Int = maxTotalTokens,
    provider: APIProvider,
    val inputTokenPricePerK: Double,
    val outputTokenPricePerK: Double,
    hasTemperature: Boolean = true,
    hasReasoningEffort: Boolean = false,
) : TextModel(
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

    companion object {

        fun values() = values.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
        val values: MutableMap<String, ChatModel?> by lazy { defaultValues().toMutableMap() }

        fun defaultValues() = OpenAIModels.values +
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

class ChatModelsSerializer : StdSerializer<ChatModel>(ChatModel::class.java) {
    override fun serialize(value: ChatModel, gen: JsonGenerator, provider: SerializerProvider) {
        val modelKey = values().entries.find { it.value == value }?.key
        gen.writeString(modelKey ?: value.modelName)
    }
}

class ChatModelsDeserializer : JsonDeserializer<ChatModel>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ChatModel {
        val modelName = p.readValueAs(String::class.java)
        return values()[modelName] ?: ChatModel(
            name = modelName,
            modelName = modelName,
            maxTotalTokens = 4096,
            provider = APIProvider.OpenAI,
            inputTokenPricePerK = 0.0,
            outputTokenPricePerK = 0.0
        )
    }
}

fun String.chatModel() = values().entries.find {
    it.key.equals(this, true) || it.value.modelName.equals(this, true)
}?.value ?: ChatModel(
    name = this,
    modelName = this,
    maxTotalTokens = 4096,
    provider = APIProvider.OpenAI,
    inputTokenPricePerK = 0.0,
    outputTokenPricePerK = 0.0
)