package com.simiacryptus.cognotik.models

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.util.DynamicEnum.Companion.register
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

object ServiceProviders {

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
    register(APIProvider::class.java, Google)
    register(APIProvider::class.java, Github)
    register(APIProvider::class.java, SearchAPI)
  }
}