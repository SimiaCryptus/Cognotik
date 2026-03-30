package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.CognotikPlugin
import com.simiacryptus.cognotik.platform.model.PluginManagerInterface
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages loading and initialization of plugin JARs.
 *
 * Plugins are discovered via [java.util.ServiceLoader] using the
 * [CognotikPlugin] service interface. Each plugin JAR should contain
 * a file `META-INF/services/com.simiacryptus.cognotik.util.CognotikPlugin`
 * listing the fully-qualified class names of its plugin implementations.
 *
 * Alternatively, plugins can be loaded by specifying an explicit entry
 * point class name.
 */
class PluginManager : PluginManagerInterface {

    init {
        log.info("PluginManager initialized", RuntimeException("PluginManager init stack trace"))
    }

  /** Map from JAR file path to the classloader used to load it */
    private val loadedJars = ConcurrentHashMap<String, URLClassLoader>()

    /** Map from JAR file path to the list of initialized plugins */
    private val loadedPlugins = ConcurrentHashMap<String, List<CognotikPlugin>>()

    private val changeSubscribers = mutableListOf<() -> Unit>()

    override fun subscribeToChanges(subscriber: () -> Unit) {
        changeSubscribers.add(subscriber)
    }

    fun triggerChange() {
        changeSubscribers.forEach { it.invoke() }
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
        require(jarFile.exists()) { "Plugin JAR does not exist: $canonicalPath" }
        require(jarFile.name.endsWith(".jar")) { "File is not a JAR: $canonicalPath" }
        check(!loadedJars.containsKey(canonicalPath)) { "Plugin JAR already loaded: $canonicalPath" }

        log.info("Loading plugin JAR: {}", canonicalPath)

        val classLoader = URLClassLoader(
            "Plugin: "+jarFile.name,
            arrayOf(jarFile.toURI().toURL()),
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
        } catch (e: Exception) {
            log.error("Failed to discover plugins via ServiceLoader in JAR: {}", canonicalPath, e)
        }

        if (plugins.isEmpty()) {
            log.warn("No CognotikPlugin implementations found in JAR: {}", canonicalPath)
        } else {
            loadedPlugins[canonicalPath] = plugins
            triggerChange()
        }

        return plugins
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
        require(jarFile.exists()) { "Plugin JAR does not exist: $canonicalPath" }
        require(jarFile.name.endsWith(".jar")) { "File is not a JAR: $canonicalPath" }

        log.info("Loading plugin JAR: {} with entry point: {}", canonicalPath, entryPointClass)

        val classLoader = loadedJars.getOrPut(canonicalPath) {
            URLClassLoader(
                arrayOf(jarFile.toURI().toURL()),
                this.javaClass.classLoader
            )
        }

        val clazz = classLoader.loadClass(entryPointClass)
        require(CognotikPlugin::class.java.isAssignableFrom(clazz)) {
            "Class $entryPointClass does not implement CognotikPlugin"
        }

        val plugin = clazz.getDeclaredConstructor().newInstance() as CognotikPlugin
        log.info("Initializing plugin: {} from {}", plugin.pluginName, canonicalPath)
        plugin.init()

        val existing = loadedPlugins.getOrDefault(canonicalPath, emptyList())
        loadedPlugins[canonicalPath] = existing + plugin
        triggerChange()

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

  companion object {
    private val log = LoggerFactory.getLogger(PluginManager::class.java)
  }
}