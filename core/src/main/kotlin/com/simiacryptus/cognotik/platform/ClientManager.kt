package com.simiacryptus.cognotik.platform

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.platform.ApplicationServices.dataStorageFactory
import com.simiacryptus.cognotik.platform.ApplicationServices.userSettingsManager
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.ImmediateExecutorService
import com.simiacryptus.jopenai.chat.ChatClientInterface
import com.simiacryptus.jopenai.chat.ProvidersChatClient
import com.simiacryptus.jopenai.chat.model.ChatModelType
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.LLMModel
import com.simiacryptus.util.LoggerFactory
import java.io.BufferedOutputStream
import java.util.concurrent.ScheduledThreadPoolExecutor

open class ClientManager {

    private data class SessionKey(val session: Session, val user: User?)

    private val chatCache = mutableMapOf<SessionKey, ChatClientInterface>()
    fun getChatClient(
        session: Session,
        user: User?,
    ) = chatCache.getOrPut(SessionKey(session, user)) {
        createChatClient(session, user) ?: throw RuntimeException("No API key")
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


fun ChatModelType.instance(
    user: User?
): ChatModelType.ChatModel {
    val userSettings = if (user == null) {
        null
    } else {
        userSettingsManager.getUserSettings(user)
    }
    val apiData = userSettings?.apis?.filter { it.provider == this.provider }?.firstOrNull()
    return this.instance(
        key = apiData?.key ?: throw RuntimeException("No API key for model ${this.name}"),
        base = apiData.baseUrl ?: this.provider.base ?: "",
    )
}