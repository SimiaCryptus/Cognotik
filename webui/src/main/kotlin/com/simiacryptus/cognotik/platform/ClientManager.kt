package com.simiacryptus.cognotik.platform

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.platform.ApplicationServices.dataStorageFactory
import com.simiacryptus.cognotik.platform.ApplicationServices.userSettingsManager
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.ImmediateExecutorService
import com.simiacryptus.cognotik.chat.ChatClientInterface
import com.simiacryptus.cognotik.chat.ProvidersChatClient
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.RecordingThreadFactory
import java.io.BufferedOutputStream
import java.util.concurrent.ScheduledThreadPoolExecutor

open class ClientManager {

    private data class SessionKey(val session: Session, val user: User?)

    private val chatCache = mutableMapOf<SessionKey, ChatClientInterface>()

    fun getChatClient(
        session: Session,
        user: User?,
    ): ChatClientInterface = chatCache.getOrPut(SessionKey(session, user)) {
        createChatClient(session, user) ?: throw RuntimeException("No API key")
    }

    private val poolCache = mutableMapOf<SessionKey, ImmediateExecutorService>()

    protected open fun createPool(session: Session, user: User?) = ImmediateExecutorService(RecordingThreadFactory(session, user))

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
    ): ChatClientInterface? {
        log.debug("Creating chat client for session: {}, user: {}", session, user)
        val sessionDir = dataStorageFactory(dataStorageRoot).getDataDir(user, session).apply { mkdirs() }
        return if (user == null) {
            log.warn("No user provided for session: $session")
            null
        } else {
            val userSettings = userSettingsManager.getUserSettings(user)
            if (userSettings.apiKeys.isEmpty()) {
                log.warn("No API key for user: $user in session: $session")
                null
            } else {
                object : ProvidersChatClient(
                    apiKeyMap = userSettings.apiKeys,
                    apiBaseMap = userSettings.apiBase,
                    workPool = getPool(session, user)
                ) {
                    override fun onUsage(
                        model: LLMModel,
                        tokens: ApiModel.Usage,
                        logStreams: MutableList<BufferedOutputStream>
                    ) {
                        super.onUsage(model, tokens, logStreams)
                        ApplicationServices.usageManager.incrementUsage(session, user, model, tokens)
                    }
                }.apply {
                    this.session = session
                    this.user = user
                    logStreams += sessionDir.resolve("openai.log").outputStream().buffered()
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ClientManager::class.java)
    }
}
