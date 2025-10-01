package com.simiacryptus.cognotik.models

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.chat.*
import com.simiacryptus.cognotik.chat.model.*
import com.simiacryptus.cognotik.image.ImageModel
import com.simiacryptus.cognotik.image.ImageModels
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer
import org.slf4j.Logger
import com.simiacryptus.cognotik.util.LoggerFactory
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val log: Logger = LoggerFactory.getLogger(APIProvider::class.java)

@JsonDeserialize(using = APIProviderDeserializer::class)
@JsonSerialize(using = APIProviderSerializer::class)
abstract class APIProvider private constructor(name: String, val base: String) : DynamicEnum<APIProvider>(name) {

    abstract fun getChatClient(
        key: String,
        base: String,
        workPool: ExecutorService,
        logLevel: Level = Level.INFO,
        logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
        scheduledPool: ListeningScheduledExecutorService
    ): ChatClientInterface

    abstract fun getChatModels(key: String, baseUrl: String): List<ChatModel>

    open fun authorize(request: HttpRequest, key: String, apiBase: String) {
        request.addHeader("Authorization", "Bearer ${key}")
    }
    open fun getEmbeddingClient(
        key: String,
        base: String,
        workPool: ExecutorService,
        logLevel: Level = Level.INFO,
        logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
        scheduledPool: ListeningScheduledExecutorService
    ): com.simiacryptus.cognotik.embedding.EmbeddingClientInterface {
        throw UnsupportedOperationException("${this.name} does not support embedding functionality")
    }

    companion object {
        val SearchAPI: APIProvider = object : APIProvider("SearchAPI", "https://api.searchapi.com") {

            override fun getChatModels(key: String, baseUrl: String): List<ChatModel> = emptyList()

            override fun getChatClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = throw UnsupportedOperationException("SearchAPI does not support chat functionality")
        }

        val Gemini: APIProvider = object : APIProvider("Gemini", "https://generativelanguage.googleapis.com") {
            override fun authorize(
                request: HttpRequest,
                key: String,
                apiBase: String
            ) {
            }

            override fun getChatModels(key: String, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator( Executors.newScheduledThreadPool(1) ),
                logLevel = Level.INFO,
                logStreams = mutableListOf()
            ).getModels() ?: GeminiModels.values.values.toList()

            override fun getChatClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = GeminiChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams,
                scheduledPool = scheduledPool
            )
        }
        val Ollama: APIProvider = object : APIProvider("Ollama", "http://localhost:11434") {
            override fun getChatModels(key: String, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator( Executors.newScheduledThreadPool(1) ),
                logLevel = Level.INFO,
                logStreams = mutableListOf()
            ).getModels() ?: emptyList()

            override fun getChatClient(
                key: String,
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
        }
        val OpenAI: APIProvider = object : APIProvider("OpenAI", "https://api.openai.com/v1") {

            override fun getChatModels(key: String, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator( Executors.newScheduledThreadPool(1) ),
                logLevel = Level.INFO,
                logStreams = mutableListOf()
            ).getModels() ?: OpenAIModels.values.values.toList()

            override fun getChatClient(
                key: String,
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


            override fun getEmbeddingClient(
                key: String,
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
            fun getImageModels(key: String, baseUrl: String): List<ImageModel> {
                return ImageModels.values.values.toList()
            }
        }
        val Anthropic: APIProvider = object : APIProvider("Anthropic", "https://api.anthropic.com/v1") {
            override fun authorize(
                request: HttpRequest,
                key: String,
                apiBase: String
            ) {
                request.addHeader("x-api-key", key)
                request.addHeader("anthropic-version", "2023-06-01")
            }

            override fun getChatModels(key: String, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator( Executors.newScheduledThreadPool(1) ),
                logLevel = Level.INFO,
                logStreams = mutableListOf()
            ).getModels() ?: AnthropicModels.values.values.toList()

            override fun getChatClient(
                key: String,
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
        val AWS: APIProvider = object : APIProvider("AWS", "https://api.openai.aws") {

            override fun getChatModels(key: String, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator( Executors.newScheduledThreadPool(1) ),
                logLevel = Level.INFO,
                logStreams = mutableListOf()
            ).getModels() ?: AWSModels.values.values.toList()

            override fun getChatClient(
                key: String,
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
            override fun getEmbeddingClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ): com.simiacryptus.cognotik.embedding.EmbeddingClientInterface {
                throw UnsupportedOperationException("AWS does not support embedding functionality yet")
            }
        }
        val Groq: APIProvider = object : APIProvider("Groq", "https://api.groq.com/openai/v1") {

            override fun getChatModels(key: String, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator( Executors.newScheduledThreadPool(1) ),
                logLevel = Level.INFO,
                logStreams = mutableListOf()
            ).getModels() ?: GroqModels.values.values.toList()

            override fun getChatClient(
                key: String,
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
        }
        val Perplexity: APIProvider = object : APIProvider("Perplexity", "https://api.perplexity.ai") {

            override fun getChatModels(key: String, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator( Executors.newScheduledThreadPool(1) ),
                logLevel = Level.INFO,
                logStreams = mutableListOf()
            ).getModels() ?: PerplexityModels.values.values.toList()

            override fun getChatClient(
                key: String,
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
        val ModelsLab: APIProvider = object : APIProvider("ModelsLab", "https://modelslab.com/api/v6") {

            override fun getChatModels(key: String, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator( Executors.newScheduledThreadPool(1) ),
                logLevel = Level.INFO,
                logStreams = mutableListOf()
            ).getModels() ?: ModelsLabModels.values.values.toList()

            override fun getChatClient(
                key: String,
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
        val Mistral: APIProvider = object : APIProvider("Mistral", "https://api.mistral.ai/v1") {

            override fun getChatModels(key: String, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator( Executors.newScheduledThreadPool(1) ),
                logLevel = Level.INFO,
                logStreams = mutableListOf()
            ).getModels() ?: MistralModels.values.values.toList()

            override fun getChatClient(
                key: String,
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
        val DeepSeek: APIProvider = object : APIProvider("DeepSeek", "https://api.deepseek.com") {

            override fun getChatModels(key: String, baseUrl: String) = getChatClient(
                key = key,
                base = baseUrl,
                workPool = MoreExecutors.newDirectExecutorService(),
                scheduledPool = MoreExecutors.listeningDecorator( Executors.newScheduledThreadPool(1) ),
                logLevel = Level.INFO,
                logStreams = mutableListOf()
            ).getModels() ?: DeepSeekModels.values.values.toList()

            override fun getChatClient(
                key: String,
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
        val Google: APIProvider = object : APIProvider("GoogleSearch", "c581d1409962d72e1") {

            override fun getChatModels(key: String, baseUrl: String): List<ChatModel> = emptyList()

            override fun getChatClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>,
                scheduledPool: ListeningScheduledExecutorService
            ) = throw UnsupportedOperationException("Google Search API does not support chat functionality")
        }
        val Github: APIProvider = object : APIProvider("Github", "https://api.github.com") {

            override fun getChatModels(key: String, baseUrl: String): List<ChatModel> = emptyList()

            override fun getChatClient(
                key: String,
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