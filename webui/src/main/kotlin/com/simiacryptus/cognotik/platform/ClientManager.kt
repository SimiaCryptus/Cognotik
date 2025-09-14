package com.simiacryptus.cognotik.platform

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.ImmediateExecutorService
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.RecordingThreadFactory
import java.util.concurrent.ScheduledThreadPoolExecutor

open class ClientManager {

    private data class SessionKey(val session: Session, val user: User?)

    private val poolCache = mutableMapOf<SessionKey, ImmediateExecutorService>()

    protected open fun createPool(session: Session, user: User?) =
        ImmediateExecutorService(RecordingThreadFactory(session, user))

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

    companion object {
        private val log = LoggerFactory.getLogger(ClientManager::class.java)
    }
}
