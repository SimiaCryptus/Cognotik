package com.simiacryptus.cognotik.models

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.chat.*
import com.simiacryptus.cognotik.chat.model.*
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer
import org.slf4j.Logger
import com.simiacryptus.cognotik.util.LoggerFactory
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

private val log: Logger = LoggerFactory.getLogger(APIProvider::class.java)

@JsonDeserialize(using = APIProviderDeserializer::class)
@JsonSerialize(using = APIProviderSerializer::class)
abstract class APIProvider private constructor(name: String, val base: String) : DynamicEnum<APIProvider>(name) {

    abstract fun getChatClient(
        key: String,
        base: String,
        workPool: ExecutorService,
        logLevel: Level = Level.INFO,
        logStreams: MutableList<BufferedOutputStream> = mutableListOf()
    ): ChatClientInterface

    abstract fun getChatModels(): List<ChatModel>

    companion object {
        val SearchAPI: APIProvider = object : APIProvider("SearchAPI", "https://api.searchapi.com") {

            override fun getChatModels(): List<ChatModel> = emptyList()

            override fun getChatClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>
            ) = throw UnsupportedOperationException("SearchAPI does not support chat functionality")
        }

        val Google: APIProvider = object : APIProvider("Google", "https://generativelanguage.googleapis.com") {

            override fun getChatModels(): List<ChatModel> = GoogleModels.values.values.toList()

            override fun getChatClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>
            ) = GoogleChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams
            )
        }
        val Ollama: APIProvider = object : APIProvider("Ollama", "http://localhost:11434") {

            override fun getChatModels(): List<ChatModel> = emptyList()

            override fun getChatClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>
            ) = throw UnsupportedOperationException("Ollama API does not support chat functionality")
        }
        val OpenAI: APIProvider = object : APIProvider("OpenAI", "https://api.openai.com/v1") {

            override fun getChatModels(): List<ChatModel> = OpenAIModels.values.values.toList()

            override fun getChatClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>
            ) = GoogleChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams
            )
        }
        val Anthropic: APIProvider = object : APIProvider("Anthropic", "https://api.anthropic.com/v1") {

            override fun getChatModels(): List<ChatModel> = AnthropicModels.values.values.toList()

            override fun getChatClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>
            ) = AnthropicChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams
            )
        }
        val AWS: APIProvider = object : APIProvider("AWS", "https://api.openai.aws") {

            override fun getChatModels(): List<ChatModel> = AWSModels.values.values.toList()

            override fun getChatClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>
            ) = AwsChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams
            )
        }
        val Groq: APIProvider = object : APIProvider("Groq", "https://api.groq.com/openai/v1") {

            override fun getChatModels(): List<ChatModel> = GroqModels.values.values.toList()

            override fun getChatClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>
            ) = GroqChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams
            )
        }
        val Perplexity: APIProvider = object : APIProvider("Perplexity", "https://api.perplexity.ai") {

            override fun getChatModels(): List<ChatModel> = PerplexityModels.values.values.toList()

            override fun getChatClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>
            ) = OpenAIChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool
            )
        }
        val ModelsLab: APIProvider = object : APIProvider("ModelsLab", "https://modelslab.com/api/v6") {

            override fun getChatModels(): List<ChatModel> = ModelsLabModels.values.values.toList()

            override fun getChatClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>
            ) = ModelsLabChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams
            )
        }
        val Mistral: APIProvider = object : APIProvider("Mistral", "https://api.mistral.ai/v1") {

            override fun getChatModels(): List<ChatModel> = MistralModels.values.values.toList()

            override fun getChatClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>
            ) = MistralChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams
            )
        }
        val DeepSeek: APIProvider = object : APIProvider("DeepSeek", "https://api.deepseek.com") {

            override fun getChatModels(): List<ChatModel> = DeepSeekModels.values.values.toList()

            override fun getChatClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>
            ) = DeepSeekChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams
            )
        }
        val GoogleSearch: APIProvider = object : APIProvider("GoogleSearch", "c581d1409962d72e1") {

            override fun getChatModels(): List<ChatModel> = emptyList()

            override fun getChatClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>
            ) = GoogleChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams
            )
        }
        val Github: APIProvider = object : APIProvider("Github", "https://api.github.com") {

            override fun getChatModels(): List<ChatModel> = emptyList()

            override fun getChatClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>
            ) = throw UnsupportedOperationException("Github API does not support chat functionality")
        }

        init {
            log.info("Registering API providers")
            register(APIProvider::class.java, Google)
            register(APIProvider::class.java, OpenAI)
            register(APIProvider::class.java, Anthropic)
            register(APIProvider::class.java, AWS)
            register(APIProvider::class.java, Groq)
            register(APIProvider::class.java, Perplexity)
            register(APIProvider::class.java, ModelsLab)
            register(APIProvider::class.java, Mistral)
            register(APIProvider::class.java, DeepSeek)
            register(APIProvider::class.java, GoogleSearch)
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