package com.simiacryptus.cognotik.platform

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.platform.ApplicationServices.dataStorageFactory
import com.simiacryptus.cognotik.platform.ApplicationServices.userSettingsManager
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.ImmediateExecutorService
import com.simiacryptus.jopenai.chat.ChatClient
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.TextModel
import org.slf4j.LoggerFactory
import java.util.concurrent.ScheduledThreadPoolExecutor

open class ClientManager {

    private data class SessionKey(val session: Session, val user: User?)

    private val chatCache = mutableMapOf<SessionKey, ChatClient>()
    fun getChatClient(
        session: Session,
        user: User?,
    ): ChatClient {
        log.debug("Fetching client for session: {}, user: {}", session, user)
        val key = SessionKey(session, user)
        return chatCache.getOrPut(key) { createChatClient(session, user) ?: throw RuntimeException("No API key") }
    }

    private val poolCache = mutableMapOf<SessionKey, ImmediateExecutorService>()
    protected open fun createPool(session: Session, user: User?) = ImmediateExecutorService(session, user)

    private val scheduledPoolCache = mutableMapOf<SessionKey, ListeningScheduledExecutorService>()
    protected open fun createScheduledPool(session: Session, user: User?, dataStorage: StorageInterface?) =
        MoreExecutors.listeningDecorator(ScheduledThreadPoolExecutor(1))

    fun getPool(
        session: Session,
        user: User?,
    ) = poolCache.getOrPut(SessionKey(session, user)) {
        log.debug("Creating thread pool for session: {}, user: {}", session, user)
        createPool(session, user)
    }

    fun getScheduledPool(
        session: Session,
        user: User?,
        dataStorage: StorageInterface?,
    ) = scheduledPoolCache.getOrPut(SessionKey(session, user)) {
        log.debug("Creating scheduled pool for session: {}", session)
        createScheduledPool(session, user, dataStorage)
    }

    protected open fun createChatClient(
        session: Session,
        user: User?,
    ): ChatClient? {
        log.debug("Creating chat client for session: {}, user: {}", session, user)
        val sessionDir = dataStorageFactory(dataStorageRoot).getDataDir(user, session).apply { mkdirs() }
        if (user != null) {
            val userSettings = userSettingsManager.getUserSettings(user)
            return if (userSettings.apiKeys.isNotEmpty()) {
                object : ChatClient(
                    apiKeyMap = userSettings.apiKeys,
                    apiBaseMap = userSettings.apiBase,
                    workPool = getPool(session, user)
                ) {
                    override fun onUsage(
                        model: TextModel,
                        tokens: ApiModel.Usage
                    ) {
                        super.onUsage(model, tokens)
                        ApplicationServices.usageManager.incrementUsage(session, user, model!!, tokens)
                    }
                }.apply {
                    this.session = session
                    this.user = user
                    logStreams += sessionDir.resolve("openai.log").outputStream().buffered()
                }
            } else {
                log.warn("No API key for user: $user in session: $session")
                return null
            }
        } else {
            log.warn("No user provided for session: $session")
        }
        return null
    }

    companion object {
        private val log = LoggerFactory.getLogger(ClientManager::class.java)
    }
}

