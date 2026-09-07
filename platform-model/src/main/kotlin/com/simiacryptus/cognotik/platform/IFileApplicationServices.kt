package com.simiacryptus.cognotik.platform

import java.io.File

interface IFileApplicationServices {
  val rootDir: File
  open val dataStorageFactory: StorageInterface
  open val metadataDB: MetadataStorageInterface
  open val usageDB: UsageInterface
  open val userSettingsManager: UserSettingsInterface

  /**
   * Promotional credit gifts. Exposed here so callers do not have to construct
   * [com.simiacryptus.cognotik.platform.hsql.GiftedCreditsDB] (and therefore its [DatabaseFacet]) themselves.
   */
  open val giftedCreditsDB: GiftedCreditsInterface
}