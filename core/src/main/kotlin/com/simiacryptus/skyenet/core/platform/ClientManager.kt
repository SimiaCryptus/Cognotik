package com.simiacryptus.skyenet.core.platform

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.APIProvider
import com.simiacryptus.jopenai.util.ClientUtil
import com.simiacryptus.skyenet.core.platform.ApplicationServices.dataStorageFactory
import com.simiacryptus.skyenet.core.platform.ApplicationServices.userSettingsManager
import com.simiacryptus.skyenet.core.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.skyenet.core.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.skyenet.core.platform.model.StorageInterface
import com.simiacryptus.skyenet.core.platform.model.User
import org.slf4j.LoggerFactory
import java.util.concurrent.*

open class ClientManager {

  private data class SessionKey(val session: Session, val user: User?)

  private val chatCache = mutableMapOf<SessionKey, ChatClient>()
  fun getChatClient(
    session: Session,
    user: User?,
  ): ChatClient {
    log.debug("Fetching client for session: {}, user: {}", session, user)
    val key = SessionKey(session, user)
    return chatCache.getOrPut(key) { createChatClient(session, user)!! }
  }

  private val openAICache = mutableMapOf<SessionKey, OpenAIClient>()
  fun getOpenAIClient(
    session: Session,
    user: User?,
  ): OpenAIClient {
    log.debug("Fetching client for session: {}, user: {}", session, user)
    val key = SessionKey(session, user)
    return openAICache.getOrPut(key) { createOpenAIClient(session, user)!! }
  }

  private val poolCache = mutableMapOf<SessionKey, ThreadPoolExecutor>()
  protected open fun createPool(session: Session, user: User?) =
    ThreadPoolExecutor(
      8, Integer.MAX_VALUE,
      500, TimeUnit.MILLISECONDS,
      SynchronousQueue(),
      RecordingThreadFactory(session, user)
    )

  private val scheduledPoolCache = mutableMapOf<SessionKey, ListeningScheduledExecutorService>()
  protected open fun createScheduledPool(session: Session, user: User?, dataStorage: StorageInterface?) =
    MoreExecutors.listeningDecorator(ScheduledThreadPoolExecutor(1))

  fun getPool(
    session: Session,
    user: User?,
  ): ThreadPoolExecutor {
    log.debug("Fetching thread pool for session: {}, user: {}", session, user)
    val key = SessionKey(session, user)
    return poolCache.getOrPut(key) {
      createPool(session, user)
    }
  }

  fun getScheduledPool(
    session: Session,
    user: User?,
    dataStorage: StorageInterface?,
  ): ListeningScheduledExecutorService {
    log.debug("Fetching scheduled pool for session: {}, user: {}", session, user)
    val key = SessionKey(session, user)
    return scheduledPoolCache.getOrPut(key) {
      createScheduledPool(session, user, dataStorage)
    }
  }

  inner class RecordingThreadFactory(
    val session: Session,
    val user: User?
  ) : ThreadFactory {
    private val inner = ThreadFactoryBuilder().setNameFormat("Session $session; User $user; #%d").build()
    val threads = mutableSetOf<Thread>()
    override fun newThread(r: Runnable): Thread {
      log.debug("Creating new thread for session: {}, user: {}", session, user)
      inner.newThread(r).also {
        threads.add(it)
        return it
      }
    }
  }

  protected open fun createChatClient(
    session: Session,
    user: User?,
  ): ChatClient? {
    log.debug("Creating client for session: {}, user: {}", session, user)
    val sessionDir = dataStorageFactory(dataStorageRoot).getDataDir(user, session).apply { mkdirs() }
    if (user != null) {
      val userSettings = userSettingsManager.getUserSettings(user)
      val userApi =
        if (userSettings.apiKeys.isNotEmpty()) {
          ChatClient(
            key = userSettings.apiKeys,
            apiBase = userSettings.apiBase,
            workPool = getPool(session, user),
          ).apply {
            this.session = session
            this.user = user
            logStreams += sessionDir.resolve("openai.log").outputStream().buffered()
          }
        } else null
      if (userApi != null) return userApi
    }
    val canUseGlobalKey = ApplicationServices.authorizationManager.isAuthorized(
      null, user, OperationType.GlobalKey
    )
    if (!canUseGlobalKey) throw RuntimeException("No API key")
    return (if (ClientUtil.keyMap.isNotEmpty()) {
      ChatClient(
        key = ClientUtil.keyMap.mapKeys { APIProvider.valueOf(it.key) },
        workPool = getPool(session, user),
      ).apply {
        this.session = session
        this.user = user
        logStreams += sessionDir.resolve("openai.log").outputStream().buffered()
      }
    } else {
      null
    })!!
  }

  protected open fun createOpenAIClient(
    session: Session,
    user: User?,
  ): OpenAIClient? {
    log.debug("Creating client for session: {}, user: {}", session, user)
    val sessionDir = dataStorageFactory(dataStorageRoot).getDataDir(user, session).apply { mkdirs() }
    if (user != null) {
      val userSettings = userSettingsManager.getUserSettings(user)
      val userApi =
        if (userSettings.apiKeys.isNotEmpty()) {
          OpenAIClient(
              key = userSettings.apiKeys,
              apiBase = userSettings.apiBase,
              workPool = getPool(session, user),
          ).apply {
            this.session = session
            this.user = user
            logStreams += sessionDir.resolve("openai.log").outputStream().buffered()
          }
        } else null
      if (userApi != null) return userApi
    }
    val canUseGlobalKey = ApplicationServices.authorizationManager.isAuthorized(
      null, user, OperationType.GlobalKey
    )
    if (!canUseGlobalKey) throw RuntimeException("No API key")
    return (if (ClientUtil.keyMap.isNotEmpty()) {
      OpenAIClient(
          key = ClientUtil.keyMap.mapKeys { APIProvider.valueOf(it.key) },
          workPool = getPool(session, user),
      ).apply {
        this.session = session
        this.user = user
        logStreams += sessionDir.resolve("openai.log").outputStream().buffered()
      }
    } else {
      null
    })!!
  }

  companion object {
    private val log = LoggerFactory.getLogger(ClientManager::class.java)
  }
}