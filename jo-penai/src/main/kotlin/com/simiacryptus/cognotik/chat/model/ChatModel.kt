package com.simiacryptus.cognotik.chat.model

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.simiacryptus.cognotik.chat.model.ChatModel.Companion.values
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ApiModel.Usage
import com.simiacryptus.cognotik.models.LLMModel
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

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
) : LLMModel(
    modelName = modelName,
    maxTotalTokens = maxTotalTokens,
    maxOutTokens = maxOutTokens,
    provider = provider,
) {
    override fun toString() = modelName ?: name

    override fun pricing(usage: Usage) =
        (usage.prompt_tokens * inputTokenPricePerK + usage.completion_tokens * outputTokenPricePerK) / 1000.0

    fun instance(
        key: String,
        base: String = provider?.base!!,
        logLevel: Level = Level.INFO,
        logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
        workPool: ExecutorService,
        temperature: Double = 0.1,
    ) : Chatter = Chatter(
        logStreams = logStreams,
        key = key,
        base = base,
        logLevel = logLevel,
        temperature = temperature,
        provider = provider!!,
        modelType = this,
        workPool = workPool,
    )

    companion object {

        fun values(): Map<String, ChatModel> = values.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
        private val values: MutableMap<String, ChatModel?> by lazy {
            (OpenAIModels.values +
                    PerplexityModels.values +
                    MistralModels.values +
                    GroqModels.values +
                    ModelsLabModels.values +
                    AWSModels.values +
                    AnthropicModels.values +
                    DeepSeekModels.values +
                    GoogleModels.values).toMutableMap() }

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
        return when (p.currentToken) {
            JsonToken.VALUE_STRING -> {
                // Handle string format
                val modelName = p.readValueAs(String::class.java)
                values().entries.find { it.key == modelName || it.value.name == modelName || it.value.modelName == modelName }?.value
                    ?: throw IllegalArgumentException("Unknown model: $modelName")
            }
            JsonToken.START_OBJECT -> {
                // Handle object format - delegate to default deserialization
                val node = p.readValueAsTree<JsonNode>()
                val modelName = node.get("modelName")?.asText() ?: node.get("name")?.asText()
                    ?: throw IllegalArgumentException("Object format must contain 'modelName' or 'name' field")
                values().entries.find { it.key == modelName || it.value.name == modelName || it.value.modelName == modelName }?.value
                    ?: throw IllegalArgumentException("Unknown model: $modelName")
            }
            else -> throw IllegalArgumentException("ChatModel must be deserialized from either a string or an object")
        }
    }
}