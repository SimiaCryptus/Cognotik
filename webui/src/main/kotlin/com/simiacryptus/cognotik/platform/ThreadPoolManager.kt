package com.simiacryptus.cognotik.platform

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.ImmediateExecutorService
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.RecordingThreadFactory
import java.util.concurrent.ScheduledThreadPoolExecutor

open class ThreadPoolManager {

    private data class SessionKey(val session: Session, val user: User?)

    private val poolCache = mutableMapOf<SessionKey, ImmediateExecutorService>()

    protected open fun createPool(session: Session, user: User?) = ImmediateExecutorService(threadFactory(session, user))

    private val scheduledPoolCache = mutableMapOf<SessionKey, ListeningScheduledExecutorService>()

    protected fun createScheduledPool(session: Session, user: User?) =
        MoreExecutors.listeningDecorator(ScheduledThreadPoolExecutor(1).apply {
            threadFactory = threadFactory(session, user)
        })

    fun threadFactory(session: Session, user: User?): RecordingThreadFactory = RecordingThreadFactory(session, user)

    fun getPool(
        session: Session,
        user: User = defaultUser,
    ) = poolCache.getOrPut(SessionKey(session, user)) {
        log.debug("Creating thread pool for session: {}, user: {}", session, user)
        createPool(session, user)
    }

    fun getScheduledPool(
        session: Session,
        user: User = defaultUser,
    ) = scheduledPoolCache.getOrPut(SessionKey(session, user)) {
        log.debug("Creating scheduled pool for session: {}", session)
        createScheduledPool(session, user)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ThreadPoolManager::class.java)
    }
}
