package com.simiacryptus.cognotik.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class EventDispatcherTest {

  @Test
  fun `listeners are invoked on notify`() {
    val dispatcher = EventDispatcher()
    val counter = AtomicInteger()
    dispatcher.addListener { counter.incrementAndGet() }
    dispatcher.notifyListeners()
    assertEquals(1, counter.get())
    dispatcher.notifyListeners()
    assertEquals(2, counter.get())
  }

  @Test
  fun `multiple listeners are all invoked`() {
    val dispatcher = EventDispatcher()
    val order = mutableListOf<String>()
    dispatcher.addListener { order.add("a") }
    dispatcher.addListener { order.add("b") }
    dispatcher.notifyListeners()
    assertEquals(listOf("a", "b"), order)
  }

  @Test
  fun `removed listeners are not invoked`() {
    val dispatcher = EventDispatcher()
    val counter = AtomicInteger()
    val listener = { counter.incrementAndGet(); Unit }
    dispatcher.addListener(listener)
    dispatcher.removeListener(listener)
    dispatcher.notifyListeners()
    assertEquals(0, counter.get())
  }

  @Test
  fun `removing an unknown listener is a no-op`() {
    val dispatcher = EventDispatcher()
    dispatcher.removeListener { }
    dispatcher.notifyListeners()
  }

  @Test
  fun `notify with no listeners is safe`() {
    EventDispatcher().notifyListeners()
  }

  @Test
  fun `a throwing listener does not stop the others`() {
    val dispatcher = EventDispatcher()
    val counter = AtomicInteger()
    dispatcher.addListener { throw RuntimeException("boom") }
    dispatcher.addListener { counter.incrementAndGet() }
    dispatcher.notifyListeners()
    assertEquals(1, counter.get())
  }
}