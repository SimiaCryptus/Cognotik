package com.simiacryptus.cognotik.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RunWithPermitTest {

  @Test
  fun `returns the block result`() {
    val semaphore = Semaphore(1)
    assertEquals("value", semaphore.runWithPermit { "value" })
  }

  @Test
  fun `permit is released after normal completion`() {
    val semaphore = Semaphore(1)
    semaphore.runWithPermit { }
    assertEquals(1, semaphore.availablePermits())
  }

  @Test
  fun `permit is released after an exception`() {
    val semaphore = Semaphore(1)
    assertThrows(IllegalStateException::class.java) {
      semaphore.runWithPermit { throw IllegalStateException("boom") }
    }
    assertEquals(1, semaphore.availablePermits())
  }

  @Test
  fun `permit is held for the duration of the block`() {
    val semaphore = Semaphore(1)
    semaphore.runWithPermit {
      assertEquals(0, semaphore.availablePermits())
    }
  }

  @Test
  fun `blocks concurrent callers beyond the permit count`() {
    val semaphore = Semaphore(1)
    val concurrent = AtomicInteger()
    val max = AtomicInteger()
    val threads = (1..8).map {
      Thread {
        semaphore.runWithPermit {
          val now = concurrent.incrementAndGet()
          max.updateAndGet { prev -> maxOf(prev, now) }
          Thread.sleep(10)
          concurrent.decrementAndGet()
        }
      }
    }
    threads.forEach { it.start() }
    threads.forEach { it.join(TimeUnit.SECONDS.toMillis(30)) }
    assertEquals(1, max.get())
    assertEquals(1, semaphore.availablePermits())
  }
}