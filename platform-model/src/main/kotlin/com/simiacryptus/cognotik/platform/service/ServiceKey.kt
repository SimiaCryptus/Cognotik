package com.simiacryptus.cognotik.platform.service

import com.simiacryptus.cognotik.platform.ThreadPoolManager
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * Typed descriptor for a service: name, type, scope, and factory delegates.
 * [factory] is the global user override; [defaultFactory] is registered by the implementing module.
 */
class ServiceKey<T : Any>(
  val name: String,
  val type: KClass<T>,
  val scope: Scope = Scope.ROOT,
) {
  enum class Scope { GLOBAL, ROOT }

  @Volatile
  var factory: ((ApplicationServices) -> T)? = null

  @Volatile
  var defaultFactory: ((ApplicationServices) -> T)? = null

  fun create(services: ApplicationServices): T =
    (factory ?: defaultFactory ?: throw UnsupportedOperationException("No factory registered for service '$name'"))
      .invoke(services)

  init {
    all.add(this)
  }

  override fun toString() = "ServiceKey($name: ${type.simpleName}, $scope)"

  companion object {
    val all: MutableList<ServiceKey<*>> = CopyOnWriteArrayList()

    val PLUGIN_MANAGER = ServiceKey("pluginManager", PluginManagerInterface::class, Scope.GLOBAL)
    val AUTHORIZATION_MANAGER = ServiceKey("authorizationManager", AuthorizationInterface::class, Scope.GLOBAL)
    val THREAD_POOL_MANAGER = ServiceKey("threadPoolManager", ThreadPoolManager::class, Scope.GLOBAL)

    val DATA_STORAGE = ServiceKey("dataStorage", StorageInterface::class)
    val METADATA_DB = ServiceKey("metadataDB", SessionMetadataInterface::class)
    val USAGE_DB = ServiceKey("usageDB", UsageInterface::class)
    val USER_SETTINGS = ServiceKey("userSettingsManager", UserSettingsInterface::class)
    val AUTHENTICATION = ServiceKey("authenticationManager", AuthenticationInterface::class)
    val GIFTED_CREDITS = ServiceKey("giftedCreditsDB", GiftedCreditsInterface::class)
  }
}

/** Adapts a legacy `(File) -> T` factory property onto a [ServiceKey]. */
internal class LegacyFileFactory<T : Any>(private val key: ServiceKey<T>) : ReadWriteProperty<Any?, ((File) -> T)?> {
  @Volatile
  private var legacy: ((File) -> T)? = null

  override fun getValue(thisRef: Any?, property: KProperty<*>): ((File) -> T)? = legacy

  override fun setValue(thisRef: Any?, property: KProperty<*>, value: ((File) -> T)?) {
    legacy = value
    key.factory = value?.let { fn -> { services: ApplicationServices -> fn(services.rootDir) } }
  }
}