package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.ServiceKey.Companion.AUTHENTICATION
import com.simiacryptus.cognotik.platform.ServiceKey.Companion.AUTHORIZATION_MANAGER
import com.simiacryptus.cognotik.platform.ServiceKey.Companion.DATA_STORAGE
import com.simiacryptus.cognotik.platform.ServiceKey.Companion.GIFTED_CREDITS
import com.simiacryptus.cognotik.platform.ServiceKey.Companion.METADATA_DB
import com.simiacryptus.cognotik.platform.ServiceKey.Companion.METRICS
import com.simiacryptus.cognotik.platform.ServiceKey.Companion.PLUGIN_MANAGER
import com.simiacryptus.cognotik.platform.ServiceKey.Companion.USAGE_DB
import com.simiacryptus.cognotik.platform.ServiceKey.Companion.USER_SETTINGS
import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.h2.*
import java.io.File

/**
 * Unified service container.
 * - The global instance (parent == null) owns GLOBAL-scoped services and forwards ROOT-scoped ones
 *   to the service set for the current [CognotikConfig.dataStorageRoot].
 * - Root instances own ROOT-scoped services and forward GLOBAL-scoped ones to the parent.
 */
object CognotikPlatform {
  private val log = org.slf4j.LoggerFactory.getLogger(CognotikPlatform::class.java)
  val rootDir: File get() = CognotikConfig.dataStorageRoot

  init {
    try {
      DATA_STORAGE.defaultFactory = { DataStorage(dataDir = rootDir.resolve("data")) }
      PLUGIN_MANAGER.defaultFactory = { PluginManager() }
      AUTHORIZATION_MANAGER.defaultFactory = { AuthorizationManager() }
      METADATA_DB.defaultFactory = { SessionMetadataDB() }
      USAGE_DB.defaultFactory = { UsageDB() }
      USER_SETTINGS.defaultFactory = { UserSettingsDB() }
      AUTHENTICATION.defaultFactory = { AuthenticationDB() }
      GIFTED_CREDITS.defaultFactory = { GiftedCreditsDB() }
      METRICS.defaultFactory = { MetricsDB() }
      log.info("CognotikPlatform initialized with data storage root: ${rootDir.absolutePath}")
    } catch (e: Exception) {
      log.error("Error initializing CognotikPlatform services", e)
    }
  }

  fun init() {
    log.debug("Initializing CognotikPlatform with data storage root: ${rootDir.absolutePath}")
  }
}