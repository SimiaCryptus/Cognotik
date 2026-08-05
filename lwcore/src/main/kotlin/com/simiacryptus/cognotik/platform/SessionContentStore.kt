package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import java.io.InputStream
import java.io.OutputStream

/**
 * Narrow, backend-agnostic content API for session data.
 *
 * This is the replacement for handing out `java.io.File` handles: it is
 * implementable over a local disk, S3/GCS, or a database blob table, and it
 * scopes callers to a single session (REVIEW.md §3.3, Phase 2 item 10).
 *
 * Paths are `/`-separated, relative to the session root, and must not escape it.
 */
interface SessionContentStore {

  /**
   * Opens a session-relative path for reading.
   *
   * @throws java.io.FileNotFoundException if the path does not exist
   * @throws IllegalArgumentException if [path] escapes the session root
   */
  fun openRead(user: User?, session: Session, path: String): InputStream

  /**
   * Opens a session-relative path for writing, creating parents as needed.
   *
   * @throws IllegalArgumentException if [path] escapes the session root
   */
  fun openWrite(user: User?, session: Session, path: String): OutputStream

  /**
   * Lists session-relative paths beginning with [prefix].
   */
  fun list(user: User?, session: Session, prefix: String = ""): List<String>

  /** @return true if the path exists within the session. */
  fun exists(user: User?, session: Session, path: String): Boolean

  /** @return true if something was deleted, false if the path did not exist. */
  fun delete(user: User?, session: Session, path: String): Boolean
}

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

    fun unload() {
        // Optional cleanup logic when the plugin is unloaded
    }

    fun initializePlugin() {}

    /**
     * A human-readable name for this plugin.
     */
    val pluginName: String get() = javaClass.simpleName
}