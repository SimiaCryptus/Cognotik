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
import com.simiacryptus.jopenai.models.EditModels
import com.simiacryptus.jopenai.models.EmbeddingModels
import com.simiacryptus.jopenai.models.AIModel

@JsonDeserialize(using = LLMModelDeserializer::class)
@JsonSerialize(using = LLMModelSerializer::class)
open class LLMModel(
    override val modelName: String,
    val provider: APIProvider,
    val maxTotalTokens: Int = -1,
    val maxOutTokens: Int = maxTotalTokens,
    val hasTemperature: Boolean = true,
    val hasReasoningEffort: Boolean = false,
) : AIModel {

    open fun pricing(usage: Usage): Double = 0.0

}

class LLMModelSerializer : StdSerializer<LLMModel>(LLMModel::class.java) {
    override fun serialize(value: LLMModel, gen: JsonGenerator, provider: SerializerProvider) {
        ((listOf(
            ChatModelType.Companion.values(),
            EmbeddingModels.Companion.values(),
        ).flatMap { it.entries }.find { it.value == value }?.key) ?: value.modelName)
            .let { gen.writeString(it) }
    }
}

class LLMModelDeserializer : JsonDeserializer<LLMModel>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LLMModel {
        val modelName = p.readValueAs(String::class.java)
        listOf(
            ChatModelType.Companion.values(),
            EmbeddingModels.Companion.values(),
            EditModels.Companion.values(),
        ).flatMap { it.entries }.find { it.key == modelName }?.value?.let { return it }
        return LLMModel(
            modelName,
            provider = APIProvider.OpenAI,
        )
    }
}
