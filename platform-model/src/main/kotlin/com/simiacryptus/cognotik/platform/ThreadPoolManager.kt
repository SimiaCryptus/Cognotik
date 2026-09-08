package com.simiacryptus.cognotik.platform

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.ImmediateExecutorService
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor

class ThreadPoolManager {

  private data class SessionKey(val session: Session, val user: User?)

  private val poolCache = ConcurrentHashMap<SessionKey, ImmediateExecutorService>()
  private val scheduledPoolCache = ConcurrentHashMap<SessionKey, ListeningScheduledExecutorService>()

  /**
   * Every factory handed out, indexed by scope. The scheduled pools are wrapped by
   * [MoreExecutors.listeningDecorator], which does not expose the underlying
   * thread factory, so [isAlive] previously could not see them at all.
   */
  private val factories = ConcurrentHashMap<SessionKey, CopyOnWriteArrayList<RecordingThreadFactory>>()

  @JvmOverloads
  fun threadFactory(session: Session, user: User? = null): RecordingThreadFactory =
    RecordingThreadFactory(session, user).also { factory ->
      factories.computeIfAbsent(SessionKey(session, user)) { CopyOnWriteArrayList() }.add(factory)
    }

  @JvmOverloads
  fun getPool(
    session: Session,
    user: User? = null,
  ): ImmediateExecutorService = poolCache.computeIfAbsent(SessionKey(session, user)) {
    log.debug("Creating thread pool for session: {}, user: {}", session, user)
    createPool(session, user)
  }

  @JvmOverloads
  fun getScheduledPool(
    session: Session,
    user: User? = null,
  ): ListeningScheduledExecutorService = scheduledPoolCache.computeIfAbsent(SessionKey(session, user)) {
    log.debug("Creating scheduled pool for session: {}, user: {}", session, user)
    createScheduledPool(session, user)
  }

  fun isAlive(
    session: Session? = null,
    user: User? = null,
  ): Boolean {
    val anyAlive = factories.entries.any { (key, list) ->
      matchesKey(key, session, user) && list.any { it.hasLiveThreads() }
    }
    if (anyAlive) {
      log.debug("Found alive threads for session: {}, user: {}", session, user)
    } else {
      log.debug("No alive threads found for session: {}, user: {}", session, user)
    }
    return anyAlive
  }

  /**
   * Evict and shut down the executors scoped to a session, releasing the thread
   * bookkeeping. Without this the caches (and the recorded thread lists) grow for
   * the lifetime of the JVM.
   */
  @JvmOverloads
  fun shutdown(session: Session, user: User? = null) {
    val key = SessionKey(session, user)
    (poolCache.remove(key) as? ExecutorService)?.let { runCatching { it.shutdown() } }
    scheduledPoolCache.remove(key)?.let { runCatching { it.shutdown() } }
    factories.remove(key)
    log.debug("Shut down pools for session: {}, user: {}", session, user)
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
      val thread = inner.newThread(r)
      synchronized(threads) {
        // Drop terminated threads so the tracker does not retain every thread
        // ever created for a long-lived session.
        threads.removeAll { !it.isAlive }
        threads.add(thread)
      }
      return thread
    }

    fun hasLiveThreads(): Boolean = synchronized(threads) { threads.any { it.isAlive } }
  }

  private fun createPool(session: Session, user: User?) = ImmediateExecutorService(threadFactory(session, user))

  private fun createScheduledPool(session: Session, user: User?) =
    MoreExecutors.listeningDecorator(ScheduledThreadPoolExecutor(1).apply {
      threadFactory = this@ThreadPoolManager.threadFactory(session, user)
    })

  companion object {
    private val log = LoggerFactory.getLogger(ThreadPoolManager::class.java)
  }
}