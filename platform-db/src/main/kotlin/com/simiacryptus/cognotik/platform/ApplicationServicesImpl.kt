package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.h2.AuthenticationDB
import com.simiacryptus.cognotik.platform.h2.GiftedCreditsDB
import com.simiacryptus.cognotik.platform.h2.SessionMetadataDB
import com.simiacryptus.cognotik.platform.h2.UsageDB
import com.simiacryptus.cognotik.platform.h2.UserSettingsDB
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.isLocked
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.service.ApplicationServices
import com.simiacryptus.cognotik.platform.service.AuthenticationInterface
import com.simiacryptus.cognotik.platform.service.AuthorizationInterface
import com.simiacryptus.cognotik.platform.service.PluginManagerInterface
import com.simiacryptus.cognotik.platform.service.ServiceKey
import com.simiacryptus.cognotik.platform.service.UsageInterface
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Unified service container.
 * - The global instance (parent == null) owns GLOBAL-scoped services and forwards ROOT-scoped ones
 *   to the service set for the current [ApplicationServicesConfig.dataStorageRoot].
 * - Root instances own ROOT-scoped services and forward GLOBAL-scoped ones to the parent.
 */
class ApplicationServicesImpl private constructor(
  private val root: File?,
  private val parent: ApplicationServices?,
) : ApplicationServices {

  constructor() : this(null, null)

  override val rootDir: File get() = root ?: ApplicationServicesConfig.dataStorageRoot

  private val instances = ConcurrentHashMap<ServiceKey<*>, Any>()
  private val rootCache = ConcurrentHashMap<File, ApplicationServices>()

  private fun owns(key: ServiceKey<*>) = (key.scope == ServiceKey.Scope.GLOBAL) == (parent == null)

  private fun delegateFor(key: ServiceKey<*>): ApplicationServices =
    parent ?: fileApplicationServices(rootDir).also {
      check(it !== this) { "Cannot resolve root-scoped service '${key.name}': resolver returned global instance" }
    }

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

  private var resolver: ((File) -> ApplicationServices)? = null

  override var fileApplicationServices: (File) -> ApplicationServices
    get() = parent?.fileApplicationServices
      ?: resolver
      ?: { dir -> rootCache.getOrPut(dir) { ApplicationServicesImpl(dir, this) } }
    set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      if (parent != null) parent.fileApplicationServices = value else resolver = value
    }

  companion object {
    val log = org.slf4j.LoggerFactory.getLogger(ApplicationServicesImpl::class.java)

    init {
      ServiceKey.PLUGIN_MANAGER.defaultFactory = { PluginManager() }
      ServiceKey.AUTHORIZATION_MANAGER.defaultFactory = { AuthorizationManager() }
      ServiceKey.THREAD_POOL_MANAGER.defaultFactory = { ThreadPoolManager() }
      ServiceKey.METADATA_DB.defaultFactory = { SessionMetadataDB() }
      ServiceKey.DATA_STORAGE.defaultFactory = {
        DataStorage(dataDir = it.rootDir.resolve("data"), metadataStorage = it.metadataDB)
      }
      ServiceKey.USAGE_DB.defaultFactory = { UsageDB() }
      ServiceKey.USER_SETTINGS.defaultFactory = { UserSettingsDB() }
      ServiceKey.AUTHENTICATION.defaultFactory = { AuthenticationDB() }
      ServiceKey.GIFTED_CREDITS.defaultFactory = { GiftedCreditsDB(it.rootDir.resolve("giftsdb")) }
      ApplicationServices.services = ApplicationServicesImpl()
    }

    /** Creates a standalone root-scoped service set (used by legacy [FileApplicationServices]). */
    fun forRoot(root: File): ApplicationServices = ApplicationServicesImpl(root, services())

    fun services(): ApplicationServices =
      ApplicationServices.services ?: throw IllegalStateException("ApplicationServices not initialized")

    /* Backward-compatible static forwarders */
    inline var pluginManager: PluginManagerInterface
      get() = services().pluginManager
      set(value) { services().pluginManager = value }
    inline var authorizationManager: AuthorizationInterface
      get() = services().authorizationManager
      set(value) { services().authorizationManager = value }
    inline var authenticationManager: AuthenticationInterface
      get() = services().authenticationManager
      set(value) { ServiceKey.AUTHENTICATION.factory = { value } }
    inline var threadPoolManager: ThreadPoolManager
      get() = services().threadPoolManager
      set(value) { services().threadPoolManager = value }
    var fileApplicationServices: (File) -> ApplicationServices
      get() = services().fileApplicationServices
      set(value) { services().fileApplicationServices = value }

    fun fileApplicationServices(root: File = ApplicationServicesConfig.dataStorageRoot) =
      services().fileApplicationServices(root)
  }
}