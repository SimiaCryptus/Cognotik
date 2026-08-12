package com.simiacryptus.cognotik.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class ImmediateExecutorServiceTest {

  private val executor = ImmediateExecutorService()

  @AfterEach
  fun tearDown() {
    executor.shutdownNow()
  }

  @Test
  fun `execute runs the command`() {
    val latch = CountDownLatch(1)
    executor.execute { latch.countDown() }
    assertTrue(latch.await(5, TimeUnit.SECONDS))
  }

  @Test
  fun `submit callable returns the result`() {
    assertEquals(42, executor.submit(Callable { 42 }).get(5, TimeUnit.SECONDS))
  }

  @Test
  fun `submit runnable with result`() {
    val counter = AtomicInteger()
    val future = executor.submit(Runnable { counter.incrementAndGet() }, "done")
    assertEquals("done", future.get(5, TimeUnit.SECONDS))
    assertEquals(1, counter.get())
  }

  @Test
  fun `submit runnable returns a future`() {
    val counter = AtomicInteger()
    executor.submit(Runnable { counter.incrementAndGet() }).get(5, TimeUnit.SECONDS)
    assertEquals(1, counter.get())
  }

  @Test
  fun `exceptions surface through the future`() {
    val future = executor.submit(Callable<Int> { throw IllegalStateException("boom") })
    val e = assertThrows(ExecutionException::class.java) { future.get(5, TimeUnit.SECONDS) }
    assertTrue(e.cause is IllegalStateException)
  }

  @Test
  fun `invokeAll runs every task`() {
    val tasks = (1..5).map { i -> Callable { i * 2 } }
    val results = executor.invokeAll(tasks.toMutableList()).map { it.get() }
    assertEquals(listOf(2, 4, 6, 8, 10), results)
  }

  @Test
  fun `invokeAny returns one result`() {
    val tasks = listOf(Callable { "a" }, Callable { "a" })
    assertEquals("a", executor.invokeAny(tasks.toMutableList()))
  }

  @Test
  fun `tasks run concurrently rather than queueing`() {
    val parallelism = 8
    val barrier = CyclicBarrier(parallelism)
    val futures = (1 until parallelism + 1).map {
      executor.submit(Callable { barrier.await(10, TimeUnit.SECONDS); it })
    }
    assertEquals((1..parallelism).toList(), futures.map { it.get(15, TimeUnit.SECONDS) })
  }

  @Test
  fun `thread factory tracks created threads and marks them daemon`() {
    executor.submit(Callable { 1 }).get(5, TimeUnit.SECONDS)
    assertTrue(executor.threadFactory.threads.isNotEmpty())
    assertTrue(executor.threadFactory.threads.all { it.isDaemon })
  }

  @Test
  fun `shutdown lifecycle`() {
    val local = ImmediateExecutorService()
    assertFalse(local.isShutdown)
    local.submit(Callable { 1 }).get(5, TimeUnit.SECONDS)
    local.shutdown()
    assertTrue(local.isShutdown)
    assertTrue(local.awaitTermination(5, TimeUnit.SECONDS))
    assertTrue(local.isTerminated)
  }

  @Test
  fun `shutdownNow returns pending tasks list`() {
    val local = ImmediateExecutorService()
    assertNotNull(local.shutdownNow())
    assertTrue(local.isShutdown)
  }
}