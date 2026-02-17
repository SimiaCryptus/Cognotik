package com.simiacryptus.cognotik.embedding

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
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.SecureString
import com.simiacryptus.cognotik.util.encrypt
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

interface Embedder {
    fun embed(input: String): DoubleArray
}

@JsonDeserialize(using = EmbeddingModelsDeserializer::class)
@JsonSerialize(using = EmbeddingModelsSerializer::class)
open class EmbeddingModel(
    modelName: String = "",
    maxTokens: Int = 0,
    provider: APIProvider? = null,
    private val tokenPricePerK: Double = 0.0,
) : LLMModel(
    modelId = modelName,
    provider = provider,
    maxTotalTokens = maxTokens
) {
    private val log = LoggerFactory.getLogger(EmbeddingModel::class.java)
    override fun toString() = modelId

    override fun pricing(usage: ModelSchema.Usage) = usage.prompt_tokens * tokenPricePerK / 1000.0
        .also { log.info("Calculated pricing for model: $modelId with prompt tokens: ${usage.prompt_tokens}, price: $it") }

    fun instance(
        key: SecureString = "".encrypt,
        base: String = provider?.base ?: "",
        logLevel: Level = Level.DEBUG,
        logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
        workPool: ExecutorService = java.util.concurrent.Executors.newFixedThreadPool(8),
        scheduledPool: ListeningScheduledExecutorService = com.google.common.util.concurrent.MoreExecutors.listeningDecorator(
            java.util.concurrent.Executors.newScheduledThreadPool(1)
        ),
        onUsage: (LLMModel, ModelSchema.Usage) -> Unit = { _, _ -> },
    ): EmbedderClient {
        val client = provider?.getEmbeddingClient(
            key = key,
            base = base,
            workPool = workPool,
            logLevel = logLevel,
            logStreams = logStreams,
            scheduledPool = scheduledPool
        ) ?: throw IllegalArgumentException("Unsupported provider: $provider")

        return EmbedderClient(client, this, onUsage)
    }

    companion object {
        val log = LoggerFactory.getLogger(EmbeddingModel::class.java)

        fun values(): Map<String, EmbeddingModel> =
            values.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()

        private val values: MutableMap<String, EmbeddingModel?> by lazy {
            (OpenAIEmbeddingModels.values +
                    OllamaEmbeddingModels.values).toMutableMap()
        }

        init {
            log.info("Initializing EmbeddingModels with predefined models")
        }
    }
}

class EmbeddingModelsSerializer : StdSerializer<EmbeddingModel>(EmbeddingModel::class.java) {
    override fun serialize(value: EmbeddingModel, gen: JsonGenerator, provider: SerializerProvider) {
        val modelKey = EmbeddingModel.values().entries.find { it.value == value }?.key
        gen.writeString(modelKey ?: value.modelId)
    }
}

class EmbeddingModelsDeserializer : JsonDeserializer<EmbeddingModel>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): EmbeddingModel {
        return when (p.currentToken) {
            JsonToken.VALUE_STRING -> {
                val modelName = p.readValueAs(String::class.java)
                EmbeddingModel.values().entries.find {
                    it.key == modelName || it.value.modelId == modelName
                }?.value ?: throw IllegalArgumentException("Unknown embedding model: $modelName")
            }

            JsonToken.START_OBJECT -> {
                val node = p.readValueAsTree<JsonNode>()
                val modelName = node.get("modelName")?.asText() ?: node.get("name")?.asText()
                ?: throw IllegalArgumentException("Object format must contain 'modelName' or 'name' field")
                EmbeddingModel.values().entries.find {
                    it.key == modelName || it.value.modelId == modelName
                }?.value ?: throw IllegalArgumentException("Unknown embedding model: $modelName")
            }

            else -> throw IllegalArgumentException("EmbeddingModel must be deserialized from either a string or an object")
        }
    }


}

