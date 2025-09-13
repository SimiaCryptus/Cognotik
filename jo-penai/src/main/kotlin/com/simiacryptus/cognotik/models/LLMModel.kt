package com.simiacryptus.cognotik.models

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.models.ApiModel.Usage

@JsonDeserialize(using = LLMModelDeserializer::class)
@JsonSerialize(using = LLMModelSerializer::class)
open class LLMModel(
    override val modelName: String?,
    val provider: APIProvider?,
    val maxTotalTokens: Int = -1,
    val maxOutTokens: Int = maxTotalTokens,
) : AIModel {
    open fun pricing(usage: Usage): Double = 0.0
}

class LLMModelSerializer : com.fasterxml.jackson.databind.ser.std.StdSerializer<LLMModel>(LLMModel::class.java) {
    override fun serialize(value: LLMModel, gen: JsonGenerator, provider: SerializerProvider) {
        ((listOf(
            ChatModel.values(),
            EmbeddingModel.values(),
        ).flatMap { it.entries }.find { it.value == value }?.key) ?: value.modelName)
            .let { gen.writeString(it) }
    }
}

class LLMModelDeserializer : com.fasterxml.jackson.databind.JsonDeserializer<LLMModel>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LLMModel {
        val modelName = p.readValueAs(String::class.java)
        listOf(
            ChatModel.values(),
            EmbeddingModel.values(),
            EditModels.values(),
        ).flatMap { it.entries }.find { it.key == modelName }?.value?.let { return it }
        return LLMModel(
            modelName,
            provider = APIProvider.OpenAI,
        )
    }
}
