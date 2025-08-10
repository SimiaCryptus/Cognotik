package com.simiacryptus.jopenai.models

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.jopenai.chat.*
import com.simiacryptus.jopenai.chat.model.AWSModels
import com.simiacryptus.jopenai.chat.model.AnthropicModels
import com.simiacryptus.jopenai.chat.model.ChatModelType
import com.simiacryptus.jopenai.chat.model.DeepSeekModels
import com.simiacryptus.jopenai.chat.model.GoogleModels
import com.simiacryptus.jopenai.chat.model.GroqModels
import com.simiacryptus.jopenai.chat.model.MistralModels
import com.simiacryptus.jopenai.chat.model.ModelsLabModels
import com.simiacryptus.jopenai.chat.model.OpenAIModels
import com.simiacryptus.jopenai.chat.model.PerplexityModels
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
        val Google: APIProvider = object : APIProvider("Google", "https://generativelanguage.googleapis.com") {

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
        val OpenAI: APIProvider = object : APIProvider("OpenAI", "https://api.openai.com/v1") {

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
        val Anthropic: APIProvider = object : APIProvider("Anthropic", "https://api.anthropic.com/v1") {

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
        val AWS: APIProvider = object : APIProvider("AWS", "https://api.openai.aws") {

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
        val Groq: APIProvider = object : APIProvider("Groq", "https://api.groq.com/openai/v1") {

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
        val Perplexity: APIProvider = object : APIProvider("Perplexity", "https://api.perplexity.ai") {

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
        val ModelsLab: APIProvider = object : APIProvider("ModelsLab", "https://modelslab.com/api/v6") {

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
        val DeepSeek: APIProvider = object : APIProvider("DeepSeek", "https://api.deepseek.com") {

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
        val GoogleSearch: APIProvider = object : APIProvider("GoogleSearch", "c581d1409962d72e1") {

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
        val Github: APIProvider = object : APIProvider("Github", "https://api.github.com") {

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