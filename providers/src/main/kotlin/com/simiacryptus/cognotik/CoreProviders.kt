package com.simiacryptus.cognotik

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.audio.AudioModels
import com.simiacryptus.cognotik.chat.AnthropicChatClient
import com.simiacryptus.cognotik.chat.AwsChatClient
import com.simiacryptus.cognotik.chat.DeepSeekChatClient
import com.simiacryptus.cognotik.chat.GeminiSdkChatClient
import com.simiacryptus.cognotik.chat.GroqChatClient
import com.simiacryptus.cognotik.chat.MistralChatClient
import com.simiacryptus.cognotik.chat.ModelsLabChatClient
import com.simiacryptus.cognotik.chat.OllamaChatClient
import com.simiacryptus.cognotik.chat.OpenAIChatClient
import com.simiacryptus.cognotik.chat.model.AWSModels
import com.simiacryptus.cognotik.chat.model.AnthropicModels
import com.simiacryptus.cognotik.chat.model.DeepSeekModels
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.chat.model.GroqModels
import com.simiacryptus.cognotik.chat.model.MistralModels
import com.simiacryptus.cognotik.chat.model.ModelsLabModels
import com.simiacryptus.cognotik.chat.model.OpenAIModels
import com.simiacryptus.cognotik.chat.model.PerplexityModels
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.embedding.OllamaEmbeddingClient
import com.simiacryptus.cognotik.embedding.OllamaEmbeddingModels
import com.simiacryptus.cognotik.embedding.OpenAIEmbeddingClient
import com.simiacryptus.cognotik.embedding.OpenAIEmbeddingModels
import com.simiacryptus.cognotik.image.GeminiImageClient
import com.simiacryptus.cognotik.image.GeminiImageModels
import com.simiacryptus.cognotik.image.ImageClientInterface
import com.simiacryptus.cognotik.image.ImageModel
import com.simiacryptus.cognotik.image.OpenAIImageClient
import com.simiacryptus.cognotik.image.OpenAIImageModels
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object CoreProviders : CognotikPlugin {

  val log = LoggerFactory.getLogger(CoreProviders::class.java)

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
    ) = GeminiSdkChatClient(
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
    ) = OllamaEmbeddingClient(
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
    ) = OpenAIEmbeddingClient(
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
      key = key.decrypt!!,
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
        AudioModels(modelId = "gpt-4o-transcribe", provider = this),
        AudioModels(modelId = "gpt-4o-mini-transcribe", provider = this),
        AudioModels(modelId = "whisper-1", provider = this)
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
        AudioModels(modelId = "whisper-large-v3", provider = this),
        AudioModels(modelId = "whisper-large-v3-turbo", provider = this),
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


  override fun init() {
    log.info("Registering API providers")
    DynamicEnum.Companion.register(APIProvider::class.java, Gemini)
    DynamicEnum.Companion.register(APIProvider::class.java, OpenAI)
    DynamicEnum.Companion.register(APIProvider::class.java, Anthropic)
    DynamicEnum.Companion.register(APIProvider::class.java, AWS)
    DynamicEnum.Companion.register(APIProvider::class.java, Groq)
    DynamicEnum.Companion.register(APIProvider::class.java, Perplexity)
    DynamicEnum.Companion.register(APIProvider::class.java, ModelsLab)
    DynamicEnum.Companion.register(APIProvider::class.java, Mistral)
    DynamicEnum.Companion.register(APIProvider::class.java, DeepSeek)
    DynamicEnum.Companion.register(APIProvider::class.java, Ollama)

//    register(APIProvider::class.java, Google)
//    register(APIProvider::class.java, Github)
//    register(APIProvider::class.java, SearchAPI)
  }
}