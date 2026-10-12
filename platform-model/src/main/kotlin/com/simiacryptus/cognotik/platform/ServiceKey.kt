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
 *
 * Every factory and wrapper remembers where it was registered (a captured stack trace).
 * Nothing is logged at registration time; instead, when [create] actually invokes a
 * factory or wrapper, it logs which one was used and where it was registered
 * (call site at DEBUG, full registration stack trace at TRACE).
 */
class ServiceKey<T : Any>(
  val name: String,
  val type: KClass<T>,
  val failFast: Boolean = false
) {

  /** A registered function together with the stack trace captured when it was registered. */
  private class Registration<F : Any>(val fn: F, val kind: String) {
    /** Captured at registration time; retained so the origin can be reported on invocation. */
    val origin: Throwable = Throwable("Registration of $kind")

    /** First stack frame outside of ServiceKey itself, i.e. the code that registered [fn]. */
    val site: String by lazy {
      origin.stackTrace.firstOrNull { !it.className.startsWith(SERVICE_KEY_CLASS) }?.toString() ?: "<unknown>"
    }

    override fun toString() = "$kind $fn (registered at $site)"
  }

  @Volatile
  private var factoryRegistration: Registration<() -> T>? = null

  @Volatile
  private var defaultFactoryRegistration: Registration<() -> T>? = null

  var factory: (() -> T)?
    get() = factoryRegistration?.fn
    set(value) {
      val existing = factoryRegistration
      when {
        null == value -> fail("Factory cannot be null")
        null != existing -> log.info(
          "Ignoring duplicate factory registration for service '{}': {} (already registered at {})",
          name, value, existing.site, RuntimeException("Stack trace")
        )
        else -> {
          val registration = Registration(value, "factory")
          factoryRegistration = registration
          log.trace("Registered factory for service '{}' at {}", name, registration.site)
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

  var defaultFactory: (() -> T)?
    get() = defaultFactoryRegistration?.fn
    set(value) {
      val existing = defaultFactoryRegistration
      when {
        null == value -> fail("Factory cannot be null")
        null != existing -> fail("Duplicate factory registration for service '$name': $value (already registered at ${existing.site})")
        else -> {
          val registration = Registration(value, "default factory")
          defaultFactoryRegistration = registration
          log.trace("Registered default factory for service '{}' at {}", name, registration.site)
        }
      }
    }

  /** Where the active factory ([factory] or else [defaultFactory]) was registered, if any. */
  val factorySite: String?
    get() = (factoryRegistration ?: defaultFactoryRegistration)?.site

  private val wrappers: MutableList<Registration<(T) -> T>> = CopyOnWriteArrayList()

  /**
   * Registers a wrapper that decorates every instance produced by [create].
   * Wrappers are applied in registration order (the first registered is innermost),
   * on top of whatever the base factory ([factory] or [defaultFactory]) returns.
   * Unlike [factory], any number of wrappers may be registered.
   */
  fun addWrapper(wrapper: (T) -> T) {
    val registration = Registration(wrapper, "wrapper")
    wrappers.add(registration)
    log.trace("Registered wrapper for service '{}' ({} total) at {}", name, wrappers.size, registration.site)
  }

  /** Snapshot of the registered wrappers, in application order. */
  fun getWrappers(): List<(T) -> T> = wrappers.map { it.fn }

  /** Snapshot of the registration sites of the wrappers, in application order. */
  fun getWrapperSites(): List<String> = wrappers.map { it.site }

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

  /** Logs that a registered function is being invoked, and where it was registered. */
  private fun logInvocation(registration: Registration<*>, detail: String = "") {
    if (log.isTraceEnabled) {
      log.trace(
        "Service '{}': invoking {}{} registered at {}",
        name, registration.kind, detail, registration.site, registration.origin
      )
    } else if (log.isDebugEnabled) {
      log.debug(
        "Service '{}': invoking {}{} registered at {}",
        name, registration.kind, detail, registration.site
      )
    }
  }

  /** Creates a new (uncached) instance from the registered factory, applying all wrappers. */
  fun create(): T {
    val factory = factoryRegistration ?: defaultFactoryRegistration
    ?: throw UnsupportedOperationException("No factory registered for service '$name'")
    logInvocation(factory)
    var newInstance = factory.fn.invoke()
    val wrapperSnapshot = wrappers.toList()
    wrapperSnapshot.forEachIndexed { index, wrapper ->
      logInvocation(wrapper, " #${index + 1}/${wrapperSnapshot.size}")
      newInstance = wrapper.fn(newInstance)
    }
    log.debug("Created service instance for '{}' ({} wrapper(s)): {}", name, wrapperSnapshot.size, newInstance)
    return newInstance
  }

  init {
    all.add(this)
  }

  override fun toString() = type.simpleName ?: super.toString()

  companion object {
    val log = LoggerFactory.getLogger(ServiceKey::class.java)
    private val SERVICE_KEY_CLASS: String = ServiceKey::class.java.name
    private val lock = Any()
    private val all: MutableList<ServiceKey<*>> = CopyOnWriteArrayList()

    val AUTHENTICATION = ServiceKey("authenticationManager", AuthenticationInterface::class)
    val AUTHORIZATION_MANAGER = ServiceKey("authorizationManager", AuthorizationInterface::class)
    val DATA_STORAGE = ServiceKey("dataStorage", StorageInterface::class)
    val GIFTED_CREDITS = ServiceKey("giftedCreditsDB", GiftedCreditsInterface::class)
    val METADATA_DB = ServiceKey("metadataDB", SessionMetadataInterface::class)
    val METRICS = ServiceKey("metrics", MetricsInterface::class)
    val NOTIFICATIONS = ServiceKey("notifications", NotificationsInterface::class)
    val PLUGIN_MANAGER = ServiceKey("pluginManager", PluginManagerInterface::class)
    val USAGE_DB = ServiceKey("usageDB", UsageInterface::class)
    val USER_SETTINGS = ServiceKey("userSettingsManager", UserSettingsInterface::class)
    val USER_RESOLVER = ServiceKey("userResolver", UserProvider::class)

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