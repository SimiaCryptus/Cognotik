package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.User
import java.io.File

/**
 * Process-wide platform configuration.
 *
 * This remains a singleton for compatibility, but all fields are now `@Volatile`
 * so that a late write is guaranteed visible to other threads and the lock cannot
 * be bypassed by a benign data race (REVIEW.md §3.9).
 */
object CognotikConfig {

  @JvmStatic
  @Volatile
  @set:Deprecated("Use lock(); this property can only ever transition false -> true.", ReplaceWith("lock()"))
  var isLocked: Boolean = false
    set(value) {
      require(!field) { "CognotikConfig is locked" }
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
  @Volatile
  var localUser: User = User(
    email = "user@localhost"
  )


  val defaultUser: User get() = localUser

}