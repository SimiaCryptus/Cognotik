package com.simiacryptus.jopenai.models

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.jopenai.chat.*
import com.simiacryptus.jopenai.models.chat.AWSModels
import com.simiacryptus.jopenai.models.chat.AnthropicModels
import com.simiacryptus.jopenai.models.chat.ChatModelType
import com.simiacryptus.jopenai.models.chat.DeepSeekModels
import com.simiacryptus.jopenai.models.chat.GoogleModels
import com.simiacryptus.jopenai.models.chat.GroqModels
import com.simiacryptus.jopenai.models.chat.MistralModels
import com.simiacryptus.jopenai.models.chat.ModelsLabModels
import com.simiacryptus.jopenai.models.chat.OpenAIModels
import com.simiacryptus.jopenai.models.chat.PerplexityModels
import com.simiacryptus.util.DynamicEnum
import com.simiacryptus.util.DynamicEnumDeserializer
import com.simiacryptus.util.DynamicEnumSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

private val log: Logger = LoggerFactory.getLogger(APIProvider::class.java)

@JsonDeserialize(using = APIProviderDeserializer::class)
@JsonSerialize(using = APIProviderSerializer::class)
abstract class APIProvider private constructor(name: String, val base: String? = null) : DynamicEnum<APIProvider>(name) {

    abstract fun getChatClient(
        key: String,
        base: String,
        workPool: ExecutorService,
        logLevel: Level = Level.INFO,
        logStreams: MutableList<BufferedOutputStream> = mutableListOf()
    ): ChatClientInterface

    abstract fun getChatModels(): List<ChatModelType>

    companion object {
        val Google = object : APIProvider("Google", "https://generativelanguage.googleapis.com") {

            override fun getChatModels(): List<ChatModelType> = GoogleModels.values.values.toList()

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
        val OpenAI = object : APIProvider("OpenAI", "https://api.openai.com/v1") {

            override fun getChatModels(): List<ChatModelType> = OpenAIModels.values.values.toList()

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
        val Anthropic = object : APIProvider("Anthropic", "https://api.anthropic.com/v1") {

            override fun getChatModels(): List<ChatModelType> = AnthropicModels.values.values.toList()

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
        val AWS = object : APIProvider("AWS", "https://api.openai.aws") {

            override fun getChatModels(): List<ChatModelType> = AWSModels.values.values.toList()

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
        val Groq = object : APIProvider("Groq", "https://api.groq.com/openai/v1") {

            override fun getChatModels(): List<ChatModelType> = GroqModels.values.values.toList()

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
        val Perplexity = object : APIProvider("Perplexity", "https://api.perplexity.ai") {

            override fun getChatModels(): List<ChatModelType> = PerplexityModels.values.values.toList()

            override fun getChatClient(
                key: String,
                base: String,
                workPool: ExecutorService,
                logLevel: Level,
                logStreams: MutableList<BufferedOutputStream>
            ) = OpenAIChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams
            )
        }
        val ModelsLab = object : APIProvider("ModelsLab", "https://modelslab.com/api/v6") {

            override fun getChatModels(): List<ChatModelType> = ModelsLabModels.values.values.toList()

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

            override fun getChatModels(): List<ChatModelType> = MistralModels.values.values.toList()

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
        val DeepSeek = object : APIProvider("DeepSeek", "https://api.deepseek.com") {

            override fun getChatModels(): List<ChatModelType> = DeepSeekModels.values.values.toList()

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
        val GoogleSearch = object : APIProvider("GoogleSearch", "c581d1409962d72e1") {

            override fun getChatModels(): List<ChatModelType> = emptyList()

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
        val Github = object : APIProvider("Github", "https://api.github.com") {

            override fun getChatModels(): List<ChatModelType> = emptyList()

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