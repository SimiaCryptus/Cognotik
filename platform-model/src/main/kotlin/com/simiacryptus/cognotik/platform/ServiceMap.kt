package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.service.UsageInterface
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object ServiceMap {

  private val instances = ConcurrentHashMap<ServiceKey<*>, Any>()

  @Suppress("UNCHECKED_CAST")
  operator fun <T : Any> get(key: ServiceKey<T>): T {
    (instances[key] as T?)?.let { return it }
    // synchronized (re-entrant) rather than computeIfAbsent: factories may resolve other services
    return synchronized(instances) {
      (instances[key] as T?) ?: key.create().also {
        instances[key] = it
        onCreated(key, it)
      }
    }
  }

  operator fun <T : Any> set(key: ServiceKey<T>, value: T) {
    instances[key] = value
    onCreated(key, value)
  }

  private fun onCreated(key: ServiceKey<*>, value: Any) {
    if (key == ServiceKey.USAGE_DB) {
      val usage = value as UsageInterface
      ChatModel.ON_USAGE = { model, u, user, session, data -> usage.incrementUsage(session, user, model, u, data) }
    }
  }
  val log = LoggerFactory.getLogger(ServiceMap::class.java)
}