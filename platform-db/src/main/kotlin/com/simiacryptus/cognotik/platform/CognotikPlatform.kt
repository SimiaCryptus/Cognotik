package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.h2.AuthenticationDB
import com.simiacryptus.cognotik.platform.h2.GiftedCreditsDB
import com.simiacryptus.cognotik.platform.h2.SessionMetadataDB
import com.simiacryptus.cognotik.platform.h2.UsageDB
import com.simiacryptus.cognotik.platform.h2.UserSettingsDB
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.service.UsageInterface
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Unified service container.
 * - The global instance (parent == null) owns GLOBAL-scoped services and forwards ROOT-scoped ones
 *   to the service set for the current [ApplicationServicesConfig.dataStorageRoot].
 * - Root instances own ROOT-scoped services and forward GLOBAL-scoped ones to the parent.
 */
class CognotikPlatform private constructor(
  private val root: File?,
  private val parent: ServiceMap?,
) : ServiceMap {

  constructor() : this(null, null)

  override val rootDir: File get() = root ?: ApplicationServicesConfig.dataStorageRoot

  private val instances = ConcurrentHashMap<ServiceKey<*>, Any>()

  private fun owns(key: ServiceKey<*>) = (key.scope == ServiceKey.Scope.GLOBAL) == (parent == null)

  private fun delegateFor(key: ServiceKey<*>): ServiceMap =
    parent ?: throw IllegalStateException("Cannot resolve root-scoped service '${key.name}': no parent available")

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> get(key: ServiceKey<T>): T {
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

  override fun <T : Any> set(key: ServiceKey<T>, value: T) {
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
    val log = org.slf4j.LoggerFactory.getLogger(CognotikPlatform::class.java)

    init {
      ServiceKey.PLUGIN_MANAGER.defaultFactory = { PluginManager() }
      ServiceKey.AUTHORIZATION_MANAGER.defaultFactory = { AuthorizationManager() }
      ServiceKey.THREAD_POOL_MANAGER.defaultFactory = { ThreadPoolManager() }
      ServiceKey.METADATA_DB.defaultFactory = { SessionMetadataDB() }
      ServiceKey.DATA_STORAGE.defaultFactory = {
        DataStorage(dataDir = it.rootDir.resolve("data"), metadataStorage = it[ServiceKey.METADATA_DB])
      }
      ServiceKey.USAGE_DB.defaultFactory = { UsageDB() }
      ServiceKey.USER_SETTINGS.defaultFactory = { UserSettingsDB() }
      ServiceKey.AUTHENTICATION.defaultFactory = { AuthenticationDB() }
      ServiceKey.GIFTED_CREDITS.defaultFactory = { GiftedCreditsDB(it.rootDir.resolve("giftsdb")) }
      ServiceMap.services = CognotikPlatform()
    }

    val services: ServiceMap get() =
      ServiceMap.services ?: throw IllegalStateException("ApplicationServices not initialized")

  }
}