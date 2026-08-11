package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.PluginEvents
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.Thread.sleep
import java.net.URLClassLoader
import java.util.ServiceLoader
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.iterator

/**
 * Manages loading and initialization of plugin JARs.
 *
 * Plugins are discovered via [ServiceLoader] using the
 * [CognotikPlugin] service interface. Each plugin JAR should contain
 * a file `META-INF/services/com.simiacryptus.cognotik.util.CognotikPlugin`
 * listing the fully-qualified class names of its plugin implementations.
 *
 * Alternatively, plugins can be loaded by specifying an explicit entry
 * point class name.
 */
class PluginManager(
  private val root: File = File("plugins")
) : PluginManagerInterface {
  /**
   * Represents a persisted plugin entry for serialization.
   */
  private data class PluginEntry(
    val jarPath: String,
    val entryPointClass: String? = null
  )

  private val manifestFile = File(root, "plugins-manifest.json")
  private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

  /** Map from JAR file path to the classloader used to load it */
  private val loadedJars = ConcurrentHashMap<String, URLClassLoader>()

  /** Map from JAR file path to the list of initialized plugins */
  private val loadedPlugins = ConcurrentHashMap<String, List<CognotikPlugin>>()

  /** Map from JAR file path to the persisted plugin entry metadata */
  private val loadedPluginEntries = ConcurrentHashMap<String, PluginEntry>()

  /** Event router: topic -> (subscriptionId -> handler) */
  private val eventSubscribers = ConcurrentHashMap<String, ConcurrentHashMap<String, (Any?) -> Unit>>()

  /** Reverse index: subscriptionId -> topic, for unsubscribe */
  private val subscriptionIndex = ConcurrentHashMap<String, String>()


  init {
    Thread({
      sleep(1000)
      root.mkdirs()
      restorePlugins()
    }, "PluginManager-restore").apply { isDaemon = true }.start()
  }

  override fun publish(topic: String, data: Any?) {
    log.debug("Publishing event on topic '{}': {}", topic, data)
    val handlers = eventSubscribers[topic]
    if (handlers == null || handlers.isEmpty()) {
      log.debug("No subscribers for topic '{}'", topic)
      return
    }
    for ((subId, handler) in handlers) {
      try {
        handler(data)
      } catch (e: Exception) {
        log.error("Error in event handler {} for topic '{}'", subId, topic, e)
      }
    }
  }

  override fun subscribe(topic: String, handler: (Any?) -> Unit): String {
    val subscriptionId = UUID.randomUUID().toString()
    eventSubscribers.computeIfAbsent(topic) { ConcurrentHashMap() }[subscriptionId] = handler
    subscriptionIndex[subscriptionId] = topic
    log.debug("Subscribed to topic '{}' with subscription ID: {}", topic, subscriptionId)
    return subscriptionId
  }

  override fun unsubscribe(subscriptionId: String) {
    val topic = subscriptionIndex.remove(subscriptionId)
    if (topic != null) {
      eventSubscribers[topic]?.remove(subscriptionId)
      log.debug("Unsubscribed {} from topic '{}'", subscriptionId, topic)
    } else {
      log.debug("Subscription ID not found for unsubscribe: {}", subscriptionId)
    }
  }


  /**
   * Persist the current set of loaded plugin JARs to the manifest file.
   */
  private fun saveManifest() {
    try {
      val entries = loadedPluginEntries.values.toList()
      manifestFile.parentFile?.mkdirs()
      manifestFile.writeText(gson.toJson(entries))
      log.debug("Saved plugin manifest with {} entries", entries.size)
    } catch (e: Exception) {
      log.error("Failed to save plugin manifest to {}", manifestFile.canonicalPath, e)
    }
  }

  /**
   * Load the manifest file and return the list of persisted plugin entries.
   */
  private fun loadManifest(): List<PluginEntry> {
    return try {
      if (manifestFile.exists()) {
        val type = object : TypeToken<List<PluginEntry>>() {}.type
        val entries: List<PluginEntry> = gson.fromJson(manifestFile.readText(), type)
        log.info("Loaded plugin manifest with {} entries", entries.size)
        entries
      } else {
        log.debug("No plugin manifest found at {}", manifestFile.canonicalPath)
        emptyList()
      }
    } catch (e: Exception) {
      log.error("Failed to load plugin manifest from {}", manifestFile.canonicalPath, e)
      emptyList()
    }
  }

  /**
   * Restore plugins from the persisted manifest on startup.
   */
  private fun restorePlugins() {
    val entries = loadManifest()
    for (entry in entries) {
      try {
        val jarFile = File(entry.jarPath)
        if (!jarFile.exists()) {
          log.warn("Persisted plugin JAR no longer exists, skipping: {}", entry.jarPath)
          continue
        }
        if (loadedJars.containsKey(jarFile.canonicalPath)) {
          log.debug("Plugin JAR already loaded during restore, skipping: {}", entry.jarPath)
          continue
        }
        if (entry.entryPointClass != null) {
          log.info("Restoring plugin JAR: {} with entry point: {}", entry.jarPath, entry.entryPointClass)
          loadPlugin(jarFile, entry.entryPointClass)
        } else {
          log.info("Restoring plugin JAR: {}", entry.jarPath)
          loadPlugin(jarFile)
        }
      } catch (e: Exception) {
        log.error("Failed to restore plugin from manifest entry: {}", entry.jarPath, e)
      }
    }
  }


  @Deprecated(
    "Use onChange, which returns a subscription id usable with unsubscribe.",
    ReplaceWith("onChange(subscriber)")
  )
  fun subscribeToChanges(subscriber: () -> Unit): String = onChange(subscriber)

  override fun triggerChangeNotification() {
    publish(PluginEvents.CHANGE_NOTIFICATION, null)
  }

  /**
   * Routed through the event bus so that [onChange] subscribers (registered via
   * the [EventBus] contract) are notified too.
   */

  fun triggerChange() {
    triggerChangeNotification()
  }

  override fun shutdown() {
    log.info("Shutting down PluginManager")
    loadedJars.keys.toList().forEach { path ->
      try {
        unloadPlugin(File(path))
      } catch (e: Exception) {
        log.warn("Error unloading plugin JAR during shutdown: {}", path, e)
      }
    }
    eventSubscribers.clear()
    subscriptionIndex.clear()
  }

  override fun installPlugin(jarFile: File): File {
    if (!jarFile.exists()) throw PluginNotFoundException("Plugin JAR does not exist: ${jarFile.canonicalPath}")
    if (!jarFile.name.endsWith(".jar")) throw PluginNotFoundException("File is not a JAR: ${jarFile.canonicalPath}")
    root.mkdirs()
    val target = File(root, jarFile.name)
    if (jarFile.canonicalFile == target.canonicalFile) return target
    log.info("Installing plugin artifact {} -> {}", jarFile.canonicalPath, target.canonicalPath)
    jarFile.copyTo(target, overwrite = true)
    return target
  }

  /**
   * Load a plugin JAR and initialize all [CognotikPlugin] implementations
   * discovered via ServiceLoader.
   *
   * @param jarFile the JAR file to load
   * @return list of initialized plugins from this JAR
   * @throws IllegalArgumentException if the file does not exist or is not a JAR
   * @throws IllegalStateException if the JAR has already been loaded
   */
  override fun loadPlugin(jarFile: File): List<CognotikPlugin> {
    val canonicalPath = jarFile.canonicalPath
    if (!jarFile.exists()) throw PluginNotFoundException("Plugin JAR does not exist: $canonicalPath")
    if (!jarFile.name.endsWith(".jar")) throw PluginNotFoundException("File is not a JAR: $canonicalPath")
    synchronized(this) {
      if (loadedJars.containsKey(canonicalPath)) {
        throw PluginAlreadyLoadedException("Plugin JAR already loaded: $canonicalPath")
      }

      log.info("Loading plugin JAR: {}", canonicalPath)

      val classLoader = URLClassLoader(
        "Plugin: " + jarFile.name,
        arrayOf(jarFile.canonicalFile.toPath().toUri().toURL()),
        this.javaClass.classLoader
      )
      loadedJars[canonicalPath] = classLoader

      val plugins = mutableListOf<CognotikPlugin>()
      try {
        val serviceLoader = ServiceLoader.load(CognotikPlugin::class.java, classLoader)
        for (plugin in serviceLoader) {
          try {
            log.info("Initializing plugin: {} from {}", plugin.pluginName, canonicalPath)
            plugin.init()
            plugins.add(plugin)
            log.info("Successfully initialized plugin: {}", plugin.pluginName)
          } catch (e: Exception) {
            log.error("Failed to initialize plugin: {} from {}", plugin.pluginName, canonicalPath, e)
          }
        }
      } catch (e: Throwable) {
        log.error("Failed to discover plugins via ServiceLoader in JAR: {}", canonicalPath, e)
      }

      if (plugins.isEmpty()) {
        log.warn("No CognotikPlugin implementations found in JAR: {}", canonicalPath)
      } else {
        loadedPlugins[canonicalPath] = plugins
        loadedPluginEntries[canonicalPath] = PluginEntry(canonicalPath)
        saveManifest()
        triggerChange()
      }

      return plugins
    }
  }

  /**
   * Load a plugin JAR and initialize a specific plugin class by name.
   *
   * @param jarFile the JAR file to load
   * @param entryPointClass fully-qualified class name implementing [CognotikPlugin]
   * @return the initialized plugin
   */
  override fun loadPlugin(jarFile: File, entryPointClass: String): CognotikPlugin {
    val canonicalPath = jarFile.canonicalPath
    if (!jarFile.exists()) throw PluginNotFoundException("Plugin JAR does not exist: $canonicalPath")
    if (!jarFile.name.endsWith(".jar")) throw PluginNotFoundException("File is not a JAR: $canonicalPath")

    log.info("Loading plugin JAR: {} with entry point: {}", canonicalPath, entryPointClass)

    val classLoader = synchronized(this) {
      loadedJars.getOrPut(canonicalPath) {
        URLClassLoader(
          "Plugin: " + jarFile.name,
          arrayOf(jarFile.canonicalFile.toPath().toUri().toURL()),
          this.javaClass.classLoader
        )
      }
    }

    val clazz = classLoader.loadClass(entryPointClass)
    if (!CognotikPlugin::class.java.isAssignableFrom(clazz)) {
      throw PluginLoadException("Class $entryPointClass does not implement CognotikPlugin")
    }

    val plugin = clazz.getDeclaredConstructor().newInstance() as CognotikPlugin
    log.info("Initializing plugin: {} from {}", plugin.pluginName, canonicalPath)
    try {
      plugin.init()
    } catch (e: Throwable) {
      log.warn("Failed to initialize plugin: {} from {}", plugin.pluginName, canonicalPath, e)
    }

    synchronized(this) {
      val existing = loadedPlugins.getOrDefault(canonicalPath, emptyList())
      loadedPlugins[canonicalPath] = existing + plugin
      loadedPluginEntries[canonicalPath] = PluginEntry(canonicalPath, entryPointClass)
      saveManifest()
      triggerChange()
    }

    log.info("Successfully initialized plugin: {}", plugin.pluginName)
    return plugin
  }

  /**
   * Load all plugin JARs from a directory.
   *
   * @param directory the directory to scan for JAR files
   * @return map from JAR file to list of initialized plugins
   */
  override fun loadPluginsFromDirectory(directory: File): Map<File, List<CognotikPlugin>> {
    require(directory.isDirectory) { "Not a directory: ${directory.canonicalPath}" }
    log.info("Scanning for plugins in directory: {}", directory.canonicalPath)

    val results = mutableMapOf<File, List<CognotikPlugin>>()
    val jarFiles = directory.listFiles { file -> file.name.endsWith(".jar") } ?: emptyArray()

    for (jarFile in jarFiles) {
      try {
        if (!loadedJars.containsKey(jarFile.canonicalPath)) {
          results[jarFile] = loadPlugin(jarFile)
        } else {
          log.debug("Skipping already-loaded JAR: {}", jarFile.canonicalPath)
          results[jarFile] = loadedPlugins[jarFile.canonicalPath] ?: emptyList()
        }
      } catch (e: Exception) {
        log.error("Failed to load plugin JAR: {}", jarFile.canonicalPath, e)
      }
    }

    triggerChange()
    return results
  }

  /**
   * Unload a previously loaded plugin JAR and close its classloader.
   * Note: classes already loaded from this JAR will remain in memory
   * until garbage collected, but no new classes can be loaded.
   *
   * @param jarFile the JAR file to unload
   */
  override fun unloadPlugin(jarFile: File) {
    val canonicalPath = jarFile.canonicalPath
    val classLoader = loadedJars.remove(canonicalPath)
    if (classLoader != null) {
      log.info("Unloading plugin JAR: {}", canonicalPath)
      val plugins = loadedPlugins.remove(canonicalPath)
      loadedPluginEntries.remove(canonicalPath)
      plugins?.forEach { plugin ->
        try {
          log.info("Unloading plugin: {}", plugin.pluginName)
          plugin.unload()
          log.info("Successfully unloaded plugin: {}", plugin.pluginName)
        } catch (e: Exception) {
          log.error("Error unloading plugin: {} from {}", plugin.pluginName, canonicalPath, e)
        }
      }
      try {
        classLoader.close()
      } catch (e: Exception) {
        log.warn("Error closing classloader for JAR: {}", canonicalPath, e)
      }
      saveManifest()
      triggerChange()
    } else {
      log.warn("Plugin JAR not loaded, cannot unload: {}", canonicalPath)
    }
  }

  /**
   * Get all currently loaded plugins.
   */
  override fun getLoadedPlugins(): Map<String, List<CognotikPlugin>> = loadedPlugins.toMap()

  /**
   * Check if a JAR file has been loaded.
   */
  override fun isLoaded(jarFile: File): Boolean = loadedJars.containsKey(jarFile.canonicalPath)

  /**
   * Delete a plugin JAR file from disk.
   * If the plugin is currently loaded, it will be unloaded first.
   *
   * @param jarFile the JAR file to delete
   * @throws IllegalArgumentException if the file does not exist
   */
  override fun deletePlugin(jarFile: File) {
    val canonicalPath = jarFile.canonicalPath
    if (!jarFile.exists()) throw PluginNotFoundException("Plugin JAR does not exist: $canonicalPath")
    // Unload first if currently loaded. If unloading fails the artifact is left in
    // place and the failure is propagated (PluginInstaller contract).
    if (loadedJars.containsKey(canonicalPath)) {
      log.info("Plugin JAR is loaded, unloading before delete: {}", canonicalPath)
      try {
        unloadPlugin(jarFile)
      } catch (e: Exception) {
        log.error("Failed to unload plugin before delete; leaving artifact in place: {}", canonicalPath, e)
        throw PluginUnloadException("Failed to unload plugin before delete: $canonicalPath", e)
      }
    }
    log.info("Deleting plugin JAR: {}", canonicalPath)
    try {
      if (jarFile.delete()) {
        log.info("Successfully deleted plugin JAR: {}", canonicalPath)
      } else {
        log.error("Failed to delete plugin JAR (delete returned false): {}", canonicalPath)
        throw RuntimeException("Failed to delete plugin JAR: $canonicalPath")
      }
    } catch (e: SecurityException) {
      log.error("Security exception deleting plugin JAR: {}", canonicalPath, e)
      throw RuntimeException("Permission denied deleting plugin JAR: $canonicalPath", e)
    }
    triggerChange()
  }


  companion object {
    private val log = LoggerFactory.getLogger(PluginManager::class.java)
  }
}