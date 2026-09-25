package com.simiacryptus.cognotik.platform

import java.io.File

interface IFileApplicationServices {
  val rootDir: File
  val dataStorageFactory: StorageInterface get() = dataStorageFactoryFn?.invoke(rootDir) ?: throw UnsupportedOperationException("dataStorageFactoryFn not initialized")
  val metadataDB: SessionMetadataInterface get() = metadataDBFn?.invoke(rootDir) ?: throw UnsupportedOperationException("metadataDBFn not initialized")
  val usageDB: UsageInterface get() = usageDBFn?.invoke(rootDir) ?: throw UnsupportedOperationException("usageDBFn not initialized")
  val userSettingsManager: UserSettingsInterface get() = userSettingsManagerFn?.invoke(rootDir) ?: throw UnsupportedOperationException("userSettingsManagerFn not initialized")

  val authenticationManager: AuthenticationInterface get() = authenticationManagerFn?.invoke(rootDir) ?: throw UnsupportedOperationException("authenticationManagerFn not initialized")

  /**
   * Promotional credit gifts. Exposed here so callers do not have to construct
   * [com.simiacryptus.cognotik.platform.h2.GiftedCreditsDB] (and therefore its [DatabaseFacet]) themselves.
   */
   val giftedCreditsDB: GiftedCreditsInterface get() = giftedCreditsDBFn?.invoke(rootDir) ?: throw UnsupportedOperationException("giftedCreditsDBFn not initialized")

  companion object {
    /*Default factories*/
    var dataStorageFactoryFn: ((File) -> StorageInterface)? = null
    var metadataDBFn: ((File) -> SessionMetadataInterface)? = null
    var usageDBFn: ((File) -> UsageInterface)? = null
    var userSettingsManagerFn: ((File) -> UserSettingsInterface)? = null
    var authenticationManagerFn: ((File) -> AuthenticationInterface)? = null
    var giftedCreditsDBFn: ((File) -> GiftedCreditsInterface)? = null
  }
}