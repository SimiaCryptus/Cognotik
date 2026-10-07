package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ThreadPoolManagerTest {

  private lateinit var manager: ThreadPoolManager
  private val sessionA = Session("U-20240101-aaaa")
  private val sessionB = Session("U-20240101-bbbb")
  private val alice = User("alice@example.com")
  private val bob = User("bob@example.com")
  private val release = CountDownLatch(1)

  @BeforeEach
  fun setUp() {
    manager = ThreadPoolManager()
  }

  @AfterEach
  fun tearDown() {
    release.countDown()
    listOf(sessionA, sessionB).forEach { manager.shutdown(it) }
  }

  /** Submit a blocking task and wait until it is running; returns the worker thread. */
  private fun blockOn(executor: java.util.concurrent.ExecutorService): Thread {
    val started = CountDownLatch(1)
    val worker = AtomicReference<Thread>()
    executor.submit {
      worker.set(Thread.currentThread())
      started.countDown()
      release.await(10, TimeUnit.SECONDS)
    }
    assertTrue(started.await(5, TimeUnit.SECONDS), "task did not start")
    return worker.get()
  }

  // ---- caching ----

  @Test
  fun `getPool is cached per session and user`() {
    val p1 = manager.getPool(sessionA)
    assertSame(p1, manager.getPool(sessionA))
    assertNotSame(p1, manager.getPool(sessionB))
    assertNotSame(p1, manager.getPool(sessionA, alice))
    assertSame(manager.getPool(sessionA, alice), manager.getPool(sessionA, User("alice@example.com", "Other")))
    assertNotSame(manager.getPool(sessionA, alice), manager.getPool(sessionA, bob))
  }

  @Test
  fun `getScheduledPool is cached per session and user`() {
    val p1 = manager.getScheduledPool(sessionA, alice)
    assertSame(p1, manager.getScheduledPool(sessionA, alice))
    assertNotSame(p1, manager.getScheduledPool(sessionA, bob))
    assertNotSame(p1, manager.getScheduledPool(sessionB, alice))
  }

  @Test
  fun `new pools are never cached`() {
    assertNotSame(manager.newCachedThreadPool(sessionA), manager.newCachedThreadPool(sessionA))
    assertNotSame(manager.newFixedThreadPool(1, sessionA), manager.newFixedThreadPool(1, sessionA))
  }

  // ---- execution & naming ----

  @Test
  fun `scheduled pool runs tasks on named daemon threads`() {
    val pool = manager.getScheduledPool(sessionA, alice)
    val thread = AtomicReference<Thread>()
    val done = CountDownLatch(1)
    pool.schedule({ thread.set(Thread.currentThread()); done.countDown() }, 10, TimeUnit.MILLISECONDS)
    assertTrue(done.await(5, TimeUnit.SECONDS))
     val name = thread.get().name
     assertTrue(name.startsWith(defaultThreadNamePrefix(sessionA, alice) + "-"), name)
     assertFalse(name.contains("alice@example.com"), "thread name must not leak PII: $name")
    assertTrue(thread.get().isDaemon)
  }

  @Test
  fun `fixed pool threads use default naming with null user`() {
    val worker = blockOn(manager.newFixedThreadPool(2, sessionA))
     assertTrue(worker.name.startsWith(defaultThreadNamePrefix(sessionA, null) + "-"), worker.name)
     assertTrue(worker.name.contains("-anon-"), worker.name)
    assertTrue(worker.isDaemon)
  }

  @Test
  fun `all factory methods produce working executors`() {
    val executors = listOf(
      manager.newCachedThreadPool(sessionA),
      manager.newFixedThreadPool(1, sessionA),
      manager.newScheduledThreadPool(1, sessionA),
      manager.newSingleThreadExecutor(sessionA),
    )
    executors.forEach { ex ->
      assertEquals(42, ex.submit<Int> { 42 }.get(5, TimeUnit.SECONDS))
    }
  }

  @Test
  fun `custom inner thread factory is used and tracked`() {
    val inner = ThreadFactory { r -> Thread(r, "custom-thread").apply { isDaemon = true } }
    val worker = blockOn(manager.newSingleThreadExecutor(sessionA, alice, inner))
    assertEquals("custom-thread", worker.name)
    assertTrue(manager.isAlive(sessionA, alice))
    assertTrue(manager.livingThreads().contains(worker))
  }

  // ---- liveness tracking ----

  @Test
  fun `fresh manager has nothing alive`() {
    assertFalse(manager.isAlive())
    assertFalse(manager.isAlive(sessionA))
    assertTrue(manager.livingThreads().isEmpty())
  }

  @Test
  fun `isAlive filters by session and user`() {
    val worker = blockOn(manager.newFixedThreadPool(1, sessionA, alice))
    assertTrue(manager.isAlive())
    assertTrue(manager.isAlive(sessionA))
    assertTrue(manager.isAlive(sessionA, alice))
    assertTrue(manager.isAlive(null, alice))
    assertFalse(manager.isAlive(sessionB))
    assertFalse(manager.isAlive(sessionA, bob))
    assertFalse(manager.isAlive(null, bob))
    assertTrue(manager.livingThreads().contains(worker))
  }

  @Test
  fun `isAlive sees scheduled pool threads`() {
    val pool = manager.getScheduledPool(sessionB)
    pool.submit {}.get(5, TimeUnit.SECONDS)
    assertTrue(manager.isAlive(sessionB))
  }

  @Test
  fun `isAlive and livingThreads are consistent`() {
    blockOn(manager.newCachedThreadPool(sessionA))
    assertEquals(manager.isAlive(), manager.livingThreads().isNotEmpty())
    release.countDown()
    manager.shutdown(sessionA)
    assertEquals(manager.isAlive(), manager.livingThreads().isNotEmpty())
  }
  @Test
  fun `livingThreads filters by session and user consistently with isAlive`() {
    val worker = blockOn(manager.newFixedThreadPool(1, sessionA, alice))
    assertEquals(listOf(worker), manager.livingThreads(sessionA, alice))
    assertTrue(manager.livingThreads(sessionB).isEmpty())
    assertTrue(manager.livingThreads(null, bob).isEmpty())
    listOf(null to null, sessionA to null, sessionA to alice, sessionB to null, null to bob).forEach { (s, u) ->
      assertEquals(manager.isAlive(s, u), manager.livingThreads(s, u).isNotEmpty(), "filter $s/$u")
    }
  }

  // ---- RecordingThreadFactory ----

  @Test
  fun `threadFactory records created threads`() {
    val factory = manager.threadFactory(sessionA, alice)
    assertEquals(sessionA, factory.session)
    assertEquals(alice, factory.user)
    assertFalse(factory.hasLiveThreads())
    val latch = CountDownLatch(1)
    val t = factory.newThread { latch.await(5, TimeUnit.SECONDS) }
    t.start()
    try {
      assertTrue(factory.hasLiveThreads())
      assertTrue(manager.isAlive(sessionA, alice))
    } finally {
      latch.countDown()
      t.join(5000)
    }
    assertFalse(factory.hasLiveThreads())
    assertFalse(manager.isAlive(sessionA, alice))
  }

  @Test
  fun `recording factory prunes dead threads`() {
    val factory = ThreadPoolManager.RecordingThreadFactory(sessionA, null)
    repeat(5) {
      val t = factory.newThread {}
      t.start()
      t.join(5000)
    }
    factory.newThread {} // triggers pruning; unstarted thread is not alive either
    val size = synchronized(factory.threads) { factory.threads.size }
    assertTrue(size <= 1, "expected pruned list, got $size")
  }

  @Test
  fun `unstarted threads are not live`() {
    val factory = ThreadPoolManager.RecordingThreadFactory(sessionA, alice)
    factory.newThread {}
    assertFalse(factory.hasLiveThreads())
  }

  // ---- shutdown ----

  @Test
  fun `shutdown without user evicts all users of session`() {
    val pAlice = manager.getPool(sessionA, alice)
    val pBob = manager.getPool(sessionA, bob)
    val pNone = manager.getPool(sessionA)
    val sched = manager.getScheduledPool(sessionA, alice)
    val fixed = manager.newFixedThreadPool(1, sessionA, bob)
    val other = manager.getPool(sessionB)

    manager.shutdown(sessionA)

    assertTrue(sched.isShutdown)
    assertTrue(fixed.isShutdown)
    assertSame(other, manager.getPool(sessionB))
    assertFalse(other.isShutdown)
     // The whole session is tombstoned: no scope may be silently resurrected.
     assertThrows(ThreadPoolManager.SessionClosedException::class.java) { manager.getPool(sessionA, alice) }
     assertThrows(ThreadPoolManager.SessionClosedException::class.java) { manager.getPool(sessionA, bob) }
     assertThrows(ThreadPoolManager.SessionClosedException::class.java) { manager.getPool(sessionA) }
     // After an explicit reopen, fresh pools are created.
     manager.reopen(sessionA)
     assertNotSame(pAlice, manager.getPool(sessionA, alice))
     assertNotSame(pBob, manager.getPool(sessionA, bob))
     assertNotSame(pNone, manager.getPool(sessionA))
  }

  @Test
  fun `shutdown with user evicts only that user`() {
    val schedAlice = manager.getScheduledPool(sessionA, alice)
    val schedBob = manager.getScheduledPool(sessionA, bob)

    manager.shutdown(sessionA, alice)

    assertTrue(schedAlice.isShutdown)
    assertFalse(schedBob.isShutdown)
    assertSame(schedBob, manager.getScheduledPool(sessionA, bob))
     // Only alice's scope is tombstoned.
     assertThrows(ThreadPoolManager.SessionClosedException::class.java) {
       manager.getScheduledPool(sessionA, alice)
     }
     manager.reopen(sessionA)
    assertNotSame(schedAlice, manager.getScheduledPool(sessionA, alice))
     assertSame(schedBob, manager.getScheduledPool(sessionA, bob))
  }

  @Test
  fun `shutdown clears liveness tracking`() {
    blockOn(manager.newFixedThreadPool(1, sessionA))
    assertTrue(manager.isAlive(sessionA))
    manager.shutdown(sessionA)
    assertFalse(manager.isAlive(sessionA))
  }

  @Test
  fun `shutdown of unknown session is a no-op`() {
    assertDoesNotThrow { manager.shutdown(Session("U-20991231-zzzz")) }
    assertDoesNotThrow { manager.shutdown(Session("U-20991231-zzzz"), alice) }
  }

  @Test
  fun `fixed pool threads terminate after shutdown`() {
    val pool = manager.newFixedThreadPool(1, sessionA)
    val worker = blockOn(pool)
    release.countDown()
    manager.shutdown(sessionA)
    assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
    worker.join(5000)
    assertFalse(worker.isAlive)
  }
}