package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.service.*
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * Typed descriptor for a service: name, type, factory delegates, and the resolved instance.
 * [factory] is the global user override; [defaultFactory] is registered by the implementing module.
 *
 * The key lazily creates and caches a single instance via [get]; [set] replaces it explicitly.
 */
class ServiceKey<T : Any>(
  val name: String,
  val type: KClass<T>,
  val failFast: Boolean = false
) {

  @Volatile
  var factory: (() -> T)? = null
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
  var defaultFactory: (() -> T)? = null
    set(value) {
      when {
        null == value -> fail("Factory cannot be null")
        null != field -> fail("Duplicate factory registration for service '$name': $value")
        else -> {
          log.info("Registering default factory for service '$name': $value", RuntimeException("Stack trace"))
          field = value
        }
      }
    }
  private val wrappers: MutableList<(T) -> T> = CopyOnWriteArrayList()
  /**
   * Registers a wrapper that decorates every instance produced by [create].
   * Wrappers are applied in registration order (the first registered is innermost),
   * on top of whatever the base factory ([factory] or [defaultFactory]) returns.
   * Unlike [factory], any number of wrappers may be registered.
   */
  fun addWrapper(wrapper: (T) -> T) {
    wrappers.add(wrapper)
    log.info("Registered wrapper for service '$name' (${wrappers.size} total): $wrapper")
  }
  /** Snapshot of the registered wrappers, in application order. */
  fun getWrappers(): List<(T) -> T> = wrappers.toList()
  /** Removes all registered wrappers (mainly useful for tests). */
  fun clearWrappers() {
    wrappers.clear()
  }

  /* ---------------------------------------------------------------- instance state */

  @Volatile
  private var instance: T? = null

  private val listeners: MutableList<(T) -> Unit> = CopyOnWriteArrayList()

  /** Registers a callback invoked whenever an instance is created via [get] or assigned via [set]. */
  fun onInstance(listener: (T) -> Unit) {
    listeners.add(listener)
  }

  /**
   * Returns the cached instance, creating it via [create] on first access.
   * Creation is serialized on a lock shared by all keys (re-entrant), so factories
   * may resolve other services without risking cross-key deadlock.
   */
  fun get(): T {
    instance?.let { return it }
    return synchronized(lock) {
      instance ?: create().also {
        instance = it
        notifyListeners(it)
      }
    }
  }

  /** Returns the cached instance without creating one. */
  fun getOrNull(): T? = instance

  /** Replaces the cached instance. */
  fun set(value: T) {
    synchronized(lock) {
      instance = value
    }
    notifyListeners(value)
  }

  /** Clears the cached instance so the next [get] recreates it (mainly useful for tests). */
  fun reset() {
    synchronized(lock) {
      instance = null
    }
  }

  private fun notifyListeners(value: T) {
    for (listener in listeners) {
      try {
        listener(value)
      } catch (e: Exception) {
        log.warn("Instance listener failed for service '$name'", e)
      }
    }
  }

  /** Creates a new (uncached) instance from the registered factory, applying all wrappers. */
  fun create(): T {
    val factory = factory ?: defaultFactory
    ?: throw UnsupportedOperationException("No factory registered for service '$name'")
    var newInstance = (factory).invoke()
    for (wrapper in wrappers) {
      newInstance = wrapper(newInstance)
    }
    log.info("Created service instance for '$name' (${wrappers.size} wrapper(s)): $newInstance")
    return newInstance
  }

  init {
    all.add(this)
  }

  override fun toString() = type.simpleName ?: super.toString()

  companion object {
    val log = LoggerFactory.getLogger(ServiceKey::class.java)
    private val lock = Any()
    private val all: MutableList<ServiceKey<*>> = CopyOnWriteArrayList()

    val PLUGIN_MANAGER = ServiceKey("pluginManager", PluginManagerInterface::class)
    val AUTHORIZATION_MANAGER = ServiceKey("authorizationManager", AuthorizationInterface::class)
    val DATA_STORAGE = ServiceKey("dataStorage", StorageInterface::class)
    val METADATA_DB = ServiceKey("metadataDB", SessionMetadataInterface::class)
    val USAGE_DB = ServiceKey("usageDB", UsageInterface::class)
    val USER_SETTINGS = ServiceKey("userSettingsManager", UserSettingsInterface::class)
    val USER_RESOLVER = ServiceKey("userResolver", UserProvider::class)
    val AUTHENTICATION = ServiceKey("authenticationManager", AuthenticationInterface::class)
    val GIFTED_CREDITS = ServiceKey("giftedCreditsDB", GiftedCreditsInterface::class)
    /**
     * Central metrics backend (CloudWatch, Prometheus, or a [CompositeMetrics] of both).
     * Callers should create it once and cache it; fall back to [NoOpMetrics] when unregistered.
     */
    val METRICS = ServiceKey("metrics", MetricsInterface::class)
    /**
     * Alert notification delivery (email, Slack, SNS, ...), called by metrics backends on
     * alert transitions. Resolve via [NotificationsInterface.resolve], which falls back to
     * [LoggingNotifications] when unregistered.
     */
    val NOTIFICATIONS = ServiceKey("notifications", NotificationsInterface::class)

    /** Snapshot of all declared service keys. */
    fun all(): List<ServiceKey<*>> = all.toList()

    init {
      // Route through the router so that usage interception (token metrics) applies to model calls.
      USAGE_DB.onInstance {
        ChatModel.ON_USAGE =
          { model, u, user, session, data -> ServiceRouter.incrementUsage(session, user, model, u, data) }
      }
    }
  }
}