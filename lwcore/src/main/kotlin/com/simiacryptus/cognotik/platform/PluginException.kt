package com.simiacryptus.cognotik.platform

/**
 * Base type for plugin subsystem failures, so callers can respond meaningfully
 * instead of catching `IllegalArgumentException`/`IllegalStateException`
 * (REVIEW.md §3.8).
 *
 * Implementations may continue to throw the older generic exception types during
 * migration; callers should catch both.
 */
open class PluginException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** The requested plugin artifact does not exist, or is not a JAR. */
class PluginNotFoundException(message: String, cause: Throwable? = null) : PluginException(message, cause)

/** The plugin artifact is already loaded. */
class PluginAlreadyLoadedException(message: String, cause: Throwable? = null) : PluginException(message, cause)

/** The plugin failed to load or initialize. */
class PluginLoadException(message: String, cause: Throwable? = null) : PluginException(message, cause)

/** The plugin failed to unload cleanly. */
class PluginUnloadException(message: String, cause: Throwable? = null) : PluginException(message, cause)