package com.simiacryptus.cognotik

/**
 * Interface that plugin JARs must implement.
 * Plugins should provide a no-arg constructor and register their
 * DynamicEnum constants (TaskType, APIProvider, etc.) in [init].
 */
interface CognotikPlugin {
    /**
     * Called when the plugin is loaded. Implementations should register
     * any DynamicEnum constants (TaskType, APIProvider, etc.) here.
     */
    fun init()

    /**
     * A human-readable name for this plugin.
     */
    val pluginName: String
        get() = javaClass.simpleName
}