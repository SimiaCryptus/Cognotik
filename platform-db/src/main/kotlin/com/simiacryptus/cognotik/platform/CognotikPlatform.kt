package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.h2.AuthenticationDB
import com.simiacryptus.cognotik.platform.h2.GiftedCreditsDB
import com.simiacryptus.cognotik.platform.h2.SessionMetadataDB
import com.simiacryptus.cognotik.platform.h2.UsageDB
import com.simiacryptus.cognotik.platform.h2.UserSettingsDB
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import java.io.File

/**
 * Unified service container.
 * - The global instance (parent == null) owns GLOBAL-scoped services and forwards ROOT-scoped ones
 *   to the service set for the current [ApplicationServicesConfig.dataStorageRoot].
 * - Root instances own ROOT-scoped services and forward GLOBAL-scoped ones to the parent.
 */
object CognotikPlatform {
  val log = org.slf4j.LoggerFactory.getLogger(CognotikPlatform::class.java)


  val rootDir: File get() = ApplicationServicesConfig.dataStorageRoot

  init {
    ServiceKey.PLUGIN_MANAGER.defaultFactory = { PluginManager() }
    ServiceKey.AUTHORIZATION_MANAGER.defaultFactory = { AuthorizationManager() }
    ServiceKey.THREAD_POOL_MANAGER.defaultFactory = { ThreadPoolManager() }
    ServiceKey.METADATA_DB.defaultFactory = { SessionMetadataDB() }
    ServiceKey.DATA_STORAGE.defaultFactory = {
      DataStorage(dataDir = rootDir.resolve("data"), metadataStorage = it[ServiceKey.METADATA_DB])
    }
    ServiceKey.USAGE_DB.defaultFactory = { UsageDB() }
    ServiceKey.USER_SETTINGS.defaultFactory = { UserSettingsDB() }
    ServiceKey.AUTHENTICATION.defaultFactory = { AuthenticationDB() }
    ServiceKey.GIFTED_CREDITS.defaultFactory = { GiftedCreditsDB(rootDir.resolve("giftsdb")) }
    log.info("CognotikPlatform initialized with data storage root: ${rootDir.absolutePath}")
  }

  fun init() {
    log.debug("Initializing CognotikPlatform with data storage root: ${rootDir.absolutePath}")
  }
}