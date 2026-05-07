package com.simiacryptus.cognotik.models

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.audio.AudioModels
import com.simiacryptus.cognotik.chat.*
import com.simiacryptus.cognotik.chat.model.*
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.image.*
import com.simiacryptus.cognotik.models.ServiceProviders.Github
import com.simiacryptus.cognotik.models.ServiceProviders.Google
import com.simiacryptus.cognotik.models.ServiceProviders.SearchAPI
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
abstract class APIProvider(name: String, val base: String) : DynamicEnum<APIProvider>(name) {

  abstract fun getChatClient(
    key: SecureString,
    base: String = this.base,
    workPool: ExecutorService = MoreExecutors.newDirectExecutorService(),
    logLevel: Level = Level.DEBUG,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
  ): ChatClientInterface

  open fun getChatModels(key: SecureString, baseUrl: String) = getChatClient(key = key, base = baseUrl).getModels()

  open fun getEmbeddingModels(key: SecureString, baseUrl: String): List<EmbeddingModel> = emptyList()

  open fun getTranscriptionModels(key: SecureString, baseUrl: String): List<AudioModels> = emptyList()
  open fun getImageModels(key: SecureString, baseUrl: String): List<ImageModel> = emptyList()

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

  open fun getEmbeddingModels() = emptyList<EmbeddingModel>()

  companion object {

    val NULL: APIProvider = object : APIProvider("NULL", "") {
      override fun getChatClient(
        key: SecureString,
        base: String,
        workPool: ExecutorService,
        logLevel: Level,
        logStreams: MutableList<BufferedOutputStream>,
        scheduledPool: ListeningScheduledExecutorService
      ): ChatClientInterface {
        throw UnsupportedOperationException("NULL provider does not support chat functionality")
      }
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