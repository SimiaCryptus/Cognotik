# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/AWSModels.kt

```
package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.util.LoggerFactory

object AWSModels {
    private val log = LoggerFactory.getLogger(AWSModels::class.java)

    init {
        log.info("Initializing AWSModels with predefined chat models.")
    }

    val AWSLLaMA31_405bChat = ChatModel(
        name = "AWSLLaMA31_405bChat",
        modelName = "meta.llama3-1-405b-instruct-v1:0",
        maxTotalTokens = 128 * 1024 - 1,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.00195,
        outputTokenPricePerK = 0.00256
    )
    val AWSLLaMA31_70bChat = ChatModel(
        name = "AWSLLaMA31_70bChat",
        modelName = "meta.llama3-1-70b-instruct-v1:0",
        maxTotalTokens = 128 * 1024 - 1,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.00195,
        outputTokenPricePerK = 0.00256
    )
    val AWSLLaMA31_8bChat = ChatModel(
        name = "AWSLLaMA31_8bChat",
        modelName = "meta.llama3-1-8b-instruct-v1:0",
        maxTotalTokens = 128 * 1024 - 1,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.00195,
        outputTokenPricePerK = 0.00256
    )

    val Mistral7bInstructV02 = ChatModel(
        name = "Mistral7bInstructV02",
        modelName = "mistral.mistral-7b-instruct-v0:2",
        maxTotalTokens = 32 * 1024,
        maxOutTokens = 2 * 1024,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.00015,
        outputTokenPricePerK = 0.0002
    )
    val Mixtral8x7bInstructV01AWS = ChatModel(
        name = "Mixtral8x7bInstructV01AWS",
        modelName = "mistral.mixtral-8x7b-instruct-v0:1",
        maxTotalTokens = 32 * 1024,
        maxOutTokens = 2 * 1024,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.00045,
        outputTokenPricePerK = 0.0007
    )
    val MistralLarge2402 = ChatModel(
        name = "MistralLarge2402",
        modelName = "mistral.mistral-large-2402-v1:0",
        maxTotalTokens = 32 * 1024,
        maxOutTokens = 4000,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.008,
        outputTokenPricePerK = 0.024
    )
    val MistralLarge2407 = ChatModel(
        name = "MistralLarge2407",
        modelName = "mistral.mistral-large-2407-v1:0",
        maxTotalTokens = 32 * 1024,
        maxOutTokens = 4000,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.008,
        outputTokenPricePerK = 0.024
    )

    val AmazonTitanTextLiteV1 = ChatModel(
        name = "AmazonTitanTextLiteV1",
        modelName = "amazon.titan-text-lite-v1",
        maxTotalTokens = 4096,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.0003,
        outputTokenPricePerK = 0.0004
    )
    val AmazonTitanTextExpressV1 = ChatModel(
        name = "AmazonTitanTextExpressV1",
        modelName = "amazon.titan-text-express-v1",
        maxTotalTokens = 8192,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.0008,
        outputTokenPricePerK = 0.0016
    )
    val Claude3OpusAWS = ChatModel(
        name = "Claude3OpusAWS",
        modelName = "anthropic.claude-3-opus-20240229-v1:0",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 15.0 / 1000.0,
        outputTokenPricePerK = 75.0 / 1000.0
    )
    val CohereCommandTextV14 = ChatModel(
        name = "CohereCommandTextV14",
        modelName = "cohere.command-text-v14",
        maxTotalTokens = 4000,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.0015,
        outputTokenPricePerK = 0.002
    )
    val AI21J2UltraV1 = ChatModel(
        name = "AI21J2UltraV1",
        modelName = "ai21.j2-ultra-v1",
        maxTotalTokens = 8191,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.0125,
        outputTokenPricePerK = 0.0125
    )
    val AI21J2MidV1 = ChatModel(
        name = "AI21J2MidV1",
        modelName = "ai21.j2-mid-v1",
        maxTotalTokens = 8191,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.0188,
        outputTokenPricePerK = 0.0188
    )
    val Claude35Sonnet = ChatModel(
        name = "Claude3SonnetAWS",
        modelName = "anthropic.claude-3-5-sonnet-20240620-v1:0",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.003,
        outputTokenPricePerK = 0.015
    )
    val Claude37Sonnet = ChatModel(
        name = "Claude37SonnetAWS",
        modelName = "anthropic.claude-3-7-sonnet-20250219-v1:0",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.003,
        outputTokenPricePerK = 0.015
    )
    val Claude3Sonnet = ChatModel(
        name = "Claude3SonnetAWS",
        modelName = "anthropic.claude-3-sonnet-20240229-v1:0",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.003,
        outputTokenPricePerK = 0.015
    )
    val Claude3Haiku = ChatModel(
        name = "Claude3HaikuAWS",
        modelName = "anthropic.claude-3-haiku-20240307-v1:0",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.00025,
        outputTokenPricePerK = 0.000125
    )
    val Claude35Haiku = ChatModel(
        name = "Claude35HaikuAWS",
        modelName = "anthropic.claude-3-5-haiku-20241022-v1:0",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.00025,
        outputTokenPricePerK = 0.000125
    )
    val LLaMA38bInstructAWS = ChatModel(
        name = "LLaMA38bInstructAWS",
        modelName = "meta.llama3-8b-instruct-v1:0",
        maxTotalTokens = 8192,
        maxOutTokens = 2048,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.0005,

        outputTokenPricePerK = 0.0015

    )
    val LLaMA370bInstructAWS = ChatModel(
        name = "LLaMA370bInstructAWS",
        modelName = "meta.llama3-70b-instruct-v1:0",
        maxTotalTokens = 8192,
        maxOutTokens = 2048,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.0005,

        outputTokenPricePerK = 0.0015

    )
    val AmazonNovaProV1 = ChatModel(
        name = "AmazonNovaProV1",
        modelName = "amazon.nova-pro-v1:0",
        maxTotalTokens = 4096,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.001,

        outputTokenPricePerK = 0.002

    )
    val AmazonNovaLiteV1 = ChatModel(
        name = "AmazonNovaLiteV1",
        modelName = "amazon.nova-lite-v1:0",
        maxTotalTokens = 4096,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.0005,

        outputTokenPricePerK = 0.001

    )
    val AmazonNovaMicroV1 = ChatModel(
        name = "AmazonNovaMicroV1",
        modelName = "amazon.nova-micro-v1:0",
        maxTotalTokens = 4096,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.00025,

        outputTokenPricePerK = 0.0005

    )
    val DeepseekLLMR1DistillQwen32b = ChatModel(
        name = "DeepseekLLMR1DistillQwen32b",
        modelName = "deepseek-llm-r1-distill-qwen-32b",
        maxTotalTokens = 8192,
        provider = APIProvider.Companion.AWS,
        inputTokenPricePerK = 0.0010,

        outputTokenPricePerK = 0.0020

    )
    val values = mapOf(
        "LLaMA370bInstructAWS" to LLaMA370bInstructAWS,
        "AmazonNovaProV1" to AmazonNovaProV1,
        "AmazonNovaLiteV1" to AmazonNovaLiteV1,
        "AmazonNovaMicroV1" to AmazonNovaMicroV1,
        "AWSLLaMA31_405bChat" to AWSLLaMA31_405bChat,
        "AWSLLaMA31_70bChat" to AWSLLaMA31_70bChat,
        "AWSLLaMA31_8bChat" to AWSLLaMA31_8bChat,
        "Mistral7bInstructV02" to Mistral7bInstructV02,
        "Mixtral8x7bInstructV01AWS" to Mixtral8x7bInstructV01AWS,
        "MistralLarge2402" to MistralLarge2402,
        "MistralLarge2407" to MistralLarge2407,
        "AmazonTitanTextLiteV1" to AmazonTitanTextLiteV1,
        "AmazonTitanTextExpressV1" to AmazonTitanTextExpressV1,
        "Claude3OpusAWS" to Claude3OpusAWS,
        "CohereCommandTextV14" to CohereCommandTextV14,
        "AI21J2UltraV1" to AI21J2UltraV1,
        "AI21J2MidV1" to AI21J2MidV1,
        "Claude35SonnetAWS" to Claude35Sonnet,
        "Claude3SonnetAWS" to Claude3Sonnet,
        "Claude3HaikuAWS" to Claude3Haiku,
        "Claude35HaikuAWS" to Claude35Haiku,
        "Claude37SonnetAWS" to Claude37Sonnet,
        "LLaMA38bInstructAWS" to LLaMA38bInstructAWS,
        "DeepseekLLMR1DistillQwen32b" to DeepseekLLMR1DistillQwen32b,
    )
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/AnthropicModels.kt

```
package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider

object AnthropicModels {
    val Claude41Opus = ChatModel(
        name = "Claude41Opus",
        modelName = "claude-opus-4-1-20250805",
        maxTotalTokens = 200000,
        maxOutTokens = 32000,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 15.0 / 1000.0,
        outputTokenPricePerK = 75.0 / 1000.0,
    )
    val Claude4Sonnet = ChatModel(
        name = "Claude4Sonnet",
        modelName = "claude-sonnet-4-20250514",
        maxTotalTokens = 200000,
        maxOutTokens = 64000,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 3.0 / 1000.0,
        outputTokenPricePerK = 15.0 / 1000.0,
    )

    val Claude35Haiku = ChatModel(
        name = "Claude35Haiku",
        modelName = "claude-3-5-haiku-latest",
        maxTotalTokens = 200000,
        maxOutTokens = 8192,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 0.80 / 1000.0,
        outputTokenPricePerK = 4.0 / 1000.0,
    )
    val Claude45Haiku = ChatModel(
        name = "Claude45Haiku",
        modelName = "claude-haiku-4-5-20251001",
        maxTotalTokens = 200000,
        maxOutTokens = 64000,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 1.0 / 1000.0,
        outputTokenPricePerK = 5.0 / 1000.0,
    )
    val Claude45Sonnet = ChatModel(
        name = "Claude45Sonnet",
        modelName = "claude-sonnet-4-5-20250929",
        maxTotalTokens = 200000,
        maxOutTokens = 64000,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 3.0 / 1000.0,
        outputTokenPricePerK = 15.0 / 1000.0,
    )
    val Claude45Opus = ChatModel(
        name = "Claude45Opus",
        modelName = "claude-opus-4-5-20251101",
        maxTotalTokens = 200000,
        maxOutTokens = 64000,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 5.0 / 1000.0,
        outputTokenPricePerK = 25.0 / 1000.0,
    )

    val values = mapOf(
        "Claude41Opus" to Claude41Opus,
        "Claude4Sonnet" to Claude4Sonnet,
        "Claude45Sonnet" to Claude45Sonnet,
        "Claude35Haiku" to Claude35Haiku,
        "Claude45Haiku" to Claude45Haiku,
    )

}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/ChatInterface.kt

```
package com.simiacryptus.cognotik.chat.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.ChatMessage
import com.simiacryptus.cognotik.models.ModelSchema.ChatRequest
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

class ChatInterface(
    val logStreams: MutableList<BufferedOutputStream>,
    private val key: SecureString,
    private val base: String,
    private val logLevel: Level,
    private val temperature: Double,
    val provider: APIProvider,
    val modelType: ChatModel,
    private val workPool: ExecutorService,
    private val scheduledPool: ListeningScheduledExecutorService,
    private val onUsage: (model: LLMModel, tokens: ModelSchema.Usage) -> Unit,
) {
    init {
        //require(key != null) { "API key must be provided" }
        require(base.isNotBlank()) { "Base URL must be provided" }
        require(temperature in 0.0..2.0) { "Temperature must be in range [0.0, 2.0]" }
    }

    fun chat(
        messages: List<ChatMessage>,
    ) = provider.getChatClient(
        key = key,
        base = base,
        workPool = workPool,
        logLevel = logLevel,
        logStreams = logStreams,
        scheduledPool = scheduledPool,
    ).apply {
        onUsageListeners.add { model, usage -> onUsage(model, usage) }
    }.chat(
        chatRequest = ChatRequest(
            model = modelType.modelName,
            messages = messages,
            temperature = temperature,
        ),
        model = modelType,
        logStreams = logStreams
    )

    @JsonIgnore
    fun getChildClient(): ChatInterface = ChatInterface(
        logStreams = this.logStreams.toTypedArray().toMutableList(),
        key = this.key,
        base = this.base,
        logLevel = this.logLevel,
        temperature = this.temperature,
        provider = this.provider,
        modelType = this.modelType,
        workPool = this.workPool,
        scheduledPool = this.scheduledPool,
        onUsage = this.onUsage,
    )

}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/ChatModel.kt

```
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
    modelName: String = name,
    maxTotalTokens: Int = -1,
    maxOutTokens: Int = maxTotalTokens,
    provider: APIProvider? = null,
    val inputTokenPricePerK: Double = 0.0,
    val outputTokenPricePerK: Double = inputTokenPricePerK,
) : LLMModel(
    modelName = modelName,
    maxTotalTokens = maxTotalTokens,
    maxOutTokens = maxOutTokens,
    provider = provider,
) {
    override fun toString() = modelName

    override fun pricing(usage: Usage): Double {
        val promptCost = usage.prompt_tokens * inputTokenPricePerK
        val completionCost = usage.completion_tokens * outputTokenPricePerK
        val estimatedUnaccountedCost = (usage.total_tokens - (usage.prompt_tokens + usage.completion_tokens)) * ((inputTokenPricePerK + outputTokenPricePerK) / 2)
        return (promptCost + completionCost + estimatedUnaccountedCost) / 1000.0
    }

    fun instance(
        key: SecureString,
        base: String = provider?.base!!,
        logLevel: Level = Level.DEBUG,
        logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
        workPool: ExecutorService = Executors.newFixedThreadPool(4),
        temperature: Double = 0.1,
        scheduledPool: ListeningScheduledExecutorService = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)),
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
            (OpenAIModels.values +
                    PerplexityModels.values +
                    MistralModels.values +
                    GroqModels.values +
                    ModelsLabModels.values +
                    AWSModels.values +
                    AnthropicModels.values +
                    DeepSeekModels.values +
                    GeminiModels.values).toMutableMap()
        }

    }
}

class ChatModelsSerializer : StdSerializer<ChatModel>(ChatModel::class.java) {
    override fun serialize(value: ChatModel, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject()
        gen.writeStringField("name", value.name)
        gen.writeStringField("modelName", value.modelName)
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
                    modelName = modelName,
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

```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/DeepSeekModels.kt

```
package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider

object DeepSeekModels {
    val DeepSeekChat = ChatModel(
        name = "DeepSeekChat",
        modelName = "deepseek-chat",
        maxTotalTokens = 64000,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.DeepSeek,
        inputTokenPricePerK = 0.14 / 1000.0,
        outputTokenPricePerK = 0.28 / 1000.0
    )
    val DeepSeekCoder = ChatModel(
        name = "DeepSeekCoder",
        modelName = "deepseek-coder",
        maxTotalTokens = 64000,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.DeepSeek,
        inputTokenPricePerK = 0.14 / 1000.0,
        outputTokenPricePerK = 0.28 / 1000.0
    )
    val DeepSeekReasoner = ChatModel(
        name = "DeepSeekReasoner",
        modelName = "deepseek-reasoner",
        maxTotalTokens = 64000,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.DeepSeek,
        inputTokenPricePerK = 0.55 / 1000.0,
        outputTokenPricePerK = 2.19 / 1000.0
    )
    val values = mapOf(
        "DeepSeekChat" to DeepSeekChat,
        "DeepSeekCoder" to DeepSeekCoder,
        "DeepSeekReasoner" to DeepSeekReasoner,
    )

}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/GeminiModels.kt

```
package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider

@Suppress("unused")
object GeminiModels {
    @JvmStatic
    val  GeminiPro_15 = ChatModel(
        name = "GeminiPro_15",
        modelName = "gemini-1.5-pro",
        maxTotalTokens = 2097152,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00125,
        outputTokenPricePerK = 0.005
    )

    @JvmStatic
    val  GeminiPro_10 = ChatModel(
        name = "GeminiPro_10",
        modelName = "gemini-1.0-pro",
        maxTotalTokens = 2097152,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00025,
        outputTokenPricePerK = 0.0005
    )

    @JvmStatic
    val  GeminiFlash_15 = ChatModel(
        name = "GeminiFlash_15",
        modelName = "gemini-1.5-flash",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.000075,
        outputTokenPricePerK = 0.0003
    )

    // Deprecated
    @JvmStatic
    val  GeminiFlash_15_8B = ChatModel(
        name = "GeminiFlash_15_8B",
        modelName = "gemini-1.5-flash-8b",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.0000375,
        outputTokenPricePerK = 0.00015
    )

    @JvmStatic
    val  GeminiFlash_20 = ChatModel(
        name = "GeminiFlash_20",
        modelName = "gemini-2.0-flash",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.0001,
        outputTokenPricePerK = 0.0004
    )

    @JvmStatic
    val  GeminiFlash_20_Lite = ChatModel(
        name = "GeminiFlash_20_Lite",
        modelName = "gemini-2.0-flash-lite",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00005,
        outputTokenPricePerK = 0.0002
    )

    @JvmStatic
    val  GeminiFlash_20_Live = ChatModel(
        name = "GeminiFlash_20_Live",
        modelName = "gemini-2.0-flash-live-001",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.0001,
        outputTokenPricePerK = 0.0004
    )

    @JvmStatic
    val  GeminiFlash_20_Preview_Image_Generation = ChatModel(
        name = "GeminiFlash_20_Preview_Image_Generation",
        modelName = "gemini-2.0-flash-exp-image-generation",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.0001,
        outputTokenPricePerK = 0.0004
    )

    @JvmStatic
    val  GeminiFlash_25_Image_Generation = ChatModel(
        name = "GeminiFlash_20_Preview_Image_Generation",
        modelName = "gemini-2.5-flash-image-preview",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.0001, // ???
        outputTokenPricePerK = 0.0004 // ???
    )

    @JvmStatic
    val  GeminiPro_25 = ChatModel(
        name = "GeminiPro_25",
        modelName = "gemini-2.5-pro-preview-03-25",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.0015,
        outputTokenPricePerK = 0.006
    )

    @JvmStatic
    val  GeminiFlash_25 = ChatModel(
        name = "GeminiFlash_25",
        modelName = "gemini-2.5-flash",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00015,
        outputTokenPricePerK = 0.0006
    )
    @JvmStatic
    val  GeminiFlash_25_Lite = ChatModel(
        name = "GeminiFlash_25_Lite",
        modelName = "gemini-2.5-flash-lite-preview-06-17",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00005,
        outputTokenPricePerK = 0.0002
    )
    @JvmStatic
    val  GeminiFlash_25_Live = ChatModel(
        name = "GeminiFlash_25_Live",
        modelName = "gemini-2.5-flash-preview-05-20",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00015,
        outputTokenPricePerK = 0.0006
    )
    @JvmStatic
    val  GeminiFlash_25_Preview_Native_Audio_Dialog = ChatModel(
        name = "GeminiFlash_25_Preview_Native_Audio_Dialog",
        modelName = "gemini-2.5-flash-preview-native-audio-dialog",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00015,
        outputTokenPricePerK = 0.0006
    )
    @JvmStatic
    val  GeminiFlash_25_Exp_Native_Audio_Thinking_Dialog = ChatModel(
        name = "GeminiFlash_25_Exp_Native_Audio_Thinking_Dialog",
        modelName = "gemini-2.5-flash-exp-native-audio-thinking-dialog",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00015,
        outputTokenPricePerK = 0.0006
    )
    @JvmStatic
    val  GeminiFlash_25_Preview_TTS = ChatModel(
        name = "GeminiFlash_25_Preview_TTS",
        modelName = "gemini-2.5-flash-preview-tts",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00015,
        outputTokenPricePerK = 0.0006
    )
    @JvmStatic
    val  GeminiPro_25_Preview_TTS = ChatModel(
        name = "GeminiPro_25_Preview_TTS",
        modelName = "gemini-2.5-pro-preview-tts",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.0015,
        outputTokenPricePerK = 0.006
    )
    @JvmStatic
    val  GeminiPro_30_Preview = ChatModel(
        name = "GeminiPro_30_Preview",
        modelName = "gemini-3-pro-preview",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.0015,
        outputTokenPricePerK = 0.006
    )
    @JvmStatic
    val  GeminiPro_30_Image_Preview = ChatModel(
        name = "GeminiPro_30_Image_Preview",
        modelName = "gemini-3-pro-image-preview",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.0015,
        outputTokenPricePerK = 0.006
    )
    @JvmStatic
    val  GeminiFlash_30_Preview = ChatModel(
        name = "GeminiFlash_30_Preview",
        modelName = "gemini-3-flash-preview",
        maxTotalTokens = 1048576,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.00015,
        outputTokenPricePerK = 0.0006
    )
    @JvmStatic
    val  GeminiRobotics_15_Preview = ChatModel(
        name = "GeminiRobotics_15_Preview",
        modelName = "gemini-robotics-er-1.5-preview",
        maxTotalTokens = 1048576,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Gemini,
        inputTokenPricePerK = 0.000075,
        outputTokenPricePerK = 0.0003
    )


    @JvmStatic
    val  values = mapOf(
        "GeminiPro_15" to GeminiPro_15,
        "GeminiFlash_15" to GeminiFlash_15,
        "GeminiFlash_15_8B" to GeminiFlash_15_8B,
        "GeminiPro" to GeminiPro_10,
        "GeminiFlash_20" to GeminiFlash_20,
        "GeminiFlash_20_Lite" to GeminiFlash_20_Lite,
        "GeminiFlash_20_Live" to GeminiFlash_20_Live,
        "GeminiPro_25" to GeminiPro_25,
        "GeminiFlash_25" to GeminiFlash_25,
        "GeminiFlash_25_Lite" to GeminiFlash_25_Lite,
        "GeminiFlash_25_Live" to GeminiFlash_25_Live,
        "GeminiPro_30_Preview" to GeminiPro_30_Preview,
        "GeminiPro_30_Image_Preview" to GeminiPro_30_Image_Preview,
        "GeminiFlash_30_Preview" to GeminiFlash_30_Preview,
        "GeminiRobotics_15_Preview" to GeminiRobotics_15_Preview,
    )
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/GroqModels.kt

```
package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider

object GroqModels {

    val Llama33_70bVersatile = ChatModel(
        name = "Llama33_70bVersatile",
        modelName = "llama-3.3-70b-versatile",
        maxTotalTokens = 128000,
        maxOutTokens = 32768,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.59,
        outputTokenPricePerK = 0.79
    )

    val Gemma2_9b = ChatModel(
        name = "Gemma2_9b",
        modelName = "gemma2-9b-it",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.20,
        outputTokenPricePerK = 0.20
    )

    val Llama33_70bSpecDec = ChatModel(
        name = "Llama33_70bSpecDec",
        modelName = "llama-3.3-70b-specdec",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.59,
        outputTokenPricePerK = 0.99
    )

    val Llama31_8bInstant = ChatModel(
        name = "Llama31_8bInstant",
        modelName = "llama-3.1-8b-instant",
        maxTotalTokens = 128000,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.05,
        outputTokenPricePerK = 0.08
    )

    val Llama32_1bPreview = ChatModel(
        name = "Llama32_1bPreview",
        modelName = "llama-3.2-1b-preview",
        maxTotalTokens = 128000,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.04,
        outputTokenPricePerK = 0.04
    )

    val Llama32_3bPreview = ChatModel(
        name = "Llama32_3bPreview",
        modelName = "llama-3.2-3b-preview",
        maxTotalTokens = 128000,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.06,
        outputTokenPricePerK = 0.06
    )

    val LlamaGuard38b = ChatModel(
        name = "LlamaGuard38b",
        modelName = "llama-guard-3-8b",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.20,
        outputTokenPricePerK = 0.20
    )

    val Llama370b8192 = ChatModel(
        name = "Llama370b8192",
        modelName = "llama3-70b-8192",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.59,
        outputTokenPricePerK = 0.79
    )

    val Llama38b8192 = ChatModel(
        name = "Llama38b8192",
        modelName = "llama3-8b-8192",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.05,
        outputTokenPricePerK = 0.08
    )

    val Qwen25_32b = ChatModel(
        name = "Qwen25_32b",
        modelName = "qwen-2.5-32b",
        maxTotalTokens = 128000,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.30,
        outputTokenPricePerK = 0.30
    )
    val Qwen25Coder32b = ChatModel(
        name = "Qwen25Coder32b",
        modelName = "qwen-2.5-coder-32b",
        maxTotalTokens = 128000,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.30,
        outputTokenPricePerK = 0.30
    )
    val QwenQwq32b = ChatModel(
        name = "QwenQwq32b",
        modelName = "qwen-qwq-32b",
        maxTotalTokens = 128000,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.30,
        outputTokenPricePerK = 0.30
    )
    val MistralSaba24b = ChatModel(
        name = "MistralSaba24b",
        modelName = "mistral-saba-24b",
        maxTotalTokens = 32000,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.25,
        outputTokenPricePerK = 0.25
    )

    val DeepseekQwen32b = ChatModel(
        name = "DeepseekQwen32b",
        modelName = "deepseek-r1-distill-qwen-32b",
        maxTotalTokens = 128000,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.30,
        outputTokenPricePerK = 0.30
    )

    val DeepseekLlama70b = ChatModel(
        name = "DeepseekLlama70b",
        modelName = "deepseek-r1-distill-llama-70b",
        maxTotalTokens = 131072,
        maxOutTokens = 131072,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.59,
        outputTokenPricePerK = 0.79
    )

    val Llama32_11bVision = ChatModel(
        name = "Llama32_11bVision",
        modelName = "llama-3.2-11b-vision-preview",
        maxTotalTokens = 128000,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.10,
        outputTokenPricePerK = 0.10
    )

    val Llama32_90bVision = ChatModel(
        name = "Llama32_90bVision",
        modelName = "llama-3.2-90b-vision-preview",
        maxTotalTokens = 128000,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.70,
        outputTokenPricePerK = 0.70
    )
    val LlamaPromptGuard2_22m = ChatModel(
        name = "LlamaPromptGuard2_22m",
        modelName = "meta-llama/llama-prompt-guard-2-22m",
        maxTotalTokens = 512,
        maxOutTokens = 512,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.05,
        outputTokenPricePerK = 0.05
    )
    val LlamaPromptGuard2_86m = ChatModel(
        name = "LlamaPromptGuard2_86m",
        modelName = "meta-llama/llama-prompt-guard-2-86m",
        maxTotalTokens = 512,
        maxOutTokens = 512,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.05,
        outputTokenPricePerK = 0.05
    )
    val KimiK2Instruct = ChatModel(
        name = "KimiK2Instruct",
        modelName = "moonshotai/kimi-k2-instruct",
        maxTotalTokens = 131072,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.30,
        outputTokenPricePerK = 0.30
    )
    val GptOss120b = ChatModel(
        name = "GptOss120b",
        modelName = "openai/gpt-oss-120b",
        maxTotalTokens = 131072,
        maxOutTokens = 32766,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 1.00,
        outputTokenPricePerK = 1.00
    )
    val GptOss20b = ChatModel(
        name = "GptOss20b",
        modelName = "openai/gpt-oss-20b",
        maxTotalTokens = 131072,
        maxOutTokens = 32768,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.50,
        outputTokenPricePerK = 0.50
    )
    val PlayAiTts = ChatModel(
        name = "PlayAiTts",
        modelName = "playai-tts",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.10,
        outputTokenPricePerK = 0.10
    )
    val PlayAiTtsArabic = ChatModel(
        name = "PlayAiTtsArabic",
        modelName = "playai-tts-arabic",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.10,
        outputTokenPricePerK = 0.10
    )
    val Qwen3_32b = ChatModel(
        name = "Qwen3_32b",
        modelName = "qwen/qwen3-32b",
        maxTotalTokens = 131072,
        maxOutTokens = 40960,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.30,
        outputTokenPricePerK = 0.30
    )

    val Llama4Scout17b = ChatModel(
        name = "Llama4Scout17b",
        modelName = "meta-llama/llama-4-scout-17b-16e-instruct",
        maxTotalTokens = 131072,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.20,
        outputTokenPricePerK = 0.20
    )
    val Llama4Maverick17b = ChatModel(
        name = "Llama4Maverick17b",
        modelName = "meta-llama/llama-4-maverick-17b-128e-instruct",
        maxTotalTokens = 131072,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.20,
        outputTokenPricePerK = 0.20
    )
    val Allam2_7b = ChatModel(
        name = "Allam2_7b",
        modelName = "allam-2-7b",
        maxTotalTokens = 4096,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.10,
        outputTokenPricePerK = 0.10
    )

    val values = mapOf(
        "Llama33_70bVersatile" to Llama33_70bVersatile,
        "Gemma2_9b" to Gemma2_9b,
        "Llama31_8bInstant" to Llama31_8bInstant,
        "Llama32_1bPreview" to Llama32_1bPreview,
        "Llama32_3bPreview" to Llama32_3bPreview,
        "Llama33_70bSpecDec" to Llama33_70bSpecDec,
        "LlamaGuard38b" to LlamaGuard38b,
        "Llama370b8192" to Llama370b8192,
        "Llama38b8192" to Llama38b8192,
        "Qwen25_32b" to Qwen25_32b,
        "Qwen25Coder32b" to Qwen25Coder32b,
        "QwenQwq32b" to QwenQwq32b,
        "MistralSaba24b" to MistralSaba24b,
        "DeepseekQwen32b" to DeepseekQwen32b,
        "DeepseekLlama70b" to DeepseekLlama70b,
        "Llama32_11bVision" to Llama32_11bVision,
        "Llama32_90bVision" to Llama32_90bVision,
        "LlamaPromptGuard2_22m" to LlamaPromptGuard2_22m,
        "LlamaPromptGuard2_86m" to LlamaPromptGuard2_86m,
        "KimiK2Instruct" to KimiK2Instruct,
        "GptOss120b" to GptOss120b,
        "GptOss20b" to GptOss20b,
        "PlayAiTts" to PlayAiTts,
        "PlayAiTtsArabic" to PlayAiTtsArabic,
        "Qwen3_32b" to Qwen3_32b,
        "Llama4Scout17b" to Llama4Scout17b,
        "Llama4Maverick17b" to Llama4Maverick17b,
        "Allam2_7b" to Allam2_7b
    )
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/MistralModels.kt

```
package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider

object MistralModels {

    val Mistral7B = ChatModel(
        name = "Mistral7B",
        modelName = "open-mistral-7b",
        maxTotalTokens = 32768,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )

    val Mixtral8x7B = ChatModel(
        name = "Mixtral8x7B",
        modelName = "open-mixtral-8x7b",
        maxTotalTokens = 32768,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val Mixtral8x22B = ChatModel(
        name = "Mixtral8x22B",
        modelName = "open-mixtral-8x22b",
        maxTotalTokens = 65536,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,

        outputTokenPricePerK = 0.0015

    )
    val MistralSmall = ChatModel(
        name = "MistralSmall",
        modelName = "mistral-small-latest",
        maxTotalTokens = 32768,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val MistralMedium = ChatModel(
        name = "MistralMedium",
        modelName = "mistral-medium-latest",
        maxTotalTokens = 32768,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val MistralLarge = ChatModel(
        name = "MistralLarge",
        modelName = "mistral-large-latest",
        maxTotalTokens = 32768,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,

        outputTokenPricePerK = 0.0015

    )
    val MistralNemo = ChatModel(
        name = "MistralNemo",
        modelName = "open-mistral-nemo",
        maxTotalTokens = 128 * 1024 - 1,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,

        outputTokenPricePerK = 0.0015

    )
    val Codestral = ChatModel(
        name = "Codestral",
        modelName = "codestral-latest",
        maxTotalTokens = 32768,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,

        outputTokenPricePerK = 0.0015

    )
    val CodestralMamba = ChatModel(
        name = "CodestralMamba",
        modelName = "open-codestral-mamba",
        maxTotalTokens = 128 * 1024 - 1,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,

        outputTokenPricePerK = 0.0015

    )
    val values = mapOf(
        "Mistral7B" to Mistral7B,
        "Mixtral8x7B" to Mixtral8x7B,
        "Mixtral8x22B" to Mixtral8x22B,
        "MistralSmall" to MistralSmall,
        "MistralMedium" to MistralMedium,
        "MistralLarge" to MistralLarge,
        "MistralNemo" to MistralNemo,
        "Codestral" to Codestral,
        "CodestralMamba" to CodestralMamba,
    )

}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/ModelsLabDataModel.kt

```
package com.simiacryptus.cognotik.chat.model

class ModelsLabDataModel {

    data class ChatRequest(
        val key: String? = null,
        val model_id: String? = null,
        val chat_id: String? = null,
        val system_prompt: String? = null,
        val prompt: String? = null,
        val max_new_tokens: Int? = null,
        val do_sample: Boolean? = null,
        val temperature: Double? = null,
        val top_k: Int? = null,
        val top_p: Double? = null,
        val no_repeat_ngram_size: Int? = null,
        val seed: Int? = null,
        val temp: Boolean? = null,
        val reset: Boolean? = null,
        val uncensored_system_prompt: Boolean? = null,
        val webhook: String? = null,
        val track_id: String? = null,
    )

    data class ChatResponse(
        val status: String? = null,
        val output: Any? = null,
        val message: String? = null,
        val chat_id: String? = null,
        val meta: Meta? = null,
        val eta: Int? = null,
    )

    data class Meta(
        val chat_id: String? = null,
        val created_at: String? = null,
        val do_sample: String? = null,
        val max_new_tokens: Int? = null,
        val model_id: String? = null,
        val no_repeat_ngram_size: Int? = null,
        val num_return_sequences: Int? = null,
        val pipeline_tag: String? = null,
        val prompt: String? = null,
        val seed: Long? = null,
        val temp: String? = null,
        val temperature: Double? = null,
        val top_k: Int? = null,
        val top_p: Double? = null,
        val updated_at: String? = null
    )

}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/ModelsLabModels.kt

```
package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider

object ModelsLabModels {

    val Zephyr7bBeta = ChatModel(
        name = "Zephyr7bBeta",
        modelName = "zephyr-7b-beta",
        maxTotalTokens = 16384,
        provider = APIProvider.Companion.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val DialoGPTLarge = ChatModel(
        name = "DialoGPTLarge",
        modelName = "DialoGPT-large",
        maxTotalTokens = 16384,
        provider = APIProvider.Companion.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val YarnMistral7b128k = ChatModel(
        name = "YarnMistral7b128k",
        modelName = "Yarn-Mistral-7b-128k",
        maxTotalTokens = 16384,
        provider = APIProvider.Companion.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val Pygmalion13b = ChatModel(
        name = "Pygmalion13b",
        modelName = "pygmalion-1.3b",
        maxTotalTokens = 16384,
        provider = APIProvider.Companion.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val Opt67b = ChatModel(
        name = "Opt67b",
        modelName = "opt-6.7b",
        maxTotalTokens = 16384,
        provider = APIProvider.Companion.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val MistralLite = ChatModel(
        name = "MistralLite",
        modelName = "MistralLite",
        maxTotalTokens = 16384,
        provider = APIProvider.Companion.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val Openchat35 = ChatModel(
        name = "Openchat35",
        modelName = "openchat_3.5",
        maxTotalTokens = 16384,
        provider = APIProvider.Companion.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val NeuralChat7bV3 = ChatModel(
        name = "NeuralChat7bV3",
        modelName = "neural-chat-7b-v3",
        maxTotalTokens = 16384,
        provider = APIProvider.Companion.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val OpenHermes25Mistral7B = ChatModel(
        name = "OpenHermes25Mistral7B",
        modelName = "OpenHermes-2.5-Mistral-7B",
        maxTotalTokens = 16384,
        provider = APIProvider.Companion.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val Dolphin221Mistral7b = ChatModel(
        name = "Dolphin221Mistral7b",
        modelName = "dolphin-2.2.1-mistral-7b",
        maxTotalTokens = 16384,
        provider = APIProvider.Companion.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val Mistral7BOpenOrca = ChatModel(
        name = "Mistral7BOpenOrca",
        modelName = "Mistral-7B-OpenOrca",
        maxTotalTokens = 16384,
        provider = APIProvider.Companion.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )

    val DeepseekCoder67bInstruct = ChatModel(
        name = "DeepseekCoder67bInstruct",
        modelName = "deepseek-coder-6.7b-instruct",
        maxTotalTokens = 16384,
        provider = APIProvider.Companion.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val Phi15 = ChatModel(
        name = "Phi15",
        modelName = "phi-1_5",
        maxTotalTokens = 16384,
        provider = APIProvider.Companion.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val Zephyr7bAlpha = ChatModel(
        name = "Zephyr7bAlpha",
        modelName = "zephyr-7b-alpha",
        maxTotalTokens = 16384,
        provider = APIProvider.Companion.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val values = mapOf(
        "Zephyr7bBeta" to Zephyr7bBeta,
        "DialoGPTLarge" to DialoGPTLarge,
        "YarnMistral7b128k" to YarnMistral7b128k,
        "Pygmalion13b" to Pygmalion13b,
        "Opt67b" to Opt67b,
        "MistralLite" to MistralLite,
        "Openchat35" to Openchat35,
        "NeuralChat7bV3" to NeuralChat7bV3,
        "OpenHermes25Mistral7B" to OpenHermes25Mistral7B,
        "Dolphin221Mistral7b" to Dolphin221Mistral7b,
        "Mistral7BOpenOrca" to Mistral7BOpenOrca,
        "DeepseekCoder67bInstruct" to DeepseekCoder67bInstruct,
        "Phi15" to Phi15,
        "Zephyr7bAlpha" to Zephyr7bAlpha,
    )

}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/OpenAIModels.kt

```
package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider

object OpenAIModels {
    val GPT4Turbo = ChatModel(
        name = "GPT4Turbo",
        modelName = "gpt-4-turbo",
        maxTotalTokens = 128000,
        provider = APIProvider.Companion.OpenAI,
        inputTokenPricePerK = 0.010,
        outputTokenPricePerK = 0.030
    )

    val GPT4o = ChatModel(
        name = "GPT4o",
        modelName = "gpt-4o",
        maxTotalTokens = 128000,
        provider = APIProvider.Companion.OpenAI,
        inputTokenPricePerK = 0.0025,
        outputTokenPricePerK = 0.010
    )

    val GPT45 = ChatModel(
        name = "GPT45",
        modelName = "gpt-4.5-preview-2025-02-27",
        maxTotalTokens = 128000,
        provider = APIProvider.Companion.OpenAI,
        inputTokenPricePerK = 0.075,
        outputTokenPricePerK = 0.150
    )

    val GPT4oMini = ChatModel(
        name = "GPT4oMini",
        modelName = "gpt-4o-mini",
        maxTotalTokens = 128000,
        provider = APIProvider.Companion.OpenAI,
        inputTokenPricePerK = 0.00015,
        outputTokenPricePerK = 0.00060
    )

    val O1Preview = ChatModel(
        name = "O1Preview",
        modelName = "o1-preview",
        maxTotalTokens = 128 * 1024,
        provider = APIProvider.Companion.OpenAI,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015,
    )

    val O1 = ChatModel(
        name = "O1",
        modelName = "o1",
        maxTotalTokens = 128 * 1024,
        provider = APIProvider.Companion.OpenAI,
        inputTokenPricePerK = 0.015,
        outputTokenPricePerK = 0.060,
    )

    val O1Mini = ChatModel(
        name = "O1Mini",
        modelName = "o1-mini",
        maxTotalTokens = 128 * 1024,
        provider = APIProvider.Companion.OpenAI,
        inputTokenPricePerK = 0.00110,
        outputTokenPricePerK = 0.00440,
    )

    val O3Mini = ChatModel(
        name = "O3Mini",
        modelName = "o3-mini",
        maxTotalTokens = 128 * 1024,
        provider = APIProvider.Companion.OpenAI,
        inputTokenPricePerK = 0.00110,
        outputTokenPricePerK = 0.00440,
    )
    val O4Mini = ChatModel(
        name = "O4Mini",
        modelName = "o4-mini",
        maxTotalTokens = 200000,
        provider = APIProvider.Companion.OpenAI,
        inputTokenPricePerK = 0.00110,
        outputTokenPricePerK = 0.00440,
    )
    val O3 = ChatModel(
        name = "O3",
        modelName = "o3",
        maxTotalTokens = 200000,
        provider = APIProvider.Companion.OpenAI,
        inputTokenPricePerK = 0.010,
        outputTokenPricePerK = 0.040,
    )

    val GPT41 = ChatModel(
        name = "GPT 4.1",
        modelName = "gpt-4.1-2025-04-14",
        maxTotalTokens = 1048576,
        provider = APIProvider.Companion.OpenAI,
        inputTokenPricePerK = 2.00 / 1000,
        outputTokenPricePerK = 8.00 / 1000,
    )

    val GPT41Mini = ChatModel(
        name = "GPT 4.1 Mini",
        modelName = "gpt-4.1-mini-2025-04-14",
        maxTotalTokens = 1048576,
        provider = APIProvider.Companion.OpenAI,
        inputTokenPricePerK = 0.40 / 1000,
        outputTokenPricePerK = 1.60 / 1000,
    )

    val GPT41Nano = ChatModel(
        name = "GPT 4.1 Nano",
        modelName = "gpt-4.1-nano-2025-04-14",
        maxTotalTokens = 1048576,
        provider = APIProvider.Companion.OpenAI,
        inputTokenPricePerK = 0.10 / 1000,
        outputTokenPricePerK = 0.40 / 1000,
    )

    val values: Map<String, ChatModel> = mapOf(
        "GPT4Turbo" to GPT4Turbo,
        "GPT4o" to GPT4o,
        "GPT4oMini" to GPT4oMini,
        "O1Preview" to O1Preview,
        "O1Mini" to O1Mini,
        "O3Mini" to O3Mini,
        "O1" to O1,
        "O4Mini" to O4Mini,
        "O3" to O3,
        "GPT45" to GPT45,
        "GPT41" to GPT41,
        "GPT41Mini" to GPT41Mini,
        "GPT41Nano" to GPT41Nano,
    )

}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/PerplexityModels.kt

```
package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider

object PerplexityModels {

    val SonarSmallChat128k = run {
        val maxTotalTokens = 128 * 1024
        ChatModel(
            name = "SonarSmallChat128k",
            modelName = "llama-3.1-sonar-small-128k-chat",
            maxTotalTokens = maxTotalTokens,
            maxOutTokens = maxTotalTokens,
            provider = APIProvider.Companion.Perplexity,
            inputTokenPricePerK = 0.0005,
            outputTokenPricePerK = 0.0015
        )
    }

    val SonarSmallOnline128k = run {
        val maxTotalTokens = 128 * 1024
        ChatModel(
            name = "SonarSmallOnline128k",
            modelName = "llama-3.1-sonar-small-128k-online",
            maxTotalTokens = maxTotalTokens,
            maxOutTokens = maxTotalTokens,
            provider = APIProvider.Companion.Perplexity,
            inputTokenPricePerK = 0.0005,
            outputTokenPricePerK = 0.0015
        )
    }

    val SonarLargeChat128k = run {
        val maxTotalTokens = 128 * 1024
        ChatModel(
            name = "SonarLargeChat128k",
            modelName = "llama-3.1-sonar-large-128k-chat",
            maxTotalTokens = maxTotalTokens,
            maxOutTokens = maxTotalTokens,
            provider = APIProvider.Companion.Perplexity,
            inputTokenPricePerK = 0.0005,
            outputTokenPricePerK = 0.0015
        )
    }

    val SonarLargeOnline128k = run {
        val maxTotalTokens = 128 * 1024
        ChatModel(
            name = "SonarLargeOnline128k",
            modelName = "llama-3.1-sonar-large-128k-online",
            maxTotalTokens = maxTotalTokens,
            maxOutTokens = maxTotalTokens,
            provider = APIProvider.Companion.Perplexity,
            inputTokenPricePerK = 0.0005,
            outputTokenPricePerK = 0.0015
        )
    }
    val values = mapOf(
        "SonarSmallChat128k" to SonarSmallChat128k,
        "SonarSmallOnline128k" to SonarSmallOnline128k,
        "SonarLargeChat128k" to SonarLargeChat128k,
        "SonarLargeOnline128k" to SonarLargeOnline128k,
    )

}
```

# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated `README.md` for the `com.simiacryptus.cognotik.chat.model` package, summarizing the chat model abstractions and the extensive set of predefined model configurations.

### core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/README.md
```markdown
# Chat Models

The `com.simiacryptus.cognotik.chat.model` package provides a unified abstraction for interacting with various Large Language Model (LLM) providers. It includes core classes for model definition and execution, along with a comprehensive library of predefined model configurations for major AI providers.

## Core Components

### [ChatModel](ChatModel.kt)
The base class for all chat models. It extends `LLMModel` and encapsulates:
- **Metadata**: Model name, provider, and token limits (`maxTotalTokens`, `maxOutTokens`).
- **Pricing**: Logic for calculating costs based on input and output token usage.
- **Serialization**: Custom Jackson serializers/deserializers for persisting model configurations.
- **Instantiation**: The `instance()` method creates a `ChatInterface` for active interaction.

### [ChatInterface](ChatInterface.kt)
Represents an active session with a specific model. It handles:
- **Configuration**: Manages API keys, base URLs, temperature, and logging.
- **Execution**: Provides the `chat()` method to send messages and receive responses via the provider's client.
- **Usage Tracking**: Reports token usage and costs via callbacks.

## Supported Providers and Models

The package includes predefined configurations for a wide array of models across multiple providers:

| Provider | Description | Key Models |
| :--- | :--- | :--- |
| **[AWS](AWSModels.kt)** | Models hosted on AWS Bedrock. | Llama 3.1 (8b to 405b), Mistral Large, Claude 3/3.5/3.7, Amazon Nova, Titan. |
| **[Anthropic](AnthropicModels.kt)** | Native Anthropic Claude models. | Claude 3.5 Haiku, Claude 4/4.5 (Sonnet, Opus, Haiku). |
| **[DeepSeek](DeepSeekModels.kt)** | DeepSeek's specialized models. | DeepSeek Chat, Coder, and Reasoner. |
| **[Gemini](GeminiModels.kt)** | Google's Gemini family. | Gemini 1.5/2.0/2.5/3.0 (Pro, Flash, Flash-Lite). |
| **[Groq](GroqModels.kt)** | High-performance inference models. | Llama 3.3, Qwen 2.5, DeepSeek R1 Distill, Vision models. |
| **[Mistral](MistralModels.kt)** | Mistral AI's native models. | Mistral Large/Medium/Small, Mixtral 8x7B/8x22B, Codestral. |
| **[OpenAI](OpenAIModels.kt)** | OpenAI's flagship models. | GPT-4o, GPT-4.5, O1/O3/O4 series (including Mini and Preview). |
| **[Perplexity](PerplexityModels.kt)** | Search-optimized models. | Sonar Small/Large (Chat and Online variants). |
| **[ModelsLab](ModelsLabModels.kt)** | Open-source models via ModelsLab. | Zephyr, MistralLite, OpenHermes, Dolphin. |

## Usage Example

To use a model, select a predefined instance and create a `ChatInterface`:

```kotlin
val model = OpenAIModels.GPT4o
val chatInterface = model.instance(
    key = SecureString("your-api-key"),
    temperature = 0.7
)

val response = chatInterface.chat(listOf(
    ChatMessage(Role.system, "You are a helpful assistant."),
    ChatMessage(Role.user, "Hello!")
))
```

## Data Models

- **[ModelsLabDataModel](ModelsLabDataModel.kt)**: Contains specific request/response structures for the ModelsLab API.
```

### Summary of Changes
- Created a new `README.md` in `core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/`.
- Provided a high-level overview of the `ChatModel` and `ChatInterface` abstractions.
- Included a categorized table of supported providers (AWS, OpenAI, Anthropic, etc.) based on the provided Kotlin source files.
- Added a usage example demonstrating how to instantiate and use a model.
- Documented the purpose of the various `*Models.kt` files as predefined configuration libraries.
</details>

                - <a href='fileIndex/G-20260120-RPFC/core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/README.md'>core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/README.md'>core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/README.md</a> Updated
