package com.simiacryptus.cognotik.platform

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.ImmediateExecutorService
import org.slf4j.LoggerFactory
import java.util.concurrent.ScheduledThreadPoolExecutor

class ThreadPoolManager {

  fun threadFactory(session: Session, user: User?): RecordingThreadFactory = RecordingThreadFactory(session, user)

  fun getPool(
      session: Session,
      user: User,
  ) = poolCache.getOrPut(SessionKey(session, user)) {
    log.debug("Creating thread pool for session: {}, user: {}", session, user)
    createPool(session, user)
  }

  fun getScheduledPool(
      session: Session,
      user: User,
  ) = scheduledPoolCache.getOrPut(SessionKey(session, user)) {
    log.debug("Creating scheduled pool for session: {}", session)
    createScheduledPool(session, user)
  }

  fun isAlive(
      session: Session? = null,
      user: User? = null,
  ): Boolean {
    val matchingFactories = mutableListOf<RecordingThreadFactory>()

    // Collect factories from the regular pool cache
    poolCache.forEach { (key, executor) ->
      if (matchesKey(key, session, user)) {
        (executor.threadFactory as? RecordingThreadFactory)?.let { matchingFactories.add(it) }
      }
    }

    // Collect factories from the scheduled pool cache
    scheduledPoolCache.forEach { (key, _) ->
      if (matchesKey(key, session, user)) {
        // Scheduled pools don't expose their factory directly via the listening decorator,
        // but we tracked them by key; check via a separate registry if needed.
      }
    }

    // Check if any thread in any matching factory is still alive
    val anyAlive = matchingFactories.any { factory ->
      factory.threads.any { it.isAlive }
    }

    if (anyAlive) {
      log.debug("Found alive threads for session: {}, user: {}", session, user)
    } else {
      log.debug("No alive threads found for session: {}, user: {}", session, user)
    }

    return anyAlive
  }

  /**
   * Determines whether a given SessionKey matches the provided session/user filter.
   * - If both session and user are null, all keys match.
   * - If session is null, match keys where the user matches.
   * - If user is null, match keys where the session matches.
   * - Otherwise, both must match.
   */
  private fun matchesKey(key: SessionKey, session: Session?, user: User?): Boolean {
    val sessionMatches = session == null || key.session == session
    val userMatches = user == null || key.user == user
    return sessionMatches && userMatches
  }

  class RecordingThreadFactory(
      val session: Session,
      val user: User?
  ) : ImmediateExecutorService.ThreadFactoryTrackerInterface() {
    private val inner =
      ThreadFactoryBuilder().setNameFormat("Session $session; User $user; #%d").setDaemon(true).build()

    override fun newThread(r: Runnable): Thread {
      log.debug("Creating new thread for session: {}, user: {}", session, user)
      inner.newThread(r).also {
        threads.add(it)
        return it
      }
    }
  }

  private data class SessionKey(val session: Session, val user: User?)

  private val poolCache = mutableMapOf<SessionKey, ImmediateExecutorService>()

  private fun createPool(session: Session, user: User?) = ImmediateExecutorService(threadFactory(session, user))

  private val scheduledPoolCache = mutableMapOf<SessionKey, ListeningScheduledExecutorService>()

  private fun createScheduledPool(session: Session, user: User?) =
    MoreExecutors.listeningDecorator(ScheduledThreadPoolExecutor(1).apply {
      threadFactory = threadFactory(session, user)
    })

  companion object {
    private val log = LoggerFactory.getLogger(ThreadPoolManager::class.java)
  }
}