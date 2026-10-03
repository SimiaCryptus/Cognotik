package com.simiacryptus.cognotik.platform.model

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.platform.ThreadPoolManager
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

private val log: Logger = getLogger(APIProvider::class.java)

@JsonDeserialize(using = APIProviderDeserializer::class)
@JsonSerialize(using = APIProviderSerializer::class)
abstract class APIProvider(name: String, open val base: String) : DynamicEnum<APIProvider>(name) {

  abstract fun getChatClient(
    key: SecureString,
    logLevel: Level = Level.DEBUG,
    session: Session,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    workPool: ExecutorService = ThreadPoolManager.newCachedThreadPool(session),
    scheduledPool: ListeningScheduledExecutorService = ThreadPoolManager.newScheduledThreadPool(1, session)
  ): ChatClientInterface

  open fun getChatModels(key: SecureString, baseUrl: String) =
    getChatClient(key = key, session = Session.newUserID()).getModels()

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
  ): com.simiacryptus.cognotik.platform.model.EmbeddingClientInterface {
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
        logLevel: Level,
        session: Session,
        logStreams: MutableList<BufferedOutputStream>,
        workPool: ExecutorService,
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