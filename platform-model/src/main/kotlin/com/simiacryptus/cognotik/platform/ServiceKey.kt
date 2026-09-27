package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.service.*
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * Typed descriptor for a service: name, type, and factory delegates.
 * [factory] is the global user override; [defaultFactory] is registered by the implementing module.
 */
class ServiceKey<T : Any>(
  val name: String,
  val type: KClass<T>,
  val failFast: Boolean = false
) {

  @Volatile
  var factory: ((ServiceMap) -> T)? = null
    set(value) {
      when {
        null == value -> fail("Factory cannot be null")
        null != field -> log.info("Ignoring duplicate factory registration for service '$name': $value", RuntimeException("Stack trace"))
        else -> {
          log.info("Registering factory for service '$name': $value", RuntimeException("Stack trace"))
          field = value
        }
      }
    }

  private fun fail(msg: String) {
    if (failFast) {
      throw IllegalArgumentException(msg)
    } else {
      log.warn(msg, RuntimeException("Stack trace"))
    }
  }

  @Volatile
  var defaultFactory: ((ServiceMap) -> T)? = null
    set(value) {
      when {
        null == value -> fail("Factory cannot be null")
        null != field -> fail("Duplicate factory registration for service '$name': $value")
        else -> {
          log.info("Registering default factory for service '$name': $value")
          field = value
        }
      }
    }

  fun create(services: ServiceMap): T {
    val factory = factory ?: defaultFactory
    ?: throw UnsupportedOperationException("No factory registered for service '$name'")
    val newInstance = (factory).invoke(services)
    log.info("Created service instance for '$name': $newInstance")
    return newInstance
  }

  init {
    all.add(this)
  }

  override fun toString() = type.simpleName ?: super.toString()

  companion object {
    val log = LoggerFactory.getLogger(ServiceKey::class.java)
    private val all: MutableList<ServiceKey<*>> = CopyOnWriteArrayList()

    val PLUGIN_MANAGER = ServiceKey("pluginManager", PluginManagerInterface::class)
    val AUTHORIZATION_MANAGER = ServiceKey("authorizationManager", AuthorizationInterface::class)
    val THREAD_POOL_MANAGER = ServiceKey("threadPoolManager", ThreadPoolManager::class)
      .apply { defaultFactory = { ThreadPoolManager() } }
    val DATA_STORAGE = ServiceKey("dataStorage", StorageInterface::class)
    val METADATA_DB = ServiceKey("metadataDB", SessionMetadataInterface::class)
    val USAGE_DB = ServiceKey("usageDB", UsageInterface::class)
    val USER_SETTINGS = ServiceKey("userSettingsManager", UserSettingsInterface::class)
    val USER_RESOLVER = ServiceKey("userResolver", UserProvider::class)
    val AUTHENTICATION = ServiceKey("authenticationManager", AuthenticationInterface::class)
    val GIFTED_CREDITS = ServiceKey("giftedCreditsDB", GiftedCreditsInterface::class)
  }
}
