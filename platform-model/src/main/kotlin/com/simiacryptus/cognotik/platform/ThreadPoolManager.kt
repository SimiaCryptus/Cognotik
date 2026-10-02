package com.simiacryptus.cognotik.platform

import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.ImmediateExecutorService
import org.slf4j.LoggerFactory
import java.util.concurrent.*

open class ThreadPoolManager {

  private data class SessionKey(val session: Session, val user: User?)

  private val poolCache = ConcurrentHashMap<SessionKey, ListeningExecutorService>()
  private val scheduledPoolCache = ConcurrentHashMap<SessionKey, ListeningScheduledExecutorService>()
  private val managedExecutors = ConcurrentHashMap<SessionKey, CopyOnWriteArrayList<ExecutorService>>()

  /**
   * Every factory handed out, indexed by scope. The scheduled pools are wrapped by
   * [MoreExecutors.listeningDecorator], which does not expose the underlying
   * thread factory, so [isAlive] previously could not see them at all.
   */
  private val factories = ConcurrentHashMap<SessionKey, CopyOnWriteArrayList<RecordingThreadFactory>>()

  @JvmOverloads
  fun threadFactory(
    session: Session,
    user: User? = null,
    inner: ThreadFactory? = null,
  ): RecordingThreadFactory =
    (if (inner != null) RecordingThreadFactory(session, user, inner) else RecordingThreadFactory(
      session,
      user
    )).also { factory ->
      factories.computeIfAbsent(SessionKey(session, user)) { CopyOnWriteArrayList() }.add(factory)
    }

  @JvmOverloads
  fun getPool(
    session: Session,
    user: User? = null,
  ) = poolCache.computeIfAbsent(SessionKey(session, user)) {
    log.debug("Creating thread pool for session: {}, user: {}", session, user)
    createPool(session, user)
  }

  @JvmOverloads
  fun getScheduledPool(
    session: Session,
    user: User? = null,
  ) = scheduledPoolCache.computeIfAbsent(SessionKey(session, user)) {
    log.debug("Creating scheduled pool for session: {}, user: {}", session, user)
    ScheduledThreadPoolExecutor(1).apply {
      this.threadFactory = threadFactory(session, user)
    }.let { MoreExecutors.listeningDecorator(it) }
  }

  @JvmOverloads
  fun newCachedThreadPool(
    session: Session,
    user: User? = null,
    threadFactory: ThreadFactory? = null,
  ) = Executors.newCachedThreadPool(threadFactory(session, user, threadFactory)).also { executor ->
    recordExecutor(session, user, executor)
  }.let { MoreExecutors.listeningDecorator(it) }

  @JvmOverloads
  fun newFixedThreadPool(
    nThreads: Int,
    session: Session,
    user: User? = null,
    threadFactory: ThreadFactory? = null,
  ) = Executors.newFixedThreadPool(nThreads, threadFactory(session, user, threadFactory)).also { executor ->
    recordExecutor(session, user, executor)
  }.let { MoreExecutors.listeningDecorator(it) }

  @JvmOverloads
  fun newScheduledThreadPool(
    nThreads: Int,
    session: Session,
    user: User? = null,
    threadFactory: ThreadFactory? = null,
  ) = Executors.newScheduledThreadPool(nThreads, threadFactory(session, user, threadFactory)).also { executor ->
    recordExecutor(session, user, executor)
  }.let { MoreExecutors.listeningDecorator(it) }

  @JvmOverloads
  fun newSingleThreadExecutor(
    session: Session,
    user: User? = null,
    threadFactory: ThreadFactory? = null,
  ) = Executors.newSingleThreadExecutor(threadFactory(session, user, threadFactory)).also { executor ->
    recordExecutor(session, user, executor)
  }.let { MoreExecutors.listeningDecorator(it) }

  private fun recordExecutor(session: Session, user: User?, executor: ExecutorService) {
    managedExecutors.computeIfAbsent(SessionKey(session, user)) { CopyOnWriteArrayList() }.add(executor)
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
    val matchingKeys = if (user == null) {
      (poolCache.keys + scheduledPoolCache.keys + managedExecutors.keys + factories.keys)
        .filter { it.session == session }
        .toSet()
    } else {
      setOf(SessionKey(session, user))
    }
    for (key in matchingKeys) {
      (poolCache.remove(key) as? ExecutorService)?.let { runCatching { it.shutdown() } }
      scheduledPoolCache.remove(key)?.let { runCatching { it.shutdown() } }
      managedExecutors.remove(key)?.forEach { runCatching { it.shutdown() } }
      factories.remove(key)
    }
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
    val user: User?,
    private val inner: ThreadFactory =
      ThreadFactoryBuilder().setNameFormat("Session $session; User $user; #%d").setDaemon(true).build(),
  ) : ImmediateExecutorService.ThreadFactoryTrackerInterface() {

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
    .let { MoreExecutors.listeningDecorator(it) }

  fun livingThreads() : List<Thread> = factories.values.flatMap { list ->
    list.flatMap { factory ->
      synchronized(factory.threads) {
        factory.threads.filter { it.isAlive }
      }
    }
  }

  companion object : ThreadPoolManager() {

    private val log = LoggerFactory.getLogger(ThreadPoolManager::class.java)
  }
}

val ListeningExecutorService.threadFactory: ThreadPoolManager.RecordingThreadFactory
  get() {
    val field = this::class.java.getDeclaredField("delegate")
    field.isAccessible = true
    val delegate = field.get(this)
    val delegateField = delegate::class.java.getDeclaredField("threadFactory")
    delegateField.isAccessible = true
    return delegateField.get(delegate) as ThreadPoolManager.RecordingThreadFactory
  }
