package com.simiacryptus.cognotik.util

import java.util.concurrent.atomic.AtomicBoolean

class LazyReference<T>(private val isInitialized: AtomicBoolean, private val initializer: () -> T) {
  @Volatile
  var value: T? = null
    get() {
      if (!isInitialized.get()) {
        synchronized(this) {
          if (!isInitialized.get()) {
            field = initializer()
          }
        }
      }
      return field!!
    }
    set(value) {
      require(!isInitialized.get()) { "Cannot set value after initialization" }
      synchronized(this) {
        require(!isInitialized.get()) { "Cannot set value after initialization" }
        field = value
      }
    }

  operator fun invoke(): T = value!!
}