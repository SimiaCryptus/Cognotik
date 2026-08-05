package com.simiacryptus.cognotik.platform.model

import java.io.File

/**
 * Process-wide platform configuration.
 *
 * This remains a singleton for compatibility, but all fields are now `@Volatile`
 * so that a late write is guaranteed visible to other threads and the lock cannot
 * be bypassed by a benign data race (REVIEW.md §3.9).
 */
object ApplicationServicesConfig {

  @JvmStatic
  @Volatile
  @set:Deprecated("Use lock(); this property can only ever transition false -> true.", ReplaceWith("lock()"))
  var isLocked: Boolean = false
    set(value) {
      require(!field) { "ApplicationServices is locked" }
      field = value
    }

  /** Locks the configuration against further changes. Idempotent only in the sense that a second call fails. */
  @JvmStatic
  fun lock() {
    @Suppress("DEPRECATION")
    isLocked = true
  }

  @JvmStatic
  @Volatile
  var dataStorageRoot: File = File(System.getProperty("user.home"), ".cognotik")
    set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }

  /**
   * The default identity used when no principal is available.
   *
   * Replaces the top-level `defaultUser` global (which now proxies here).
   */
  @JvmField
  //@JvmStatic
  @Volatile
  var defaultUser: User = User(
    id = "1",
    email = "user@localhost"
  )

  /**
   * Validates and returns [dataStorageRoot], creating it if necessary, so that
   * misconfiguration fails fast rather than at first write.
   *
   * @throws IllegalStateException if the root cannot be created or is not writable
   */
  @JvmStatic
  fun requireDataStorageRoot(): File {
    val root = dataStorageRoot
    if (!root.exists() && !root.mkdirs()) {
      throw IllegalStateException("Cannot create dataStorageRoot: $root")
    }
    if (!root.isDirectory) throw IllegalStateException("dataStorageRoot is not a directory: $root")
    if (!root.canWrite()) throw IllegalStateException("dataStorageRoot is not writable: $root")
    return root
  }

}