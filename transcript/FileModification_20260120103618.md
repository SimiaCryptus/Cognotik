# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/models/AIModel.kt

```
package com.simiacryptus.cognotik.models

interface AIModel {
    val modelName: String?
    val provider: APIProvider?
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/models/APIProvider.kt

```
package com.simiacryptus.cognotik.models

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.audio.AudioModels
import com.simiacryptus.cognotik.chat.*
import com.simiacryptus.cognotik.chat.model.*
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.embedding.OllamaEmbeddingModels
import com.simiacryptus.cognotik.embedding.OpenAIEmbeddingModels
import com.simiacryptus.cognotik.image.*
import com.simiacryptus.cognotik.util.*
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.Logger
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val log: Logger = LoggerFactory.getLogger(APIProvider::class.java)

@JsonDeserialize(using = APIProviderDeserializer::class)
@JsonSerialize(using = APIProviderSerializer::class)
abstract class APIProvider private constructor(name: String, val base: String) : DynamicEnum<APIProvider>(name) {

    abstract fun getChatClient(
        key: SecureString,
        base: String = this.base,
        workPool: ExecutorService,
        logLevel: Level = Level.DEBUG,
        logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
        scheduledPool: ListeningScheduledExecutorService
    ): ChatClientInterface

    abstract fun getChatModels(key: SecureString, baseUrl: String): List<ChatModel>
    open fun getEmbeddingModels(key: SecureString, baseUrl: String): List<EmbeddingModel> = emptyList()

    open fun getTranscriptionModels(key: SecureString, baseUrl: String): List<AudioModels> = emptyList()
    open fun getImageModels(key: SecureString, baseUrl: String): List<ImageModel> = emptyList()

    open fun authorize(request: HttpRequest, key: String, apiBase: String) {
        request.addHeader("Authorization", "Bearer ${key}")
    }

    open fun getEmbeddingClient(
        key: SecureString,
        base: String,
        workPool: ExecutorService,
        logLevel: Level = Level.DEBUG,
        logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
        scheduledPool: ListeningScheduledExecutorService
    ): com.simiacryptus.cognotik.embedding.EmbeddingClientInterface {
        throw UnsupportedOperationException("${this.name} does not support embedding functionality")
    }

    open fun getImageClient(
        key: SecureString,
        base: String,
        workPool: ExecutorService,
        logLevel: Level = Level.DEBUG,
        logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
        scheduledPool: ListeningScheduledExecutorService
    ): ImageClientInterface {
        throw UnsupportedOperationException("${this.name} does not support image generation functionality")
    }

    companion object {
        @JvmStatic
        val SearchAPI: APIProvider = object : APIProvider("SearchAPI", "https://api.searchapi.com") {

            override fun getChatModels(key: SecureString, baseUrl: String): List<ChatModel> = emptyList()

            override fun getChatClient(
                key: SecureString,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = throw UnsupportedOperationException("SearchAPI does not support chat functionality")
        }

        @JvmStatic
        val Gemini: APIProvider = object : APIProvider("Gemini", "https://generativelanguage.googleapis.com") {
            override fun authorize(
                request: HttpRequest,
                key: String,
                apiBase: String
            ) {
            }

            override fun getChatModels(key: SecureString, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)),
                logLevel = Level.DEBUG,
                logStreams = mutableListOf()
            ).getModels() ?: GeminiModels.values.values.toList()

            override fun getChatClient(
                key: SecureString,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = if (false) GeminiChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams,
                scheduledPool = scheduledPool
            ) else GeminiSdkChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams,
                scheduledPool = scheduledPool
            )

            override fun getImageModels(key: SecureString, baseUrl: String): List<ImageModel> {
                return GeminiImageModels.values.values.toList()
            }

            override fun getImageClient(
                key: SecureString,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ): ImageClientInterface = GeminiImageClient(
                apiKey = key,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams,
                scheduledPool = scheduledPool
            )

        }

        @JvmStatic
        val Ollama: APIProvider = object : APIProvider("Ollama", "http://localhost:11434") {
            override fun getChatModels(key: SecureString, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)),
                logLevel = Level.DEBUG,
                logStreams = mutableListOf()
            ).getModels() ?: emptyList()

            override fun getChatClient(
                key: SecureString,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = OllamaChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                scheduledPool = scheduledPool,
                logLevel = logLevel,
                logStreams = logStreams
            )

            override fun getEmbeddingModels(key: SecureString, baseUrl: String): List<EmbeddingModel> {
                return OllamaEmbeddingModels.values.values.toList()
            }

            override fun getEmbeddingClient(
                key: SecureString,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = com.simiacryptus.cognotik.embedding.OllamaEmbeddingClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams,
                scheduledPool = scheduledPool
            )
        }

        @JvmStatic
        val OpenAI: APIProvider = object : APIProvider("OpenAI", "https://api.openai.com/v1") {

            override fun getChatModels(key: SecureString, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)),
                logLevel = Level.DEBUG,
                logStreams = mutableListOf()
            ).getModels() ?: OpenAIModels.values.values.toList()

            override fun getChatClient(
                key: SecureString,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = OpenAIChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                scheduledPool = scheduledPool
            )


            override fun getEmbeddingModels(key: SecureString, baseUrl: String): List<EmbeddingModel> {
                return OpenAIEmbeddingModels.values.values.toList()
            }

            override fun getEmbeddingClient(
                key: SecureString,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = com.simiacryptus.cognotik.embedding.OpenAIEmbeddingClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams,
                scheduledPool = scheduledPool
            )

            override fun getImageModels(key: SecureString, baseUrl: String): List<ImageModel> {
                return OpenAIImageModels.values.values.toList()
            }

            override fun getImageClient(
                key: SecureString,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ): ImageClientInterface = OpenAIImageClient(
                key = key.decrypt,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams,
                scheduledPool = scheduledPool
            )

            override fun getTranscriptionModels(
                key: SecureString,
                baseUrl: String
            ): List<AudioModels> {
                return listOf(
                    AudioModels(modelName = "gpt-4o-transcribe", provider = this),
                    AudioModels(modelName = "gpt-4o-mini-transcribe", provider = this),
                    AudioModels(modelName = "whisper-1", provider = this)
                )
            }
        }

        @JvmStatic
        val Anthropic: APIProvider = object : APIProvider("Anthropic", "https://api.anthropic.com/v1") {
            override fun authorize(
                request: HttpRequest,
                key: String,
                apiBase: String
            ) {
                request.addHeader("x-api-key", key)
                request.addHeader("anthropic-version", "2023-06-01")
            }

            override fun getChatModels(key: SecureString, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)),
                logLevel = Level.DEBUG,
                logStreams = mutableListOf()
            ).getModels() ?: AnthropicModels.values.values.toList()

            override fun getChatClient(
                key: SecureString,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = AnthropicChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams,
                scheduledPool = scheduledPool
            )
        }

        @JvmStatic
        val AWS: APIProvider = object : APIProvider("AWS", "https://api.openai.aws") {

            override fun getChatModels(key: SecureString, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)),
                logLevel = Level.DEBUG,
                logStreams = mutableListOf()
            ).getModels() ?: AWSModels.values.values.toList()

            override fun getChatClient(
                key: SecureString,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = AwsChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams,
                scheduledPool = scheduledPool
            )

        }

        @JvmStatic
        val Groq: APIProvider = object : APIProvider("Groq", "https://api.groq.com/openai/v1") {

            override fun getChatModels(key: SecureString, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)),
                logLevel = Level.DEBUG,
                logStreams = mutableListOf()
            ).getModels() ?: GroqModels.values.values.toList()

            override fun getChatClient(
                key: SecureString,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = GroqChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams,
                scheduledPool = scheduledPool
            )

            override fun getTranscriptionModels(
                key: SecureString,
                baseUrl: String
            ): List<AudioModels> {
                return listOf(
                    AudioModels(modelName = "whisper-large-v3", provider = this),
                    AudioModels(modelName = "whisper-large-v3-turbo", provider = this),
                )
            }
        }

        @JvmStatic
        val Perplexity: APIProvider = object : APIProvider("Perplexity", "https://api.perplexity.ai") {

            override fun getChatModels(key: SecureString, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)),
                logLevel = Level.DEBUG,
                logStreams = mutableListOf()
            ).getModels() ?: PerplexityModels.values.values.toList()

            override fun getChatClient(
                key: SecureString,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = OpenAIChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                scheduledPool = scheduledPool
            )
        }

        @JvmStatic
        val ModelsLab: APIProvider = object : APIProvider("ModelsLab", "https://modelslab.com/api/v6") {

            override fun getChatModels(key: SecureString, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)),
                logLevel = Level.DEBUG,
                logStreams = mutableListOf()
            ).getModels() ?: ModelsLabModels.values.values.toList()

            override fun getChatClient(
                key: SecureString,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = ModelsLabChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams,
                scheduledPool = scheduledPool
            )
        }

        @JvmStatic
        val Mistral: APIProvider = object : APIProvider("Mistral", "https://api.mistral.ai/v1") {

            override fun getChatModels(key: SecureString, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)),
                logLevel = Level.DEBUG,
                logStreams = mutableListOf()
            ).getModels() ?: MistralModels.values.values.toList()

            override fun getChatClient(
                key: SecureString,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = MistralChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams,
                scheduledPool = scheduledPool
            )
        }

        @JvmStatic
        val DeepSeek: APIProvider = object : APIProvider("DeepSeek", "https://api.deepseek.com") {

            override fun getChatModels(key: SecureString, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)),
                logLevel = Level.DEBUG,
                logStreams = mutableListOf()
            ).getModels() ?: DeepSeekModels.values.values.toList()

            override fun getChatClient(
                key: SecureString,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = DeepSeekChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams,
                scheduledPool = scheduledPool
            )
        }

        @JvmStatic
        val Google: APIProvider = object : APIProvider("GoogleSearch", "c581d1409962d72e1") {

            override fun getChatModels(key: SecureString, baseUrl: String): List<ChatModel> = emptyList()

            override fun getChatClient(
                key: SecureString,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = throw UnsupportedOperationException("Google Search API does not support chat functionality")
        }

        @JvmStatic
        val Github: APIProvider = object : APIProvider("Github", "https://api.github.com") {

            override fun getChatModels(key: SecureString, baseUrl: String): List<ChatModel> = emptyList()

            override fun getChatClient(
                key: SecureString,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = throw UnsupportedOperationException("Github API does not support chat functionality")
        }

        init {
            log.info("Registering API providers")
            register(APIProvider::class.java, Gemini)
            register(APIProvider::class.java, OpenAI)
            register(APIProvider::class.java, Anthropic)
            register(APIProvider::class.java, AWS)
            register(APIProvider::class.java, Groq)
            register(APIProvider::class.java, Perplexity)
            register(APIProvider::class.java, ModelsLab)
            register(APIProvider::class.java, Mistral)
            register(APIProvider::class.java, DeepSeek)
            register(APIProvider::class.java, Google)
            register(APIProvider::class.java, Github)
            register(APIProvider::class.java, Ollama)
            register(APIProvider::class.java, SearchAPI)
        }

        @JvmStatic
        fun valueOf(name: String): APIProvider = valueOf(APIProvider::class.java, name)

        @JvmStatic
        fun values(): Collection<APIProvider> {
            log.debug("Retrieving all APIProvider values")
            return values(APIProvider::class.java)
        }
    }
}

class APIProviderSerializer : DynamicEnumSerializer<APIProvider>(APIProvider::class.java)
class APIProviderDeserializer : DynamicEnumDeserializer<APIProvider>(APIProvider::class.java)
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/models/EditModels.kt

```
package com.simiacryptus.cognotik.models

import com.simiacryptus.cognotik.models.ModelSchema.Usage
import com.simiacryptus.cognotik.util.LoggerFactory
import org.slf4j.Logger

open class EditModels(
    modelName: String,
    maxTokens: Int,
    private val tokenPricePerK: Double,
    provider: APIProvider = APIProvider.OpenAI,
) : LLMModel(
    modelName = modelName,
    provider = provider,
    maxTotalTokens = maxTokens
) {
    private val log: Logger = LoggerFactory.getLogger(EditModels::class.java)

    init {
        log.info("Initialized EditModels with modelName: $modelName, maxTokens: $maxTokens, tokenPricePerK: $tokenPricePerK")
    }

    override fun pricing(usage: Usage) = usage.prompt_tokens * tokenPricePerK / 1000.0

    companion object {
        fun values() = mapOf("DaVinciEdit" to DaVinciEdit)

        private val DaVinciEdit = EditModels("text-davinci-edit-001", 2049, 0.002)

        init {
            LoggerFactory.getLogger(EditModels::class.java)
                .info("Initialized DaVinciEdit model with maxTokens: 2049 and tokenPricePerK: 0.002")
        }
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/models/LLMModel.kt

```
package com.simiacryptus.cognotik.models

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.models.ModelSchema.Usage

@JsonDeserialize(using = LLMModelDeserializer::class)
@JsonSerialize(using = LLMModelSerializer::class)
open class LLMModel(
    override val modelName: String,
    override val provider: APIProvider?,
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
            provider = null,
        )
    }
}

```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/models/ModelSchema.kt

```
package com.simiacryptus.cognotik.models

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.node.ObjectNode
import com.simiacryptus.cognotik.util.LoggerFactory
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO

@Suppress("PropertyName", "SpellCheckingInspection")
interface ModelSchema {
    data class AudioInput(
        var data: String,
        var format: String
    ) {
        var audioBytes: ByteArray
            @JsonIgnore
            get() = Base64.getDecoder().decode(data)
            @JsonIgnore
            set(value) {
                data = Base64.getEncoder().encodeToString(value)
            }
    }

    data class ApiError(
        var message: String? = null,
        var type: String? = null,
        var param: String? = null,
        var code: Double? = null,
    )

    data class LogProbs(
        var tokens: List<CharSequence> = ArrayList(),
        var token_logprobs: DoubleArray = DoubleArray(0),
        var top_logprobs: List<ObjectNode> = ArrayList(),
        var text_offset: IntArray = IntArray(0),
    ) {
        private var log = LoggerFactory.getLogger(LogProbs::class.java)
        override fun equals(other: Any?): Boolean {
            log.info("Comparing LogProbs objects")
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as LogProbs
            if (tokens != other.tokens) return false
            if (!token_logprobs.contentEquals(other.token_logprobs)) return false
            if (top_logprobs != other.top_logprobs) return false
            if (!text_offset.contentEquals(other.text_offset)) return false
            return true
        }

        override fun hashCode(): Int {
            log.info("Calculating hashCode for LogProbs")
            var result = tokens.hashCode()
            result = 31 * result + token_logprobs.contentHashCode()
            result = 31 * result + top_logprobs.hashCode()
            result = 31 * result + text_offset.contentHashCode()
            return result
        }
    }

    data class Usage(
        var prompt_tokens: Long = 0,
        var completion_tokens: Long = 0,
        var total_tokens: Long = prompt_tokens + completion_tokens,
        var cost: Double? = null
    )

    data class TranscriptionPacket(
        var id: Int? = 0,
        var seek: Int? = 0,
        var start: Double? = 0.0,
        var end: Double? = 0.0,
        var text: String? = "",
        var tokens: IntArray? = null,
        var temperature: Double? = 0.0,
        var avg_logprob: Double? = 0.0,
        var compression_ratio: Double? = 0.0,
        var no_speech_prob: Double? = 0.0,
        var transient: Boolean? = false
    ) {
        private var log = LoggerFactory.getLogger(TranscriptionPacket::class.java)
        override fun equals(other: Any?) = when {
            this === other -> true
            javaClass != other?.javaClass -> false
            else -> {
                other as TranscriptionPacket
                when {
                    id != other.id -> false
                    seek != other.seek -> false
                    start != other.start -> false
                    end != other.end -> false
                    text != other.text -> false
                    tokens != null -> when {
                        other.tokens == null -> false
                        !tokens.contentEquals(other.tokens) -> false
                        else -> true
                    }

                    other.tokens != null -> false
                    temperature != other.temperature -> false
                    avg_logprob != other.avg_logprob -> false
                    compression_ratio != other.compression_ratio -> false
                    no_speech_prob != other.no_speech_prob -> false
                    transient != other.transient -> false
                    else -> true
                }
            }
        }

        override fun hashCode(): Int {
            log.info("Calculating hashCode for TranscriptionPacket")
            var result = id ?: 0
            result = 31 * result + (seek ?: 0)
            result = 31 * result + (start?.hashCode() ?: 0)
            result = 31 * result + (end?.hashCode() ?: 0)
            result = 31 * result + (text?.hashCode() ?: 0)
            result = 31 * result + (tokens?.contentHashCode() ?: 0)
            result = 31 * result + (temperature?.hashCode() ?: 0)
            result = 31 * result + (avg_logprob?.hashCode() ?: 0)
            result = 31 * result + (compression_ratio?.hashCode() ?: 0)
            result = 31 * result + (no_speech_prob?.hashCode() ?: 0)
            result = 31 * result + (transient?.hashCode() ?: 0)
            return result
        }
    }

    data class TranscriptionResult(
        var task: String? = "",
        var language: String? = "",
        var duration: Double = 0.0,
        var segments: List<TranscriptionPacket> = listOf(),
        var text: String? = ""
    )

    data class ChatRequest(
        var messages: List<ChatMessage> = listOf(),
        var model: String? = null,
        var temperature: Double = 0.0,
        var max_tokens: Int? = null,
        var stop: List<CharSequence>? = listOf(),
        var function_call: String? = null,
        var response_format: Map<String, Any>? = null,
        var n: Int? = null,
        var functions: List<RequestFunction>? = null,
        var store: Boolean? = null,
        var metadata: Map<String, Any?>? = null,
        var modalities: List<String>? = null,
        var audio: Map<String, String>? = null,
        var reasoning_effort: String? = null,
    )

    data class GroqChatRequest(
        var messages: List<GroqChatMessage> = listOf(),
        var model: String? = null,
        var temperature: Double = 0.0,
        var max_tokens: Int? = null,
        var stop: List<CharSequence>? = listOf(),
        var function_call: String? = null,
        var n: Int? = null,
        var functions: List<RequestFunction>? = null,
    )

    data class RequestFunction(
        var name: String = "",
        var description: String = "",
        var parameters: Map<String, String> = mapOf(),
    )

    data class ChatResponse(
        var id: String? = null,
        var `object`: String? = null,
        var created: Long = 0,
        var model: String? = null,
        var choices: List<ChatChoice> = listOf(),
        var error: ApiError? = null,
        var usage: Usage? = null,
    )

    data class ChatChoice(
        var message: ChatMessageResponse? = null,
        var index: Int = 0,
        var finish_reason: String? = null,
    )

    data class ContentPart(
        var text: String? = null,
        var image_url: String? = null,
        //var input_audio: AudioInput? = null
    ) {
        var image_data: ByteArray?
            @JsonIgnore
            get() {
                return if (image_url != null && image_url!!.startsWith("data:image/")) {
                    var parts = image_url!!.split(",")
                    Base64.getDecoder().decode(parts[1])
                } else {
                    null
                }
            }
            @JsonIgnore
            set(value) {
                if (value != null) {
                    var base64Data = Base64.getEncoder().encodeToString(value)
                    image_url = "data:image/jpeg;base64,$base64Data"
                } else {
                    image_url = null
                }
            }
        var image: BufferedImage?
            @JsonIgnore
            get() {
                var data = image_data
                return if (data != null) {
                    ImageIO.read(data.inputStream())
                } else {
                    null
                }
            }
            @JsonIgnore
            set(value) {
                if (value != null) {
                    var output = ByteArrayOutputStream()
                    ImageIO.write(value, "jpg", output)
                    var base64Data = Base64.getEncoder().encodeToString(output.toByteArray())
                    image_url = "data:image/jpeg;base64,$base64Data"
                } else {
                    image_url = null
                }
            }
//        var audio_data: ByteArray?
//            @JsonIgnore
//            get() {
//                return input_audio?.audioBytes
//            }
//            @JsonIgnore
//            set(value) {
//                input_audio = if (value != null) {
//                    AudioInput(Base64.getEncoder().encodeToString(value), input_audio?.format ?: "mp3")
//                } else {
//                    null
//                }
//            }

        companion object {
            private var log = LoggerFactory.getLogger(ContentPart::class.java)
            fun text(content: String): ContentPart {
                log.info("Creating text ContentPart")
                return ContentPart(text = content)
            }

            fun jpg(img: BufferedImage): ContentPart {
                log.info("Creating jpg ContentPart")
                return ContentPart(image_url = "data:image/jpeg;base64," + toBase64(img, "jpg"))
            }

            fun png(img: BufferedImage): ContentPart {
                log.info("Creating png ContentPart")
                return ContentPart(image_url = "data:image/png;base64," + toBase64(img, "png"))
            }

//            fun audio(data: String, format: String): ContentPart {
//                log.info("Creating audio ContentPart")
//                return ContentPart(input_audio = AudioInput(data, format))
//            }
//
//            fun audio(data: ByteArray, format: String): ContentPart {
//                log.info("Creating audio ContentPart")
//                return ContentPart(input_audio = AudioInput(Base64.getEncoder().encodeToString(data), format))
//            }


            fun toBase64(image: BufferedImage, fmt: String): String {
                log.info("Converting image to Base64")
                var output = ByteArrayOutputStream()
                ImageIO.write(image, fmt, output)
                return Base64.getEncoder().encodeToString(output.toByteArray())
            }
        }
    }

    data class ChatMessage(
        var role: Role? = null,
        var content: List<ContentPart>? = null,
        //var function_call: FunctionCall? = null,
    )

    data class ChatMessageResponse(
        var role: Role? = null,
        var content: String? = null,
        var function_call: FunctionCall? = null,
        var image_url: String? = null,
        var image_mime_type: String? = null,
    ) {
        var image: BufferedImage?
            @JsonIgnore
            get() {
                return if (image_url != null && image_url!!.startsWith("data:image/")) {
                    var parts = image_url!!.split(",")
                    var data = Base64.getDecoder().decode(parts[1])
                    ImageIO.read(data.inputStream())
                } else {
                    null
                }
            }
            @JsonIgnore
            set(value) {
                if (value != null) {
                    var output = ByteArrayOutputStream()
                    ImageIO.write(value, "jpg", output)
                    var base64Data = Base64.getEncoder().encodeToString(output.toByteArray())
                    image_url = "data:image/jpeg;base64,$base64Data"
                } else {
                    image_url = null
                }
            }
        var image_data: ByteArray?
            @JsonIgnore
            get() {
                return if (image_url != null && image_url!!.startsWith("data:image/")) {
                    var parts = image_url!!.split(",")
                    Base64.getDecoder().decode(parts[1])
                } else {
                    null
                }
            }
            @JsonIgnore
            set(value) {
                if (value != null) {
                    var base64Data = Base64.getEncoder().encodeToString(value)
                    image_url = "data:image/jpeg;base64,$base64Data"
                } else {
                    image_url = null
                }
            }
    }

    enum class Role {
        assistant, user, system
    }

    data class FunctionCall(
        var name: String? = null,
        var arguments: String? = null,
    )

    data class GroqChatMessage(
        var role: Role? = null,
        var content: String? = null,
        var function_call: FunctionCall? = null,
    )


    data class EmbeddingResponse(
        var `object`: String? = null,
        var data: List<EmbeddingData> = listOf(),
        var model: String? = null,
        var usage: Usage? = null,
    )

    data class EmbeddingData(
        var `object`: String? = null,
        var embedding: DoubleArray? = null,
        var index: Int? = null
    ) {
        private var log = LoggerFactory.getLogger(EmbeddingData::class.java)
        override fun equals(other: Any?): Boolean {
            log.info("Comparing EmbeddingData objects")
            when {
                this === other -> return true
                javaClass != other?.javaClass -> return false
                else -> {
                    other as EmbeddingData
                    when {
                        `object` != other.`object` -> return false
                        embedding != null -> {
                            when {
                                other.embedding == null -> return false
                                !embedding.contentEquals(other.embedding) -> return false
                            }
                        }

                        other.embedding != null -> return false
                        index != other.index -> return false
                    }
                    return true
                }
            }
        }

        override fun hashCode(): Int {
            log.info("Calculating hashCode for EmbeddingData")
            var result = `object`?.hashCode() ?: 0
            result = 31 * result + (embedding?.contentHashCode() ?: 0)
            result = 31 * result + (index ?: 0)
            return result
        }
    }

    data class EmbeddingRequest(
        var model: String? = null,
        var input: String? = null,
    )

    data class ImageGenerationRequest(
        var prompt: String,
        var model: String? = null,
        var n: Int? = null,
        var quality: String? = null,
        var response_format: String? = null,
        var size: String? = null,
        var style: String? = null,
        var user: String? = null
    )

    data class ImageObject(
        var url: String? = null,
        var b64_json: String? = null
    )

    data class ImageGenerationResponse(
        var created: Long,
        var data: List<ImageObject>
    )

}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/models/ToolProvider.kt

```
package com.simiacryptus.cognotik.models

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer
import com.simiacryptus.cognotik.util.LoggerFactory
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.TimeUnit

private val log: Logger = LoggerFactory.getLogger(ToolProvider::class.java)

@JsonDeserialize(using = ToolProviderDeserializer::class)
@JsonSerialize(using = ToolProviderSerializer::class)
open class ToolProvider(name: String) : DynamicEnum<ToolProvider>(name) {

    open fun getExecutables(): List<String> = emptyList()
    open fun getVersion(path: String): String? = null

    open fun validate(path: String): Boolean {
        return try {
            val version = getVersion(path)
            log.info("Tool $name found at $path version: $version")
            version != null
        } catch (e: Exception) {
            log.warn("Tool $name validation failed at $path", e)
            false
        }
    }

    open fun resolve(root: String? = null, tool: String? = null): List<String> {
        val foundPaths = mutableListOf<String>()
        val executables = getExecutables()
        if (root != null) {
            var rootFile = File(root)
            if (rootFile.exists()) {
                if (!rootFile.isDirectory) {
                    rootFile = rootFile.parentFile
                }
                for (exe in executables) {
                    if (tool != null && !exe.equals(tool, ignoreCase = true)) continue
                    val candidates = listOf(
                        File(rootFile, exe),
                        File(rootFile, "$exe.exe"),
                        File(File(rootFile, "bin"), exe),
                        File(File(rootFile, "bin"), "$exe.exe")
                    )
                    candidates.firstOrNull { canExecute(it) }?.let { foundPaths += it.absolutePath }
                }
            }
        }
        return foundPaths;
    }

    private fun canExecute(file: File) = file.exists() && file.canExecute()

    companion object {
        private fun runCommand(command: List<String>): String? {
            return try {
                val process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroy()
                    return null
                }
                process.inputStream.bufferedReader().readText().trim()
            } catch (e: Exception) {
                log.debug("Failed to run command: $command", e)
                null
            }
        }

        val Git = object : ToolProvider("Git") {
            override fun getExecutables() = listOf("git")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Latex = object : ToolProvider("Latex") {
            override fun getExecutables() = listOf("pdflatex", "latex")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Python = object : ToolProvider("Python") {
            override fun getExecutables() = listOf("python", "python3")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val SSH = object : ToolProvider("SSH") {
            override fun getExecutables() = listOf("ssh")
            override fun getVersion(path: String) = runCommand(listOf(path, "-V"))
        }
        val Rust = object : ToolProvider("Rust") {
            override fun getExecutables() = listOf("cargo", "rustc")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Node = object : ToolProvider("Node") {
            override fun getExecutables() = listOf("node", "npm")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Jdk = object : ToolProvider("Jdk") {
            override fun getExecutables() = listOf(
                "java", // Java Runtime
                "javac", // Java Compiler
                "jdb", // Java Debugger
                "javap", // Java Class File Disassembler
                "jlink", // Java Linker
                "jarsigner", // Java Archive Signer
                "javadoc", // Java Documentation Generator
                "jshell", // Java Shell (REPL)
                "jcmd", // Java Command Tool
                "jconsole", // Java Monitoring and Management Console
                "jstat", // Java Virtual Machine Statistics Monitoring Tool
                "jmap", // Java Memory Map Tool
                "jhat", // Java Heap Analysis Tool
                "jinfo", // Java Configuration Info Tool
                "jstack"  // Java Stack Trace Tool
            )
            override fun getVersion(path: String) = runCommand(listOf(path, "-version"))
        }
        val Docker = object : ToolProvider("Docker") {
            override fun getExecutables() = listOf("docker")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Go = object : ToolProvider("Go") {
            override fun getExecutables() = listOf("go")
            override fun getVersion(path: String) = runCommand(listOf(path, "version"))
        }
        val Gradle = object : ToolProvider("Gradle") {
            override fun getExecutables() = listOf("gradle", "gradle.bat")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Maven = object : ToolProvider("Maven") {
            override fun getExecutables() = listOf("mvn", "mvn.cmd")
            override fun getVersion(path: String) = runCommand(listOf(path, "-version"))
        }
        val Ant = object : ToolProvider("Ant") {
            override fun getExecutables() = listOf("ant", "ant.bat", "ant.cmd")
            override fun getVersion(path: String) = runCommand(listOf(path, "-version"))
        }
        val Bash = object : ToolProvider("Bash") {
            override fun getExecutables() = listOf("bash")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Zsh = object : ToolProvider("Zsh") {
            override fun getExecutables() = listOf("zsh")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Powershell = object : ToolProvider("Powershell") {
            override fun getExecutables() = listOf("pwsh", "powershell")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Ruby = object : ToolProvider("Ruby") {
            override fun getExecutables() = listOf("ruby")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val PHP = object : ToolProvider("PHP") {
            override fun getExecutables() = listOf("php")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Gcc = object : ToolProvider("Gcc") {
            override fun getExecutables() = listOf("gcc", "g++")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Make = object : ToolProvider("Make") {
            override fun getExecutables() = listOf("make")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Cmake = object : ToolProvider("Cmake") {
            override fun getExecutables() = listOf("cmake")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Terraform = object : ToolProvider("Terraform") {
            override fun getExecutables() = listOf("terraform")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Kubectl = object : ToolProvider("Kubectl") {
            override fun getExecutables() = listOf("kubectl")
            override fun getVersion(path: String) = runCommand(listOf(path, "version", "--client"))
        }
        val Gcloud = object : ToolProvider("Gcloud") {
            override fun getExecutables() = listOf("gcloud", "gcloud.cmd")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Aws = object : ToolProvider("Aws") {
            override fun getExecutables() = listOf("aws")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val LanguageServer = object : ToolProvider("LanguageServer") {
            override fun getExecutables() = listOf(
                "pylsp", // Python Language Server
                "typescript-language-server", // TypeScript Language Server
                "kotlin-language-server", // Kotlin Language Server
                "jdtls", // Java Language Server
                "clangd", // C/C++ Language Server
                "gopls", // Go Language Server
                "rust-analyzer", // Rust Language Server
                "bash-language-server", // Bash Language Server
                "docker-langserver", // Docker Language Server
                "texlab", // LaTeX Language Server
                "yaml-language-server" // YAML Language Server
            )

            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Dot = object : ToolProvider("Dot") {
            override fun getExecutables() = listOf("dot")
            override fun getVersion(path: String) = runCommand(listOf(path, "-V"))
        }
        val Octave = object : ToolProvider("Octave") {
            override fun getExecutables() = listOf("octave", "octave-cli")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Gnuplot = object : ToolProvider("Gnuplot") {
            override fun getExecutables() = listOf("gnuplot")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Pandoc = object : ToolProvider("Pandoc") {
            override fun getExecutables() = listOf("pandoc")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Ffmpeg = object : ToolProvider("Ffmpeg") {
            override fun getExecutables() = listOf("ffmpeg")
            override fun getVersion(path: String) = runCommand(listOf(path, "-version"))
        }
        val Julia = object : ToolProvider("Julia") {
            override fun getExecutables() = listOf("julia")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val PariGP = object : ToolProvider("PariGP") {
            override fun getExecutables() = listOf("gp")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Prolog = object : ToolProvider("Prolog") {
            override fun getExecutables() = listOf("swipl", "gprolog", "prolog")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Z3 = object : ToolProvider("Z3") {
            override fun getExecutables() = listOf("z3")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val CVC5 = object : ToolProvider("CVC5") {
            override fun getExecutables() = listOf("cvc5")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Lean = object : ToolProvider("Lean") {
            override fun getExecutables() = listOf("lean")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Coq = object : ToolProvider("Coq") {
            override fun getExecutables() = listOf("coqc")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Isabelle = object : ToolProvider("Isabelle") {
            override fun getExecutables() = listOf("isabelle")
            override fun getVersion(path: String) = runCommand(listOf(path, "version"))
        }
        val Agda = object : ToolProvider("Agda") {
            override fun getExecutables() = listOf("agda")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Haskell = object : ToolProvider("Haskell") {
            override fun getExecutables() = listOf("ghc", "runhaskell")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val OCaml = object : ToolProvider("OCaml") {
            override fun getExecutables() = listOf("ocaml", "ocamlc")
            override fun getVersion(path: String) = runCommand(listOf(path, "-version"))
        }
        val Maxima = object : ToolProvider("Maxima") {
            override fun getExecutables() = listOf("maxima")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Singular = object : ToolProvider("Singular") {
            override fun getExecutables() = listOf("Singular", "singular")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Sage = object : ToolProvider("Sage") {
            override fun getExecutables() = listOf("sage")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Gap = object : ToolProvider("Gap") {
            override fun getExecutables() = listOf("gap")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }

        init {
            register(ToolProvider::class.java, Git)
            register(ToolProvider::class.java, Latex)
            register(ToolProvider::class.java, Python)
            register(ToolProvider::class.java, SSH)
            register(ToolProvider::class.java, Rust)
            register(ToolProvider::class.java, Node)
            register(ToolProvider::class.java, Jdk)
            register(ToolProvider::class.java, Docker)
            register(ToolProvider::class.java, Go)
            register(ToolProvider::class.java, Gradle)
            register(ToolProvider::class.java, Maven)
            register(ToolProvider::class.java, Ant)
            register(ToolProvider::class.java, Bash)
            register(ToolProvider::class.java, Zsh)
            register(ToolProvider::class.java, Powershell)
            register(ToolProvider::class.java, PHP)
            register(ToolProvider::class.java, Ruby)
            register(ToolProvider::class.java, Gcc)
            register(ToolProvider::class.java, Make)
            register(ToolProvider::class.java, Cmake)
            register(ToolProvider::class.java, Terraform)
            register(ToolProvider::class.java, Kubectl)
            register(ToolProvider::class.java, Gcloud)
            register(ToolProvider::class.java, Aws)
            register(ToolProvider::class.java, LanguageServer)
            register(ToolProvider::class.java, Dot)
            register(ToolProvider::class.java, Octave)
            register(ToolProvider::class.java, Gnuplot)
            register(ToolProvider::class.java, Pandoc)
            register(ToolProvider::class.java, Ffmpeg)
            register(ToolProvider::class.java, Julia)
            register(ToolProvider::class.java, PariGP)
            register(ToolProvider::class.java, Prolog)
            register(ToolProvider::class.java, Z3)
            register(ToolProvider::class.java, CVC5)
            register(ToolProvider::class.java, Lean)
            register(ToolProvider::class.java, Coq)
            register(ToolProvider::class.java, Isabelle)
            register(ToolProvider::class.java, Agda)
            register(ToolProvider::class.java, Haskell)
            register(ToolProvider::class.java, OCaml)
            register(ToolProvider::class.java, Maxima)
            register(ToolProvider::class.java, Singular)
            register(ToolProvider::class.java, Sage)
            register(ToolProvider::class.java, Gap)
        }

        @JvmStatic
        fun valueOf(name: String): ToolProvider = valueOf(ToolProvider::class.java, name)

        @JvmStatic
        fun values(): Collection<ToolProvider> = values(ToolProvider::class.java)

        @JvmStatic
        private fun discoverFromPath(provider: ToolProvider): Collection<String> {
            val pathEnv = System.getenv("PATH") ?: ""
            val pathSeparator = System.getProperty("path.separator")
            val paths = pathEnv.split(pathSeparator)
            val found = mutableSetOf<String>()
            for (p in paths) {
                val dir = File(p)
                if (!dir.exists()) continue
                if (!dir.isDirectory) continue
                found += provider.resolve(dir.absolutePath)
            }
            return found
        }


        @JvmStatic
        fun discoverAllToolsFromPath(): List<ToolData> {
            val result = mutableListOf<ToolData>()
            for (provider in values()) {
                val paths = discoverFromPath(provider)
                for (path in paths) {
                    result.add(ToolData(provider, path))
                }
            }
            return result
        }

        @JvmStatic
        fun scanRecursive(root: File, depth: Int = 3): List<ToolData> {
            val results = mutableListOf<ToolData>()
            if (!root.exists() || !root.isDirectory) return results
            for (provider in values()) {
                provider.resolve(root.absolutePath).forEach { path ->
                    results.add(ToolData(provider, path))
                }
            }
            if (depth > 0) {
                val files = root.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isDirectory) {
                            results.addAll(scanRecursive(file, depth - 1))
                        }
                    }
                }
            }
            return results
        }

    }
}

/**
 * Represents configuration data for a tool/command that can be executed.
 *
 * Get via e.g.
 * ```
 *   val executables : List<String>? = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().tools.flatMap { it.absoluteExecutablePaths() }.distinct().sorted()
 * ```
 *
 * @property name The display name of the tool
 * @property description A human-readable description of what the tool does
 * @property command The actual command or script to execute when the tool is invoked
 */
data class ToolData(
    val provider: ToolProvider? = null,
    val path: String? = null,
) {
    fun absoluteExecutablePaths(): List<String> {
        val result = mutableListOf<String>()
        provider?.let {
            result += it.resolve(path)
        }
        return result
    }

    fun resolve(tool: String?): String? {
        return provider?.resolve(path, tool)?.firstOrNull();
    }
}

class ToolProviderSerializer : DynamicEnumSerializer<ToolProvider>(ToolProvider::class.java)
class ToolProviderDeserializer : DynamicEnumDeserializer<ToolProvider>(ToolProvider::class.java)
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

Here is the updated `README.md` for the `com.simiacryptus.cognotik.models` package.

### /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/models/README.md
```markdown
# AI Models and Providers

This package contains the core abstractions and data structures for interacting with various AI models and external tools. It provides a unified interface for chat, embedding, image generation, and transcription services across multiple providers.

## Core Abstractions

### [AIModel](AIModel.kt)
The base interface for all AI models. It defines the basic properties:
- `modelName`: The identifier for the model.
- `provider`: The `APIProvider` that hosts the model.

### [APIProvider](APIProvider.kt)
An abstract base class and registry for AI service providers. It handles:
- **Client Creation**: Methods to instantiate chat, embedding, and image clients.
- **Model Discovery**: Methods to list available models for different modalities.
- **Authorization**: Custom header injection for different API requirements (e.g., Bearer tokens vs. custom headers).

**Supported Providers:**
- **OpenAI**: Full support for Chat, Embeddings, Images, and Transcription (Whisper).
- **Gemini**: Google's generative models (Chat and Images).
- **Anthropic**: Claude series chat models.
- **Ollama**: Local model execution for Chat and Embeddings.
- **Groq**: High-performance inference for Chat and Transcription.
- **Mistral / DeepSeek / Perplexity**: Specialized LLM providers.
- **AWS**: Integration with Amazon Bedrock/SageMaker endpoints.
- **SearchAPI / Google / Github**: Specialized providers for search and repository metadata.

### [LLMModel](LLMModel.kt)
A base class for Large Language Models that includes:
- **Token Limits**: Tracking of `maxTotalTokens` and `maxOutTokens`.
- **Pricing**: A `pricing(Usage)` method to calculate costs based on token consumption.
- **Serialization**: Custom Jackson serializers to handle model references by name.

### [ModelSchema](ModelSchema.kt)
A comprehensive collection of data classes representing the JSON structures used by various AI APIs. This includes:
- **Chat**: `ChatRequest`, `ChatResponse`, `ChatMessage`, `ContentPart` (supporting text and images).
- **Embeddings**: `EmbeddingRequest`, `EmbeddingResponse`.
- **Images**: `ImageGenerationRequest`, `ImageGenerationResponse`.
- **Transcription**: `TranscriptionResult`, `TranscriptionPacket`.
- **Usage**: Tracking token counts and costs.

## Tool Integration

### [ToolProvider](ToolProvider.kt)
Manages external executable tools and environments. It allows the system to discover, validate, and resolve paths for various development and utility tools.

**Supported Tools include:**
- **Languages/Runtimes**: Python, Jdk, Node, Rust, Go, Ruby, PHP, Julia.
- **Build Tools**: Gradle, Maven, Ant, Make, Cmake.
- **Infrastructure**: Docker, Terraform, Kubectl, AWS/Gcloud CLI.
- **Version Control**: Git.
- **Scientific/Math**: Octave, Gnuplot, Maxima, Sage, Gap.
- **Formal Verification**: Z3, CVC5, Lean, Coq, Isabelle, Agda.
- **Utilities**: Bash, Zsh, Powershell, SSH, Ffmpeg, Pandoc, Dot (Graphviz).

## Usage Example

To get a chat client for a specific provider:

```kotlin
val provider = APIProvider.OpenAI
val client = provider.getChatClient(
    key = SecureString("your-api-key"),
    workPool = executorService,
    scheduledPool = scheduledExecutorService
)

val models = provider.getChatModels(SecureString("your-api-key"), provider.base)
```

To discover installed tools on the system:

```kotlin
val installedTools = ToolProvider.discoverAllToolsFromPath()
installedTools.forEach { tool ->
    println("Found ${tool.provider?.name} at ${tool.path}")
}
```
```

### Summary of Changes
- Created a comprehensive `README.md` for the `com.simiacryptus.cognotik.models` package.
- Documented the primary interfaces (`AIModel`) and classes (`APIProvider`, `LLMModel`, `ToolProvider`).
- Listed all supported AI providers defined in the `APIProvider` companion object.
- Listed the extensive set of external tools supported by `ToolProvider`.
- Summarized the data structures available in `ModelSchema`.
- Provided basic usage examples for client instantiation and tool discovery.
</details>

                - <a href='fileIndex/G-20260120-RPFC//home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/models/README.md'>/home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/models/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC//home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/models/README.md'>/home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/models/README.md</a> Updated
