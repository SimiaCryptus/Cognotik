package com.simiacryptus.cognotik.platform

import org.slf4j.LoggerFactory
import java.io.File

interface ServiceMap {
  /** Root this service set is scoped to (for [ServiceKey.Scope.ROOT] services). */
  val rootDir: File

  /** Single resolution path for all services. */
  operator fun <T : Any> get(key: ServiceKey<T>): T
  operator fun <T : Any> set(key: ServiceKey<T>, value: T)

  companion object {
    val log = LoggerFactory.getLogger(ServiceMap::class.java)
    private var _services: ServiceMap? = null
    @JvmStatic
    var services: ServiceMap?
      get() = _services
      set(value) {
        if (value == null) {
          throw IllegalArgumentException("Cannot set services to null")
        }
        log.info("Setting global ApplicationServices instance: $value", RuntimeException("Stack trace"))
        _services = value
      }

  }
}