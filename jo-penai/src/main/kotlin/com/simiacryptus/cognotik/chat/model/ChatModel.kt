package com.simiacryptus.cognotik.chat.model

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.models.ApiModel.Usage
import com.simiacryptus.cognotik.models.EmbeddingModel
import com.simiacryptus.cognotik.models.ImageModels
import com.simiacryptus.cognotik.chat.model.ChatModel.Companion.values
import com.simiacryptus.cognotik.models.LLMModel
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import kotlin.collections.toMutableMap

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
    override fun toString() = modelName

    override fun pricing(usage: Usage) =
        ((usage.prompt_tokens ?: 0L) * inputTokenPricePerK + (usage.completion_tokens ?: 0L) * outputTokenPricePerK) / 1000.0

    interface Chatter {
        fun chat(
            chatRequest: ApiModel.ChatRequest,
            workPool: ExecutorService,
        ): ApiModel.ChatResponse
        val modelType: ChatModel
    }

    fun instance(
        key: String,
        base: String = provider.base!!,
        logLevel: Level = Level.INFO,
        logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    ) = object : Chatter {
        override val modelType = this@ChatModel
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
            model = this@ChatModel,
            logStreams = logStreams
        )
    }

    companion object {

        fun values() = values.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
        val values: MutableMap<String, ChatModel?> by lazy { defaultValues().toMutableMap() }

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
            provider = APIProvider.Companion.OpenAI,
            inputTokenPricePerK = 0.0,
            outputTokenPricePerK = 0.0
        )
    }
}

fun String.chatModelType(): ChatModel = (values().entries.find {
    it.key.equals(this, true) || it.value.modelName.equals(this, true)
}?.value ?: ChatModel(
    name = this,
    modelName = this,
    maxTotalTokens = 4096,
    provider = APIProvider.Companion.OpenAI,
    inputTokenPricePerK = 0.0,
    outputTokenPricePerK = 0.0
))
fun getModel(modelName: String?): AIModel? = values().values.find { it.modelName == modelName }
    ?: EmbeddingModel.values().values.find { it.modelName == modelName }
    ?: ImageModels.values().find { it.modelName == modelName }

