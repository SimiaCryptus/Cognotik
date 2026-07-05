package com.simiacryptus.cognotik.util

import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

fun <T> oneAtATime(handler: Consumer<T>): Consumer<T> {
  val guard = AtomicBoolean(false)
  return Consumer { t ->
    if (guard.getAndSet(true)) return@Consumer
    handler.accept(t)
    guard.set(false)
  }
}