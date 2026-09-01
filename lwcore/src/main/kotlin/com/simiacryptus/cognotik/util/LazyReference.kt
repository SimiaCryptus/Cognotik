package com.simiacryptus.cognotik.util

import java.util.concurrent.atomic.AtomicBoolean

class LazyReference<T>(private val isInitialized: AtomicBoolean, private val initializer: () -> T) {
  @Volatile
  private var _value: T? = null
  var value: T
    get() = _value ?: construct()
    set(value) {
      require(!isInitialized.get()) { "Cannot set value after initialization" }
      synchronized(this) {
        require(!isInitialized.get()) { "Cannot set value after initialization" }
        _value = value
      }
    }

  private fun construct(): T & Any = synchronized(this) {
    require(!isInitialized.get()) { "Cannot set value after initialization" }
    _value = initializer()
    require(null != _value) { "Initializer returned null" }
    return _value!!
  }

  operator fun invoke(): T = value
}