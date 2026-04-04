package com.simiacryptus.cognotik.platform.model

import com.simiacryptus.cognotik.CognotikPlugin
import java.io.File

interface PluginManagerInterface {

  fun subscribeToChanges(subscriber: () -> Unit)
  fun triggerChangeNotification()
  /**
   * Load a plugin JAR and initialize all [com.simiacryptus.cognotik.platform.CognotikPlugin] implementations
   * discovered via ServiceLoader.
   *
   * @param jarFile the JAR file to load
   * @return list of initialized plugins from this JAR
   * @throws IllegalArgumentException if the file does not exist or is not a JAR
   * @throws IllegalStateException if the JAR has already been loaded
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
   * Get all currently loaded plugins.
   */
  fun getLoadedPlugins(): Map<String, List<CognotikPlugin>>

  /**
   * Check if a JAR file has been loaded.
   */
  fun isLoaded(jarFile: File): Boolean
  /**
   * Delete a plugin JAR file from disk.
   * If the plugin is currently loaded, it will be unloaded first.
   *
   * @param jarFile the JAR file to delete
   * @throws IllegalArgumentException if the file does not exist
   */
  fun deletePlugin(jarFile: File)
}