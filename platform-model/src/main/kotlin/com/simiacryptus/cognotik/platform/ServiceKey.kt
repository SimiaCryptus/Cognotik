package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.service.AuthenticationInterface
import com.simiacryptus.cognotik.platform.service.AuthorizationInterface
import com.simiacryptus.cognotik.platform.service.GiftedCreditsInterface
import com.simiacryptus.cognotik.platform.service.PluginManagerInterface
import com.simiacryptus.cognotik.platform.service.SessionMetadataInterface
import com.simiacryptus.cognotik.platform.service.StorageInterface
import com.simiacryptus.cognotik.platform.service.UsageInterface
import com.simiacryptus.cognotik.platform.service.UserProvider
import com.simiacryptus.cognotik.platform.service.UserSettingsInterface
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

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
  var factory: ((ServiceMap) -> T)? = null
    set(value) {
      log.info("Registering factory for service '$name': $value", RuntimeException("Stack trace"))
      field = value
    }

  @Volatile
  var defaultFactory: ((ServiceMap) -> T)? = null

  fun create(services: ServiceMap): T {
    log.info("Creating service instance for '$name'", RuntimeException("Stack trace"))
    return (factory ?: defaultFactory
    ?: throw UnsupportedOperationException("No factory registered for service '$name'"))
      .invoke(services)
  }

  init {
    all.add(this)
  }

  override fun toString() = "ServiceKey($name: ${type.simpleName}, $scope)"

  companion object {
    val log = LoggerFactory.getLogger(ServiceKey::class.java)
    val all: MutableList<ServiceKey<*>> = CopyOnWriteArrayList()

    val PLUGIN_MANAGER = ServiceKey("pluginManager", PluginManagerInterface::class, Scope.GLOBAL)
    val AUTHORIZATION_MANAGER = ServiceKey("authorizationManager", AuthorizationInterface::class, Scope.GLOBAL)
    val THREAD_POOL_MANAGER = ServiceKey("threadPoolManager", ThreadPoolManager::class, Scope.GLOBAL)
    val DATA_STORAGE = ServiceKey("dataStorage", StorageInterface::class)
    val METADATA_DB = ServiceKey("metadataDB", SessionMetadataInterface::class)
    val USAGE_DB = ServiceKey("usageDB", UsageInterface::class)
    val USER_SETTINGS = ServiceKey("userSettingsManager", UserSettingsInterface::class)
    val USER_RESOLVER = ServiceKey("userResolver", UserProvider::class)
    val AUTHENTICATION = ServiceKey("authenticationManager", AuthenticationInterface::class)
    val GIFTED_CREDITS = ServiceKey("giftedCreditsDB", GiftedCreditsInterface::class)
  }
}
