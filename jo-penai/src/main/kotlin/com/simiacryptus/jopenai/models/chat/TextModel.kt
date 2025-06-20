package com.simiacryptus.jopenai.models.chat

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.simiacryptus.jopenai.models.APIProvider
import com.simiacryptus.jopenai.models.ApiModel.Usage
import com.simiacryptus.jopenai.models.chat.ChatModel
import com.simiacryptus.jopenai.models.CompletionModels
import com.simiacryptus.jopenai.models.EditModels
import com.simiacryptus.jopenai.models.EmbeddingModels
import com.simiacryptus.jopenai.models.OpenAIModel

@JsonDeserialize(using = OpenAITextModelDeserializer::class)
@JsonSerialize(using = OpenAITextModelSerializer::class)
open class TextModel(
    override val modelName: String = "",
    val maxTotalTokens: Int = -1,
    val maxOutTokens: Int = maxTotalTokens,
    val provider: APIProvider = APIProvider.Companion.OpenAI,
    val hasTemperature: Boolean = true,
    val hasReasoningEffort: Boolean = false,
) : OpenAIModel {

    open fun pricing(usage: Usage): Double = 0.0
}

class OpenAITextModelSerializer : StdSerializer<TextModel>(TextModel::class.java) {
    override fun serialize(value: TextModel, gen: JsonGenerator, provider: SerializerProvider) {
        ((listOf(
            ChatModel.Companion.values(),
            CompletionModels.Companion.values(),
            EmbeddingModels.Companion.values(),
            EditModels.Companion.values(),
        ).flatMap { it.entries }.find { it.value == value }?.key) ?: value.modelName)
            .let { gen.writeString(it) }
    }
}

class OpenAITextModelDeserializer : JsonDeserializer<TextModel>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): TextModel {
        val modelName = p.readValueAs(String::class.java)
        listOf(
            ChatModel.Companion.values(),
            CompletionModels.Companion.values(),
            EmbeddingModels.Companion.values(),
            EditModels.Companion.values(),
        ).flatMap { it.entries }.find { it.key == modelName }?.value?.let { return it }
        return TextModel(modelName)
    }
}
