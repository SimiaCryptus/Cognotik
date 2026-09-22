package com.simiacryptus.cognotik.platform

import java.io.File

interface IFileApplicationServices {
  val rootDir: File
  val dataStorageFactory: StorageInterface
  val metadataDB: MetadataStorageInterface
  val usageDB: UsageInterface
  val userSettingsManager: UserSettingsInterface

  val authenticationManager: AuthenticationInterface

  /**
   * Promotional credit gifts. Exposed here so callers do not have to construct
   * [com.simiacryptus.cognotik.platform.h2.GiftedCreditsDB] (and therefore its [DatabaseFacet]) themselves.
   */
  val giftedCreditsDB: GiftedCreditsInterface
}