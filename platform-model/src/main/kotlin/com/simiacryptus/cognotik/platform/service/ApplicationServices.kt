package com.simiacryptus.cognotik.platform.service

import com.simiacryptus.cognotik.platform.ThreadPoolManager
import com.simiacryptus.cognotik.platform.service.ServiceKey.Companion.AUTHENTICATION
import com.simiacryptus.cognotik.platform.service.ServiceKey.Companion.AUTHORIZATION_MANAGER
import com.simiacryptus.cognotik.platform.service.ServiceKey.Companion.DATA_STORAGE
import com.simiacryptus.cognotik.platform.service.ServiceKey.Companion.GIFTED_CREDITS
import com.simiacryptus.cognotik.platform.service.ServiceKey.Companion.METADATA_DB
import com.simiacryptus.cognotik.platform.service.ServiceKey.Companion.PLUGIN_MANAGER
import com.simiacryptus.cognotik.platform.service.ServiceKey.Companion.THREAD_POOL_MANAGER
import com.simiacryptus.cognotik.platform.service.ServiceKey.Companion.USAGE_DB
import com.simiacryptus.cognotik.platform.service.ServiceKey.Companion.USER_SETTINGS
import java.io.File

interface ApplicationServices {
  /** Root this service set is scoped to (for [ServiceKey.Scope.ROOT] services). */
  val rootDir: File

  /** Single resolution path for all services. */
  operator fun <T : Any> get(key: ServiceKey<T>): T
  operator fun <T : Any> set(key: ServiceKey<T>, value: T)

  var pluginManager: PluginManagerInterface
    get() = this[PLUGIN_MANAGER]
    set(value) { this[PLUGIN_MANAGER] = value }
  var authorizationManager: AuthorizationInterface
    get() = this[AUTHORIZATION_MANAGER]
    set(value) { this[AUTHORIZATION_MANAGER] = value }
  var threadPoolManager: ThreadPoolManager
    get() = this[THREAD_POOL_MANAGER]
    set(value) { this[THREAD_POOL_MANAGER] = value }

  val dataStorageFactory: StorageInterface get() = this[DATA_STORAGE]
  val metadataDB: SessionMetadataInterface get() = this[METADATA_DB]
  val usageDB: UsageInterface get() = this[USAGE_DB]
  val userSettingsManager: UserSettingsInterface get() = this[USER_SETTINGS]
  val authenticationManager: AuthenticationInterface get() = this[AUTHENTICATION]
  val giftedCreditsDB: GiftedCreditsInterface get() = this[GIFTED_CREDITS]

  /** Resolver for root-scoped service sets. */
  var fileApplicationServices: (File) -> ApplicationServices

  fun fileApplicationServices(root: File): ApplicationServices = fileApplicationServices.invoke(root)

  companion object {
    @JvmStatic
    var services: ApplicationServices? = null

    /* Legacy global factories (formerly IFileApplicationServices.Companion) */
    @Deprecated("Use ServiceKey.DATA_STORAGE.factory")
    var dataStorageFactoryFn: ((File) -> StorageInterface)? by LegacyFileFactory(DATA_STORAGE)
    @Deprecated("Use ServiceKey.METADATA_DB.factory")
    var metadataDBFn: ((File) -> SessionMetadataInterface)? by LegacyFileFactory(METADATA_DB)
    @Deprecated("Use ServiceKey.USAGE_DB.factory")
    var usageDBFn: ((File) -> UsageInterface)? by LegacyFileFactory(USAGE_DB)
    @Deprecated("Use ServiceKey.USER_SETTINGS.factory")
    var userSettingsManagerFn: ((File) -> UserSettingsInterface)? by LegacyFileFactory(USER_SETTINGS)
    @Deprecated("Use ServiceKey.AUTHENTICATION.factory")
    var authenticationManagerFn: ((File) -> AuthenticationInterface)? by LegacyFileFactory(AUTHENTICATION)
    @Deprecated("Use ServiceKey.GIFTED_CREDITS.factory")
    var giftedCreditsDBFn: ((File) -> GiftedCreditsInterface)? by LegacyFileFactory(GIFTED_CREDITS)
  }
}