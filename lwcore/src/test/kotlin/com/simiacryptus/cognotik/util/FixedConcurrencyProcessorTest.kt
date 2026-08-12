package com.simiacryptus.cognotik.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class FixedConcurrencyProcessorTest {

  private val pool: ExecutorService = Executors.newCachedThreadPool()

  @AfterEach
  fun tearDown() {
    pool.shutdownNow()
  }

  @Test
  fun `rejects a non-positive concurrency limit`() {
    assertThrows(IllegalArgumentException::class.java) { FixedConcurrencyProcessor(pool, 0) }
    assertThrows(IllegalArgumentException::class.java) { FixedConcurrencyProcessor(pool, -1) }
  }

  @Test
  fun `returns task results`() {
    val processor = FixedConcurrencyProcessor(pool, 2)
    val futures = (1..10).map { i -> processor.submit { i * i } }
    assertEquals((1..10).map { it * it }, futures.map { it.get(10, TimeUnit.SECONDS) })
  }

  @Test
  fun `never exceeds the concurrency limit`() {
    val limit = 3
    val processor = FixedConcurrencyProcessor(pool, limit)
    val current = AtomicInteger()
    val max = AtomicInteger()
    val futures = (1..24).map {
      processor.submit {
        val now = current.incrementAndGet()
        max.updateAndGet { prev -> maxOf(prev, now) }
        Thread.sleep(20)
        current.decrementAndGet()
        it
      }
    }
    CompletableFuture.allOf(*futures.toTypedArray()).get(60, TimeUnit.SECONDS)
    assertTrue(max.get() <= limit, "Observed concurrency ${max.get()} exceeded limit $limit")
    assertTrue(max.get() > 1, "Expected some parallelism, observed ${max.get()}")
  }

  @Test
  fun `all submitted tasks eventually execute`() {
    val processor = FixedConcurrencyProcessor(pool, 2)
    val executed = AtomicInteger()
    val futures = (1..50).map { processor.submit { executed.incrementAndGet() } }
    CompletableFuture.allOf(*futures.toTypedArray()).get(60, TimeUnit.SECONDS)
    assertEquals(50, executed.get())
    assertEquals(0, processor.getWaitingTaskCount())
    assertEquals(0, processor.getActiveTaskCount())
  }

  @Test
  fun `failures propagate to the caller`() {
    val processor = FixedConcurrencyProcessor(pool, 1)
    val future = processor.submit<Int> { throw IllegalStateException("boom") }
    val e = assertThrows(CompletionException::class.java) { future.join() }
    assertTrue(generateSequence(e as Throwable) { it.cause }.any { it is IllegalStateException })
  }

  @Test
  fun `a failing task does not block subsequent tasks`() {
    val processor = FixedConcurrencyProcessor(pool, 1)
    val failing = processor.submit<Int> { throw RuntimeException("boom") }
    val succeeding = processor.submit { 7 }
    assertThrows(CompletionException::class.java) { failing.join() }
    assertEquals(7, succeeding.get(10, TimeUnit.SECONDS))
  }

  @Test
  fun `queued tasks are reported as waiting`() {
    val processor = FixedConcurrencyProcessor(pool, 1)
    val gate = CountDownLatch(1)
    val started = CountDownLatch(1)
    val blocking = processor.submit { started.countDown(); gate.await(10, TimeUnit.SECONDS) }
    assertTrue(started.await(10, TimeUnit.SECONDS))
    val queued = (1..3).map { processor.submit { it } }
    assertTrue(processor.getWaitingTaskCount() > 0)
    gate.countDown()
    blocking.get(10, TimeUnit.SECONDS)
    queued.forEach { it.get(10, TimeUnit.SECONDS) }
    assertEquals(0, processor.getWaitingTaskCount())
  }

  @Test
  fun `shutdown terminates the pool`() {
    val localPool = Executors.newCachedThreadPool()
    val processor = FixedConcurrencyProcessor(localPool, 2)
    processor.submit { 1 }.get(10, TimeUnit.SECONDS)
    processor.shutdown()
    assertTrue(localPool.awaitTermination(10, TimeUnit.SECONDS))
  }
}