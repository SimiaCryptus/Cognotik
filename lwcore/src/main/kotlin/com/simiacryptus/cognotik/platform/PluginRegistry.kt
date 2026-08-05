package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.PluginId
import java.io.File

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
   * Load a plugin JAR and initialize all [CognotikPlugin] implementations
   * discovered via ServiceLoader.
   *
   * @param jarFile the JAR file to load
   * @return list of initialized plugins from this JAR
   * @throws PluginNotFoundException if the file does not exist or is not a JAR
   * @throws PluginAlreadyLoadedException if the JAR has already been loaded
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