package com.simiacryptus.cognotik.platform.service

import com.simiacryptus.cognotik.platform.CognotikPlugin
import com.simiacryptus.cognotik.platform.PluginNotFoundException
import com.simiacryptus.cognotik.platform.model.PluginEvents
import com.simiacryptus.cognotik.platform.model.PluginId
import com.simiacryptus.cognotik.platform.model.Topic
import java.io.File

/**
 * Facade retained for compatibility: it is now simply the composition of the three
 * responsibilities it used to conflate.
 *
 * New consumers should depend on the narrowest port they need:
 * - [com.simiacryptus.cognotik.platform.EventBus] for publish/subscribe
 * - [PluginRegistry] for plugin lifecycle
 * - [com.simiacryptus.cognotik.platform.PluginInstaller] for artifact installation/removal
 *
 * Not yet specified (tracked in REVIEW.md §3.8): API version compatibility,
 * load ordering, inter-plugin dependencies, and classloader isolation guarantees.
 */
interface PluginManagerInterface : EventBus, PluginRegistry, PluginInstaller

/**
 * Publish/subscribe event router.
 *
 * Split out of `PluginManagerInterface` so that consumers which only need events
 * are not exposed to plugin lifecycle operations such as `deletePlugin`
 * (REVIEW.md §3.8).
 */
interface EventBus {

  /**
   * Publish an event to all subscribers of the given topic.
   *
   * @param topic the event topic/channel name
   * @param data the event payload
   */
  fun publish(topic: String, data: Any?)

  /** Typed variant of [publish]. */
  fun <T : Any> publish(topic: Topic<T>, data: T?) = publish(topic.name, data)

  /**
   * Subscribe to events on a given topic.
   *
   * @param topic the event topic/channel name
   * @param handler callback invoked with the event payload when an event is published
   * @return a subscription ID that can be used to unsubscribe
   */
  fun subscribe(topic: String, handler: (Any?) -> Unit): String

  /**
   * Typed variant of [subscribe]: payloads that do not match [Topic.payloadType]
   * are delivered as null rather than causing a ClassCastException inside a plugin.
   */
  fun <T : Any> subscribe(topic: Topic<T>, handler: (T?) -> Unit): String =
    subscribe(topic.name) { raw -> handler(topic.cast(raw)) }

  /**
   * Unsubscribe a previously registered event handler.
   *
   * @param subscriptionId the subscription ID returned by [subscribe] or [onChange]
   */
  fun unsubscribe(subscriptionId: String)

  /**
   * Register a change listener, returning a handle for [unsubscribe].
   *
   * @return a subscription ID
   */
  fun onChange(subscriber: () -> Unit): String =
    subscribe(PluginEvents.CHANGE_NOTIFICATION) { subscriber() }

  /** Notify all change listeners. */
  fun triggerChangeNotification()
}

/**
 * Plugin lifecycle (load/unload/introspect).
 *
 * Split out of `PluginManagerInterface` so lifecycle is separable from the event bus.
 *
 * Security note: loading a plugin JAR executes untrusted code with full JVM
 * privileges. No sandboxing is provided; deployments are expected to install only
 * trusted, ideally signature-verified, artifacts (REVIEW.md §3.8).
 */
interface PluginRegistry {

  /**
   * Load a plugin JAR and initialize all [com.simiacryptus.cognotik.platform.CognotikPlugin] implementations
   * discovered via ServiceLoader.
   *
   * @param jarFile the JAR file to load
   * @return list of initialized plugins from this JAR
   * @throws com.simiacryptus.cognotik.platform.PluginNotFoundException if the file does not exist or is not a JAR
   * @throws com.simiacryptus.cognotik.platform.PluginAlreadyLoadedException if the JAR has already been loaded
   */
  fun loadPlugin(jarFile: File): List<CognotikPlugin>

  /**
   * Load a plugin JAR and initialize a specific plugin class by name.
   *
   * @param jarFile the JAR file to load
   * @param entryPointClass fully-qualified class name implementing [CognotikPlugin]
   * @return the initialized plugin
   */
  fun loadPlugin(jarFile: File, entryPointClass: String): CognotikPlugin

  /**
   * Load all plugin JARs from a directory.
   *
   * @param directory the directory to scan for JAR files
   * @return map from JAR file to list of initialized plugins
   */
  fun loadPluginsFromDirectory(directory: File): Map<File, List<CognotikPlugin>>

  /**
   * Unload a previously loaded plugin JAR and close its classloader.
   * Note: classes already loaded from this JAR will remain in memory
   * until garbage collected, but no new classes can be loaded.
   *
   * @param jarFile the JAR file to unload
   */
  fun unloadPlugin(jarFile: File)

  /**
   * Get all currently loaded plugins, keyed by the absolute path of their JAR.
   */
  fun getLoadedPlugins(): Map<String, List<CognotikPlugin>>

  /** [getLoadedPlugins] with typed keys. */
  fun getLoadedPluginsById(): Map<PluginId, List<CognotikPlugin>> =
    getLoadedPlugins().mapKeys { PluginId(it.key) }

  /**
   * Check if a JAR file has been loaded.
   */
  fun isLoaded(jarFile: File): Boolean

  /**
   * Drain subscribers and close plugin classloaders deterministically.
   *
   * Default is a no-op for source compatibility.
   */
  fun shutdown() {
    // no-op by default
  }
}

/**
 * Installation/removal of plugin artifacts.
 *
 * Irreversible filesystem side effects live here rather than in
 * [com.simiacryptus.cognotik.platform.service.PluginRegistry]/[com.simiacryptus.cognotik.platform.service.PluginManagerInterface] (REVIEW.md §3.8).
 */
interface PluginInstaller {

  /**
   * Delete a plugin JAR file from disk.
   * If the plugin is currently loaded, it will be unloaded first; if unloading
   * fails the file is left in place and the failure is propagated.
   *
   * @param jarFile the JAR file to delete
   * @throws PluginNotFoundException if the file does not exist
   */
  fun deletePlugin(jarFile: File)

  /**
   * Copy/verify an artifact into the managed plugin directory without loading it.
   *
   * @return the installed file location
   */
  fun installPlugin(jarFile: File): File =
    throw UnsupportedOperationException("installPlugin is not implemented by ${this.javaClass.name}")
}