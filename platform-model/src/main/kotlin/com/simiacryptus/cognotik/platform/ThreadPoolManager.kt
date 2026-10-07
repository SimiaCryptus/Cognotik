package com.simiacryptus.cognotik.platform

import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.ImmediateExecutorService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

// Top-level (not in the companion): the companion is itself a ThreadPoolManager, so a logger
// declared there would still be null while the companion's superclass constructor runs.
private val log = LoggerFactory.getLogger(ThreadPoolManager::class.java)

/**
 * Process-wide, reflection-free index from a decorated executor to the factory that creates its
 * threads. Weak keys: an entry disappears once the executor itself is unreachable.
 */
private val executorFactories: MutableMap<ExecutorService, ThreadPoolManager.RecordingThreadFactory> =
  Collections.synchronizedMap(WeakHashMap())

/**
 * Default thread-name prefix. It deliberately avoids `user.toString()` (which may contain an email
 * address or other PII) and sanitises/truncates the session id. The result is also safe to embed
 * in a `String.format` pattern (no `%`).
 */
fun defaultThreadNamePrefix(session: Session, user: User?): String {
  val s = session.toString().replace(Regex("[^A-Za-z0-9_.-]"), "_").take(48)
  val u = user?.let { "u" + Integer.toHexString(it.hashCode()) } ?: "anon"
  return "session-$s-$u"
}

internal fun defaultInnerFactory(prefix: String): ThreadFactory =
  ThreadFactoryBuilder()
    .setNameFormat(prefix.replace("%", "%%") + "-%d")
    .setDaemon(true)
    .build()

/**
 * Owns all executors and thread factories, grouped per (session, user) scope.
 *
 * Prefer injecting an instance (`ThreadPoolManager()` / `ThreadPoolManager(config)`) into new code
 * and tests. The companion object is the process-wide default instance. It is kept so that existing
 * `ThreadPoolManager.getPool(...)` call sites keep compiling.
 */
open class ThreadPoolManager @JvmOverloads constructor(
  private val config: Config = Config(),
) : AutoCloseable {

  data class Config(
    /** Total time [shutdown]/[close] waits for tasks to finish before calling `shutdownNow`. */
    val shutdownGracePeriod: Duration = Duration.ofSeconds(5),
    /**
     * Upper bound on threads per [newCachedThreadPool]. Once saturated, tasks run on the
     * submitting thread instead of spawning more threads.
     */
    val maxCachedPoolThreads: Int = 256,
    /** If true, requesting resources for a session/user after it was shut down throws [SessionClosedException]. */
    val rejectAfterShutdown: Boolean = true,
    /** How many shut-down sessions/scopes are remembered for [rejectAfterShutdown] (LRU-bounded). */
    val closedScopeMemory: Int = 10_000,
    /** Produces the thread-name prefix for a scope. Must not leak PII. */
    val threadNamePrefix: (Session, User?) -> String = ::defaultThreadNamePrefix,
  ) {
    init {
      require(maxCachedPoolThreads > 0) { "maxCachedPoolThreads must be positive" }
      require(closedScopeMemory >= 0) { "closedScopeMemory must be non-negative" }
      require(!shutdownGracePeriod.isNegative) { "shutdownGracePeriod must be non-negative" }
    }
  }

  /**
   * Explicit user filter for operations that may span several users. It replaces the overloaded
   * meaning of `null` ("anonymous user" in getters, "any user" in shutdown/queries).
   */
  sealed class UserSelector {
    abstract fun matches(user: User?): Boolean

    /** Every user of the session, including the anonymous scope. */
    object AnyUser : UserSelector() {
      override fun matches(user: User?) = true
      override fun toString() = "AnyUser"
    }

    /** Exactly one scope; `Exactly(null)` is the anonymous-user scope. */
    data class Exactly(val user: User?) : UserSelector() {
      override fun matches(user: User?) = this.user == user
    }

    companion object {
      @JvmField
      val ANONYMOUS: UserSelector = Exactly(null)

      @JvmStatic
      fun of(user: User?): UserSelector = Exactly(user)
    }
  }

  /** Thrown when resources are requested for a scope that has been shut down. */
  class SessionClosedException(message: String) : IllegalStateException(message)

  private data class SessionKey(val session: Session, val user: User?)

  /** Internal signal: a scope was closed between lookup and use. The caller retries. */
  private object ScopeClosed : RuntimeException(null, null, false, false) {
    private fun readResolve(): Any = ScopeClosed
  }

  private class Drained(val executors: List<ExecutorService>, val factories: List<RecordingThreadFactory>)

  private val scopes = ConcurrentHashMap<SessionKey, SessionScope>()

  /** Read: scope creation. Write: shutdown/close. This makes "tombstone + evict" atomic with respect to creation. */
  private val lifecycleLock = ReentrantReadWriteLock()
  private val closedKeys = BoundedSet<SessionKey>(config.closedScopeMemory)
  private val closedSessions = BoundedSet<Session>(config.closedScopeMemory)

  @Volatile
  private var managerClosed = false

  // ------------------------------------------------------------------------------------------
  // Factories and executors
  // ------------------------------------------------------------------------------------------

  @JvmOverloads
  fun threadFactory(
    session: Session,
    user: User? = null,
    inner: ThreadFactory? = null,
  ): RecordingThreadFactory = withScope(session, user) { it.newStandaloneFactory(inner) }

  /** The shared (cached) pool for this exact scope; `user == null` is the anonymous scope. */
  @JvmOverloads
  fun getPool(
    session: Session,
    user: User? = null,
  ): ListeningExecutorService = withScope(session, user) { it.pool() }

  /** The shared (cached) scheduled pool for this exact scope; `user == null` is the anonymous scope. */
  @JvmOverloads
  fun getScheduledPool(
    session: Session,
    user: User? = null,
  ): ListeningScheduledExecutorService = withScope(session, user) { it.scheduledPool() }

  @JvmOverloads
  fun newCachedThreadPool(
    session: Session,
    user: User? = null,
    threadFactory: ThreadFactory? = null,
  ): ListeningExecutorService = withScope(session, user) { scope ->
    scope.newExecutor(threadFactory, { f, onTerminated ->
      TrackedThreadPoolExecutor(
        0, config.maxCachedPoolThreads, 60L, TimeUnit.SECONDS, SynchronousQueue(),
        f, CallerRunsUnlessShutdown, onTerminated,
      )
    }) { MoreExecutors.listeningDecorator(it) }
  }

  @JvmOverloads
  fun newFixedThreadPool(
    nThreads: Int,
    session: Session,
    user: User? = null,
    threadFactory: ThreadFactory? = null,
  ): ListeningExecutorService = withScope(session, user) { scope ->
    scope.newExecutor(threadFactory, { f, onTerminated ->
      TrackedThreadPoolExecutor(
        nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue(),
        f, ThreadPoolExecutor.AbortPolicy(), onTerminated,
      )
    }) { MoreExecutors.listeningDecorator(it) }
  }

  @JvmOverloads
  fun newScheduledThreadPool(
    nThreads: Int,
    session: Session,
    user: User? = null,
    threadFactory: ThreadFactory? = null,
  ): ListeningScheduledExecutorService = withScope(session, user) { scope ->
    scope.newExecutor(threadFactory, { f, onTerminated ->
      TrackedScheduledThreadPoolExecutor(nThreads, f, onTerminated)
    }) { MoreExecutors.listeningDecorator(it) }
  }

  @JvmOverloads
  fun newSingleThreadExecutor(
    session: Session,
    user: User? = null,
    threadFactory: ThreadFactory? = null,
  ): ListeningExecutorService = withScope(session, user) { scope ->
    scope.newExecutor(threadFactory, { f, onTerminated ->
      TrackedThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue(),
        f, ThreadPoolExecutor.AbortPolicy(), onTerminated,
      )
    }) { MoreExecutors.listeningDecorator(it) }
  }

  // ------------------------------------------------------------------------------------------
  // Queries
  // ------------------------------------------------------------------------------------------

  /** Alive threads matching the filter, de-duplicated. `null` session = any session. */
  fun livingThreads(session: Session?, users: UserSelector): List<Thread> =
    scopes.entries.asSequence()
      .filter { (key, _) -> (session == null || key.session == session) && users.matches(key.user) }
      .flatMap { (_, scope) -> scope.liveThreads().asSequence() }
      .distinct()
      .toList()

  /** True if any thread matching the filter is alive. Uses the same snapshot logic as [livingThreads]. */
  fun isAlive(session: Session?, users: UserSelector): Boolean {
    val alive = livingThreads(session, users).isNotEmpty()
    log.debug("isAlive(session={}, users={}) = {}", session, users, alive)
    return alive
  }

  /** Legacy filter semantics: `null` session/user means *any*. Prefer the [UserSelector] overload. */
  @JvmOverloads
  fun livingThreads(
    session: Session? = null,
    user: User? = null,
  ): List<Thread> = livingThreads(session, legacySelector(user))

  /** Legacy filter semantics: `null` session/user means *any*. Prefer the [UserSelector] overload. */
  @JvmOverloads
  fun isAlive(
    session: Session? = null,
    user: User? = null,
  ): Boolean = isAlive(session, legacySelector(user))

  // ------------------------------------------------------------------------------------------
  // Lifecycle
  // ------------------------------------------------------------------------------------------

  /**
   * Atomically evicts the matching scopes and marks them closed, so later getters cannot silently
   * resurrect them (see [Config.rejectAfterShutdown]). It then shuts down every executor they own:
   * it waits up to [gracePeriod] in total, then calls `shutdownNow` on whatever is still running.
   *
   * If called from one of the session's own threads, the wait-and-force step runs on a daemon
   * reaper thread instead of deadlocking on itself.
   */
  @JvmOverloads
  fun shutdown(
    session: Session,
    users: UserSelector,
    gracePeriod: Duration = config.shutdownGracePeriod,
  ) {
    val removed = lifecycleLock.write {
      when (users) {
        is UserSelector.AnyUser -> closedSessions.add(session)
        is UserSelector.Exactly -> closedKeys.add(SessionKey(session, users.user))
      }
      scopes.keys
        .filter { it.session == session && users.matches(it.user) }
        .mapNotNull { scopes.remove(it) }
    }
    stop(removed.map { it.drain() }, gracePeriod)
    log.debug("Shut down {} scope(s) for session: {}, users: {}", removed.size, session, users)
  }

  /** Shuts down every user's scope for [session]. */
  @JvmOverloads
  fun shutdownSession(session: Session, gracePeriod: Duration = config.shutdownGracePeriod) =
    shutdown(session, UserSelector.AnyUser, gracePeriod)

  @Deprecated(
    "Ambiguous: here null means 'every user', but in getPool it means 'the anonymous user'. " +
        "Use shutdownSession(session) or shutdown(session, UserSelector.of(user)).",
    ReplaceWith("shutdown(session, ThreadPoolManager.UserSelector.of(user))"),
  )
  @JvmOverloads
  fun shutdown(session: Session, user: User? = null) = shutdown(session, legacySelector(user))

  /** Allows a previously shut-down session (all of its users) to acquire resources again. */
  fun reopen(session: Session) = lifecycleLock.write {
    closedSessions.remove(session)
    closedKeys.removeIf { it.session == session }
  }

  /** Shuts down every scope and rejects all further requests. */
  override fun close() = close(config.shutdownGracePeriod)

  fun close(gracePeriod: Duration) {
    val removed = lifecycleLock.write {
      managerClosed = true
      scopes.keys.toList().mapNotNull { scopes.remove(it) }
    }
    stop(removed.map { it.drain() }, gracePeriod)
  }

  // ------------------------------------------------------------------------------------------
  // Internals
  // ------------------------------------------------------------------------------------------

  private fun legacySelector(user: User?): UserSelector =
    if (user == null) UserSelector.AnyUser else UserSelector.Exactly(user)

  private inline fun <T> withScope(session: Session, user: User?, block: (SessionScope) -> T): T {
    val key = SessionKey(session, user)
    while (true) {
      val scope = scopeFor(key)
      try {
        return block(scope)
      } catch (e: ScopeClosed) {
        // Raced with shutdown. Retry: scopeFor either rejects (tombstoned) or creates a fresh scope.
      }
    }
  }

  private fun scopeFor(key: SessionKey): SessionScope {
    scopes[key]?.let { return it }
    return lifecycleLock.read {
      if (managerClosed) throw SessionClosedException("ThreadPoolManager has been closed")
      if (config.rejectAfterShutdown && (key in closedKeys || key.session in closedSessions)) {
        throw SessionClosedException("Session ${key.session} has been shut down")
      }
      scopes.computeIfAbsent(key) { SessionScope(it) }
    }
  }

  private fun stop(drained: List<Drained>, gracePeriod: Duration) {
    val executors = drained.flatMap { it.executors }
    if (executors.isEmpty()) return
    executors.forEach { e ->
      runCatching { e.shutdown() }.onFailure { log.warn("Error shutting down executor {}", e, it) }
    }
    val current = Thread.currentThread()
    if (drained.any { d -> d.factories.any { it.owns(current) } }) {
      log.debug("Shutdown requested from a managed thread; awaiting termination asynchronously")
      Thread({ awaitOrForce(executors, gracePeriod) }, "thread-pool-reaper")
        .apply { isDaemon = true }
        .start()
    } else {
      awaitOrForce(executors, gracePeriod)
    }
  }

  private fun awaitOrForce(executors: List<ExecutorService>, gracePeriod: Duration) {
    val deadline = System.nanoTime() + gracePeriod.toNanos()
    var stragglers: List<ExecutorService> = try {
      executors.filterNot { e ->
        val remaining = (deadline - System.nanoTime()).coerceAtLeast(0)
        e.awaitTermination(remaining, TimeUnit.NANOSECONDS)
      }
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      executors.filterNot { it.isTerminated }
    }
    stragglers = stragglers.filterNot { it.isTerminated }
    stragglers.forEach { e ->
      val dropped = runCatching { e.shutdownNow() }.getOrDefault(emptyList())
      log.warn(
        "Executor {} did not terminate within {}; forced shutdown, dropped {} queued task(s)",
        e, gracePeriod, dropped.size
      )
    }
  }

  /** Everything owned by one (session, user) pair. All mutable state is guarded by [lock]. */
  private inner class SessionScope(val key: SessionKey) {
    private val lock = Any()
    private var closed = false
    private var pool: ListeningExecutorService? = null
    private var scheduledPool: ListeningScheduledExecutorService? = null

    /** Raw (undecorated) executor -> the factory creating its threads. */
    private val executors = LinkedHashMap<ExecutorService, RecordingThreadFactory>()
    private val standaloneFactories = ArrayList<RecordingThreadFactory>()

    private fun checkOpen() {
      if (closed) throw ScopeClosed
    }

    private fun newFactory(inner: ThreadFactory?): RecordingThreadFactory = RecordingThreadFactory(
      key.session, key.user,
      inner ?: defaultInnerFactory(config.threadNamePrefix(key.session, key.user)),
    )

    fun newStandaloneFactory(inner: ThreadFactory?): RecordingThreadFactory = synchronized(lock) {
      checkOpen()
      newFactory(inner).also { standaloneFactories += it }
    }

    fun pool(): ListeningExecutorService = synchronized(lock) {
      checkOpen()
      pool?.takeUnless { it.isShutdown }
        ?: newExecutorLocked(null, { f, _ -> ImmediateExecutorService(f) }) {
          MoreExecutors.listeningDecorator(it)
        }.also { pool = it }
    }

    fun scheduledPool(): ListeningScheduledExecutorService = synchronized(lock) {
      checkOpen()
      scheduledPool?.takeUnless { it.isShutdown }
        ?: newExecutorLocked(null, { f, cb -> TrackedScheduledThreadPoolExecutor(1, f, cb) }) {
          MoreExecutors.listeningDecorator(it)
        }.also { scheduledPool = it }
    }

    fun <E : ExecutorService, L : ListeningExecutorService> newExecutor(
      inner: ThreadFactory?,
      build: (RecordingThreadFactory, (ExecutorService) -> Unit) -> E,
      decorate: (E) -> L,
    ): L = synchronized(lock) {
      checkOpen()
      newExecutorLocked(inner, build, decorate)
    }

    private fun <E : ExecutorService, L : ListeningExecutorService> newExecutorLocked(
      inner: ThreadFactory?,
      build: (RecordingThreadFactory, (ExecutorService) -> Unit) -> E,
      decorate: (E) -> L,
    ): L {
      // Forget executors that callers already shut down themselves (and their factories with them).
      executors.keys.removeAll { it.isTerminated }
      val factory = newFactory(inner)
      val raw = build(factory) { onTerminated(it) }
      val decorated = decorate(raw)
      executors[raw] = factory
      executorFactories[decorated] = factory
      return decorated
    }

    /**
     * Called from `ThreadPoolExecutor.terminated()`. Never call executor methods while holding
     * [lock], or this callback could deadlock.
     */
    private fun onTerminated(raw: ExecutorService) {
      synchronized(lock) { if (!closed) executors.remove(raw) }
    }

    /** Marks the scope closed and hands its resources to the caller for shutdown. */
    fun drain(): Drained = synchronized(lock) {
      if (closed) return Drained(emptyList(), emptyList())
      closed = true
      val drained = Drained(executors.keys.toList(), executors.values.toList() + standaloneFactories)
      executors.clear()
      standaloneFactories.clear()
      pool = null
      scheduledPool = null
      drained
    }

    fun liveThreads(): List<Thread> {
      val factories = synchronized(lock) { executors.values.toList() + standaloneFactories }
      return factories.flatMap { it.liveThreads() }
    }
  }

  /**
   * Records the threads it creates. A thread is removed from [threads] as soon as its runnable
   * exits, so an idle session holds no thread references.
   */
  class RecordingThreadFactory @JvmOverloads constructor(
    val session: Session,
    val user: User?,
    private val inner: ThreadFactory = defaultInnerFactory(defaultThreadNamePrefix(session, user)),
  ) : ImmediateExecutorService.ThreadFactoryTrackerInterface() {

    override fun newThread(r: Runnable): Thread {
      log.debug("Creating new thread for session: {}", session)
      val tracked = Runnable {
        try {
          r.run()
        } finally {
          synchronized(threads) { threads.remove(Thread.currentThread()) }
        }
      }
      val thread = checkNotNull(inner.newThread(tracked)) { "Inner ThreadFactory returned null" }
      synchronized(threads) { threads.add(thread) }
      return thread
    }

    /** Snapshot of the currently-alive threads created by this factory. */
    fun liveThreads(): List<Thread> = synchronized(threads) { threads.filter { it.isAlive } }

    fun hasLiveThreads(): Boolean = liveThreads().isNotEmpty()

    fun owns(thread: Thread): Boolean = synchronized(threads) { threads.contains(thread) }
  }

  companion object : ThreadPoolManager() {
    /**
     * The factory backing an executor returned by any [ThreadPoolManager] instance, or null if the
     * executor was not created here. Uses no reflection.
     */
    @JvmStatic
    fun factoryOf(executor: ExecutorService): RecordingThreadFactory? = executorFactories[executor]
  }
}

/** Reflection-free replacement for the old Guava/JDK-internals-poking extension. */
@Deprecated(
  "Use ThreadPoolManager.factoryOf(executor), which returns null instead of throwing.",
  ReplaceWith("ThreadPoolManager.factoryOf(this)"),
)
val ListeningExecutorService.threadFactory: ThreadPoolManager.RecordingThreadFactory
  get() = ThreadPoolManager.factoryOf(this)
    ?: throw IllegalArgumentException("Executor was not created by ThreadPoolManager")

private class TrackedThreadPoolExecutor(
  core: Int,
  max: Int,
  keepAlive: Long,
  unit: TimeUnit,
  queue: BlockingQueue<Runnable>,
  factory: ThreadFactory,
  handler: RejectedExecutionHandler,
  private val onTerminated: (ExecutorService) -> Unit,
) : ThreadPoolExecutor(core, max, keepAlive, unit, queue, factory, handler) {
  override fun terminated() {
    try {
      super.terminated()
    } finally {
      runCatching { onTerminated(this) }
    }
  }
}

private class TrackedScheduledThreadPoolExecutor(
  core: Int,
  factory: ThreadFactory,
  private val onTerminated: (ExecutorService) -> Unit,
) : ScheduledThreadPoolExecutor(core, factory) {
  init {
    setRemoveOnCancelPolicy(true)                         // cancelled tasks leave the queue immediately
    setExecuteExistingDelayedTasksAfterShutdownPolicy(false) // shutdown does not wait for future delays
  }

  override fun terminated() {
    try {
      super.terminated()
    } finally {
      runCatching { onTerminated(this) }
    }
  }
}

/** Back-pressure for bounded cached pools: run on the caller when saturated, reject after shutdown. */
private object CallerRunsUnlessShutdown : RejectedExecutionHandler {
  override fun rejectedExecution(r: Runnable, executor: ThreadPoolExecutor) {
    if (executor.isShutdown) throw RejectedExecutionException("Executor has been shut down")
    log.debug("Cached pool saturated ({} threads); running task on caller thread", executor.maximumPoolSize)
    r.run()
  }
}

/** Small LRU-bounded set used for shutdown tombstones. */
private class BoundedSet<T>(private val capacity: Int) {
  private val map = object : LinkedHashMap<T, Boolean>() {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<T, Boolean>?) = size > capacity
  }

  @Synchronized
  fun add(item: T) {
    if (capacity > 0) map[item] = true
  }

  @Synchronized
  operator fun contains(item: T): Boolean = map.containsKey(item)

  @Synchronized
  fun remove(item: T) {
    map.remove(item)
  }

  @Synchronized
  fun removeIf(predicate: (T) -> Boolean) {
    map.keys.removeAll(predicate)
  }
}