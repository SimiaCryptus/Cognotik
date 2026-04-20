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
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema.Usage
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@JsonDeserialize(using = ChatModelsDeserializer::class)
@JsonSerialize(using = ChatModelsSerializer::class)
open class ChatModel(
  val name: String = "",
  modelId: String = name,
  maxTotalTokens: Int = -1,
  maxOutTokens: Int = maxTotalTokens,
  provider: APIProvider? = null,
  val inputTokenPricePerK: Double = 0.0,
  val outputTokenPricePerK: Double = inputTokenPricePerK,
  val supportsTemperature : Boolean = true,
  val supportsReasoning : Boolean = false,
  val deprecated: Boolean = false,
) : LLMModel(
  modelId = modelId,
  maxTotalTokens = maxTotalTokens,
  maxOutTokens = maxOutTokens,
  provider = provider,
) {
  override fun toString() = modelId

  override fun pricing(usage: Usage): Double {
    val promptCost = usage.prompt_tokens * inputTokenPricePerK
    val completionCost = usage.completion_tokens * outputTokenPricePerK
    val estimatedUnaccountedCost =
      (usage.total_tokens - (usage.prompt_tokens + usage.completion_tokens)) * ((inputTokenPricePerK + outputTokenPricePerK) / 2)
    return (promptCost + completionCost + estimatedUnaccountedCost) / 1000.0
  }

  fun instance(
    key: SecureString,
    base: String = provider?.base!!,
    logLevel: Level = Level.DEBUG,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    workPool: ExecutorService = Executors.newFixedThreadPool(4),
    temperature: Double = 0.1,
    scheduledPool: ListeningScheduledExecutorService = MoreExecutors.listeningDecorator(
      Executors.newScheduledThreadPool(
        1
      )
    ),
    onUsage: (LLMModel, Usage) -> Unit = { _, _ -> },
  ): ChatInterface = ChatInterface(
    logStreams = logStreams,
    key = key,
    base = base,
    logLevel = logLevel,
    temperature = temperature,
    provider = provider!!,
    modelType = this,
    workPool = workPool,
    scheduledPool = scheduledPool,
    onUsage = onUsage
  )

  companion object {
    val log = com.simiacryptus.cognotik.util.LoggerFactory.getLogger(ChatModel::class.java)

    fun values(): Map<String, ChatModel> = values.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
    private val values: MutableMap<String, ChatModel?> by lazy {
      APIProvider.values().flatMap { it.getChatModels().map {
        it.modelId to it
      } }.toMap().toMutableMap()
    }
  }


}

class ChatModelsSerializer : StdSerializer<ChatModel>(ChatModel::class.java) {
  override fun serialize(value: ChatModel, gen: JsonGenerator, provider: SerializerProvider) {
    gen.writeStartObject()
    gen.writeStringField("name", value.name)
    gen.writeStringField("modelName", value.modelId)
    gen.writeNumberField("maxTotalTokens", value.maxTotalTokens)
    gen.writeNumberField("maxOutTokens", value.maxOutTokens)
    value.provider?.let { gen.writeStringField("provider", it.name) }
    gen.writeNumberField("inputTokenPricePerK", value.inputTokenPricePerK)
    gen.writeNumberField("outputTokenPricePerK", value.outputTokenPricePerK)
    gen.writeEndObject()
  }
}

class ChatModelsDeserializer : JsonDeserializer<ChatModel>() {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ChatModel {
    return when (p.currentToken) {
      JsonToken.START_OBJECT -> {
        // Handle object format
        val node = p.readValueAsTree<JsonNode>()
        val name = node.get("name")?.asText() ?: ""
        val modelName = node.get("modelName")?.asText() ?: name
        val maxTotalTokens = node.get("maxTotalTokens")?.asInt() ?: -1
        val maxOutTokens = node.get("maxOutTokens")?.asInt() ?: maxTotalTokens
        val providerName = node.get("provider")?.asText()
        val provider = providerName?.let { APIProvider.valueOf(it) }
        val inputTokenPricePerK = node.get("inputTokenPricePerK")?.asDouble() ?: 0.0
        val outputTokenPricePerK = node.get("outputTokenPricePerK")?.asDouble() ?: inputTokenPricePerK

        return ChatModel(
          name = name,
          modelId = modelName,
          maxTotalTokens = maxTotalTokens,
          maxOutTokens = maxOutTokens,
          provider = provider,
          inputTokenPricePerK = inputTokenPricePerK,
          outputTokenPricePerK = outputTokenPricePerK
        )
      }

      else -> throw IllegalArgumentException("ChatModel must be deserialized from an object")
    }
  }
}
