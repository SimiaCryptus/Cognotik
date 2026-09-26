package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.service.UsageInterface
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

open class ServiceMap(

  private val parent: ServiceMap? = null,
) {


  private val instances = ConcurrentHashMap<ServiceKey<*>, Any>()

  private fun owns(key: ServiceKey<*>) = (key.scope == ServiceKey.Scope.GLOBAL) == (parent == null)

  private fun delegateFor(key: ServiceKey<*>): ServiceMap =
    parent ?: throw IllegalStateException("Cannot resolve root-scoped service '${key.name}': no parent available")

  @Suppress("UNCHECKED_CAST")
  operator fun <T : Any> get(key: ServiceKey<T>): T {
    if (!owns(key)) return delegateFor(key)[key]
    (instances[key] as T?)?.let { return it }
    // synchronized (re-entrant) rather than computeIfAbsent: factories may resolve other services
    return synchronized(instances) {
      (instances[key] as T?) ?: key.create(this).also {
        instances[key] = it
        onCreated(key, it)
      }
    }
  }

  operator fun <T : Any> set(key: ServiceKey<T>, value: T) {
    if (!owns(key)) return delegateFor(key).set(key, value)
    instances[key] = value
    onCreated(key, value)
  }

  private fun onCreated(key: ServiceKey<*>, value: Any) {
    if (key == ServiceKey.USAGE_DB) {
      val usage = value as UsageInterface
      ChatModel.ON_USAGE = { model, u, user, session, data -> usage.incrementUsage(session, user, model, u, data) }
    }
  }
  companion object {
    val log = LoggerFactory.getLogger(ServiceMap::class.java)
    private var _services: ServiceMap = ServiceMap()
    @JvmStatic
    var services: ServiceMap
      get() = _services
      set(value) {
        log.info("Setting global ApplicationServices instance: $value", RuntimeException("Stack trace"))
        _services = value
      }

  }
}